# RAIEC — Deployment Guide

The app is two separate programs, so it deploys to two places, plus a database:

| Part | Host | Cost |
|---|---|---|
| Frontend (HTML/CSS/JS) | **Vercel** | Free |
| Backend (Spring Boot) | **Render** | Free |
| Database (PostgreSQL) | **Neon** | Free |

> Vercel cannot host the backend — it runs serverless JavaScript/Python, not a Java JVM.
> That is why the API goes to Render.

Everything in the repo is already prepared. What remains is creating the three accounts
and pasting values between their dashboards — steps that need your own credentials.

---

## 1. Database — Neon

1. Sign up at **https://neon.tech** with GitHub.
2. Create a project named `raiec`. Pick the region closest to you (Singapore or Mumbai).
3. From the dashboard copy the connection details. Neon gives you something like:
   ```
   postgresql://myuser:mypassword@ep-cool-name-123.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   ```
4. Convert it to the **JDBC** form the backend expects, keeping `sslmode=require`:
   ```
   jdbc:postgresql://ep-cool-name-123.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   ```
   Keep the username and password separately — they go in as their own variables.

Tables are created automatically on first startup (`ddl-auto=update`), so there is no
schema to import. The database starts empty.

---

## 2. Backend — Render

1. Sign up at **https://render.com** with GitHub and authorise access to the `raiec` repo.
2. **New → Blueprint**, select the repo. Render reads `render.yaml` and configures the
   service from `backend/Dockerfile` automatically.
3. Set these environment variables in the dashboard:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | the `jdbc:postgresql://...` URL from step 1 |
   | `DB_USER` | Neon username |
   | `DB_PASSWORD` | Neon password |
   | `RAIEC_JWT_SECRET` | click **Generate** (must be 32+ characters) |
   | `RAIEC_CORS_ORIGINS` | leave blank for now — filled in at step 4 |
   | `RAIEC_LLM_API_KEY` | optional; enables the real LLM assessment |

4. Deploy. The first build takes 5–10 minutes (Maven downloads dependencies).
   You will get a URL like `https://raiec-api.onrender.com`.

5. Verify it is alive:
   ```
   https://raiec-api.onrender.com/api/auth/me
   ```
   A **401 Unauthorized** is the correct response — it proves the app is running and
   security is active. A 502 or timeout means the build failed; check the Render logs.

> **Free tier caveat:** Render spins the service down after ~15 minutes of inactivity, so the
> first request afterwards takes 30–60 seconds to wake it. Open the API URL a minute before
> any demo so it is warm.

---

## 3. Frontend — Vercel

1. Sign up at **https://vercel.com** with GitHub.
2. **Add New → Project**, import the `raiec` repo.
3. Framework preset: **Other**. Leave the build command empty and the output directory as
   the repo root — these are static files with no build step.
4. Deploy. You will get a URL like `https://raiec.vercel.app`.

---

## 4. Connect the two

Two values must now point at each other.

**a) Tell the frontend where the API is.** Edit `config.js` and set:
```js
var RAIEC_DEPLOYED_API = 'https://raiec-api.onrender.com/api';
```
Commit and push — Vercel redeploys automatically.

**b) Tell the backend which frontend may call it.** In Render set:
```
RAIEC_CORS_ORIGINS = https://raiec.vercel.app
```
Save; Render restarts the service.

Without (b) the browser blocks every API call as a cross-origin request, and the site
will look logged-out and empty with CORS errors in the console.

---

## 5. First run

1. Open the Vercel URL and log in with `admin` / `admin@123`.
2. The dashboard will be **empty** — the cloud database is new.
3. Load the rate books (needed for DSR/IRUSSOR comparison). These read PDFs from the
   `rate-books/` folder, which is **not** in the repo, so run this against your **local**
   backend, or upload the PDFs to the server first.
4. Upload tenders from `tenders-to-scan/` through the UI.

---

## 6. Change the default passwords

`admin/admin@123` and `officer/officer@123` are created automatically on first run and are
public knowledge — they are in this repo. Before sharing the URL with anyone, change them.
There is no password-change screen yet, so update the `app_user` rows directly (the column
stores a BCrypt hash, never plain text).

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| Site loads, but no data and CORS errors in console | `RAIEC_CORS_ORIGINS` not set to the exact Vercel URL |
| Every API call 502s, first time only | Render free tier waking up; retry after 60s |
| 401 on every page after login | `RAIEC_JWT_SECRET` changed after tokens were issued; log in again |
| Backend won't start, logs show a connection error | `DATABASE_URL` is not in `jdbc:` form, or `sslmode=require` is missing |
| Frontend still calls localhost | `config.js` not updated, or the browser cached it — hard refresh |

