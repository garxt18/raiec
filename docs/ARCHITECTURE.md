# RAIEC — System Architecture

RAIEC (Railway Automated Intelligent Estimate Checker) helps a **Finance department
officer of North Western Railway vet a construction tender estimate**. The Construction
department files a tender on IREPS; Finance has to decide whether the rates in it are
defensible. Doing that by hand across three separate rate references is slow, error-prone,
and — because one officer decides alone — open to favouring a familiar filer.

This document describes how the system is put together and, where a choice is not
obvious, why it was made that way.

---

## 1. The shape of the system

```
                 ┌──────────────────────────────────────────┐
   Officer ────► │  frontend/   static pages, no build step │
                 │  Vercel                                  │
                 └───────────────────┬──────────────────────┘
                                     │  HTTPS + JWT
                                     ▼
                 ┌──────────────────────────────────────────┐
                 │  backend/    Spring Boot REST API        │
                 │  Render (Docker)                         │
                 └───────────────────┬──────────────────────┘
                                     │  JDBC
                                     ▼
                 ┌──────────────────────────────────────────┐
                 │  PostgreSQL (Neon)                       │
                 │  tenders · rate books · LAR · audit trail│
                 └──────────────────────────────────────────┘
```

Three pieces, deployed independently. The frontend is plain HTML, CSS and JavaScript with
no build step, so what sits in the repository is exactly what runs in the browser — there
is no compiled output in which a bug can hide.

---

## 2. Repository layout

```
RAIEC/
├── frontend/              the web application  (Vercel Root Directory)
│   ├── index.html         home: search, live overview, entry to the workflow
│   ├── vercel.json        headers and caching; must sit at the Vercel root
│   ├── pages/             dashboard · lar-dataset · guide · administration
│   ├── workflow/          the five vetting steps, in order
│   ├── scripts/
│   │   ├── core/          config · auth · ui   (loaded by every page, in that order)
│   │   ├── home.js        home page
│   │   ├── dashboard.js   dashboard and LAR dataset
│   │   ├── workflow.js    all five workflow steps
│   │   └── administration.js   accounts and the decision record
│   ├── styles/            theme.css (tokens) + one stylesheet per surface
│   └── assets/            images/ and video/
│
├── backend/               Spring Boot service, organised by domain (§4)
├── docs/                  this file, deployment, setup, and the written report
├── deploy/data/           rate-book SQL export for seeding a fresh database
├── render.yaml            Render blueprint; must stay at the repository root
└── README.md
```

### Why the frontend is nested

Vercel's **Root Directory** is set to `frontend`, so the deployed site contains only what
the browser needs. Documentation, the Render blueprint and the database seed never reach
the CDN. This is also why `vercel.json` lives inside `frontend/` rather than beside
`render.yaml`: Vercel reads it from the configured root, not from the repository root.

### Two depths, and how scripts cope

Pages sit at two levels: `index.html` at the top, and everything in `pages/` and
`workflow/` one below. HTML handles this with ordinary relative paths (`../styles/…`).

JavaScript cannot, because the same file runs at both depths — `dashboard.js` serves a
page in `pages/`, while `home.js` serves the top level. `config.js` therefore works out
the site's top level **from its own script URL**, which the browser always reports as
absolute:

```js
// config.js is always at <root>/scripts/core/config.js
var at = document.currentScript.src.indexOf('scripts/core/config.js');
siteRoot = document.currentScript.src.slice(0, at);
```

Any script needing a link to another page calls `raiecUrl('workflow/step1-upload.html')`.
The previous approach tested `location.pathname` for the literal folder name `/upload/`,
which broke silently the moment that folder was renamed. Reading the script's own location
survives renaming, any page depth, and serving the site from a subfolder.

---

## 3. The vetting workflow

The five steps are a deliberate sequence, each answering one question:

| Step | Page | Question it answers |
|------|------|--------------------|
| 1 | `step1-upload.html` | Which tender are we looking at? |
| 2 | `step2-ocr-extract.html` | What line items does the PDF actually contain? |
| 3 | `step3-rate-match.html` | Which rates are above their reference, and by how much in rupees? |
| 4 | `step4-ai-analysis.html` | What do the problem-statement checks and the written assessment say? |
| 5 | `step5-officer-review.html` | Approve, reject, or ask for clarification — recorded against a name |

