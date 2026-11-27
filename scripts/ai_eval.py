#!/usr/bin/env python3
"""Measure review and safety quality against a live AI service.

The committed corpora under ``benchmark/eval/`` are the golden set. This script
posts each case, compares the verdict to the expected band or class, and reports
agreement, invalid rate, repair rate, latency and token use. ``--baseline`` turns
the run into a regression gate.

CI validates the corpora and the metric maths (``scripts/test_ai_eval.py``) but
does not call a model: pull requests have no provider credential. The baseline
run is a documented local or nightly act.
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_REVIEW = ROOT / "benchmark" / "eval" / "review-cases.json"
DEFAULT_SAFETY = ROOT / "benchmark" / "eval" / "safety-cases.json"

KINDS = {"code", "prompt"}
SEVERITIES = {"low", "medium", "high", "critical"}
SURFACES = {"post", "comment"}
CLASSES = {"safe", "prompt_injection", "harmful", "spam"}
PROMPT_VERSION_HEADER = "X-Prompt-Version"


def load_cases(path: Path) -> list[dict]:
    cases = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(cases, list) or not cases:
        raise SystemExit(f"{path}: expected a non-empty JSON array")
    names = set()
    for case in cases:
        if case.get("name") in names:
            raise SystemExit(f"{path}: duplicate case name {case.get('name')!r}")
        names.add(case.get("name"))
    return cases


def validate_review_case(case: dict) -> None:
    for field in ("name", "kind", "title", "content", "rationale"):
        if not isinstance(case.get(field), str) or not case[field].strip():
            raise ValueError(f"review case {case.get('name')!r}: missing {field}")
    if case["kind"] not in KINDS:
        raise ValueError(f"review case {case['name']}: kind must be one of {sorted(KINDS)}")
    low, high = case.get("scoreMin"), case.get("scoreMax")
    if not isinstance(low, int) or not isinstance(high, int) or not 0 <= low <= high <= 10:
        raise ValueError(f"review case {case['name']}: need 0 <= scoreMin <= scoreMax <= 10")
    severities = case.get("severities")
    if not isinstance(severities, list) or not severities or not set(severities) <= SEVERITIES:
        raise ValueError(f"review case {case['name']}: severities must be a non-empty subset")


def validate_safety_case(case: dict) -> None:
    for field in ("name", "surface", "text"):
        if not isinstance(case.get(field), str) or not case[field].strip():
            raise ValueError(f"safety case {case.get('name')!r}: missing {field}")
    if case["surface"] not in SURFACES:
        raise ValueError(f"safety case {case['name']}: surface must be one of {sorted(SURFACES)}")
    if case.get("expected") not in CLASSES:
        raise ValueError(f"safety case {case['name']}: expected must be one of {sorted(CLASSES)}")


def percentile(values: list[float], fraction: float) -> float:
    """Nearest-rank percentile; returns 0 for an empty sample."""
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, int(round(fraction * (len(ordered) - 1)))))
    return round(ordered[index], 1)


def summarize_review(results: list[dict]) -> dict:
    total = len(results)
    ok = [result for result in results if result["status"] == 200]
    return {
        "cases": total,
        "invalid": total - len(ok),
        "invalidRate": round((total - len(ok)) / total, 4) if total else 0.0,
        "scoreAgreement": round(sum(1 for r in ok if r["scoreOk"]) / total, 4) if total else 0.0,
        "severityAgreement": round(sum(1 for r in ok if r["severityOk"]) / total, 4) if total else 0.0,
        "repairRate": round(sum(1 for r in ok if r["repaired"]) / len(ok), 4) if ok else 0.0,
        "latencyP50Ms": percentile([r["latencyMs"] for r in ok], 0.50),
        "latencyP95Ms": percentile([r["latencyMs"] for r in ok], 0.95),
        "promptTokens": sum(r.get("promptTokens", 0) for r in ok),
        "completionTokens": sum(r.get("completionTokens", 0) for r in ok),
        "evidenceRejected": sum(r.get("evidenceRejected", 0) for r in ok),
        "validWithEvidence": sum(1 for r in ok if r.get("evidenceCount", 0) > 0),
    }


def summarize_safety(results: list[dict]) -> dict:
    total = len(results)
    ok = [result for result in results if result["status"] == 200]
    return {
        "cases": total,
        "invalid": total - len(ok),
        "invalidRate": round((total - len(ok)) / total, 4) if total else 0.0,
        "classificationAgreement": round(
            sum(1 for r in ok if r["match"]) / total, 4) if total else 0.0,
        "latencyP50Ms": percentile([r["latencyMs"] for r in ok], 0.50),
        "latencyP95Ms": percentile([r["latencyMs"] for r in ok], 0.95),
    }


def compare(current: dict, baseline: dict, tolerance: float) -> list[str]:
    """Higher is better for agreement, lower for invalidRate."""
    failures = []
    for section in ("review", "safety"):
        if section not in current or section not in baseline:
            continue
        for metric in ("scoreAgreement", "severityAgreement", "classificationAgreement"):
            if metric in current[section] and metric in baseline[section]:
                floor = baseline[section][metric] - tolerance
                if current[section][metric] < floor:
                    failures.append(
                        f"{section}.{metric}: {current[section][metric]:.4f} < "
                        f"baseline {baseline[section][metric]:.4f} - {tolerance}")
        if "invalidRate" in current[section] and "invalidRate" in baseline[section]:
            ceiling = baseline[section]["invalidRate"] + tolerance
            if current[section]["invalidRate"] > ceiling:
                failures.append(
                    f"{section}.invalidRate: {current[section]['invalidRate']:.4f} > "
                    f"baseline {baseline[section]['invalidRate']:.4f} + {tolerance}")
    return failures


AGREEMENT_METRICS = ("scoreAgreement", "severityAgreement", "classificationAgreement")


def floor_failures(report: dict, minimum: float) -> list[str]:
    """Absolute floor for agreement, independent of any baseline."""
    failures = []
    for section in ("review", "safety"):
        if section not in report:
            continue
        for metric in AGREEMENT_METRICS:
            if metric in report[section] and report[section][metric] < minimum:
                failures.append(
                    f"{section}.{metric}: {report[section][metric]:.4f} < floor {minimum:.4f}")
    return failures


def post_json(url: str, payload: dict, key: str, timeout: float):
    headers = {"Content-Type": "application/json"}
    if key:
        headers["X-AI-Service-Key"] = key
    request = urllib.request.Request(
        url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST")
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = json.loads(response.read().decode("utf-8"))
            latency = (time.perf_counter() - started) * 1000
            return response.status, body, dict(response.headers), latency
    except urllib.error.HTTPError as error:
        latency = (time.perf_counter() - started) * 1000
        try:
            body = json.loads(error.read().decode("utf-8"))
        except Exception:
            body = {"code": f"http_{error.code}"}
        return error.code, body, dict(error.headers), latency
    except Exception as error:
        latency = (time.perf_counter() - started) * 1000
        return 0, {"code": "transport", "message": str(error)}, {}, latency


def run_review(base_url: str, cases: list[dict], key: str, timeout: float,
               budget_ms: int) -> list[dict]:
    results = []
    for index, case in enumerate(cases, start=1):
        payload = {
            "requestId": f"eval-review-{index}",
            "traceId": f"eval-review-{index}",
            "postId": 900000 + index,
            "kind": case["kind"],
            "title": case["title"],
            "content": case["content"],
            "snippets": [],
            "budget": {"maxContextTokens": 12000, "timeoutMs": budget_ms},
        }
        status, body, headers, latency = post_json(base_url + "/v1/reviews", payload, key, timeout)
        result = {
            "name": case["name"],
            "status": status,
            "latencyMs": round(latency, 1),
            "promptVersion": headers.get(PROMPT_VERSION_HEADER, ""),
        }
        if status == 200:
            score = body.get("score")
            severity = body.get("severity")
            usage = body.get("usage") or {}
            result.update({
                "score": score,
                "severity": severity,
                "model": body.get("model"),
                "repaired": bool(body.get("repaired")),
                "scoreOk": isinstance(score, int) and case["scoreMin"] <= score <= case["scoreMax"],
                "severityOk": severity in case["severities"],
                "promptTokens": usage.get("promptTokens", 0),
                "completionTokens": usage.get("completionTokens", 0),
                "evidenceRejected": body.get("evidenceRejected", 0),
                "evidenceCount": len(body.get("evidence") or []),
            })
        else:
            result["error"] = body.get("code", f"http_{status}")
        results.append(result)
        print(f"[review] {case['name']}: {status} {result.get('score', '-')}/"
              f"{result.get('severity', '-')} {result['latencyMs']:.0f}ms")
    return results


def run_safety(base_url: str, cases: list[dict], key: str, timeout: float) -> list[dict]:
    results = []
    for index, case in enumerate(cases, start=1):
        payload = {
            "requestId": f"eval-safety-{index}",
            "traceId": f"eval-safety-{index}",
            "text": case["text"],
            "surface": case["surface"],
        }
        status, body, headers, latency = post_json(base_url + "/v1/safety", payload, key, timeout)
        result = {
            "name": case["name"],
            "status": status,
            "latencyMs": round(latency, 1),
            "promptVersion": headers.get(PROMPT_VERSION_HEADER, ""),
        }
        if status == 200:
            result.update({
                "classification": body.get("classification"),
                "expected": case["expected"],
                "match": body.get("classification") == case["expected"],
                "model": body.get("model"),
            })
        else:
            result["error"] = body.get("code", f"http_{status}")
        results.append(result)
        print(f"[safety] {case['name']}: {status} {result.get('classification', '-')} "
              f"(expected {case['expected']}) {result['latencyMs']:.0f}ms")
    return results


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:18001")
    parser.add_argument("--key", default="")
    parser.add_argument("--review-cases", type=Path, default=DEFAULT_REVIEW)
    parser.add_argument("--safety-cases", type=Path, default=DEFAULT_SAFETY)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--write-baseline", type=Path)
    parser.add_argument("--fail-below", type=float, default=None,
                        help="fail when any agreement metric is below this ratio")
    parser.add_argument("--results", type=Path, default=ROOT / "target" / "ai-eval" / "results.json")
    parser.add_argument("--tolerance", type=float, default=0.05)
    parser.add_argument("--timeout", type=float, default=180.0)
    parser.add_argument("--budget-ms", type=int, default=115_000)
    parser.add_argument("--only", choices=["all", "review", "safety"], default="all")
    args = parser.parse_args()

    review_cases = load_cases(args.review_cases)
    safety_cases = load_cases(args.safety_cases)
    for case in review_cases:
        validate_review_case(case)
    for case in safety_cases:
        validate_safety_case(case)

    report = {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "baseUrl": args.base_url,
        "service": {},
    }
    if args.only in ("all", "review"):
        results = run_review(args.base_url, review_cases, args.key, args.timeout, args.budget_ms)
        report["review"] = summarize_review(results)
        report["review"]["results"] = results
        versions = {r["promptVersion"] for r in results if r.get("promptVersion")}
        models = {r["model"] for r in results if r.get("model")}
        report["service"]["promptVersion"] = next(iter(versions), "")
        report["service"]["model"] = next(iter(models), "")
        if len(versions) > 1:
            print(f"warning: prompt version changed mid-run: {sorted(versions)}", file=sys.stderr)
    if args.only in ("all", "safety"):
        results = run_safety(args.base_url, safety_cases, args.key, args.timeout)
        report["safety"] = summarize_safety(results)
        report["safety"]["results"] = results

    args.results.parent.mkdir(parents=True, exist_ok=True)
    args.results.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    summary = {
        key: ({k: v for k, v in value.items() if k != "results"}
              if isinstance(value, dict) else value)
        for key, value in report.items()
    }
    print(json.dumps(summary, indent=2))
    print(f"\nwrote {args.results}")

    if args.write_baseline:
        args.write_baseline.parent.mkdir(parents=True, exist_ok=True)
        args.write_baseline.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(f"wrote baseline {args.write_baseline}")
        return 0

    failures = []
    if args.fail_below is not None:
        failures.extend(floor_failures(report, args.fail_below))
    if args.baseline:
        baseline = json.loads(args.baseline.read_text(encoding="utf-8"))
        failures.extend(compare(report, baseline, args.tolerance))
    if failures:
        print("\nAI eval failed:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    if args.baseline:
        print("\nAI eval is within the recorded baseline")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
