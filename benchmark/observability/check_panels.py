"""Ask every provisioned dashboard panel's real expression whether it returns anything.

The drill checks the ALERT RULES against the scrape. Nothing checked the dashboards, and that is how
a "Latency p50/p95/p99" panel querying `http_server_requests_seconds_bucket` - a series Spring Boot
does not publish unless a percentiles-histogram is turned on - shipped green and renders "No data".

Runs against the loopback-published Grafana of docker-compose.render.yml:

    docker compose -p nexus-render -f docker-compose.yml `
      -f benchmark/observability/docker-compose.render.yml --profile monitoring up -d
    python benchmark/observability/check_panels.py

Exit code is 1 only when Grafana rejects an expression; an expression that returns zero series is
printed and counted, because "no data" is sometimes the truth (no LLM traffic in this stack).
"""
import json
import os
import pathlib
import sys
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = os.environ.get("GRAFANA_URL", "http://127.0.0.1:3000")
PASSWORD = os.environ.get("RENDER_GRAFANA_PASSWORD", "render-grafana")
FILES = [
    ROOT / "docker/observability/grafana/provisioning/dashboards/json/nexus-overview.json",
    ROOT / "docker/observability/grafana/provisioning/dashboards/json/nexus-ai-pipeline.json",
    # Added with the round that added the panel: a dashboard nobody asks Prometheus about is how the
    # latency panel shipped broken, and a fourth file in provisioning that these scripts never read
    # is the same blind spot wearing a new label.
    ROOT / "docker/observability/grafana/provisioning/dashboards/json/nexus-product-loop.json",
]

jar = CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def api(path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(BASE + path, data=data,
                                 headers={"Content-Type": "application/json"})
    with opener.open(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


api("/login", {"user": "admin", "password": PASSWORD})
datasources = api("/api/datasources")
uid = next(d["uid"] for d in datasources if d["type"] == "prometheus")
print(f"datasource uid={uid}")


def query(expr):
    path = (f"/api/datasources/proxy/uid/{uid}/api/v1/query?query="
            + urllib.parse.quote(expr))
    try:
        body = api(path)
    except Exception as exc:                       # noqa: BLE001 - report, do not crash the sweep
        return None, f"HTTP error: {exc}"
    if body.get("status") != "success":
        return None, body.get("error", "unknown")
    return body["data"]["result"], None


def walk(panels):
    for panel in panels:
        if panel.get("type") == "row":
            yield from walk(panel.get("panels", []))
            continue
        for target in panel.get("targets", []):
            expr = target.get("expr")
            if expr:
                yield panel.get("title", "(untitled)"), expr


empty, failed, ok = [], [], []
for path in FILES:
    dash = json.load(open(path, encoding="utf-8"))
    name = dash.get("title", str(path))
    print(f"\n=== {name} ===")
    for title, expr in walk(dash.get("panels", [])):
        result, err = query(expr)
        if err is not None:
            failed.append((name, title, expr, err))
            verdict = f"ERROR   {err}"
        elif not result:
            empty.append((name, title, expr))
            verdict = "EMPTY   (0 series -> the panel renders 'No data')"
        else:
            sample = result[0]["value"][1] if result[0].get("value") else "?"
            ok.append((name, title, expr))
            verdict = f"OK      ({len(result)} series, first={sample})"
        print(f"  [{title}] {verdict}\n      {expr}")

print(f"\nsummary: ok={len(ok)} empty={len(empty)} error={len(failed)}")
for name, title, expr, err in failed:
    print(f"  ERROR  {name} / {title}: {err}\n         {expr}")
for name, title, expr in empty:
    print(f"  EMPTY  {name} / {title}\n         {expr}")
sys.exit(1 if failed else 0)
