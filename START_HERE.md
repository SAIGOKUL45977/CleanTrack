# CleanTrack — your next steps

The existing Supabase project, private photo bucket, tables, and citizen/officer roles are ready. Your next task is to apply the prepared Android source to your current Google AI Studio app and test a real report. You do not need to create another Supabase project or rerun the initial SQL.

## 1. Back up the current app

Open your existing Google AI Studio Android project and download its current source ZIP. Keep that backup separately. The prepared migration is based on the source shared here; if your current project has newer screens or fixes, preserve them when applying it.

## 2. Apply the Android migration

Open [docs/GOOGLE_AI_STUDIO_HANDOFF.md](docs/GOOGLE_AI_STUDIO_HANDOFF.md). Paste the Android prompt in section 3 into your existing AI Studio project, and supply the prepared source files/SQL contract using the file or code facilities available in that workspace. Tell AI Studio to compare against the current app and fix compiler errors. Do not replace the Android app with a web app.

The supplied existing project link is https://ai.studio/apps/25087f4d-5584-4a89-a2ef-d307235a1a68 . A GitHub commit does not automatically update that project.

Check the existing `.env` or AI Studio build properties. Use these exact Android property names:

```dotenv
SUPABASE_URL=https://gkdksqpxkzxeqmzurhub.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_XNNmhtN6Jkdnjs5f2ky5jg_fHsvUXFH
```

These are public client values. The supplied `.env.example` already contains them, but an existing `.env` takes precedence. Rebuild after changing configuration.

## 3. Build and install the citizen APK

Ask AI Studio to build the Android project and show the actual build result. Download and install that new APK on your physical phone. Use the existing citizen account and your own password. Grant camera and location permissions. Turn on device location and internet access.

The older APK on your phone may still be local-only. Installing the rebuilt app is necessary; configuring Supabase alone does not update an old APK. If testing requires uninstalling the old app, first preserve any local records you need.

## 4. Open the officer dashboard on your laptop

Extract the complete package. On Windows, double-click `start-officer-dashboard.cmd`; keep that terminal open. It requires Python 3. Alternatively, open a terminal in the project root and run:

```powershell
py -m http.server 8000 --bind 127.0.0.1 --directory officer-dashboard
```

On macOS/Linux, use `python3` in place of `py`. Open **http://localhost:8000** in the laptop browser. The dashboard's public project configuration is prefilled; check it if the browser remembers an older project. Sign in with the existing officer account and your own password. There is no officer signup button, and another promotion is unnecessary.

The phone and laptop communicate through Supabase over the internet. The phone does not need to open the laptop's localhost address. This local server is for testing; a public pilot needs HTTPS hosting.

## 5. Verify the shared complaint flow

1. On the rebuilt citizen app, capture a real photo of garbage, lock the actual GPS location, choose one of the five categories, and submit. Note the complaint ID.
2. On the officer dashboard, wait for refresh or refresh manually. Confirm the same ID, photo, category, and coordinates.
3. Select the responsible officer, change the status to **In Progress**, and add a clear note. If a field team is involved, include its name/task in that note. Save.
4. On the citizen app, refresh and confirm the same status and note appear.
5. Try resolving without a cleanup photo: it must be refused. Then attach an appropriate after-cleaning photo, add the resolution note, and save **Resolved**.
6. On the citizen app, confirm **Resolved**, the after-photo, and the full history. Test private access with a second citizen account using the detailed checklist before a live pilot.

Use clearly labeled test reports for rehearsal. Do not present a test after-photo as evidence of an actual cleanup. The current verification label is manual review; real Gemini screening is a later step.

## If something fails

- **Login fails:** check that you are using the correct existing account, confirmed email, and password. Citizen accounts cannot enter the officer dashboard.
- **The old app still shows random AI results or local-only reports:** verify that you installed the rebuilt APK rather than the earlier APK.
- **No shared complaint appears:** record the app's submission error, check internet/project configuration, and check whether a row exists in Supabase's `complaints` table. Do not create a fake row to hide a failed submission.
- **Dashboard remembers another project:** replace its connection values with the ones above, then sign in again.
- **“Complaint changed” while saving:** reload the current record, review the other update, and reapply your note/status.
- **Port 8000 is occupied:** use another port in the command and open that same port in the browser.

The source/API tests have passed, but the native build and live phone/dashboard test have not yet been completed here. Record the result of those steps before claiming the integration works.
