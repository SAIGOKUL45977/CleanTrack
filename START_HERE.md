# CleanTrack — simple CSP prototype

Your current APK still uses local storage. This revised source removes citizen email/password entry and connects citizen reports to the team-operated officer dashboard.

## 1. Enable one Supabase setting

Open [Authentication providers for your project](https://supabase.com/dashboard/project/gkdksqpxkzxeqmzurhub/auth/providers).

Find **Anonymous Sign-Ins** and enable/save it. Navigation may say **Sign In / Providers** or **Providers**. This creates a private reporting session behind the citizen screen; the citizen does not enter an email/password.

The existing database, private bucket, officer account, and name/mobile migration are already configured. Do not repeat the SQL setup or officer promotion.

## 2. Update your existing AI Studio Android project

Download a backup of its current source first. Open the existing project:
https://ai.studio/apps/25087f4d-5584-4a89-a2ef-d307235a1a68

Use the **Android prompt** in [docs/GOOGLE_AI_STUDIO_HANDOFF.md](docs/GOOGLE_AI_STUDIO_HANDOFF.md), together with the revised source and SQL contract. Preserve newer working screens. Keep the app native Android.

Check build properties or an existing `.env`:

```dotenv
SUPABASE_URL=https://gkdksqpxkzxeqmzurhub.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_XNNmhtN6Jkdnjs5f2ky5jg_fHsvUXFH
```

The supplied `.env.example` already has these public values. An existing `.env` overrides it. Use these exact property names without `NEXT_PUBLIC_`.

## 3. Build and install the new APK

Ask AI Studio to build and fix actual compiler errors. Install the rebuilt APK on a physical phone. The initial screen should show **Name**, **Mobile number**, and **Continue**, with no citizen email/password fields.

Use clearly labeled test details for rehearsal. The mobile number is contact information only and is not verified. No Aadhaar is collected. Allow camera and location permissions, and turn on internet/device location.

The app remembers the guest session. Complaints are accessible through that installation. Clearing app data, uninstalling, or losing the session loses access to that history; no recovery feature is included. Preserve any old local records before uninstalling an earlier APK.

## 4. Run the officer dashboard

Extract the full package. On Windows, double-click `start-officer-dashboard.cmd` and keep the terminal open. Python 3 is required. Or run this from the project root:

```powershell
py -m http.server 8000 --bind 127.0.0.1 --directory officer-dashboard
```

Use `python3` instead of `py` on macOS/Linux. Open **http://localhost:8000** on the laptop. Check the prefilled connection values and sign in with the already provisioned officer account and your own password. There is no officer signup button.

The phone and laptop each access Supabase over the internet. The phone does not need the laptop's localhost address.

## 5. Rehearse one complete complaint

1. Enter test citizen details, capture a real garbage photo and GPS, choose a category, and submit. Record the complaint ID.
2. Confirm that same ID, photo, coordinates, name, and entered mobile appear on the officer dashboard.
3. Change the status to **In Progress**, select the responsible officer, add a clear action note, and save.
4. Refresh the citizen app and confirm the status/note changed.
5. Try resolving without a cleanup photo: it must fail. Attach an appropriate after-photo and resolution note, then save **Resolved**.
6. Confirm the citizen sees Resolved, the after-photo, and history. Restart the citizen app and confirm its same-phone history remains available.

Label rehearsals as test reports; a test after-photo is not proof of municipal cleanup. AI and automatic escalation remain planned. Do not present them as working features.

## Troubleshooting

- **Guest reporting disabled:** complete step 1 and retry Continue.
- **Email/password screen still appears:** you are using the old APK or have not applied the revised source.
- **No shared complaint:** inspect the submission error and check internet/project configuration. Check whether Supabase has the complaint row.
- **Officer access denied:** use the provisioned officer account. A guest citizen has no officer permissions.
- **Complaint changed:** reload its current version and reapply the update.
- **Port occupied:** choose another port and open that port in the browser.

The source/backend checks pass. Native compilation and the actual installed-app/dashboard test still need to be completed.
