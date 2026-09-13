# Nexus-Vibe Restore Runbook

> Ticket: E5 in [evidence-credibility.md](../tickets/evidence-credibility.md).
> Producer of the input: [scripts/backup.ps1](../../scripts/backup.ps1).
> **Status: never executed.** Every command below is written to be run literally, and the first
> rehearsal is still an open item at the bottom of this file. A restore nobody has performed is not
> a tested backup, which is the whole reason E5 exists.

## 0. What one backup set is

`scripts/backup.ps1 -Destination <second-volume>` writes one directory per run, named
`yyyyMMdd-HHmmss`, containing:

| File | Contents |
| --- | --- |
| `db.sql.gz` | `mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF` of `nexus_campus`, gzip'd on the host |
| `app-uploads.tar.gz` | the whole `app-uploads` volume (the files behind `/uploads/<uuid>.<ext>`), unless the run used `-SkipUploads` |
| `volumes.tsv` | every named volume from `docker-compose.yml`, its resolved name, size, and whether anything was copied for it |
| `manifest.json` | timestamps, sha256 and byte counts for both compressed and plain dumps, statement line count, per-table row counts, the volume inventory, and `complete` |

`manifest.json` is the record the assertions in step 5 compare against. A run with
`complete: false` (a `-SkipUploads` set) is a database dump, not a restorable site - say so out
loud in the rehearsal notes rather than restoring half of it and calling it green.

## 1. Read this before typing anything

- **Never restore into the live project.** Everything here runs against
  `docker compose -p nexus-restore-test`. Compose prefixes named volumes with the project name, so
  the scratch project's database volume is `nexus-restore-test_db-data` and the real site's is
  `nexus-vibe_db-data`. They are different volumes with the same declared name, and that is the
  only thing in this procedure that makes a mistake survivable. Check it with the `volume ls` step
  before you create anything.
- Run every command from the repository root, `D:\Nexus-Campus`. The compose file is found by the
  `-f` argument, and `init.sql` is mounted by a path relative to it.
