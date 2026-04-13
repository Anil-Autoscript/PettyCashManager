# 💰 Petty Cash Manager

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square&logo=kotlin" />
  <img src="https://img.shields.io/badge/Architecture-MVVM-blue?style=flat-square" />
  <img src="https://img.shields.io/badge/Backend-Google%20Apps%20Script-orange?style=flat-square&logo=google" />
  <img src="https://img.shields.io/badge/Build-GitHub%20Actions-black?style=flat-square&logo=github-actions" />
  <img src="https://img.shields.io/badge/Min%20SDK-24-red?style=flat-square" />
</p>

> A professional multi-company petty cash management Android app powered by Google Sheets as a backend database.

---

## ✨ Features

| Feature | Details |
|---|---|
| 🏢 Multi-Company | Each company has its own isolated Google Sheet |
| 🔐 Secure Login | SHA-256 hashed passwords, EncryptedSharedPreferences |
| 💵 Cash In & Expenses | Full transaction tracking with categories |
| 🖼️ Bill Photos | Upload receipt images to Google Drive |
| ✅ Approval System | Optional manager-approval workflow for expenses |
| 📊 Dashboard | Live balance, totals, pending approvals |
| 📋 Reports | Filter by type/category/date, export to Excel |
| 🔄 Offline Support | Room SQLite cache + WorkManager background sync |
| 📤 Excel Export | Apache POI-powered .xlsx export with summary |
| 🎨 Material Design | Clean white + blue theme, bottom navigation |

## 🏗️ Architecture

```
MVVM  +  Room (SQLite)  +  Retrofit (Google Apps Script API)  +  WorkManager
```

## 🚀 Quick Start

1. **Create Master Google Sheet** → Copy Sheet ID
2. **Deploy Apps Script** (`google-apps-script/Code.gs`) → Copy Web App URL
3. **Set URL** in `app/build.gradle` → `MASTER_SCRIPT_URL`
4. **Push to GitHub** → Actions builds the APK automatically
5. **Install APK** → Setup company → Login → Done!

📖 **Full setup guide**: [SETUP_GUIDE.md](SETUP_GUIDE.md)

## 📁 Project Structure

```
PettyCashManager/
├── .github/workflows/build.yml       # CI/CD — auto-build APK
├── app/src/main/
│   ├── java/com/pettycash/manager/
│   │   ├── data/
│   │   │   ├── local/               # Room DB, DAOs, PreferenceManager
│   │   │   ├── model/               # Data classes
│   │   │   ├── remote/              # Retrofit API service
│   │   │   └── repository/          # Single source of truth
│   │   ├── ui/
│   │   │   ├── splash/              # Routing screen
│   │   │   ├── setup/               # Company setup
│   │   │   ├── login/               # Authentication
│   │   │   ├── main/                # Main activity + nav
│   │   │   ├── dashboard/           # Home screen
│   │   │   ├── transaction/         # Add transaction
│   │   │   ├── reports/             # Filters + export
│   │   │   └── profile/             # Settings + logout
│   │   ├── utils/                   # Helpers
│   │   └── workers/                 # Background sync
│   └── res/                         # Layouts, drawables, values
├── google-apps-script/Code.gs        # Full backend API
└── SETUP_GUIDE.md                    # Step-by-step instructions
```

## 🔒 Security

- Master Sheet ID is **never** exposed to client apps
- Every API call validates `companyId` before data access
- Passwords are SHA-256 hashed **before** leaving the device
- Local secrets stored in **AES-256-GCM** EncryptedSharedPreferences
- Google Drive image folders are company-scoped

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Architecture | MVVM + LiveData |
| Local DB | Room (SQLite) |
| Networking | Retrofit + OkHttp |
| Backend | Google Apps Script (doGet/doPost) |
| Database | Google Sheets (per company) |
| Image storage | Google Drive |
| Background sync | WorkManager |
| Excel export | Apache POI |
| Image loading | Glide |
| UI | Material Components 3 |
| CI/CD | GitHub Actions |

## 📲 Build

### Debug (push to main):
```bash
git push origin main
# → APK artifact available in Actions
```

### Release (create tag):
```bash
git tag v1.0.0 && git push origin v1.0.0
# → Signed APK + GitHub Release created automatically
```

## 📄 License

MIT License — free for personal and commercial use.
