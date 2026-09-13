"""Golden-vector tests for the Feishu bridge.

The signature is the part that fails in production and succeeds in a manual curl, because it is
specified by a vendor page rather than by a type system. Both the algorithm and the request body
are therefore pinned against a fixed key and a fixed timestamp.

Run: python -m unittest discover -s docker/observability/alert-bridge
"""

import unittest

import alert_bridge

FIXED_TIMESTAMP = 1700000000
FIXED_SECRET = "test-secret"
# Independent implementation of Feishu's published algorithm, kept as a literal so a change here
# has to be deliberate: base64(HMAC-SHA256(key="1700000000\ntest-secret", msg="")).
EXPECTED_SIGN = "mbm4Y4oluIPQ00qlBIhX8vAZ0EKv3nw0LuTb91jPL84="

GRAFANA_PAYLOAD = {
    "title": "LLM circuit breaker has been open for 5m",
    "state": "alerting",
    "alerts": [
        {
            "status": "firing",
            "labels": {
                "alertname": "LLM circuit breaker has been open for 5m",
                "severity": "critical",
                "service": "nexus-vibe",
            },
            "annotations": {
                "summary": "The breaker is open, so every AI review is failing fast.",
            },
            "values": {"A": {"value": 1}},
        }
    ],
}


class SignatureTest(unittest.TestCase):

    def test_matches_the_published_algorithm(self):
        self.assertEqual(EXPECTED_SIGN, alert_bridge.feishu_sign(FIXED_TIMESTAMP, FIXED_SECRET))


class BodyTest(unittest.TestCase):

    def test_signed_body_is_the_exact_feishu_request(self):
        body = alert_bridge.build_body(GRAFANA_PAYLOAD, FIXED_TIMESTAMP, FIXED_SECRET)
        self.assertEqual(
            {
                "timestamp": "1700000000",
                "sign": EXPECTED_SIGN,
                "msg_type": "text",
                "content": {
                    "text": "[Nexus-Vibe] alerting: LLM circuit breaker has been open for 5m\n"
                            "- LLM circuit breaker has been open for 5m (critical) current=1: "
                            "The breaker is open, so every AI review is failing fast."
                },
            },
            body,
        )

    def test_unsigned_body_omits_the_sign_fields(self):
        body = alert_bridge.build_body(GRAFANA_PAYLOAD, FIXED_TIMESTAMP, "")
        self.assertNotIn("sign", body)
        self.assertNotIn("timestamp", body)

    def test_resolved_state_survives_the_translation(self):
        payload = {"state": "resolved", "title": "all clear", "alerts": []}
        body = alert_bridge.build_body(payload, FIXED_TIMESTAMP, FIXED_SECRET)
        self.assertEqual("[Nexus-Vibe] resolved: all clear", body["content"]["text"])

    def test_empty_payload_does_not_raise(self):
        # Grafana can post a notification with no alerts (e.g. a recovered group); a crash here
        # would answer 502 and hide the fact that the rule actually recovered.
        body = alert_bridge.build_body({}, FIXED_TIMESTAMP, FIXED_SECRET)
        self.assertEqual("[Nexus-Vibe] alerting: 0 alert(s)", body["content"]["text"])


if __name__ == "__main__":
    unittest.main()