- The scratch project reads the same `.env` as the real stack (compose takes the environment file
  from the compose file's directory). That means the restored database is created with the
  production `DB_PASSWORD` as its root password. Do not point this runbook at a shared host.
- Only `db` is needed for the restore itself. `web` is never started, so the published host port
  (`${WEB_PORT:-8080}:80`) cannot collide with a running site.
- Do not run `docker compose down -v` without `-p nexus-restore-test` in the same command line.

## 2. Bring up an empty database

```powershell
docker compose -p nexus-restore-test up -d db
docker compose -p nexus-restore-test ps db
```

On a first run the volume is empty, so the MySQL entrypoint executes
`docker/mysql/init.sql`: schema, plus the 7 channels and 7 tags that count as reference data.
Demo content is not in `init.sql` (it lives in
`src/main/resources/db/mysql/demo-content.sql` and is applied by the app only when
`DEMO_SEED_ENABLED=true`), so a fresh scratch database has the right tables and zero posts. That
is the state you want to see immediately before the restore, otherwise the restore proves nothing.

Wait for the healthcheck rather than for luck:

```powershell
docker compose -p nexus-restore-test exec -T db mysqladmin ping -h 127.0.0.1 -uroot -p"not-the-password"
```

The password stays inside the container for everything below, via `MYSQL_PWD` and the container's
own `MYSQL_ROOT_PASSWORD`. Note the `` `$ `` in the commands that follow: PowerShell expands `$`
itself, and the value has to survive into the container's shell, not be resolved on the host.

```powershell
# expected: the empty reference data, and nothing else
docker compose -p nexus-restore-test exec -T db sh -c "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -N -s -h 127.0.0.1 -uroot nexus_campus -e 'SELECT COUNT(*) FROM vibe_post; SELECT COUNT(*) FROM sys_user; SELECT COUNT(*) FROM vibe_channel'"
```

## 3. Load the dump

Decompress on the host (the `mysql:8.0` base image is not guaranteed to ship `gzip`, which is also
why the backup compresses on this side of the boundary):

```powershell
$set = 'E:\nexus-vibe-backups\20260913-031500'   # the set you are restoring
$src = Join-Path $set 'db.sql.gz'
$dst = Join-Path $env:TEMP 'nexus-restore.sql'
$in  = [IO.File]::OpenRead($src)
$out = [IO.File]::Create($dst)
$gz  = New-Object IO.Compression.GZipStream($in, [IO.Compression.CompressionMode]::Decompress)
$gz.CopyTo($out); $gz.Dispose(); $out.Dispose(); $in.Dispose()
(Get-FileHash -LiteralPath $dst -Algorithm SHA256).Hash.ToLowerInvariant()
```

**Stop and compare before loading:** that hash must equal `manifest.json` ->
`verified.db.plainSha256`, and the byte count must equal `verified.db.plainBytes`. If either
differs, the archive is corrupt and the rest of this runbook is fiction.

Copy it in and load it with the container's own shell doing the redirect - PowerShell has no
`<` redirection for a native command's stdin, and `Get-Content` would decode the file as text:

```powershell
docker compose -p nexus-restore-test cp $dst db:/tmp/nexus-restore.sql
docker compose -p nexus-restore-test exec -T db sh -c "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -h 127.0.0.1 -uroot nexus_campus < /tmp/nexus-restore.sql"
docker compose -p nexus-restore-test exec -T db sh -c 'rm -f /tmp/nexus-restore.sql'
```

The load is expected to succeed on top of `init.sql`'s tables: `mysqldump` emits
`DROP TABLE IF EXISTS` before every `CREATE TABLE` by default, so the schema and the reference rows
are replaced by what the source database actually held.

## 4. The `migrate-*.sql` question, answered from the tree

There are three one-shot scripts in `docker/mysql/`, and they are all folded into `init.sql`:

| File | What it changes | In `init.sql` already? | Apply when |
| --- | --- | --- | --- |
| `migrate-0005-add-user-email.sql` | `sys_user.email` | yes (`email varchar(100) DEFAULT NULL UNIQUE`) | a volume created before `ee09486` (2026-09-03) |
| `migrate-0006-add-ai-sort-index.sql` | `vibe_post` index `idx_post_ai_sort` | yes (same index, same column order) | a volume created before `056c912` (2026-09-04) |
| `migrate-0007-add-review-lease.sql` | `vibe_post.review_lock_until`, `review_owner`, `review_attempts` | yes (all three columns) | a volume created before `614bf9a` (2026-09-05) |

The rules that follow from that table:

1. **After a dump restore, apply none of them.** The dump carries its own `CREATE TABLE`, so it
   overwrites the schema with whatever the source volume had. Applying `migrate-0005` on top of a
   dump that already has the column fails with `Duplicate column name 'email'`; if it did not
   fail, it would mean the dump came from a database older than the migration, which is case 3.
2. **A brand-new volume needs none of them either.** `init.sql` runs on first boot and contains all
   three changes.
3. **They exist for the case "the app was upgraded, the volume was not".** A dump taken from before
   those commits restores the older schema. Then the migration that post-dates the dump has to be
   applied after the load, in number order:

   ```powershell
   docker compose -p nexus-restore-test cp docker/mysql/migrate-0007-add-review-lease.sql db:/tmp/m.sql
   docker compose -p nexus-restore-test exec -T db sh -c "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -h 127.0.0.1 -uroot nexus_campus < /tmp/m.sql"
   ```

4. **Nothing in the repository records which of them the live volume has had.** There is no
   migration ledger and no schema-version table, so rule 3 is decided by the age of the dump and a
   live probe. This one is worth running on the restored database, and worth running on the real
   one before you trust any dump from it:

   ```powershell
    docker compose -p nexus-restore-test exec -T db sh -c "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -N -s -h 127.0.0.1 -uroot nexus_campus -e ""SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='nexus_campus' AND COLUMN_NAME IN ('email','review_lock_until','review_owner','review_attempts') UNION ALL SELECT 'index', INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='nexus_campus' AND INDEX_NAME='idx_post_ai_sort' ORDER BY 1, 2"" | sort"
   ```

Two naming facts to keep in mind while reading those files:

- `migrate-0001` through `migrate-0004` have never existed. E5's glob `migrate-000[1-7]-*.sql`
  describes files that are not in the repository; `git log --all -- docker/mysql/` and
  `git log --all -S migrate-0001` both come back empty. The numbering simply starts at 0005.
- `migrate-0005`'s how-to-run comment loads the change into `nexus_vibe`. There is no such
  database: compose sets `MYSQL_DATABASE=nexus_campus` and `init.sql` creates `nexus_campus`.
  Copy-pasting that comment line fails, which is the correct outcome for a wrong command but the
  wrong reason to see on screen at 3am. `0006` and `0007` say `nexus_campus`.
- `docker/mysql/benchmark/` is not migrations. Those six files are the EXPLAIN/seed harness for
  the deep-pagination and AI-sort studies and must never be applied to a production volume.

## 5. Assert the data came back

Read `manifest.json` -> `rowCounts`, then compare every table. One command, one number per line,
in the same table order the manifest used:

```powershell
docker compose -p nexus-restore-test exec -T db sh -c "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -N -s -h 127.0.0.1 -uroot nexus_campus -e 'SELECT ""sys_user"", COUNT(*) FROM sys_user UNION ALL SELECT ""vibe_post"", COUNT(*) FROM vibe_post UNION ALL SELECT ""vibe_comment"", COUNT(*) FROM vibe_comment UNION ALL SELECT ""vibe_post_like"", COUNT(*) FROM vibe_post_like'"
```

Counts must match exactly. The manifest numbers are taken a moment after the dump's transaction
closes, so a post written in that window can make one differ: the honest response is to say which
one and why, not to call it close enough.

Content check - the schema can survive a bad restore while the text does not. `vibe_post.content`
is where the Chinese prose lives, and mojibake in a backup is invisible until someone reads a post:

```powershell
docker compose -p nexus-restore-test exec -T db sh -c "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -N -s -h 127.0.0.1 -uroot --default-character-set=utf8mb4 nexus_campus -e 'SELECT id, LEFT(title, 40) FROM vibe_post ORDER BY id LIMIT 5'"
```

Paste five rows. If they came back as `?` or as box-drawing garbage, the dump was taken or loaded
through a text-decoding pipe; `--default-character-set=utf8mb4` is in `backup.ps1` for this reason
and it is not optional here.

## 6. Assert one uploaded file came back

The dump records only the `/uploads/<uuid>.<ext>` string a post points at; the bytes are a second
artifact. Take the same file two ways, and compare hashes.

6a. From the archive, on the host:

```powershell
tar -tzf (Join-Path $set 'app-uploads.tar.gz')          # pick one name out of this list
$one = './8f2c1f4a-...-9b7c3d1e4f2a.png'
New-Item -ItemType Directory -Force -Path (Join-Path $env:TEMP 'nexus-up') | Out-Null
Push-Location (Join-Path $env:TEMP 'nexus-up')
tar -xzf (Join-Path $set 'app-uploads.tar.gz') $one
(Get-FileHash ".\$(Split-Path $one -Leaf)" -Algorithm SHA256).Hash.ToLowerInvariant()
Pop-Location
```

6b. Into the scratch volume, then read it back the way the application reads it:

```powershell
docker compose -p nexus-restore-test up -d --no-deps db redis app
docker compose -p nexus-restore-test cp (Join-Path $set 'app-uploads.tar.gz') app:/tmp/uploads.tar.gz
docker compose -p nexus-restore-test exec -T app tar -C /app/uploads -xzf /tmp/uploads.tar.gz
docker compose -p nexus-restore-test exec -T app sha256sum /app/uploads/8f2c1f4a-...-9b7c3d1e4f2a.png
docker compose -p nexus-restore-test exec -T app curl -sS -o /dev/null -w '%{http_code} %{size_download}\n' http://localhost:8080/uploads/8f2c1f4a-...-9b7c3d1e4f2a.png
```

The two hashes must be identical, and the last line must be `200` with a byte count equal to the
file's size. That is the assertion the ticket actually asked for: not "the file is in the archive",
but "the running application serves the restored file".

Expect noise from this container and know why before you chase it: `app` comes up with
`SPRING_PROFILES_ACTIVE=prod` against the scratch `db` and `redis`, with no `elasticsearch`, no
`ollama` and no LLM key pointed anywhere live, so search and review paths will log failures.
Two things that are *not* noise: `spring.sql.init.mode: never` in `application-prod.yml` is what
stops the app from touching the schema you just restored, and `BootstrapAdminInitializer` stays
quiet because the restored database already contains an `ADMIN`.

## 7. Tear it down

```powershell
docker compose -p nexus-restore-test ps --format '{{.Name}} {{.Status}}'
docker compose -p nexus-restore-test down --remove-orphans
docker compose -p nexus-restore-test down -v
docker volume ls --format '{{.Name}}' | Select-String 'nexus-restore-test'
docker compose -p nexus-vibe ps --format '{{.Name}} {{.Status}}'
```

The fourth command must print nothing. The fifth must show the real stack exactly as it was before
the rehearsal - same containers, `Up`, untouched. `down -v` without `-p` in front of it deletes the
production volumes, and nothing in Docker will ask twice.

## 8. What a successful restore still does not cover

These are the rows of `volumes.tsv` where the action says `inventory only`, and each one is a
deliberate gap rather than an oversight:

- **`es-data`** is the sharp one. The `nexus_posts` index is filled one post at a time by
  `PostSearchService.indexPost`, and there is no bulk reindex path anywhere in the code. A restore
  that brings the database back and an empty index leaves full-text search returning nothing for
  every pre-existing post, with a `200` and no error. Either reindex on restore or stop pretending
  search is backed up; this is E5's real follow-up work.
- **`redis-data`**: cache plus like counters, and `LikeSyncTask` flushes the counters to MySQL every
  5 minutes, so a Redis loss costs at most one flush window of the dirty set. Rebuildable.
- **`app-logs`**: logback caps the volume at 100 MB per file, 7 days, 1 GB total by design. Losing
  it is the designed outcome, not an incident.
- **`ollama-data`**: model weights, re-downloadable and by far the largest thing on the disk. E5's
  own scope list omits it; the inventory keeps it visible so its size is never a surprise.
- **`prometheus-data` / `grafana-data`**: monitoring profile only, so on a stack started without
  `--profile monitoring` they do not exist yet and the inventory records them as `<absent>`. The
  dashboards and alert rules are in git under `docker/observability/grafana/provisioning`; what a
  lost `grafana-data` really costs is the Grafana admin password and UI state.

## First rehearsal record (to fill)

Not executed. This section is the evidence checklist for whoever runs it; paste the real output
under each heading, in this file, and correct any step that needed improvising - that correction is
part of E5's acceptance, not a bonus.

- [ ] Date, host, and the exact `docker compose ps` state of `nexus-vibe` while the drill ran
      (the point is that the live stack was up and untouched throughout).
- [ ] The full command list actually used, copied from the terminal, including which
      `yyyyMMdd-HHmmss` set was restored and where it came from.
- [ ] `manifest.json` for that set: `takenAt`, `complete`, `verified.db.gzBytes`,
      `verified.db.plainBytes`, `verified.db.statements`, `verified.db.plainSha256`.
- [ ] The host-side `Get-FileHash` of the decompressed dump, next to the manifest value, and the
      word "match" or what happened instead.
- [ ] The pre-restore `COUNT(*)` output from step 2 (proves the scratch database was empty).
- [ ] The `mysql` load command with its exit status and any stderr it produced.
- [ ] `dump 行数`: the statement line count from `manifest.json`, and the `wc`-equivalent on the
      decompressed file: `(Get-Content -LiteralPath $dst -ReadCount 1000 | Measure-Object -Line).Lines`.
- [ ] `select count(*)` comparison table: every table in `rowCounts`, manifest value, restored
      value, and a difference column that is empty.
- [ ] Five `id, LEFT(title, 40)` rows from step 5, legible, so a reader can see the Chinese came
      back as Chinese.
- [ ] One uploaded file: its name, the host-side sha256 from 6a, the in-volume sha256 from 6b, and
      the `curl` line showing `200` plus `size_download`.
- [ ] The output of all four teardown commands in step 7, including the empty `Select-String`.
- [ ] Anything this runbook got wrong, fixed in place, with a one-line note on what the first
      attempt actually needed.
- [ ] The schema probe from rule 4 of step 4, so the record shows which one-shot migrations the
      restored database really carries.

## 9. How far this file has actually been checked

So the record is unambiguous about what is measured and what is written:

- `scripts/backup.ps1` parses under PowerShell 7.6 (`Get-Command ./scripts/backup.ps1`), and
  `-DryRun` / `-WhatIf` walk the whole planning path, printing the exact argv every docker call
  would use. Neither mode contacts the daemon or writes a byte.
- The two host-side functions that hold no docker dependency are covered by a scratch harness run
  against the real source text: the destination guard (rejects the repository tree, rejects the
  same volume, rejects `\\localhost\`, accepts another drive) and the artifact verifier (accepts a
  good gzip with a matching sha256, rejects a physically truncated one, rejects a complete one
  whose `-- Dump completed` marker is missing). The byte-fidelity test streams 4 KB of `init.sql`
  plus a NUL and an 0xFF through the gzip path and hashes what comes back out.
- The failure path is executed for real: a refused destination produces one `BACKUP FAILED` line on
  stderr, the next-eyes block, and exit code 1.
- **No docker command in this file has been run, and no restore has been performed.** The dump,
  load, count and upload assertions are untested prose until the rehearsal above fills in.
  `-DryRun` is a rehearsal of the plan, never of the outcome.
