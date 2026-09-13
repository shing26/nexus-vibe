"""Stand-in for the Feishu custom-bot endpoint, for the drill only.

The bridge's unit test can pin the body it builds, but a unit test cannot show that the bytes a
real Grafana notification produces actually survive the trip out of the container. This is the far
side the drill aims at: it recomputes the Feishu signature over "<timestamp>\\n<secret>" with the
same secret the bridge was given, and it answers the way Feishu does -- a refusal arrives as HTTP
200 with a non-zero code in the body, which is exactly the shape alert_bridge.feishu_error exists to
catch. Return 200/{"code":0} and the bridge may claim a delivery that nobody received.

Every message prints one line to stdout so `docker compose logs webhook-sink` is the evidence:
the alert title, whether the signature verified, and the text a user would have read in the group.
"""

import base64
import hashlib
import hmac
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SECRET = os.environ.get("SINK_FEISHU_SECRET", "")
PORT = int(os.environ.get("SINK_PORT", "9000"))


def verify(timestamp, sign):
    """ok / unsigned / missing / mismatch, against the secret the bridge is signing with."""
    if not SECRET:
        return "unsigned" if not sign else "no-secret-configured"
    if not timestamp or not sign:
        return "missing"
    key = ("%s\n%s" % (timestamp, SECRET)).encode("utf-8")
    want = base64.b64encode(hmac.new(key, msg=b"", digestmod=hashlib.sha256).digest()).decode("ascii")
    return "ok" if hmac.compare_digest(want, sign) else "mismatch"


class Handler(BaseHTTPRequestHandler):

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length)
        try:
            doc = json.loads(raw or b"{}")
        except ValueError as error:
            print("[sink] refused non-JSON body (%d bytes): %s" % (len(raw), error), flush=True)
            self._respond({"code": 1, "msg": "not json"})
            return
        state = verify(str(doc.get("timestamp") or ""), str(doc.get("sign") or ""))
        text = ((doc.get("content") or {}).get("text")) or ""
        print("[sink] feishu msg_type=%s signature=%s text=%s"
              % (doc.get("msg_type"), state, text.replace("\n", " / ")), flush=True)
        if state in ("missing", "mismatch", "no-secret-configured"):
            # Mirror Feishu: 200 on the wire, the failure only in the body.
            self._respond({"code": 19021, "msg": "sign mismatch"})
            return
        self._respond({"code": 0, "msg": "success"})

    def do_GET(self):
        self._respond({"status": "ok", "verifying": bool(SECRET)})

    def _respond(self, doc):
        body = json.dumps(doc).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("[sink] " + (fmt % args), flush=True)


def main():
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print("[sink] listening on %d, signature verification %s"
          % (PORT, "on" if SECRET else "off (no SINK_FEISHU_SECRET)"), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
