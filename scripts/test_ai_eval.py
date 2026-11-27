"""Tests for the AI eval harness, runnable without a model.

The harness has two jobs: reject a malformed golden set before it becomes a
misleading baseline, and turn a run into a pass/fail gate. Both are tested
here with a stub HTTP server, so CI needs no provider credential.

Run: python -m unittest discover -s scripts -p "test_ai_eval.py"
"""

from __future__ import annotations

import contextlib
import io
import json
import sys
import tempfile
import threading
import unittest
import unittest.mock as mock
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import ai_eval


def review_case(**overrides):
    case = {
        "name": "case",
        "kind": "code",
        "title": "title",
        "content": "content",
        "scoreMin": 1,
        "scoreMax": 5,
        "severities": ["high"],
        "rationale": "why",
    }
    case.update(overrides)
    return case


def safety_case(**overrides):
    case = {"name": "case", "surface": "post", "text": "text", "expected": "safe"}
    case.update(overrides)
    return case


class CorpusValidationTest(unittest.TestCase):
    def test_committed_corpora_are_valid(self):
        reviews = ai_eval.load_cases(ai_eval.DEFAULT_REVIEW)
        safety = ai_eval.load_cases(ai_eval.DEFAULT_SAFETY)
        self.assertEqual(20, len(reviews))
        self.assertEqual(12, len(safety))
        for case in reviews:
            ai_eval.validate_review_case(case)
        for case in safety:
            ai_eval.validate_safety_case(case)

    def test_empty_corpus_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "empty.json"
            path.write_text("[]", encoding="utf-8")
            with self.assertRaises(SystemExit):
                ai_eval.load_cases(path)

    def test_duplicate_names_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "cases.json"
            path.write_text(json.dumps([review_case(), review_case()]), encoding="utf-8")
            with self.assertRaises(SystemExit):
                ai_eval.load_cases(path)

    def test_review_case_requires_every_field(self):
        for field in ("name", "kind", "title", "content", "rationale"):
            with self.subTest(field=field):
                with self.assertRaises(ValueError):
                    ai_eval.validate_review_case(review_case(**{field: "  "}))

    def test_review_kind_is_checked(self):
        with self.assertRaises(ValueError):
            ai_eval.validate_review_case(review_case(kind="essay"))

    def test_review_band_must_be_ordered_and_in_range(self):
        for low, high in ((-1, 3), (6, 5), (5, 11), ("1", 5)):
            with self.subTest(low=low, high=high):
                with self.assertRaises(ValueError):
                    ai_eval.validate_review_case(review_case(scoreMin=low, scoreMax=high))

    def test_review_severities_must_be_a_known_non_empty_subset(self):
        for severities in ([], ["urgent"], "high"):
            with self.subTest(severities=severities):
                with self.assertRaises(ValueError):
                    ai_eval.validate_review_case(review_case(severities=severities))

    def test_safety_surface_and_class_are_checked(self):
        with self.assertRaises(ValueError):
            ai_eval.validate_safety_case(safety_case(surface="profile"))
        with self.assertRaises(ValueError):
            ai_eval.validate_safety_case(safety_case(expected="maybe"))

    def test_safety_case_requires_text(self):
        with self.assertRaises(ValueError):
            ai_eval.validate_safety_case(safety_case(text=" "))


class PercentileTest(unittest.TestCase):
    def test_empty_sample_is_zero(self):
        self.assertEqual(0.0, ai_eval.percentile([], 0.95))

    def test_single_sample(self):
        self.assertEqual(12.3, ai_eval.percentile([12.345], 0.95))

    def test_nearest_rank(self):
        values = [10.0, 20.0, 30.0, 40.0, 50.0]
        self.assertEqual(30.0, ai_eval.percentile(values, 0.50))
        self.assertEqual(50.0, ai_eval.percentile(values, 0.95))
        self.assertEqual(10.0, ai_eval.percentile(values, 0.0))
        self.assertEqual(50.0, ai_eval.percentile(values, 1.0))

    def test_does_not_mutate_input(self):
        values = [3.0, 1.0, 2.0]
        ai_eval.percentile(values, 0.5)
        self.assertEqual([3.0, 1.0, 2.0], values)


