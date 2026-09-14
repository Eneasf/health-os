# Withings API Integration & Sync Guide

## 1. Overview

The Withings Health API provides direct, programmatic access to all data captured by your Withings smart scale (and other Withings devices). Connecting via the API eliminates the need to manually screenshot or photograph the scale every morning.

### Available Scale Metrics via API
- **Weight (kg)** (`meastype: 1`)
- **Fat Free Mass (kg)** (`meastype: 5`)
- **Body Fat Percentage (%)** (`meastype: 6`)
- **Fat Mass Weight (kg)** (`meastype: 8`)
- **Muscle Mass (kg)** (`meastype: 76`)
- **Hydration / Total Body Water (kg)** (`meastype: 77`)
- **Bone Mass (kg)** (`meastype: 88`)
- **Visceral Fat Index** (`meastype: 168`)
- **Vascular Age / Pulse Wave Velocity (m/s)** (`meastype: 91`)
- **Heart Rate / Pulse (bpm)** (`meastype: 11`)

---

## 2. One-Time Setup Steps (10 Minutes)

### Step 1: Create a Free Withings Developer Application
1. Go to the [Withings Developer Dashboard](https://developer.withings.com/dashboard/).
2. Log in with your standard Withings account.
3. Click **"Create an App"** and fill in:
   - **Application Name:** `My Health Dashboard`
   - **Description:** `Personal health tracking and bio-optimization dashboard`
   - **Callback URL:** `http://localhost:8080/callback` (or `https://wbsapi.withings.net/fake_url`)
4. Copy your **Client ID** and **Consumer Secret (Client Secret)**.

### Step 2: Authorization & Token Retrieval
Run our interactive authorization script:
```bash
python scripts/withings_auth.py
```
1. The script will generate an authorization link for you to open in your browser.
2. Log in and grant permission for `user.metrics` and `user.activity`.
3. After approving, paste the redirected URL or authorization code back into the terminal.
4. The script saves your tokens (including the **refresh token**) into a local, gitignored `config/withings_tokens.json` file.

---

## 3. Daily Automated Sync Architecture

Once authorized:
- **Zero Ongoing Input:** The script (`scripts/sync_withings.py`) runs in the background.
- **Automatic Token Refresh:** When the 3-hour access token expires, the script automatically uses the refresh token to obtain a new token without requiring browser logins.
- **Dual Persistence:**
  - Saves raw JSON to `data/records/withings/YYYY-MM-DD.json`.
  - Upserts daily metrics into the SQLite table `withings_readings` in `health_dashboard.db`.

---

## 4. Alternative Ingestion Paths (Fallback)

If you prefer not to set up the API credentials immediately:
1. **Inbox Screenshot Drop:** Take a screenshot of the Withings App summary screen $\rightarrow$ drop into `data/inbox/` $\rightarrow$ parsed automatically.
2. **Withings Web CSV Export:** Export data from your Withings online dashboard $\rightarrow$ drop CSV into `data/inbox/` $\rightarrow$ imported automatically.
