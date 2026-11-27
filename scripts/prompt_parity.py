#!/usr/bin/env python3
"""Measure prompt-text drift between direct mode and the AI service.

Both sides render the same fixtures from ``benchmark/parity/review-fixtures.json``
into ``target/prompt-parity/{direct,service}/`` with per-request nonces replaced
by ``NONCE``. This script diffs the two corpora and compares the result against
the committed baseline, so a prompt change that is not mirrored on the other
side fails instead of silently widening the gap.

Render both corpora first, then compare (the CI job does the same three steps):

    mvn -B -q -Djacoco.skip=true -Dtest=PromptTextCorpusTest test
    mvn -B -q -f ai-service/pom.xml -Dtest=PromptTextCorpusTest test
    python scripts/prompt_parity.py

``--write-baseline`` records the current measurement. It is a deliberate,
reviewable act: the baseline file is the record of how far apart the two
prompt builders were when the drift was accepted.
"""

from __future__ import annotations

import argparse
import difflib
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_BASELINE = ROOT / "benchmark" / "parity" / "prompt-drift-baseline.json"

TOKEN = re.compile(r"\S+")


def read_corpus(directory: Path) -> dict[str, str]:
    if not directory.is_dir():
        raise SystemExit(f"corpus not found: {directory} (render it first)")
    corpus = {path.stem: path.read_text(encoding="utf-8") for path in sorted(directory.glob("*.txt"))}
    if not corpus:
        raise SystemExit(f"corpus is empty: {directory}")
    return corpus


def line_ratio(left: str, right: str) -> float:
    matcher = difflib.SequenceMatcher(None, left.splitlines(), right.splitlines(), autojunk=False)
    return matcher.ratio()


def token_jaccard(left: str, right: str) -> float:
    left_tokens = Counter(TOKEN.findall(left.lower()))
    right_tokens = Counter(TOKEN.findall(right.lower()))
    union = left_tokens | right_tokens
    if not union:
        return 1.0
    return sum((left_tokens & right_tokens).values()) / sum(union.values())


def changed_lines(left: str, right: str) -> int:
    diff = difflib.unified_diff(left.splitlines(), right.splitlines(), lineterm="", n=0)
    return sum(1 for line in diff if line.startswith(("+", "-")) and not line.startswith(("+++", "---")))


def measure(direct: dict[str, str], service: dict[str, str]) -> dict[str, dict[str, float]]:
    if set(direct) != set(service):
        only_direct = sorted(set(direct) - set(service))
        only_service = sorted(set(service) - set(direct))
        raise SystemExit(
            f"corpora cover different fixtures; direct-only={only_direct} service-only={only_service}"
        )
    results = {}
    for name in sorted(direct):
        results[name] = {
            "lineRatio": round(line_ratio(direct[name], service[name]), 4),
            "tokenJaccard": round(token_jaccard(direct[name], service[name]), 4),
            "changedLines": changed_lines(direct[name], service[name]),
            "directLines": len(direct[name].splitlines()),
            "serviceLines": len(service[name].splitlines()),
        }
    return results


def print_table(results: dict[str, dict[str, float]]) -> None:
    print("| fixture | lines (direct/service) | line similarity | token Jaccard | changed lines |")
    print("|---|---|---|---|---|")
    for name, result in results.items():
        print(
            f"| {name} | {result['directLines']}/{result['serviceLines']} "
            f"| {result['lineRatio']:.4f} ({result['lineRatio'] * 100:.1f}%) "
            f"| {result['tokenJaccard']:.4f} | {result['changedLines']} |"
        )
    mean = sum(result["lineRatio"] for result in results.values()) / len(results)
    print(f"\nmean line ratio: {mean:.4f}")


def print_small_diffs(direct: dict[str, str], service: dict[str, str], results: dict[str, dict[str, float]]) -> None:
    for name, result in results.items():
        if result["changedLines"] > 40:
            continue
        diff = difflib.unified_diff(
            direct[name].splitlines(), service[name].splitlines(),
            fromfile=f"{name}/direct", tofile=f"{name}/service", lineterm="", n=1,
        )
        lines = list(diff)
        if lines:
            print(f"\n--- {name} ---")
            print("\n".join(lines))


def gate(results: dict[str, dict[str, float]], baseline: dict[str, dict[str, float]], tolerance: float) -> list[str]:
    failures = []
    if set(results) != set(baseline):
        failures.append(
            f"fixture set changed: only-in-baseline={sorted(set(baseline) - set(results))} "
            f"only-in-current={sorted(set(results) - set(baseline))}"
        )
        return failures
    for name, current in results.items():
        recorded = baseline[name]
        for metric in ("lineRatio", "tokenJaccard"):
            floor = recorded[metric] - tolerance
            if current[metric] < floor:
                failures.append(
                    f"{name}: {metric} {current[metric]:.4f} < baseline {recorded[metric]:.4f} - {tolerance}"
                )
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--direct", type=Path, default=ROOT / "target" / "prompt-parity" / "direct")
    parser.add_argument("--service", type=Path, default=ROOT / "ai-service" / "target" / "prompt-parity" / "service")
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--results", type=Path, default=ROOT / "target" / "prompt-parity" / "prompt-drift.json")
    parser.add_argument("--tolerance", type=float, default=0.005,
                        help="allowed drop below the baseline before the check fails")
    parser.add_argument("--write-baseline", action="store_true",
                        help="record the current measurement as the baseline")
    args = parser.parse_args()

    direct = read_corpus(args.direct)
    service = read_corpus(args.service)
    results = measure(direct, service)
    print_table(results)
    print_small_diffs(direct, service, results)

    args.results.parent.mkdir(parents=True, exist_ok=True)
    args.results.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(f"\nwrote {args.results}")

    if args.write_baseline:
        args.baseline.parent.mkdir(parents=True, exist_ok=True)
        args.baseline.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
        print(f"wrote baseline {args.baseline}")
        return 0

    if not args.baseline.exists():
        print(f"no baseline at {args.baseline}; run with --write-baseline", file=sys.stderr)
        return 2
    baseline = json.loads(args.baseline.read_text(encoding="utf-8"))
    failures = gate(results, baseline, args.tolerance)
    if failures:
        print("\nprompt drift regressed:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print("\nprompt drift is within the recorded baseline")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
