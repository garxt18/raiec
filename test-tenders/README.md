# Test tenders

Real IREPS tender PDFs, kept out of the database so a deployment starts clean.

## `smoke-test/` — 5 tenders

The set to upload first after deploying. Chosen to span different IREPS layouts
rather than five of the same shape, so a pass here means the parser works, not
just that one happy path does:

| Tender | What it exercises |
|---|---|
| 232-25-26   | Multi-schedule, DSR + IRUSSOR + NS, `(-) 38.00` escalation |
| 154-2025    | Continuous 1–N item numbering |
| 236-25-26   | Large estimate, many unmatched items |
| 153-2025    | Single-schedule layout |
| 160-2025    | NS items interspersed among scheduled ones |

## `full-set/` — the rest

Load these once the smoke test passes and you want the dashboard, calendar and
LAR dataset to have realistic volume.

## Checking a deployment

1. Log in, confirm the dashboard shows an empty state rather than an error.
2. Upload one tender from `smoke-test/` and walk all five steps.
3. Upload the *same* file again — it should raise the duplicate dialog naming the
   existing record, not create a second copy.
4. Approve it and confirm rates appear in the LAR dataset.
5. Upload the remaining four, then check the calendar, status tabs and filters.

Rate books are **not** in the repo. Without them, scheduled items report
"no reference" — which is correct behaviour, not a failure. Load them separately
if you want DSR/IRUSSOR comparison on the deployment.
