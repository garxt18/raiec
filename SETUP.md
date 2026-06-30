# RAIEC — Setup & Run Guide

**RAIEC** (Railway Automated Intelligent Estimate Checker) vets construction tender estimates:
it ingests IREPS tender PDFs, extracts line items, rate-matches them against IRUSSOR / CPWD DSR
and the LAR dataset, runs AI checks, and supports an officer approve/reject workflow.

This guide explains how to run it from the project folder (e.g. an unzipped copy).

---

## TL;DR — what's required vs optional

| Part | Required? | Notes |
|------|-----------|-------|
| **PostgreSQL** | ✅ Required | Stores all data. Tables are created automatically on first run. |
| **Backend (Spring Boot)** | ✅ Required | The API on `http://localhost:8080`. |
| **Frontend (open `index.html`)** | ✅ Required | Static pages; no build step. |
| **Rate-book imports (DSR / IRUSSOR)** | ⛔ Optional | Only enables DSR/IRUSSOR rate comparison. App works fully without it (NS→LAR still matches). |
| **Demo data (bulk-ingest)** | ⛔ Optional | The database starts empty; this populates it from a folder of PDFs. |
| **LLM API key** | ⛔ Optional | The AI assessment falls back to a built-in rule-based version when no key is set. |

> **Note:** The database is **not** part of the zip. A fresh copy starts with an **empty dashboard**
> (the `admin`/`officer` logins are created automatically). Upload a tender or run the bulk-ingest to see data.

---

## 1. Prerequisites

- **Java JDK 21** (the project is built for Java 21).
  - Check: `java -version` should report 21.
- **PostgreSQL** (any recent version, e.g. 15+). Note your **postgres password**.
- A web browser.
- *(No need to install Maven — the `mvnw` wrapper is included.)*

---

## 2. One-time database setup

1. Make sure PostgreSQL is running (default port `5432`).
2. Create a database named **`raiec`**:
   ```sql
   CREATE DATABASE raiec;
   ```
   (Use pgAdmin, or: `psql -U postgres -c "CREATE DATABASE raiec;"`)
3. The default DB user is **`postgres`**. If your user/db/port differ, edit
   `backend/src/main/resources/application.properties` (`spring.datasource.url` / `username`).

Tables are created automatically on the first backend run (`ddl-auto=update`).

---

## 3. Start the backend

**Windows (PowerShell)** — from the project root:
```powershell
cd backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'   # change to YOUR JDK 21 path
$env:DB_PASSWORD='your_postgres_password'
.\mvnw.cmd spring-boot:run
```

**macOS / Linux:**
```bash
cd backend
export DB_PASSWORD='your_postgres_password'
./mvnw spring-boot:run
```

Wait until you see **`Started RaiecApplication`**. The API is now live at `http://localhost:8080`.
On first start it auto-creates two logins:

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `admin@123` |
| Officer | `officer` | `officer@123` |

> These are demo credentials — change them (and set `RAIEC_JWT_SECRET`) before any real deployment.

---

## 4. Open the frontend

Open **`index.html`** (in the project root) in your browser, then click **Login**.

- If your browser blocks the API calls from a `file://` page, serve the folder instead, e.g. from the project root:
  ```bash
  python -m http.server 5500
  ```
  then open `http://localhost:5500/index.html`.

Log in with `admin / admin@123`. You'll land on the dashboard (empty until you add tenders).

---

## 5. Add tenders

**Option A — one at a time (UI):** Upload Estimate → drop a tender PDF → step through to AI analysis → send to officer review → approve.

**Option B — bulk (many at once):**
1. Put tender PDFs in `sample-tenders/batch/`.
2. Get an admin token and trigger the ingest (PowerShell):
   ```powershell
   cd backend
   Set-Content -Path login.json -Value '{"username":"admin","password":"admin@123"}' -Encoding ascii
   $token = (curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" --data-binary "@login.json" | ConvertFrom-Json).token
   $report = curl.exe -s -m 600 -X POST "http://localhost:8080/api/admin/bulk-ingest" -H "Authorization: Bearer $token" | ConvertFrom-Json
   $report | Select-Object totalFiles, ingested, duplicates, errors, totalLarAdded | Format-List
   ```
   Each PDF is uploaded, rate-matched, AI-analysed, and approved (growing the LAR dataset).

---

## 6. (Optional) Load the rate books — DSR & IRUSSOR

Only needed if you want DSR/IRUSSOR **rate comparison** to show variance. Without it, those items
appear as "No reference" (NS items still match the LAR dataset normally).

1. Place the rate-book PDFs in a `rate-books/` folder at the project root:
   - `irussor-2021.pdf`
   - `dsr-2021-vol1.pdf`
   - `dsr-2021-vol2.pdf`
2. With an admin token (see step 5), run:
   ```powershell
   curl.exe -s -X POST "http://localhost:8080/api/reference/import/irussor" -H "Authorization: Bearer $token"
   curl.exe -s -X POST "http://localhost:8080/api/reference/import/dsr" -H "Authorization: Bearer $token"
   ```
   (DSR can take ~30–60s — it parses two large volumes.)

---

## 7. (Optional) Enable the real AI LLM

The **AI assessment** on the analysis page works out of the box using a built-in rule-based summary.
To have a real model write it instead, set an OpenAI-compatible key **before** starting the backend:

```powershell
$env:RAIEC_LLM_API_KEY='your_key'
# Optional: point at another provider (e.g. Groq):
$env:RAIEC_LLM_BASE_URL='https://api.groq.com/openai/v1'
$env:RAIEC_LLM_MODEL='llama-3.1-8b-instant'
```
The assessment's source label flips from "Rule-based assessment" to "Generated by AI model".
If the model call fails, it silently falls back to the rule-based text.

---

## Troubleshooting

- **Dashboard is empty** → the database has no tenders yet. Upload one or run the bulk-ingest (step 5).
- **401 Unauthorized** → you're not logged in (or the token expired after 8 hours). Log in again.
- **400 on login from PowerShell `curl`** → use the file-based body shown in step 5 (`--data-binary "@login.json"`), or `Invoke-RestMethod`. The browser login is unaffected.
- **Port 8080 already in use** → stop the other process, or change `server.port` in `application.properties`.
- **`java -version` is not 21** → install/point `JAVA_HOME` to JDK 21.
- **Backend won't connect to DB** → check PostgreSQL is running, the `raiec` database exists, and `DB_PASSWORD` matches your postgres password.
