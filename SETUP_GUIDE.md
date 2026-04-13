# Petty Cash Manager — Complete Setup & Deployment Guide

---

## Table of Contents
1. [Project Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Step 1 — Create the Master Google Sheet](#step-1)
4. [Step 2 — Deploy the Google Apps Script](#step-2)
5. [Step 3 — Configure the Android Project](#step-3)
6. [Step 4 — Build via GitHub Actions](#step-4)
7. [Step 5 — First-Time App Setup](#step-5)
8. [Google Sheet Structure Reference](#sheet-structure)
9. [Security Notes](#security)
10. [Troubleshooting](#troubleshooting)

---

## 1. Project Overview <a name="overview"></a>

Petty Cash Manager is a multi-tenant Android application that:
- Supports **multiple companies**, each with a completely isolated Google Sheet database
- Uses a **private Master Sheet** to map Company IDs → Sheet IDs (never exposed to users)
- Stores all transaction/user data in the **company's own sheet** (zero cross-company access)
- Works **offline** with automatic background sync via WorkManager
- Exports data to **Excel** with full financial summaries
- Uploads bill images to **Google Drive** per company folder

---

## 2. Prerequisites <a name="prerequisites"></a>

| Requirement | Details |
|---|---|
| Google Account | For Sheets, Drive, Apps Script |
| Android Studio | Flamingo or later (for local builds) |
| GitHub Account | For CI/CD via Actions |
| JDK | 17 (set in Actions workflow) |
| Android Device | API 24+ (Android 7.0+) |

---

## 3. Step 1 — Create the Master Google Sheet <a name="step-1"></a>

1. Go to [sheets.google.com](https://sheets.google.com) and create a **new blank spreadsheet**.
2. Name it: `PettyCash_MASTER` *(keep this private — never share)*
3. **Do NOT add any sheets manually.** The script's `initMasterSheet()` will set them up.
4. Copy the **Sheet ID** from the URL:
   ```
   https://docs.google.com/spreadsheets/d/THIS_IS_YOUR_SHEET_ID/edit
   ```
5. Keep this ID safe — you'll paste it in Step 2.

> ⚠️ **IMPORTANT**: Never share this Master Sheet ID publicly. It contains company registry data.

---

## 4. Step 2 — Deploy the Google Apps Script <a name="step-2"></a>

### 4a. Open Apps Script

1. From the Master Sheet, click **Extensions → Apps Script**.
2. Delete all default code in `Code.gs`.
3. Copy the entire contents of `google-apps-script/Code.gs` from this project and paste it.

### 4b. Set Your Master Sheet ID

Find this line near the top:
```javascript
var MASTER_SHEET_ID = 'YOUR_MASTER_GOOGLE_SHEET_ID';
```
Replace `YOUR_MASTER_GOOGLE_SHEET_ID` with the ID you copied in Step 1:
```javascript
var MASTER_SHEET_ID = '1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms';  // example
```

### 4c. Initialise the Master Sheet

1. In the Apps Script editor, select `initMasterSheet` from the function dropdown.
2. Click **Run (▶)**.
3. Grant all requested permissions (Drive, Sheets, etc.).
4. You'll see "Master Sheet initialised successfully!" if it works.
5. Check your Master Sheet — it should now have `Companies` and `Users` tabs.

### 4d. Deploy as Web App

1. Click **Deploy → New Deployment**.
2. Click the ⚙️ gear icon → **Web app**.
3. Set the following:
   - **Description**: `Petty Cash Manager API v1`
   - **Execute as**: `Me` (your Google account)
   - **Who has access**: `Anyone`  ← Required for the Android app to call it
4. Click **Deploy**.
5. **Copy the Web App URL** — it looks like:
   ```
   https://script.google.com/macros/s/AKfycbxxxxxxxxxxxxxxxx/exec
   ```
6. Keep this URL — you'll paste it in Step 3.

> 💡 Every time you **modify** the script, you must click **Deploy → Manage Deployments → Edit → New Version** to publish the changes.

---

## 5. Step 3 — Configure the Android Project <a name="step-3"></a>

### 3a. Set the Script URL

Open `app/build.gradle` and replace the placeholder:

```groovy
buildConfigField "String", "MASTER_SCRIPT_URL", "\"https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec\""
```

Replace with your actual URL:
```groovy
buildConfigField "String", "MASTER_SCRIPT_URL", "\"https://script.google.com/macros/s/AKfycbXXXXXXXXXX/exec\""
```

### 3b. Add GitHub Repository Secrets (for signing release builds)

In your GitHub repo → **Settings → Secrets and variables → Actions**, add:

| Secret Name | Value |
|---|---|
| `SIGNING_KEY` | Base64-encoded `.jks` keystore file |
| `ALIAS` | Key alias (e.g., `pettycash`) |
| `KEY_STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |

**To generate a keystore:**
```bash
keytool -genkey -v -keystore pettycash-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias pettycash
```

**To convert to Base64:**
```bash
base64 pettycash-release.jks | tr -d '\n'
```
Paste the output as the `SIGNING_KEY` secret.

---

## 6. Step 4 — Build via GitHub Actions <a name="step-4"></a>

### Automatic builds (on every push to `main`):
```
git add .
git commit -m "Initial commit"
git push origin main
```
→ GitHub Actions automatically builds a **Debug APK** and uploads it as an artifact.

### Release build (on version tag):
```bash
git tag v1.0.0
git push origin v1.0.0
```
→ GitHub Actions builds a **signed Release APK** and creates a GitHub Release.

### Download the APK:
1. Go to your GitHub repo → **Actions**.
2. Click the latest workflow run.
3. Scroll to **Artifacts** at the bottom.
4. Download `PettyCashManager-Debug-XX` or `PettyCashManager-Release-XX`.

---

## 7. Step 5 — First-Time App Setup <a name="step-5"></a>

### On the Android Device:

1. **Install the APK**  
   Enable *Install from Unknown Sources* in Settings if needed.

2. **Company Setup Screen** (first launch only)  
   - Enter your **Company Name**
   - Upload your **Company Logo** (optional but recommended)
   - Enter **Admin Username** and **Password** (min 6 chars)
   - Tap **Create Company** — this calls the Apps Script, which:
     - Creates a new Google Sheet for your company
     - Registers it in the Master Sheet
     - Creates the admin account

3. **Login Screen**  
   - Your Company ID is auto-filled (stored securely)
   - Enter the admin credentials you just created
   - Tap **Sign In**

4. **Dashboard**  
   You're in! Start adding Cash In and Expenses.

### Adding More Users (Admin only):

From **Profile → Manage Users**, call the API to add users with:
- Role: `admin`, `manager`, or `employee`
- Each user logs in with the same Company ID

---

## 8. Google Sheet Structure Reference <a name="sheet-structure"></a>

### Master Sheet (PRIVATE)

**Companies Tab:**
| Column | Field | Example |
|---|---|---|
| A | CompanyID | `uuid-xxxx-xxxx` |
| B | CompanyName | `Acme Corp` |
| C | SheetID | `1BxiMVs0XRA5n...` |
| D | CreatedAt | `2025-01-15T10:30:00Z` |
| E | LogoUrl | `https://drive.google.com/...` |

**Users Tab (Master Registry):**
| Column | Field | Example |
|---|---|---|
| A | UserID | `uuid-yyyy-yyyy` |
| B | CompanyID | `uuid-xxxx-xxxx` |
| C | Username | `john_admin` |
| D | PasswordHash | `sha256(password)` |
| E | Role | `admin` |
| F | IsActive | `true` |
| G | CreatedAt | `2025-01-15T...` |

### Company Sheet (Auto-created per company)

**Transactions Tab:**
| Column | Field |
|---|---|
| A | TransactionID |
| B | CompanyID |
| C | Type (EXPENSE / CASH_IN) |
| D | Amount |
| E | Date (yyyy-MM-dd) |
| F | Category |
| G | Description |
| H | BillImageUrl |
| I | AddedBy |
| J | ApprovalStatus |
| K | ApprovedBy |
| L | CreatedAt |

**Settings Tab:**
| Key | Default Value |
|---|---|
| `approval_enabled` | `false` |
| `logo_url` | *(uploaded URL)* |
| `company_name` | *(company name)* |
| `created_at` | *(ISO timestamp)* |

---

## 9. Security Notes <a name="security"></a>

| Concern | How it's handled |
|---|---|
| Master Sheet access | Sheet ID never sent to client apps; only SheetID from Master is returned |
| Cross-company access | Every API call validates `companyId` before touching any sheet |
| Password storage | SHA-256 hashed on device before transmission; never stored in plain text |
| Local credentials | Stored in Android `EncryptedSharedPreferences` (AES-256-GCM) |
| Image access | Drive files set to "anyone with link" — URLs are UUID-based (not guessable) |
| API key | No API key needed — Apps Script uses Google OAuth internally |
| HTTPS | All API calls use HTTPS (Google Apps Script enforces this) |

### What to Keep Private:
- ✅ The **Master Sheet ID** (never in code, never shared)
- ✅ Your **Google Account** credentials
- ✅ The `.jks` **keystore file** (for signing releases)
- ✅ The `MASTER_SCRIPT_URL` should be in `buildConfigField` only, not hardcoded in source

---

## 10. Troubleshooting <a name="troubleshooting"></a>

### "Invalid company ID" on login
- Verify `MASTER_SHEET_ID` in `Code.gs` is correct and the sheet exists.
- Re-run `initMasterSheet()` and check the Companies tab has data.

### "Network error" on all requests
- Check the Web App URL in `build.gradle` matches exactly what was deployed.
- Re-deploy the script as a **new version** (not just save — must deploy).
- Ensure **Who has access = Anyone** in the deployment settings.

### APK builds fail in GitHub Actions
- Check `SIGNING_KEY` is the correct Base64 string (no newlines).
- Verify the tag format is `v*` (e.g., `v1.0.0`).
- Check **Actions** tab in GitHub for the full error log.

### Images not uploading
- The Apps Script needs **Drive permissions** — re-run authorization by calling `uploadImage` manually in the Apps Script editor.
- Check image size — keep bill photos under 5MB.

### Transactions not syncing
- Toggle airplane mode OFF and pull-to-refresh on Dashboard.
- WorkManager syncs every 15 minutes in background when connected.
- Check **Profile** → Last synced time.

### "Permission denied" on first run
- Apps Script permissions must be granted for: Sheets, Drive, and External requests.
- Go to Apps Script → Run `initMasterSheet` → Grant all permissions when prompted.

---

## Architecture Summary

```
Android App (MVVM)
│
├── UI Layer
│   ├── SplashActivity      → routing logic
│   ├── SetupActivity       → first-time company setup
│   ├── LoginActivity       → authentication
│   └── MainActivity        → bottom nav host
│       ├── DashboardFragment
│       ├── AddTransactionFragment
│       ├── ReportsFragment
│       └── ProfileFragment
│
├── ViewModel Layer         → business logic, LiveData
│
├── Repository Layer        → decides local vs network
│   ├── AuthRepository
│   └── TransactionRepository
│
├── Data Layer
│   ├── Room Database       → offline SQLite cache
│   │   ├── TransactionDao
│   │   └── UserDao
│   ├── PreferenceManager   → EncryptedSharedPreferences
│   └── ApiService (Retrofit) → Google Apps Script HTTP calls
│
└── Workers
    └── SyncWorker          → WorkManager background sync

Google Apps Script (Backend)
│
├── doGet()   → getTransactions, getDashboard, login, getUsers
├── doPost()  → addTransaction, updateApproval, addUser, uploadImage
│
├── Master Sheet (PRIVATE)
│   ├── Companies: CompanyID → SheetID mapping
│   └── Users: all user credentials
│
└── Company Sheets (auto-created, one per company)
    ├── Transactions
    ├── Users
    └── Settings
```
