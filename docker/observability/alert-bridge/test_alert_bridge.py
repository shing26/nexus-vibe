"""Golden-vector tests for the Feishu bridge.

The signature is the part that fails in production and succeeds in a manual curl, because it is
specified by a vendor page rather than by a type system. Both the algorithm and the request body
are therefore pinned against a fixed key and a fixed timestamp.

The rest of the file covers the two ways this bridge can lose an alert without saying so: an empty
FEISHU_ALERT_WEBHOOK, and Feishu answering HTTP 200 with a refusal in the body.

Run: python -m pytest docker/observability/alert-bridge -q
"""

import json
import unittest

import alert_bridge

FIXED_TIMESTAMP = 1700000000
FIXED_SECRET = "test-secret"
# Independent implementation of Feishu's published algorithm, kept as a literal so a change here
# has to be deliberate: base64(HMAC-SHA256(key="1700000000\ntest-secret", msg="")).
EXPECTED_SIGN = "mbm4Y4oluIPQ00qlBIhX8vAZ0EKv3nw0LuTb91jPL84="
# A hook URL is a bearer secret, so it doubles as the redaction fixture: nothing below may echo
# HOOK_TOKEN back in an error message.
HOOK_TOKEN = "0f9c1e77-4f6a-4c48-b8f1-2d3a5b7c9e11"
HOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/" + HOOK_TOKEN

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


class ConfigurationTest(unittest.TestCase):

    def test_empty_webhook_refuses_startup_and_names_the_consequence(self):
        # The exit message is the only artifact left in `docker compose logs alert-bridge` once the
        # container is crash-looping, so it has to say what is blind, not just what is missing.
        error = alert_bridge.configuration_error("")
        self.assertIsNotNone(error)
        self.assertIn("FEISHU_ALERT_WEBHOOK is not set", error)
        self.assertIn("nowhere to go", error)
        self.assertIn("--profile monitoring", error)

    def test_whitespace_only_webhook_counts_as_unset(self):
        # .env files produce this shape, and it is exactly as useless as an empty one.
        self.assertIsNotNone(alert_bridge.configuration_error("   "))

    def test_a_configured_bridge_reports_no_startup_error(self):
        self.assertIsNone(alert_bridge.configuration_error(HOOK_URL))

    def test_an_unconfigured_bridge_still_refuses_at_delivery_time(self):
        # do_POST calls deliver(). If that path ever learned to return quietly on an empty target,
        # the stack would be silent again even though main() checks.
        with self.assertRaises(RuntimeError) as caught:
            alert_bridge.deliver(GRAFANA_PAYLOAD, url="", sender=fake_sender(b"{}"))
        self.assertIn("FEISHU_ALERT_WEBHOOK is not set", str(caught.exception))


class DeliveryTest(unittest.TestCase):

    def test_a_transport_failure_is_reported_against_a_redacted_target(self):
        def refused(body, url=None):
            raise OSError(111, "Connection refused")

        with self.assertRaises(RuntimeError) as caught:
            alert_bridge.deliver(GRAFANA_PAYLOAD, url=HOOK_URL, sender=refused)
        message = str(caught.exception)
        self.assertIn("Connection refused", message)
        self.assertIn("bot/v2/hook/***", message)
        self.assertNotIn(HOOK_TOKEN, message)

    def test_feishu_http_200_with_a_refusal_code_is_not_delivered(self):
        # The vendor's real shape for "signature did not match": a 200 and a code. A bridge that
        # only trusts the status line reports every alert as delivered while the group stays empty.
        reply = json.dumps({"code": 19021, "msg": "sign match fail"}).encode("utf-8")
        with self.assertRaises(RuntimeError) as caught:
            alert_bridge.deliver(GRAFANA_PAYLOAD, url=HOOK_URL, secret=FIXED_SECRET,
                                 timestamp=FIXED_TIMESTAMP, sender=fake_sender(reply))
        self.assertIn("19021", str(caught.exception))
        self.assertIn("sign match fail", str(caught.exception))

    def test_a_success_reply_passes_through_untouched(self):
        reply = json.dumps({"StatusCode": 0, "StatusMessage": "success"}).encode("utf-8")
        self.assertEqual(
            reply,
            alert_bridge.deliver(GRAFANA_PAYLOAD, url=HOOK_URL, secret=FIXED_SECRET,
                                 timestamp=FIXED_TIMESTAMP, sender=fake_sender(reply)),
        )

    def test_the_signed_body_is_what_reaches_the_wire(self):
        seen = {}

        def capture(body, url=None):
            seen["body"] = body
            seen["url"] = url
            return b'{"code":0}'

        alert_bridge.deliver(GRAFANA_PAYLOAD, url=HOOK_URL, secret=FIXED_SECRET,
                             timestamp=FIXED_TIMESTAMP, sender=capture)
        self.assertEqual(HOOK_URL, seen["url"])
        self.assertEqual(EXPECTED_SIGN, seen["body"]["sign"])


class VendorReplyTest(unittest.TestCase):

    def test_zero_in_either_wire_format_is_success(self):
        self.assertIsNone(alert_bridge.feishu_error(b'{"code":0}'))
        self.assertIsNone(alert_bridge.feishu_error(b'{"code":"0"}'))
        self.assertIsNone(alert_bridge.feishu_error(b'{"StatusCode":0}'))
        self.assertIsNone(alert_bridge.feishu_error(b'{"msg":"success"}'))

    def test_a_body_this_bridge_cannot_parse_is_not_called_a_success(self):
        # A proxy error page says nothing either way; deliver() must not invent a failure, and the
        # comment in feishu_error records why that is safe here (urlopen already handles status).
        self.assertIsNone(alert_bridge.feishu_error(b"<html>502 Bad Gateway</html>"))
        self.assertIsNone(alert_bridge.feishu_error(b""))
        self.assertIsNone(alert_bridge.feishu_error(b"[]"))


class DigestTest(unittest.TestCase):

    def test_the_rule_name_reaches_the_log_for_the_drill_to_grep(self):
        digest = alert_bridge.alert_titles(GRAFANA_PAYLOAD)
        self.assertIn("LLM circuit breaker has been open for 5m", digest)

    def test_a_payload_cannot_forge_extra_log_lines(self):
        payload = {
            "title": "Prometheus cannot scrape the app",
            "alerts": [{"labels": {"alertname": "X\n[alert-bridge] forged line"}}],
        }
        digest = alert_bridge.alert_titles(payload)
        # One notification must stay one log line, or a crafted alertname could impersonate the
        # delivery of a rule that never fired.
        self.assertEqual(1, len(digest.splitlines()))
        self.assertIn("Prometheus cannot scrape the app", digest)


def fake_sender(reply):
    """A forward that always answers `reply`, for asserting on the bridge's reading of it."""

    def send_reply(body, url=None):
        return reply

    return send_reply


if __name__ == "__main__":
    unittest.main()
