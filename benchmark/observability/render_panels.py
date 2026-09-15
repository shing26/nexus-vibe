"""Render the provisioned Grafana dashboards headlessly and report what actually drew.

The drill proves provisioning through the API (does the engine load the rules, is the datasource
registered, do the rule expressions select metrics that exist). It has never opened a panel, and a
dashboard JSON that provisions cleanly can still render nothing but "No data" boxes. This is the
check for that.

Companion to check_panels.py: that script asks each expression for series, this one asks the browser
to draw them. Both ran against benchmark/observability/docker-compose.render.yml, which publishes
Grafana on 127.0.0.1 only.

  docker compose -p nexus-render -f docker-compose.yml `
    -f benchmark/observability/docker-compose.render.yml --profile monitoring up -d
  python benchmark/observability/render_panels.py
  docker compose -p nexus-render -f docker-compose.yml `
    -f benchmark/observability/docker-compose.render.yml --profile monitoring down -v

Needs the Python playwright package (sync API) and a Chromium it can download.

The default output lives under benchmark/observability/evidence/, which is gitignored: the report is
a run artifact, and what belongs in the repository is the conclusion it fed (see
docs/research/observability-drill-2026-09.md section 9).
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

from playwright.sync_api import sync_playwright

BASE = os.environ.get("GRAFANA_URL", "http://127.0.0.1:3000")


def wait_for_grafana(timeout_s=180):
    """Block until Grafana answers /api/health with a healthy database.

    A cold Grafana spends tens of seconds on sqlite migrations before it can serve a datasource
    query, and the first render of this script hit that window: one panel's fetch failed, the browser
    logged "TypeError: Failed to fetch", and the console-error gate went red on a stack whose panels
    were fine. This is the same race the drill hit on its single /api/health probe (section 6, item 3
    of docs/research/observability-drill-2026-09.md), and the fix is the same shape - wait, rather
    than loosen what the run is allowed to ignore.
    """
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(BASE + "/api/health", timeout=5) as resp:
                body = json.loads(resp.read().decode())
                if body.get("database") == "ok":
                    print("grafana ready")
                    return True
        except (urllib.error.URLError, ValueError):
            pass
        time.sleep(3)
    print(f"RENDER FAILED: grafana not ready at {BASE} after {timeout_s}s")
    return False

PASSWORD = os.environ.get("RENDER_GRAFANA_PASSWORD", "render-grafana")
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = (sys.argv[1] if len(sys.argv) > 1 else
       os.path.join(ROOT, "benchmark", "observability", "evidence", "grafana-render"))
DASHBOARDS = [
    ("nexus-overview", "overview"),
    ("nexus-ai-pipeline", "ai-pipeline"),
    ("nexus-product-loop", "product-loop"),
]

PROBE = """() => {
  // Grafana 11.1.4 does not put data-node-id on the panels in this build, so the panel container is
  // found by walking up from a painted canvas to the element that carries a heading. Counting
  // canvases is the part that cannot lie: a panel that renders "No data" paints none.
  const canvases = Array.from(document.querySelectorAll('canvas'));
  const painted = canvases.filter(c => c.width > 10 && c.height > 10);
  const headings = Array.from(document.querySelectorAll('h2'))
    .map(h => (h.innerText || '').trim()).filter(Boolean);
  const bodyText = document.body.innerText;
  return {
    canvasCount: canvases.length,
    canvasPainted: painted.length,
    panelsWithHeading: headings.length,
    noDataOccurrences: (bodyText.match(/No data/gi) || []).length,
    headings,
  };
}"""

errors = []
report = []
if not wait_for_grafana():
    sys.exit(1)
with sync_playwright() as pw:
    browser = pw.chromium.launch()
    ctx = browser.new_context(viewport={"width": 1680, "height": 1050})
    page = ctx.new_page()
    page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
    page.on("pageerror", lambda e: errors.append(str(e)))

    page.goto(f"{BASE}/login", wait_until="domcontentloaded")
    page.fill('input[name="user"]', "admin")
    page.fill('input[name="password"]', PASSWORD)
    with page.expect_navigation(timeout=30000):
        page.click('button[type="submit"]')
    print(f"logged in -> {page.url}")

    for uid, name in DASHBOARDS:
        url = f"{BASE}/d/{uid}?orgId=1&from=now-15m&to=now&refresh=5m"
        page.goto(url, wait_until="domcontentloaded")
        page.wait_for_timeout(6000)
        # panels fetch their series after mount; give every query time to answer
        page.wait_for_timeout(15000)
        info = page.evaluate(PROBE)
        shot = f"{OUT}-{name}.png"
        page.screenshot(path=shot, full_page=True)
        report.append({"uid": uid, "name": name, "url": url, "shot": shot, **info})
        print(f"[{name}] canvas {info['canvasPainted']}/{info['canvasCount']} painted,"
              f" 'No data' x{info['noDataOccurrences']}, headings={info['headings']}")

    browser.close()

print(json.dumps(report, indent=2, ensure_ascii=False))
print(f"console_errors={len(errors)}")
for e in errors[:10]:
    print("  ", e[:200])
with open(f"{OUT}-report.json", "w", encoding="utf-8") as fh:
    json.dump({"report": report, "errors": errors}, fh, indent=2, ensure_ascii=False)

bad = [r for r in report if r["canvasPainted"] == 0]
if bad:
    print("RENDER FAILED for: " + ", ".join(r["name"] for r in bad))
    sys.exit(1)
if errors:
    print(f"RENDER FAILED: {len(errors)} console errors")
    sys.exit(1)
print(f"RENDER OK: all {len(DASHBOARDS)} dashboards drew painted canvases with no console errors")