## Testing against a different backend without redeploying

`config.js` accepts an override, which is useful for pointing the live site at your laptop:
```
https://raiec.vercel.app/?api=http://localhost:8080/api
```
The value is remembered in `localStorage`. Clear it with `localStorage.removeItem('raiec_api_base')`.

---

## Keeping the backend awake

Render's free tier suspends a web service after about 15 minutes without traffic. The next
request then waits the better part of a minute while it starts. That is tolerable for you,
who knows why it is slow, and looks broken to anyone you send the link to.

The fix is a scheduled request to `/api/health`, which is public, needs no credentials, and
checks the database — so a `200` proves both the service and Neon are reachable.

### Option A — GitHub Actions (already in this repository)

`.github/workflows/keep-awake.yml` pings the service every 10 minutes between 09:00 and
21:00 IST on weekdays. Nothing to sign up for, and it lives with the code.

1. Push the repository. GitHub picks the workflow up automatically.
2. Open **Actions → Keep Render awake → Run workflow** to test it immediately rather than
   waiting for the schedule.
3. A green run means the service answered. A red one means it did not, which is itself
   worth knowing.

To stop it: **Actions → Keep Render awake → ⋯ → Disable workflow**.

Two caveats worth knowing:

- GitHub delays scheduled runs when it is busy, sometimes by several minutes. For keeping a
  service warm that is harmless; it is not a precision timer.
- GitHub **disables scheduled workflows in a repository with no activity for 60 days**. If
  you stop committing for two months, the pings stop too.

### Option B — cron-job.org (independent of GitHub)

More reliable timing, and unaffected by repository activity.

1. Sign up at **[cron-job.org](https://cron-job.org)** (free).
2. **Create cronjob**.
3. **URL**: `https://raiec-api.onrender.com/api/health`
4. **Schedule**: every 10 minutes. Under the advanced settings you can restrict it to
   working hours, which is worth doing for the reason below.
5. Save, then use **Test run** to confirm a `200`.

The dashboard keeps a history, so it doubles as a simple uptime record.

### Do not ping around the clock

A free Render service has a monthly instance-hour budget. Keeping it awake 24/7 consumes
that budget for hours when nobody is using the site, and can leave it suspended when
someone is. Restricting the pings to working hours is the point of the schedule above, not
an oversight.

If the site needs to be reliably instant for a demonstration, the honest fix is Render's
paid tier, which does not sleep at all.

---

## Schema changes and `ddl-auto=update`

The app uses `spring.jpa.hibernate.ddl-auto=update`, which **adds** tables and columns but
never **alters or drops** anything that already exists. That is fine for new fields and
caused a real failure once already, so it is worth knowing before the next schema change.

Hibernate maps an enum column with a `CHECK` constraint listing the values it knew about at
the time the table was created. Adding a value to the enum in Java does **not** widen that
constraint on an existing database, so every write of the new value fails with:

```
ERROR: new row for relation "tender" violates check constraint "tender_status_check"
```

This happened when `INFO_REQUESTED` was added to `TenderStatus`. The fix on an existing
database is to recreate the constraint with the full list:

```sql
ALTER TABLE tender DROP CONSTRAINT IF EXISTS tender_status_check;
ALTER TABLE tender ADD CONSTRAINT tender_status_check
  CHECK (status IN ('UPLOADED','OCR_EXTRACTED','RATE_MATCHED','AI_ANALYZED',
                    'OFFICER_REVIEW','INFO_REQUESTED','APPROVED','REJECTED'));
```

**A fresh deployment is unaffected** — on an empty database Hibernate creates the constraint
with every current value, so Neon will be correct from the start. Only databases created
before the change need the statement above.

### This is now handled automatically

Running that `ALTER` by hand works exactly once and is forgotten by the next deployment,
which is why the same failure happened a second time when `FILER` was added to `Role`.

`EnumConstraintSync` now runs at startup and rebuilds the check constraint for
`app_user.role` and `tender.status` from the current Java enums. It is idempotent, so a
boot where nothing changed does nothing, and it logs what it allowed:

```
Enum constraint app_user_role_check now allows 'FILER', 'OFFICER', 'ADMIN'
```

If that line is missing from the Render logs after a deploy, the statement above is still
the manual fallback.

**This covers enum values only.** Any other schema change — a column type, a length, a
dropped field — still needs a hand-written `ALTER`. The durable fix remains **Flyway
migrations** with `ddl-auto=validate`, which the application properties already flag as the
intended direction.
