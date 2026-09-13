"""Grafana webhook to Feishu custom-bot bridge.

Grafana reaches this container over the compose network only; Feishu accepts only a signed body.
Translating between the two in one stateless process keeps the signing secret out of Grafana's own
configuration, where anyone able to open the UI could read it back.

An alert that reaches nowhere is the failure this file exists to prevent, so the bridge refuses to
start without a target (see configuration_error / main) and refuses to call a Feishu "200 with an
error code in the body" a delivery (see feishu_error).

Run the tests with: python -m pytest docker/observability/alert-bridge -q
"""

import base64
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

WEBHOOK_URL = os.environ.get("FEISHU_ALERT_WEBHOOK", "")
WEBHOOK_SECRET = os.environ.get("FEISHU_ALERT_SECRET", "")
LISTEN_PORT = int(os.environ.get("ALERT_BRIDGE_PORT", "8080"))

# What to do about a refusal, spelled out at the point of refusal: this text is the only thing left
# in `docker compose logs alert-bridge` once the container starts exiting.
MISSING_WEBHOOK = (
    "FEISHU_ALERT_WEBHOOK is not set: Grafana alerts have nowhere to go, so every rule in "
    "docker/observability/grafana/provisioning/alerting/rules.yaml is un-notified and the "
    "monitoring profile is decorative. Put the bot webhook (plus FEISHU_ALERT_SECRET if the bot "
    "signs) in .env, or start the stack without --profile monitoring so nobody reads a green "
    "dashboard as \"alerting works\"."
)


def configuration_error(webhook_url=None):
    """Why this bridge cannot deliver, or None when it can."""
    if not (WEBHOOK_URL if webhook_url is None else webhook_url).strip():
        return MISSING_WEBHOOK
    return None


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


def redact_url(url):
    """A Feishu bot URL is a bearer secret: the hook token is the last path segment."""
    parts = urllib.parse.urlsplit(url)
    tail = parts.path.rsplit("/", 1)[-1]
    if len(tail) > 8:
        return "%s://%s%s***" % (parts.scheme, parts.netloc, parts.path[: len(parts.path) - len(tail)])
    return "%s://%s%s" % (parts.scheme, parts.netloc, parts.path)


def feishu_error(reply):
    """Feishu refuses a message with HTTP 200 and the reason in the body, so the body is the truth.

    urlopen() only proves the transport worked. Reading that as delivered is precisely how an
    alert goes missing while every log line on both sides says 200.
    """
    try:
        doc = json.loads(reply or b"{}")
    except (TypeError, ValueError):
        return None
    if not isinstance(doc, dict):
        return None
    # Both wire formats are in the wild: {"code":0} on the v2 bot, {"StatusCode":0} on the legacy.
    code = doc.get("code", doc.get("StatusCode"))
    if code in (None, 0, "0"):
        return None
    return "code=%s msg=%s" % (code, doc.get("msg") or doc.get("StatusMessage") or "")


def deliver(payload, url=None, secret=None, timestamp=None, sender=None):
    """Translate, forward and confirm one Grafana notification. Raises RuntimeError if undelivered."""
    target = WEBHOOK_URL if url is None else url
    error = configuration_error(target)
    if error:
        raise RuntimeError(error)
    body = build_body(
        payload,
        int(time.time()) if timestamp is None else timestamp,
        WEBHOOK_SECRET if secret is None else secret,
    )
    try:
        reply = (send if sender is None else sender)(body, url=target)
    except Exception as forward_error:
        raise RuntimeError("forward to %s failed: %s" % (redact_url(target), forward_error))
    refused = feishu_error(reply)
    if refused:
        raise RuntimeError("Feishu took the connection but refused the alert (%s)" % refused)
    return reply


def alert_titles(payload):
    """One-line digest of a notification, for the container log.

    benchmark/observability/drill.ps1 greps these lines to prove that a rule fired and that
    Grafana's engine reached this process - the titles it matches against come out of rules.yaml,
    so the two cannot drift apart. A payload must not be able to forge extra log lines, hence the
    whitespace collapse.
    """
    titles = [payload.get("title") or ""]
    for alert in payload.get("alerts") or []:
        name = (alert.get("labels") or {}).get("alertname") or ""
        if name and name not in titles:
            titles.append(name)
    return " ".join(" | ".join([t for t in titles if t]).split())


class Handler(BaseHTTPRequestHandler):

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        try:
            payload = json.loads(self.rfile.read(length) or b"{}")
        except ValueError as error:
            self._respond(400, json.dumps({"error": "not JSON: %s" % error}).encode("utf-8"))
            return
        print("[alert-bridge] notify state=%s %s" % (payload.get("state") or "alerting",
                                                    alert_titles(payload)), flush=True)
        try:
            reply = deliver(payload)
            self._respond(200, reply)
        except Exception as error:
            # A forward that fails must report why rather than drop the alert silently: Grafana
            # keeps the notification state, and an unlogged 502 is how alerts go missing.
            self._respond(502, json.dumps({"error": str(error)}).encode("utf-8"))

    def do_GET(self):
        # For a human with `docker compose exec alert-bridge wget -qO- localhost:8080/`. It answers
        # 200 either way - the process is alive - but it says which of the two states it is in,
        # and never echoes the webhook URL.
        forwarding = configuration_error() is None
        self._respond(200, json.dumps({"status": "ok" if forwarding else "not-configured",
                                      "forwarding": forwarding}).encode("utf-8"))

    def _respond(self, status, body):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("[alert-bridge] " + (fmt % args), flush=True)


def main():
    # Refuse before binding the port. A bridge that starts and then answers 502 to every alert is
    # the silent failure this file exists to remove, and compose's restart policy turning it into a
    # crash loop is the visible version of the same fact. Not at import time: the unit tests import
    # this module with an empty environment on purpose.
    error = configuration_error()
    if error:
        print("[alert-bridge] refusing to start: %s" % error, file=sys.stderr, flush=True)
        return 2
    server = ThreadingHTTPServer(("0.0.0.0", LISTEN_PORT), Handler)
    print("[alert-bridge] listening on %d, forwarding to %s" % (LISTEN_PORT, redact_url(WEBHOOK_URL)),
          flush=True)
    server.serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
