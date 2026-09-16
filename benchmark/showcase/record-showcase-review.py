"""Re-record the landing page's showcase review from the live pipeline.

The seeded review is a recording of one real run, so it has to be reproducible rather than
asserted. This is the script that produced the committed artifact:

    benchmark/showcase/record-showcase-review.py --base https://<public-origin>

What it does, in order:

1. Registers a throwaway account and publishes the showcase snippet through the ordinary
   publish path, so the review is produced by the same code that reviews everything else.
2. Polls the post's comments until the AI review appears, then writes the comment verbatim
   to src/main/resources/showcase/recorded-review-comment.md.
3. Copies the matching ai_review_log.result_json row out of MySQL into
   src/main/resources/showcase/recorded-review-result.json, byte for byte.
4. Prints the SQL that removes the three seeded rows so the next application start writes the
   new recording. The seeder is insert-only and idempotent by primary key, so it will not
   replace rows that are already there.

Steps 3 and 4 need this repository's compose stack running (`docker compose ps`), because the
result_json column is not served by any public endpoint.

Afterwards, re-run the suites: ShowcasePostSeederTest asserts that the comment and the log row
agree on score and severity, and that every field AiReviewDetailService reads is non-blank, so
a recording that does not match the reader fails there rather than on the deployed page.
"""

import argparse
import json
import pathlib
import random
import subprocess
import sys
import time
import urllib.error
import urllib.request

REPO = pathlib.Path(__file__).resolve().parents[2]
RESOURCE_DIR = REPO / "src" / "main" / "resources" / "showcase"

BODY = """This cache started as a five-line optimisation and now holds every key the service has \
ever been asked for. It never expires anything, it reloads the entire world on a timer, and it \
has no opinion about two callers asking for the same missing key at the same time.

```java
Map<String, Value> cache = new HashMap<>();

Value get(String key) {
    Value hit = cache.get(key);
    if (hit == null) {
        hit = load(key);
        cache.put(key, hit);
    }
    return hit;
}

void refreshAll() {
    cache.clear();
    for (String key : registry.keys()) {
        cache.put(key, load(key));
    }
}
```"""


def call(base, method, path, payload=None, token=None):
    data = json.dumps(payload).encode() if payload is not None else None
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, json.loads(response.read().decode() or "{}")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def sql(statement):
    """Run one statement in the compose MySQL, returning its tab-separated output."""
    password = ""
    for line in (REPO / ".env").read_text(encoding="utf-8").splitlines():
        if line.startswith("DB_PASSWORD="):
            password = line.split("=", 1)[1].strip()
    if not password:
        raise SystemExit("DB_PASSWORD is not set in .env; step 3 needs the compose database.")
    command = [
        "docker", "compose", "exec", "-T", "-e", "MYSQL_PWD=" + password,
        "db", "mysql", "--default-character-set=utf8mb4", "-uroot", "-N", "-B",
        "-D", "nexus_campus", "-e", statement,
    ]
    result = subprocess.run(command, cwd=REPO, capture_output=True, text=True, encoding="utf-8")
    if result.returncode != 0:
        raise SystemExit("mysql failed: " + (result.stderr or "").strip())
    return result.stdout


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--base",
        default="http://localhost:8080",
        help="origin to publish against; use the public address to record through the tunnel",
    )
    args = parser.parse_args()
    base = args.base.rstrip("/")

    suffix = random.randint(100000, 999999)
    username = "showcaserec" + str(suffix)
    password = "Showcase!2345"

    status, body = call(base, "POST", "/api/v1/auth/register", {
        "username": username,
        "email": username + "@example.com",
        "nickname": "Showcase Recorder",
        "password": password,
    })
    print("register ->", status)
    if status not in (200, 201):
        print(body)
        return 1
    token = body["data"]["token"]

    status, body = call(base, "POST", "/api/v1/posts", {
        "title": "Showcase: reviewing an unbounded cache with a stampede window",
        "content": BODY,
        "categoryId": 6,
        "postType": "post",
    }, token)
    print("create post ->", status)
    if status not in (200, 201):
        print(body)
        return 1
    post_id = body["data"]["postId"]
    print("probe post id =", post_id)

    comment = None
    for attempt in range(40):
        time.sleep(6)
        status, body = call(base, "GET", "/api/v1/comments/post/" + str(post_id))
        if status != 200:
            continue
        for item in body.get("data") or []:
            if "## AI Code Review" in (item.get("content") or ""):
                comment = item
                break
        if comment:
            print("review appeared after", (attempt + 1) * 6, "seconds")
            break
    if not comment:
        print("FAILED: no AI review appeared; is AI_REVIEW_ENABLED on and the LLM reachable?")
        return 1

    RESOURCE_DIR.mkdir(parents=True, exist_ok=True)
    comment_text = comment["content"]
    if not comment_text.endswith("\n"):
        comment_text += "\n"
    (RESOURCE_DIR / "recorded-review-comment.md").write_text(
        comment_text, encoding="utf-8", newline="\n")
    print("wrote", (RESOURCE_DIR / "recorded-review-comment.md").relative_to(REPO))

    result_json = sql(
        "SELECT result_json FROM ai_review_log WHERE post_id=" + str(post_id)
        + " AND reviewer='code-review-agent' LIMIT 1;"
    ).strip()
    if not result_json:
        print("FAILED: no ai_review_log row for probe post", post_id)
        return 1
    (RESOURCE_DIR / "recorded-review-result.json").write_text(
        result_json + "\n", encoding="utf-8", newline="\n")
    print("wrote", (RESOURCE_DIR / "recorded-review-result.json").relative_to(REPO))

    parsed = json.loads(result_json)
    print("score =", parsed.get("score"), "severity =", parsed.get("severity"))
    print()
    print("Delete the old seeded rows so the next start writes this recording:")
    print("  docker compose exec -e MYSQL_PWD=$DB_PASSWORD db mysql -uroot -D nexus_campus -e \\")
    print("    \"DELETE FROM vibe_comment WHERE id=900000000000000002; \\")
    print("     DELETE FROM ai_review_log WHERE id=900000000000000003; \\")
    print("     DELETE FROM vibe_post WHERE id=900000000000000001;\"")
    print("Then: mvn -q -Dtest=ShowcasePostSeederTest test")
    return 0


if __name__ == "__main__":
    sys.exit(main())
