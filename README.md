# ETF Dip Scanner (Android)

A small native Android app that runs your "Mack ETF Strategy" screen against
**your own Google Sheet** and alerts you at **3:15 PM IST** with the ETF(s)
that qualify.

## Why it reads your sheet
Your sheet already pulls live quotes via GoogleFinance. Rather than depend on a
separate (and often laggy) market API, the app downloads the sheet's CSV export,
so the app always shows the same numbers you'd see opening the sheet yourself.

Data source (already wired in `StrategyConfig.SHEET_CSV_URL`):
`.../export?format=csv&gid=0`

> The sheet must stay shared as **"Anyone with the link – Viewer"** for the
> export to work without login. It currently is.

## Filters (now editable inside the app)
Open the app and edit any of the four values in the **Filters** card, then tap
**Apply** — the results below update instantly against the last fetched data.
**Refresh** (top-right) pulls the latest GoogleFinance rows from your sheet and
re-runs the filters. Your edits persist across restarts, and the 3:15 PM alert
uses the same saved values.

- 1-day change  ≤  (default -1%)
- 30-day split  (default -2.5%)
- Volume  >  (default 5,00,000)
- Traded value  >  (default ₹2 Cr)

### Two result sets (so you never have to pick the 30-day direction blind)
- **Set A — dip, stable:** 30-day **> split** (the sheet's own rule)
- **Set B — dip, falling:** 30-day **< split** (your original filter)

Both use the same 1-day, volume and value conditions. A third list shows ETFs
that are down >=1% today but blocked by volume/value, with the reason.

The 3:15 PM alert is pinned to `Asia/Kolkata`, so it fires at the right India
time even though your phone is on Gulf time. Reset button restores defaults.

## Get the APK with ZERO local setup (GitHub Actions)
If you don't want to install Android Studio, let GitHub build the APK for you:

1. Create a new **empty GitHub repo** (private is fine).
2. Push this project to it:
   ```
   cd etf-scanner
   git init
   git add .
   git commit -m "ETF scanner"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
3. The push auto-triggers the **Build APK** workflow (`.github/workflows/build.yml`).
   Open the repo's **Actions** tab, wait for the green check (~3-5 min).
4. Open that run, scroll to **Artifacts**, download **etf-scanner-debug-apk**.
   Unzip it to get `app-debug.apk`.
5. Copy the APK to your phone and tap it to install (you'll need to allow
   "install from unknown sources" for your file manager the first time).

You can also re-run the build anytime from the Actions tab
(**Build APK -> Run workflow**) — handy after you change default thresholds.

> This is a **debug-signed** APK: perfect for personal use. It can't be uploaded
> to the Play Store as-is (that needs a release signing key), but it installs and
> runs on your own phone with no problem.

---

## Build & install (Android Studio)
1. Open Android Studio (Koala/Ladybug or newer) → **Open** → select this folder.
2. Let Gradle sync (it downloads AGP 8.5.2, Kotlin 2.0.21, Compose BOM).
3. Plug in your phone (USB debugging on) or start an emulator.
4. Press **Run ▶**. First launch asks for notification permission — allow it.

To get a shareable APK instead: **Build → Build APK(s)**, then copy
`app/build/outputs/apk/debug/app-debug.apk` to your phone and install.

## What each screen/piece does
- `MainActivity.kt` — Compose UI. Shows what qualifies *now* plus a second list
  of ETFs that are down ≥1% today but blocked by another rule (with the reason).
- `SheetRepository.kt` — downloads + follows Google's redirects, returns rows.
- `EtfModels.kt` — CSV parsing + the filter logic + all your thresholds.
- `ScanWorker.kt` — the background scan + notification.
- `App.kt` — schedules the daily 3:15 PM IST run via WorkManager.

## Honest limitations
- **Data lag:** GoogleFinance quotes can trail the live market by up to ~20 min,
  and the exact 1-day % right at 3:15 may differ slightly from your broker's tick.
  For execution, still confirm the number in Dhan. For a truly live scan, Dhan's
  own in-app screener/alerts (same 4 conditions) will beat any GoogleFinance feed.
- **Background timing:** Android may shift the exact fire time by a few minutes
  under Doze/battery optimisation. If precise timing matters, exclude the app from
  battery optimisation in system settings.
- Not investment advice — it screens data against your rules; the buy/skip call
  is yours.
