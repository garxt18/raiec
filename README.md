# RAIEC — Railway Automated Intelligent Estimate Checker

A tender-estimate vetting system built for a problem statement issued by the
**Construction department of North Western Railway**, Jaipur.

When any NWR department needs something built, it files a tender on the IREPS portal. An
officer of the **Finance department** then has to decide whether the rates in that estimate
are defensible, by checking every line against three separate rate references. Done by
hand this is slow, easy to get wrong, and — because one person decides alone — open to
quietly favouring a familiar filer.

RAIEC does the checking in seconds, says what each finding is worth **in rupees**, and
records who decided what.

> B.Tech internship project. Live at **[raiec.vercel.app](https://raiec.vercel.app)**.

---

## Contents

- [The problem](#the-problem)
- [How a tender flows through it](#how-a-tender-flows-through-it)
- [How rate matching works](#how-rate-matching-works)
- [The financial layer](#the-financial-layer)
- [Accountability](#accountability)
- [Search](#search)
- [Roles](#roles)
- [Architecture](#architecture)
- [Repository layout](#repository-layout)
- [Running it locally](#running-it-locally)
- [Deployment](#deployment)
- [API reference](#api-reference)
- [Testing](#testing)
- [Design principles](#design-principles)
- [Known gaps](#known-gaps)

---

## The problem

Three things were wrong with manual vetting, and the tool is built around them.

| Problem | Why it costs something | What RAIEC does |
|---|---|---|
| **Speed** | A division may urgently need work — a track expansion, say — that sits waiting in vetting | Checks a full estimate in seconds |
| **Accuracy** | Comparing hundreds of rates across three references by hand invites mistakes | Matches every line automatically and shows its working |
| **Manipulation** | One officer decides alone, and can favour someone they know | Records every decision against a name, and surfaces the pattern |

A fourth goal runs underneath: **building a rate history for Non-Scheduled items**, which
have no published rate anywhere and are therefore the hardest part of any estimate to
challenge.

### The three rate references

| Reference | What it is |
|---|---|
| **IRUSSOR** | Indian Railway Unified Standard Schedule of Rates, published centrally |
| **CPWD DSR** | Delhi Schedule of Rates, published by the Central Public Works Department |
| **LAR** | Last Accepted Rate — what NWR itself has previously agreed to pay |

Items priced in IRUSSOR or DSR are **scheduled**. Everything else is **Non-Scheduled (NS)**
and can only be judged against the LAR, which is why the dataset grows with every approval.

---

## How a tender flows through it

Five steps, each answering exactly one question:

| Step | Question it answers |
|---|---|
| **1 · Upload** | Which tender are we looking at? |
| **2 · Extract** | What line items does this PDF actually contain? |
| **3 · Rate match** | Which rates are above their reference, and by how much in rupees? |
| **4 · AI analysis** | What do the automated checks and the written assessment say? |
| **5 · Officer review** | Approve, reject, or ask for clarification — recorded against a name |

A completed step is **computed once per tender** and replayed afterwards, so walking back
and forth through the workflow never re-runs the analysis or re-shows a progress animation
over work that already finished.

On approval, every Non-Scheduled rate in the tender flows into the LAR dataset, keeping
the **lowest** rate per item. The benchmark only ever moves down.

---

## How rate matching works

For each priced line, the service finds a reference and compares against it.

**Scheduled items** are looked up in three passes, stopping at the first hit:

1. **Item code + edition** — e.g. code `2.8.1` in DSR 2021. The reliable path.
2. **Item code, any edition** — if that edition is not loaded.
3. **Description similarity** — trigram overlap, needing at least 45% similarity. The
   source is then labelled with its confidence, like `DSR 2021 · ~62%`.

**Non-Scheduled items** go to the LAR under the rule *latest **and** least*: the lowest
rate inside a 12-month window; an older rate is used only when nothing recent matches, and
is then flagged as stale. A tender is never checked against LAR rates it produced itself.

### The verdict

```
variance % = (quoted rate − reference rate) ÷ reference rate × 100
```

The check is **directional**, because quoting cheaper is not a problem:

| Variance | Verdict |
|---|---|
| At or below reference, up to +5% | ✅ OK |
| +5% to +10% | ⚠️ WARN |
| Above +10% | ❌ FAIL |
| No reference found | No reference — *unchecked, not acceptable* |

Thresholds live in the database, so an administrator can change them without a rebuild.

### Escalation

A tenderer quotes one percentage against a whole schedule — `(+) 8.50`, `(−) 3.20`, or
At Par. That percentage applies to the **schedule total**, not to individual rates: the
breakup rates sum to the schedule's Basic Value, and the percentage is applied once to
that total. Both sides of the comparison are therefore pre-escalation figures.

This was verified against a real document, where the breakup total of ₹28,67,651 matched
the stated Basic Value exactly. Applying escalation per item instead turned 107 of 119
correct items into false failures.

---

## The financial layer

Counting flagged items answers *"how many things look wrong"*, which is not the question
a vetting officer is asked. Three items at +40% on ₹8,000 of work matter less than one
item at +6% on ₹2 crore — but a count ranks them the other way round.

Every result therefore carries:

| Figure | What it tells you |
|---|---|
| **Excess** | Rupees above reference — `quantity × (quoted − reference)`, summed |
| **Saving** | Rupees below reference, kept **separate** from excess |
| **Coverage** | How much of the estimate could be checked at all |
| **Materiality** | Excess as a share of the estimate — makes two tenders comparable |
| **Concentration** | How few items carry 80% of the excess |
| **By schedule / by rate book** | Where the excess actually sits |

Excess and saving are never netted into one number: a tender ₹5 lakh over on one item and
₹5 lakh under on another is not the same as one that matches its references throughout,
and a single net figure of zero would claim it was.

Line items are **sorted by rupee impact**, so the costliest line is first rather than
wherever the PDF happened to put it.

### The clustering signal

The tolerance is public: quote within the warning band and nothing is raised. A rate
honestly derived from cost has no particular reason to land just under that line, and
across many items variances should scatter.

When an unusual share of them instead bunch **just below the flag threshold**, RAIEC says
so — with the money attached, and worded as an observation rather than a verdict. It
requires a sample of at least 8 referenced items, because two out of three landing in the
band is 67% and means nothing.

---

## Accountability

Every step of a tender's life is recorded in an **append-only audit trail**: what happened,
who caused it, when, and the excess at that moment. The excess is stored on the row rather
than recomputed later, because rate books change and the question afterwards is what the
officer *was shown*, not what the tender would score today.

The **Administration** page turns that trail into the answer to a question nobody could
previously ask: for each person, how often they approve, and how much above reference they
have accepted. No single approval proves anything — the point is that the *pattern* becomes
visible, and an officer who approves everything is answerable to a question about it.

---

## Search

The archive is searchable by **what the work is**, not just by tender number — including
the text of line items buried inside each document. Searching `earthwork` finds a tender
whose title is about a laundry building, because the earthwork is three levels down in its
schedule.

Matching crosses the gap between how an officer speaks and how an estimate is written:
`RCC` reaches "reinforced cement concrete", `FOB` reaches "foot over bridge". This is a
**curated railway vocabulary rather than an embedding model** — deliberately, because it
needs no model or network, behaves identically on a laptop and a free-tier server, and
every match can be explained to the person relying on it.

Each result states *why* it matched, since a hit from deep inside a document otherwise
looks like a bug.

---

## Roles

Three roles, mirroring the three real parties to a tender:

| Role | May do | May **not** do |
|---|---|---|
| **Filer** (Construction) | Upload estimates, view everything | Approve, reject, or request information |
| **Officer** (Finance) | Everything a filer can, plus decide on tenders | Change thresholds, import rate books, manage accounts |
| **Admin** | Everything, plus settings, imports and accounts | — |

The separation is the point rather than a convenience: **the department that files an
estimate must not be the one that passes it.**

Accounts are managed from the Administration page. Two safeguards are enforced by the
server, not just the interface: an administrator cannot disable or demote their own
account, and the last enabled administrator cannot be removed.

---

## Architecture

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

| Layer | Technology |
|---|---|
| Frontend | HTML, CSS, vanilla JavaScript — **no build step**, no framework |
| Backend | Java 21, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA |
| PDF parsing | Apache PDFBox 3.0.4 |
| Database | PostgreSQL |
| Build | Maven wrapper (`mvnw`) |

The frontend has no build step on purpose: what sits in the repository is exactly what runs
in the browser, so there is no compiled output for a bug to hide in.

`docs/ARCHITECTURE.md` goes further, including how scripts resolve paths across page
depths and why the backend is grouped by domain rather than by layer.

---

## Repository layout

```
RAIEC/
├── frontend/              the web application  (Vercel Root Directory)
│   ├── index.html         home: search, live overview, entry to the workflow
│   ├── vercel.json        headers and caching — must sit at the Vercel root
│   ├── pages/             dashboard · lar-dataset · guide · administration
│   ├── workflow/          the five vetting steps
│   ├── scripts/
│   │   ├── core/          config · auth · ui   (loaded by every page, in that order)
│   │   ├── home.js        home page
│   │   ├── dashboard.js   dashboard and LAR dataset
│   │   ├── workflow.js    all five workflow steps
│   │   └── administration.js
│   ├── styles/            theme.css (tokens) + one stylesheet per surface
│   └── assets/            images/ and video/
│
├── backend/               Spring Boot service, grouped by domain
│   └── src/main/java/com/raiec/
│       ├── tender/        PDF ingest, the tender tree, the audit trail
│       ├── ratematch/     rate comparison and the financial analysis
│       ├── reference/     IRUSSOR and CPWD DSR rate books
│       ├── lar/           Last Accepted Rates
│       ├── ai/            automated checks and the written assessment
│       ├── search/        content search
│       ├── settings/      tolerance thresholds
│       ├── auth/          users, roles, JWT
│       └── common/        security, CORS, health, error handling
│
├── docs/                  architecture, deployment, setup, written report
├── deploy/data/           rate-book SQL export for seeding a database
├── render.yaml            Render blueprint — must stay at the repository root
└── README.md
```

---

## Running it locally

**You need:** Java 21, PostgreSQL, and a way to serve static files.

**1. Create the database**

```sql
CREATE DATABASE raiec;
```

**2. Start the backend** (defaults to `localhost:5432`, user `postgres`, password `postgres`)

```bash
cd backend
./mvnw spring-boot:run
```

Override the connection with environment variables if yours differs:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/raiec DB_USER=postgres DB_PASSWORD=secret ./mvnw spring-boot:run
```

The API comes up on **http://localhost:8080**. Hibernate creates the schema on first run.

**3. Serve the frontend** from the `frontend/` folder — any static server will do:

```bash
cd frontend
python -m http.server 5500
```

Open **http://localhost:5500**. The frontend points itself at `localhost:8080`
automatically when served from localhost.

**4. Load the rate books** (optional, but nothing can be checked without them)

Place the rate-book PDFs in `rate-books/`, then as an admin:

```bash
curl -X POST "http://localhost:8080/api/reference/import/dsr?edition=2021" -H "Authorization: Bearer <token>"
```

Or seed a database directly from the SQL export in `deploy/data/`.

**Default accounts** are created on first run and can be set with the
`RAIEC_SEED_ADMIN_USERNAME` / `RAIEC_SEED_ADMIN_PASSWORD` environment variables.
**Change them before exposing the service to anyone** — the defaults are in this public
repository.

> Full setup notes: **[docs/SETUP.md](docs/SETUP.md)**

---

## Deployment

| Piece | Host | Notes |
|---|---|---|
| Frontend | Vercel | **Root Directory must be `frontend`** |
| Backend | Render | Docker, built from `backend/Dockerfile` via `render.yaml` |
| Database | Neon | PostgreSQL |

Required environment variables on Render:

| Variable | Purpose |
|---|---|
| `DATABASE_URL`, `DB_USER`, `DB_PASSWORD` | Neon connection |
| `RAIEC_JWT_SECRET` | Signs login tokens — must be long and random |
| `RAIEC_CORS_ORIGINS` | The frontend origin, e.g. `https://raiec.vercel.app` |
| `RAIEC_SEED_ADMIN_USERNAME` / `_PASSWORD` | First administrator |
| `RAIEC_SEED_OFFICER_USERNAME` / `_PASSWORD` | First officer |

The free Render tier suspends an idle service, so the first request of the day takes up to
a minute. The interface says so rather than appearing to hang, and
`docs/DEPLOY.md` explains how to keep it awake with a scheduled ping.

> Full deployment walkthrough: **[docs/DEPLOY.md](docs/DEPLOY.md)**

---

## API reference

All endpoints are under `/api` and need a bearer token except where noted.

**Authentication**

| Method | Path | Notes |
|---|---|---|
| `POST` | `/auth/login` | Public. Returns a JWT valid for 8 hours |
| `GET` | `/auth/me` | The current user and role |
| `GET` | `/health` | Public. Server and database status |

**Tenders**

| Method | Path | Notes |
|---|---|---|
| `POST` | `/tenders/upload` | Upload a PDF (multipart) |
| `GET` | `/tenders` · `/tenders/{id}` | List, or full detail |
| `GET` | `/tenders/stats` | Dashboard counts and money under review |
| `GET` | `/tenders/{id}/rate-match` | Comparison and the financial analysis |
| `GET` | `/tenders/{id}/ai-analysis` · `/ai-summary` | Checks, and the written assessment |
| `GET` | `/tenders/{id}/events` | That tender's audit trail |
| `POST` | `/tenders/{id}/approve` · `/reject` · `/request-info` | **Officer or Admin** |
| `DELETE` | `/tenders/{id}` | **Admin** |

**Search, references and settings**

| Method | Path | Notes |
|---|---|---|
| `GET` | `/search?q=` | Search by number, title, office or line item |
| `GET` | `/reference/status` | Whether the rate books are loaded |
| `POST` | `/reference/import/dsr` · `/irussor` | **Admin** |
| `GET` | `/lar` | The LAR dataset |
| `POST` | `/lar` | **Admin** — hand-entered rate |
| `GET` / `PUT` | `/settings/thresholds` | Read by anyone; **Admin** to change |

**Administration**

| Method | Path | Notes |
|---|---|---|
| `GET` | `/admin/accountability` | Who decided what, and what it cost |
| `GET` `POST` | `/admin/users` | List or create accounts |
| `PUT` | `/admin/users/{id}/role` · `/enabled` · `/password` | Change an account |

---

## Testing

```bash
cd backend
./mvnw test
```

**130 tests**, covering PDF parsing against real IREPS documents, rate matching and
escalation, the financial analysis and clustering detector, LAR recency, search and its
vocabulary, the audit trail, role separation through the real security filter chain, and
the account-management safeguards.

---

## Design principles

These are applied throughout, and explain a lot of the smaller decisions:

- **Nothing static or placeholder.** Every figure comes from the database or says plainly
  that it could not be loaded. "Server unreachable" and "no data yet" are different
  messages, because they call for opposite responses.
- **Say what was *not* checked.** Coverage sits beside every total, so "₹0 excess" can
  never be mistaken for a clean estimate when nothing was comparable.
- **Rank by money, not percentage.** The officer's time is finite.
- **Signals, not verdicts.** The clustering detector reports what it observed and leaves
  the conclusion to the officer.
- **Explain every match.** A search hit or a fuzzy rate match always says where it came
  from and how confident it is.

---

## Known gaps

Stated plainly so they are not mistaken for finished work:

- **Rate matching does not compare units.** If a rate book prices per `cum` and the tender
  per `10 sqm`, the two are not comparable, but the difference is reported as though they
  were. This is the most valuable correctness fix outstanding.
- **LAR records cannot be corrected or deleted.** Since the lowest rate wins, one wrong
  low rate becomes the benchmark permanently.
- **Non-Scheduled coverage depends on history.** Until tenders are approved, NS items have
  nothing to be checked against — the interface reports this rather than implying a pass.
- **The home page hero shows illustrative figures**, not live ones.

---

## Acknowledgements

Built during an internship with the Construction department of North Western Railway,
Jaipur, whose officers explained how vetting actually works — including the parts that do
not appear in any manual.
