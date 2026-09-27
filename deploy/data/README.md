# Deployment data

## `rate-books.sql`

The parsed CPWD DSR and IRUSSOR reference rates — 2,789 + 1,086 rows.

The rate-book PDFs are not in this repository (large, and licensed), so
`POST /api/reference/import/*` cannot be run against a deployment: that endpoint
reads PDFs from the server's own filesystem. This file carries the already-parsed
rows instead.

It is also the *cleaned* output. The DSR importer previously emitted around 174
junk rows from wrapped table lines — item codes like `7.75` taken from a
description that happened to begin with a number. Those are not in here.

### Loading it into Neon

    psql "postgresql://USER:PASSWORD@HOST/neondb?sslmode=require" -f deploy/data/rate-books.sql

Use the connection string exactly as Neon gives it — the `postgresql://` form,
not the `jdbc:` form the backend needs.

The backend must have started at least once first, so the tables exist.

Safe to re-run: it truncates both tables and resets their sequences.

### Verifying

    psql "postgresql://..." -c "SELECT
      (SELECT count(*) FROM dsr_item)     AS dsr,
      (SELECT count(*) FROM irussor_item) AS irussor;"

Expect 2789 and 1086.

Then re-run a rate match in the app. Items previously showing "no reference"
should resolve to OK / WARN / FAIL against a real published rate.
