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