class SummarizeTest(unittest.TestCase):
    def test_review_summary_separates_invalid_from_disagreement(self):
        results = [
            {"status": 200, "scoreOk": True, "severityOk": True, "repaired": False,
             "latencyMs": 100.0, "promptTokens": 10, "completionTokens": 2},
            {"status": 200, "scoreOk": False, "severityOk": True, "repaired": True,
             "latencyMs": 300.0, "promptTokens": 20, "completionTokens": 4},
            {"status": 503, "latencyMs": 50.0, "error": "model_unavailable"},
        ]
        summary = ai_eval.summarize_review(results)
        self.assertEqual(3, summary["cases"])
        self.assertEqual(1, summary["invalid"])
        self.assertEqual(0.3333, summary["invalidRate"])
        self.assertEqual(0.3333, summary["scoreAgreement"])
        self.assertEqual(0.6667, summary["severityAgreement"])
        self.assertEqual(0.5, summary["repairRate"])
        self.assertEqual(100.0, summary["latencyP50Ms"])
        self.assertEqual(300.0, summary["latencyP95Ms"])
        self.assertEqual(30, summary["promptTokens"])
        self.assertEqual(6, summary["completionTokens"])

    def test_review_summary_counts_evidence(self):
        results = [
            {"status": 200, "scoreOk": True, "severityOk": True, "repaired": False,
             "latencyMs": 1.0, "evidenceRejected": 2, "evidenceCount": 1},
            {"status": 200, "scoreOk": True, "severityOk": True, "repaired": False,
             "latencyMs": 1.0, "evidenceRejected": 0, "evidenceCount": 0},
        ]
        summary = ai_eval.summarize_review(results)
        self.assertEqual(2, summary["evidenceRejected"])
        self.assertEqual(1, summary["validWithEvidence"])

    def test_safety_summary(self):
        results = [
            {"status": 200, "match": True, "latencyMs": 80.0},
            {"status": 200, "match": False, "latencyMs": 120.0},
            {"status": 500, "latencyMs": 5.0},
        ]
        summary = ai_eval.summarize_safety(results)
        self.assertEqual(3, summary["cases"])
        self.assertEqual(1, summary["invalid"])
        self.assertEqual(0.3333, summary["classificationAgreement"])
        self.assertEqual(80.0, summary["latencyP50Ms"])

    def test_empty_results_do_not_divide_by_zero(self):
        self.assertEqual(0.0, ai_eval.summarize_review([])["invalidRate"])
        self.assertEqual(0.0, ai_eval.summarize_safety([])["classificationAgreement"])


class CompareTest(unittest.TestCase):
    BASELINE = {
        "review": {"scoreAgreement": 0.8, "severityAgreement": 0.9, "invalidRate": 0.0},
        "safety": {"classificationAgreement": 0.75, "invalidRate": 0.0},
    }

    def test_identical_run_passes(self):
        self.assertEqual([], ai_eval.compare(self.BASELINE, self.BASELINE, 0.05))

    def test_agreement_drop_beyond_tolerance_fails(self):
        current = {
            "review": {"scoreAgreement": 0.70, "severityAgreement": 0.9, "invalidRate": 0.0},
            "safety": {"classificationAgreement": 0.75, "invalidRate": 0.0},
        }
        failures = ai_eval.compare(current, self.BASELINE, 0.05)
        self.assertEqual(1, len(failures))
        self.assertIn("review.scoreAgreement", failures[0])

    def test_agreement_drop_inside_tolerance_passes(self):
        current = {
            "review": {"scoreAgreement": 0.76, "severityAgreement": 0.9, "invalidRate": 0.0},
            "safety": {"classificationAgreement": 0.75, "invalidRate": 0.0},
        }
        self.assertEqual([], ai_eval.compare(current, self.BASELINE, 0.05))

    def test_invalid_rate_rise_fails(self):
        current = {
            "review": {"scoreAgreement": 0.8, "severityAgreement": 0.9, "invalidRate": 0.10},
            "safety": {"classificationAgreement": 0.75, "invalidRate": 0.0},
        }
        failures = ai_eval.compare(current, self.BASELINE, 0.05)
        self.assertEqual(1, len(failures))
        self.assertIn("review.invalidRate", failures[0])

    def test_missing_section_is_skipped(self):
        current = {"review": {"scoreAgreement": 0.8, "severityAgreement": 0.9,
                              "invalidRate": 0.0}}
        self.assertEqual([], ai_eval.compare(current, self.BASELINE, 0.05))


class FloorTest(unittest.TestCase):
    REPORT = {
        "review": {"scoreAgreement": 0.85, "severityAgreement": 0.40,
                   "invalidRate": 0.0},
        "safety": {"classificationAgreement": 0.92, "invalidRate": 0.0},
    }

    def test_floor_passes_when_every_metric_is_at_or_above_it(self):
        self.assertEqual([], ai_eval.floor_failures(self.REPORT, 0.40))

    def test_floor_fails_each_metric_below_it(self):
        failures = ai_eval.floor_failures(self.REPORT, 0.90)
        self.assertEqual(2, len(failures))
        self.assertTrue(any("review.severityAgreement" in f for f in failures))
        self.assertTrue(any("review.scoreAgreement" in f for f in failures))

    def test_floor_does_not_apply_to_invalid_rate(self):
        report = {"review": {"scoreAgreement": 1.0, "severityAgreement": 1.0,
                             "invalidRate": 0.5}}
        self.assertEqual([], ai_eval.floor_failures(report, 0.9))


