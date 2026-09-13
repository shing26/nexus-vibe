"""Grafana webhook to Feishu custom-bot bridge.

Grafana reaches this container over the compose network only; Feishu accepts only a signed body.
Translating between the two in one stateless process keeps the signing secret out of Grafana's own
configuration, where anyone able to open the UI could read it back.

Run the tests with: python -m unittest discover -s docker/observability/alert-bridge
"""

import base64
import hashlib
import hmac
import json
import os
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

WEBHOOK_URL = os.environ.get("FEISHU_ALERT_WEBHOOK", "")
WEBHOOK_SECRET = os.environ.get("FEISHU_ALERT_SECRET", "")
LISTEN_PORT = int(os.environ.get("ALERT_BRIDGE_PORT", "8080"))


def feishu_sign(timestamp, secret):
    """Feishu signs with the key "<timestamp>\\n<secret>" over an empty message."""
    key = ("%d\n%s" % (timestamp, secret)).encode("utf-8")
    digest = hmac.new(key, msg=b"", digestmod=hashlib.sha256).digest()
    return base64.b64encode(digest).decode("ascii")


def render_text(payload):
    state = payload.get("state") or "alerting"
    alerts = payload.get("alerts") or []
    title = payload.get("title") or ("%d alert(s)" % len(alerts))
    lines = ["[Nexus-Vibe] %s: %s" % (state, title)]
    for alert in alerts:
        labels = alert.get("labels") or {}
        annotations = alert.get("annotations") or {}
        value = ((alert.get("values") or {}).get("A") or {}).get("value")
        detail = annotations.get("summary") or annotations.get("description") or ""
        line = "- %s (%s)" % (labels.get("alertname", "alert"), labels.get("severity", "-"))
        if value is not None:
            line += " current=%s" % value
        if detail:
            line += ": %s" % detail
        lines.append(line)
    return "\n".join(lines)


def build_body(payload, timestamp, secret):
    """The exact Feishu request body. Unsigned when no secret is configured."""
    body = {"msg_type": "text", "content": {"text": render_text(payload)}}
    if secret:
        body["timestamp"] = str(timestamp)
        body["sign"] = feishu_sign(timestamp, secret)
    return body


def send(body, url=None, timeout=5.0):
    request = urllib.request.Request(
        url or WEBHOOK_URL,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


class Handler(BaseHTTPRequestHandler):

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        try:
            payload = json.loads(self.rfile.read(length) or b"{}")
            if not WEBHOOK_URL:
                raise RuntimeError("FEISHU_ALERT_WEBHOOK is not set")
            reply = send(build_body(payload, int(time.time()), WEBHOOK_SECRET))
            self._respond(200, reply)
        except Exception as error:
            # A forward that fails must report why rather than drop the alert silently: Grafana
            # keeps the notification state, and an unlogged 502 is how alerts go missing.
            self._respond(502, json.dumps({"error": str(error)}).encode("utf-8"))

    def do_GET(self):
        self._respond(200, b'{"status":"ok"}')

    def _respond(self, status, body):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("[alert-bridge] " + (fmt % args), flush=True)


def main():
    target = WEBHOOK_URL or "<FEISHU_ALERT_WEBHOOK unset>"
    server = ThreadingHTTPServer(("0.0.0.0", LISTEN_PORT), Handler)
    print("[alert-bridge] listening on %d, forwarding to %s" % (LISTEN_PORT, target), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