**Each step is computed once per tender.** A completed step stores its result and replays
it, so walking back and forth through the workflow neither re-runs the analysis nor
re-announces finished work with a progress overlay. Tracking direction of travel was tried
first and failed: the "went backwards" flag was consumed by the page being returned to, so
going 4 → 3 → 4 arrived with the flag already spent.

---

## 4. Backend organisation

Packages are grouped by **domain**, not by technical layer, so everything about one
subject sits together:

```
com.raiec
├── tender/       ingest PDFs, the Tender→Schedule→Entry→BreakupItem tree, the audit trail
├── ratematch/    compare each rate against its reference; the financial analysis
├── reference/    IRUSSOR and CPWD DSR rate books, and their import
├── lar/          Last Accepted Rates, built from approved tenders
├── ai/           problem-statement checks and the written assessment
├── search/       find tenders by the work, not just the number
├── settings/     tolerance thresholds, editable without a rebuild
├── auth/         users, roles, JWT
└── common/       security config, CORS, health, error handling
```

Each domain follows the same internal shape: `entity/` → `repository/` → `service/` →
`web/` with its own `dto/`. Web DTOs are always separate from entities so a lazily-loaded
JPA graph is never serialised straight to the browser.

### Rate matching, in brief

For every priced line the service finds a reference and compares:

- **Scheduled items** → CPWD DSR or IRUSSOR, by item code and edition, falling back to a
  trigram description match (≥45% similar) labelled with its confidence.
- **Non-Scheduled items** → the LAR dataset, "latest AND least": the lowest rate inside a
  12-month window, using an older one only when nothing recent matches and flagging it.

Variance is directional — only over-quoting is flagged, because quoting cheaper is not a
problem. Escalation is applied to the **schedule total**, never to individual rates: in an
IREPS tender the breakup rates sum to the schedule's Basic Value and the tenderer's
percentage is applied once to that total.

Findings are then ranked **by rupee impact rather than percentage**, because a 3% variance
on a ₹40 lakh line matters more than 14% on a ₹2,000 one.

---

## 5. Cross-cutting decisions

**Nothing static or placeholder.** Every figure on screen comes from the database or says
plainly that it could not be loaded. An unreachable server and an empty dataset produce
different messages, because they call for opposite responses from the officer.

**Say what was not checked.** Coverage is reported alongside every total. Without it,
"₹0 excess" reads as a clean estimate when it may only mean nothing was comparable.

**Roles mirror the real parties.** A `FILER` (Construction) uploads and follows estimates
but cannot decide on them; an `OFFICER` (Finance) decides; an `ADMIN` additionally holds
the department-wide settings and the accounts. The department that files an estimate must
not be the one that passes it, and that is enforced in the security filter chain rather
than in the interface.

**Enum constraints are repaired at startup.** `ddl-auto=update` adds columns but never
widens an existing `CHECK`, so adding a value to an enum made every write of that value
fail — once for `INFO_REQUESTED`, then again for `FILER`. `EnumConstraintSync` now rebuilds
those constraints from the Java enums on boot, because a fix applied by hand works exactly
once and is forgotten by the next deployment.

**The audit trail is append-only.** Every lifecycle step records what happened, who caused
it, when, and the excess at that moment — kept on the row because rate books change, and
the question later is what the officer was shown, not what the tender would score today.

**Theme tokens live in one file.** `styles/theme.css` defines both palettes; no other
stylesheet hard-codes a colour.

---

## 6. Deployment

| Piece | Host | Notes |
|-------|------|-------|
| frontend | Vercel | Root Directory `frontend`; redeploys on push |
| backend | Render | Docker, built from `backend/Dockerfile` via `render.yaml` |
| database | Neon | PostgreSQL; schema maintained by Hibernate |

The free Render tier suspends an idle service, so the first request of the day takes up to
a minute. The interface says so rather than appearing to hang. Full steps are in
[DEPLOY.md](DEPLOY.md).

---

## 7. Known gaps

Recorded here so they are not mistaken for finished work:

- **Rate matching does not compare units.** If a rate book prices per `cum` and the tender
  per `10 sqm`, the two are not comparable and the difference is reported as though it
  were. This is the most valuable correctness fix outstanding.
- **LAR records cannot be corrected or deleted.** Since the lowest rate wins, one wrong low
  rate becomes the benchmark permanently.
- Working directories `rate-books/`, `sample-tenders/`, `tenders-to-scan/`,
  `test-tenders/` and `backups/` stay at the repository root because backend code and
  tests reach them by relative path. They are git-ignored, so they do not appear in a
  clone.