class StubHandler(BaseHTTPRequestHandler):
    review_score = 3
    safety_class = None
    status = 200
    seen: list[dict] = []
    expected_by_text = {
        case["text"]: case["expected"] for case in ai_eval.load_cases(ai_eval.DEFAULT_SAFETY)
    }

    def do_POST(self):  # noqa: N802 - http.server API
        length = int(self.headers.get("Content-Length", 0))
        payload = json.loads(self.rfile.read(length).decode("utf-8"))
        type(self).seen.append({"path": self.path, "payload": payload,
                                "key": self.headers.get("X-AI-Service-Key")})
        if self.path.endswith("/v1/reviews"):
            body = {"score": type(self).review_score, "severity": "medium",
                    "model": "stub", "repaired": False,
                    "usage": {"promptTokens": 7, "completionTokens": 3}}
        else:
            classification = (type(self).safety_class
                              or type(self).expected_by_text.get(payload["text"], "safe"))
            body = {"classification": classification, "model": "stub"}
        raw = json.dumps(body).encode("utf-8")
        self.send_response(type(self).status)
        self.send_header("Content-Type", "application/json")
        self.send_header("X-Prompt-Version", "abc123def456")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, *args):
        pass


@contextlib.contextmanager
def stub_service(**attributes):
    handler = type("ConfiguredStub", (StubHandler,), dict(attributes, seen=[]))
    server = HTTPServer(("127.0.0.1", 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}", handler
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


def run_main(argv: list[str]) -> int:
    with mock.patch.object(sys, "argv", ["ai_eval.py", *argv]):
        return ai_eval.main()


class EndToEndTest(unittest.TestCase):
    def test_run_writes_baseline_and_records_prompt_version(self):
        with stub_service() as (base_url, handler), tempfile.TemporaryDirectory() as temp:
            results = Path(temp) / "results.json"
            baseline = Path(temp) / "baseline.json"
            code = run_main(["--base-url", base_url, "--key", "secret",
                             "--results", str(results), "--write-baseline", str(baseline)])
            self.assertEqual(0, code)
            report = json.loads(baseline.read_text(encoding="utf-8"))
            self.assertEqual("abc123def456", report["service"]["promptVersion"])
            self.assertEqual("stub", report["service"]["model"])
            self.assertEqual(20, report["review"]["cases"])
            self.assertEqual(0.0, report["review"]["invalidRate"])
            self.assertEqual(12, report["safety"]["cases"])
            self.assertEqual(1.0, report["safety"]["classificationAgreement"])
            self.assertEqual(32, len(handler.seen))
            self.assertEqual({"secret"}, {call["key"] for call in handler.seen})
            self.assertEqual({"code", "prompt"},
                             {call["payload"]["kind"] for call in handler.seen
                              if call["path"].endswith("/v1/reviews")})

    def test_regression_against_baseline_exits_non_zero(self):
        with stub_service() as (base_url, _), tempfile.TemporaryDirectory() as temp:
            baseline = Path(temp) / "baseline.json"
            baseline.write_text(json.dumps({
                "review": {"scoreAgreement": 1.0, "severityAgreement": 1.0,
                           "invalidRate": 0.0},
                "safety": {"classificationAgreement": 1.0, "invalidRate": 0.0},
            }), encoding="utf-8")
            with contextlib.redirect_stderr(io.StringIO()) as stderr:
                code = run_main(["--base-url", base_url,
                                 "--results", str(Path(temp) / "results.json"),
                                 "--baseline", str(baseline)])
            self.assertEqual(1, code)
            self.assertIn("review.scoreAgreement", stderr.getvalue())

    def test_http_errors_are_counted_as_invalid(self):
        with stub_service(status=503) as (base_url, _), tempfile.TemporaryDirectory() as temp:
            with contextlib.redirect_stdout(io.StringIO()):
                code = run_main(["--base-url", base_url, "--only", "review",
                                 "--results", str(Path(temp) / "results.json")])
            self.assertEqual(0, code)
            report = json.loads((Path(temp) / "results.json").read_text(encoding="utf-8"))
            self.assertEqual(1.0, report["review"]["invalidRate"])
            self.assertEqual(20, report["review"]["invalid"])

    def test_fail_below_floor_exits_non_zero_without_a_baseline(self):
        with stub_service() as (base_url, _), tempfile.TemporaryDirectory() as temp:
            with contextlib.redirect_stdout(io.StringIO()), \
                    contextlib.redirect_stderr(io.StringIO()) as stderr:
                code = run_main(["--base-url", base_url, "--only", "review",
                                 "--fail-below", "0.9",
                                 "--results", str(Path(temp) / "results.json")])
            self.assertEqual(1, code)
            self.assertIn("review.scoreAgreement", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
