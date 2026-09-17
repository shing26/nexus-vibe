"""Golden-vector tests for the Feishu bridge.

The signature is the part that fails in production and succeeds in a manual curl, because it is
specified by a vendor page rather than by a type system. Both the algorithm and the request body
are therefore pinned against a fixed key and a fixed timestamp.

The rest of the file covers the ways this bridge can lose an alert without saying so: an empty
FEISHU_ALERT_WEBHOOK, Feishu answering HTTP 200 with a refusal in the body, and -- learned the hard
way on 2026-09-17 -- a payload whose shape the translation assumed instead of recording. The last
one has its evidence in docs/research/alert-bridge-values-shape-2026-09.md.

The same date produced a second way to lose an alert: a CDN edge that accepts TCP and blackholes
TLS. That one is AddressFallbackTest, with its evidence in
docs/research/alert-bridge-address-fallback-2026-09.md.

Run: python -m pytest docker/observability/alert-bridge -q
"""

import contextlib
import http.server
import io
import json
import socket
import ssl
import threading
import unittest
import unittest.mock as mock
import urllib.error
import urllib.request

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
            # Grafana's documented webhook shape: plain numbers keyed by refId, not the
            # {"A": {"value": 1}} nesting this fixture used to carry. That nesting is a shape some
            # other senders use, and it is kept working by ValueShapeTest below -- but it is not
            # what Grafana sends, and pretending it was cost an afternoon of undelivered alerts.
            "values": {"A": 1},
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
        # The 2026-09-17 outage in one payload. The old version of this test carried `alerts: []`,
        # so it asserted the state prefix and never touched the line that broke: a resolved
        # notification is a real alert whose value is non-zero (`up == 1`), and the translation
        # raised AttributeError on it before send() was reached. A test named for the failure that
        # cannot reach it is worse than no test, because it reads as coverage.
        payload = {
            "state": "ok",
            "title": "[RESOLVED] Prometheus has not been able to scrape the app for 2m",
            "alerts": [
                {
                    "status": "resolved",
                    "labels": {
                        "alertname": "Prometheus has not been able to scrape the app for 2m",
                        "severity": "critical",
                    },
                    "annotations": {"summary": "the target came back"},
                    "values": {"A": 1},
                }
            ],
        }
        body = alert_bridge.build_body(payload, FIXED_TIMESTAMP, FIXED_SECRET)
        self.assertEqual(
            "[Nexus-Vibe] ok: [RESOLVED] Prometheus has not been able to scrape the app for 2m\n"
            "- Prometheus has not been able to scrape the app for 2m (critical) current=1: "
            "the target came back",
            body["content"]["text"],
        )

    def test_empty_payload_does_not_raise(self):
        # Grafana can post a notification with no alerts (e.g. a recovered group); a crash here
        # would answer 502 and hide the fact that the rule actually recovered.
        body = alert_bridge.build_body({}, FIXED_TIMESTAMP, FIXED_SECRET)
        self.assertEqual("[Nexus-Vibe] alerting: 0 alert(s)", body["content"]["text"])


class ValueShapeTest(unittest.TestCase):
    """`values` is the field that took the bridge down, so every shape it must survive is pinned."""

    def render(self, values):
        return alert_bridge.render_text({
            "state": "ok",
            "title": "t",
            "alerts": [{"labels": {"alertname": "A", "severity": "critical"},
                        "annotations": {}, "values": values}],
        })

    def test_grafana_scalars_render(self):
        # The documented Grafana shape, and the one that raised whenever the number was not 0.
        self.assertEqual("[Nexus-Vibe] ok: t\n- A (critical) current=1", self.render({"A": 1}))

    def test_zero_is_a_reading_and_not_an_absence(self):
        # min_over_time(up{job="nexus-vibe"}[2m]) is 0 exactly while the target is down, so this is
        # the value a firing notification carries. It is falsy, which is what hid the bug: the old
        # expression fell through `or {}` to "no reading" instead of crashing, and only the
        # non-zero half was loud.
        self.assertEqual("[Nexus-Vibe] ok: t\n- A (critical) current=0", self.render({"A": 0}))

    def test_a_nested_reading_still_renders(self):
        self.assertEqual("[Nexus-Vibe] ok: t\n- A (critical) current=1",
                         self.render({"A": {"value": 1}}))

    def test_absent_values_render_without_a_reading(self):
        self.assertEqual("[Nexus-Vibe] ok: t\n- A (critical)", self.render(None))

    def test_an_unknown_shape_does_not_drop_the_alert(self):
        # A list where a map belongs is a vendor change, not a reason to lose the notification.
        self.assertEqual("[Nexus-Vibe] ok: t\n- A (critical)", self.render([1, 2]))


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


