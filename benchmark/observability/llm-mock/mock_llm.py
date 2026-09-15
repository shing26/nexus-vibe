"""A stand-in LLM for the observability drill.

The drill's recovery step is not about review quality: it needs an endpoint that stops
refusing connections, so the health probe goes back to healthy and the reconciliation task
dares to re-dispatch the posts the outage parked. A canned 200 is the smallest thing that
does that, and it keeps the drill off any paid API key.

Deliberately permissive: every POST answers 200 with an OpenAI-shaped completion whose
content is "OK". That reads as an invalid review body to the semantic validator, which is
correct for a mock and is exactly why the drill asserts the repair counter moved, not that
a review landed.
"""

import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("MOCK_PORT", "8000"))


class MockLlmHandler(BaseHTTPRequestHandler):
    def _answer(self, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self._answer({"object": "list", "data": [{"id": "drill-mock"}]})

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)
        self._answer({
            "id": "chatcmpl-drill",
            "object": "chat.completion",
            "model": "drill-mock",
            "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": "OK"},
                "finish_reason": "stop",
            }],
        })

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", PORT), MockLlmHandler).serve_forever()