class RefusalLogTest(unittest.TestCase):

    def test_a_502_puts_its_reason_in_the_log_and_not_only_in_the_body(self):
        # Grafana records "webhook response status 502" and shows the body to nobody, so a reason
        # that stays in the body is a reason nobody reads. The absence of this assertion is why the
        # 2026-09-17 regression could answer 502 every five minutes for an afternoon while the only
        # thing in `docker logs alert-bridge` was the status code.
        server = alert_bridge.ThreadingHTTPServer(("127.0.0.1", 0), alert_bridge.Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            captured = io.StringIO()
            with mock.patch.object(alert_bridge, "WEBHOOK_URL", "http://127.0.0.1:9/dead"):
                with contextlib.redirect_stdout(captured):
                    request = urllib.request.Request(
                        "http://127.0.0.1:%d/notify" % server.server_address[1],
                        data=json.dumps(GRAFANA_PAYLOAD).encode("utf-8"),
                        headers={"Content-Type": "application/json"},
                        method="POST",
                    )
                    with self.assertRaises(urllib.error.HTTPError) as raised:
                        urllib.request.urlopen(request, timeout=5)
            self.assertEqual(502, raised.exception.code)
            log = captured.getvalue()
            self.assertIn("[alert-bridge] refused:", log)
            self.assertIn("failed:", log)
        finally:
            server.shutdown()
            server.server_close()


class AddressFallbackTest(unittest.TestCase):
    """One blackholed edge must not take the alert down with it.

    `open.feishu.cn` answers with twenty A records in rotating order, and urlopen() keeps the first
    address that completes a *TCP* handshake -- once TLS starts there is no fallback. On 2026-09-17
    one address (202.168.180.27) accepted TCP and then blackholed TLS while two neighbours finished
    in 0.036s, so every delivery failed for as long as the resolver answered with it first. The
    live evidence is in docs/research/alert-bridge-address-fallback-2026-09.md.
    """

    def setUp(self):
        # How long one address may spend before the next gets a turn. The tests make it tiny so a
        # blackhole costs milliseconds instead of the real budget.
        self.addCleanup(setattr, alert_bridge, "CONNECT_TIMEOUT_SECONDS",
                        alert_bridge.CONNECT_TIMEOUT_SECONDS)
        alert_bridge.CONNECT_TIMEOUT_SECONDS = 0.05

    def test_a_blackholed_first_address_is_skipped_for_the_live_one(self):
        seen = []
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), sink_handler(seen))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.shutdown)
        self.addCleanup(server.server_close)
        # 192.0.2.0/24 is TEST-NET-1: routable nowhere, so the connect sits until its budget ends.
        # This is the shape of the real failure -- it is the *first* address that stalls -- and the
        # delivery below only succeeds if send() moves on to the second.
        with mock.patch.object(alert_bridge, "_resolved_addresses",
                               return_value=["192.0.2.1", "127.0.0.1"]):
            reply = alert_bridge.send(
                {"msg_type": "text"},
                url="http://127.0.0.1:%d/notify" % server.server_address[1],
                timeout=5.0,
            )
        self.assertEqual(b'{"code":0}', reply)
        self.assertEqual(["/notify"], seen)

    def test_a_stall_does_not_outlive_the_call_budget(self):
        # Walking addresses must not turn one slow alert into a hung notification loop: the whole
        # call stays inside `timeout`, and when nothing answers the error that surfaces is the real
        # one rather than a placeholder.
        with mock.patch.object(alert_bridge, "_resolved_addresses", return_value=["192.0.2.1"]):
            with self.assertRaises(OSError):
                alert_bridge.send({"msg_type": "text"},
                                  url="http://192.0.2.1/notify", timeout=0.2)

    def test_connect_wraps_the_socket_so_the_handshake_is_bounded_too(self):
        # This is the assertion that makes the fallback above reachable in the real failure.
        # 2026-09-17 stalled *after* TCP succeeded, inside the TLS handshake, so the per-address
        # budget only covers it because wrap_socket() lives in connect(). Move the handshake to
        # request time and the dead address would still eat the whole call while the nineteen
        # reachable ones went untried -- the test would stay green and the alerts would stay lost.
        connection = alert_bridge._PinnedHTTPSConnection("open.feishu.cn", 443, "203.0.113.9", 0.5)
        connection._context = mock.MagicMock()
        handshake = mock.MagicMock()
        connection._context.wrap_socket.return_value = handshake
        socket_before_tls = object()
        with mock.patch.object(alert_bridge.socket, "create_connection",
                               return_value=socket_before_tls) as created:
            connection.connect()
        created.assert_called_once_with(("203.0.113.9", 443), 0.5)
        connection._context.wrap_socket.assert_called_once_with(
            socket_before_tls, server_hostname="open.feishu.cn")
        self.assertIs(handshake, connection.sock)

    def test_a_repeated_address_is_only_tried_once(self):
        # getaddrinfo returns one entry per (family, socktype, protocol), so the same address can
        # arrive several times. Trying it twice would spend the budget on an address already known
        # to be dead and could cost the call its last attempt.
        answers = [
            (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("203.0.113.9", 443)),
            (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("203.0.113.9", 443)),
            (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("198.51.100.7", 443)),
        ]
        with mock.patch.object(alert_bridge.socket, "getaddrinfo", return_value=answers):
            self.assertEqual(["203.0.113.9", "198.51.100.7"],
                             alert_bridge._resolved_addresses("open.feishu.cn", 443))

    def test_pinning_an_address_does_not_turn_off_certificate_verification(self):
        # The whole point of the subclass is that the address changes and nothing else does. If it
        # ever grew a relaxed context, an alert bridge would be quietly willing to post into a
        # machine-in-the-middle while still reporting a successful delivery.
        connection = alert_bridge._PinnedHTTPSConnection("open.feishu.cn", 443, "203.0.113.9", 5.0)
        self.assertEqual("open.feishu.cn", connection.host)
        self.assertEqual("203.0.113.9", connection._address)
        self.assertTrue(connection._context.check_hostname)
        self.assertEqual(ssl.CERT_REQUIRED, connection._context.verify_mode)


def sink_handler(seen):
    """A handler that records the path it was asked for, so a test can prove which address answered."""

    class Sink(http.server.BaseHTTPRequestHandler):

        def do_POST(self):
            self.rfile.read(int(self.headers.get("Content-Length") or 0))
            seen.append(self.path)
            body = b'{"code":0}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    return Sink


def fake_sender(reply):
    """A forward that always answers `reply`, for asserting on the bridge's reading of it."""

    def send_reply(body, url=None):
        return reply

    return send_reply


if __name__ == "__main__":
    unittest.main()
