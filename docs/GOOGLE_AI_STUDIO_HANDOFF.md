# CleanTrack Supabase migration and Google AI Studio guide

Keep the existing Google AI Studio Android citizen app. Replace its Firebase backend configuration and local complaint flow with Supabase, then connect a separate officer web app to the same project. The first milestone is a real citizen-to-officer-to-citizen update. Add AI screening after this flow passes live testing.

The prepared source is based on the repository and archive you shared. If you have made newer changes inside AI Studio, keep those changes and adapt the migration to that newer code. Download a backup ZIP of the current AI Studio project before editing.

## 1 What changes

| Service or feature | Supabase version |
| --- | --- |
| Firebase Authentication | Supabase Auth with email/password accounts |
| Firestore complaint data | PostgreSQL `complaints` table |
| Firebase image storage | Private `complaint-images` Storage bucket |
| Firebase access rules | PostgreSQL row-level security and validated RPCs |
| Officer authorization | `profiles.role`, assigned only by the administrator |
| Local status demonstration | Officer updates saved to the shared database |
| Random AI verification | Removed; honest manual-review label |
| Future server-side Gemini | Supabase Edge Function with a server-held Gemini secret |

Both clients need the same Supabase project URL and public publishable/legacy anon key. They do not connect directly to one another. They read and update shared records through Supabase.

The prepared Android `.env.example` and officer `config.mjs` now use your existing `cleantrack-csp` project at `https://gkdksqpxkzxeqmzurhub.supabase.co`. The supplied publishable key was accepted by the Auth settings API on 7 October 2026. Email signup is enabled and confirmation is required. The Data API could not find `public.profiles` in its schema cache; run the migration below before testing complaints. No user accounts, complaints, or database schema were created by this read-only connection check.

## 2 Supabase setup order

1. Create your Supabase project. Record its project URL and public publishable key. The legacy anon key is also supported. Do not select a secret/service-role key.
2. Open SQL Editor and run `supabase/migrations/202610070001_cleantrack.sql` from the prepared branch.
3. Check that `profiles`, `complaints`, and `complaint_events` exist and RLS is enabled. Check Storage: `complaint-images` must be private and have a 5 MB limit.
4. Create and confirm three test accounts: citizen A, citizen B, and officer A. Registration creates a citizen profile even if a client submits a role in signup metadata.
5. Replace `REPLACE_WITH_OFFICER_EMAIL` in `supabase/promote-officer.sql` with officer A's actual account email, then run that script as administrator.
6. Keep citizen A and citizen B as citizens. Do not expose a role selector in either client.
7. The prepared Android `.env.example` already contains this project's public values. Update any existing AI Studio `.env` to match using `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` (without `NEXT_PUBLIC_`). Rebuild after editing them.
8. The prepared dashboard's connection section is prefilled from `config.mjs`. Check the values if the browser remembers an older project. The public configuration is remembered on that browser; the login session is stored for that tab/session.

The prepared clients use Supabase's REST APIs, available under `/auth/v1`, `/rest/v1`, and `/storage/v1`. Camera, location, or network errors produce an error/retry flow instead of a fabricated complaint.

## 3 Prompt for your EXISTING Android project

Paste this into the chat in your existing Google AI Studio Android project. Supply the prepared changed files and SQL contract from the branch through the Code panel/file attachments supported by your current workspace. A GitHub change alone does not update an AI Studio Android project.

```text
Continue my existing CleanTrack native Android app. Keep Kotlin, Jetpack Compose,
CameraX, my package/application ID, and my existing citizen screens. Migrate the
backend from Firebase/local demo storage to Supabase. Do not create a replacement
web citizen app.

Use the provided CleanTrack migration and changed Kotlin files as the contract.
First compare them with my current project; preserve any newer working UI changes.

Implement email/password Supabase Auth, confirmed-email handling, session restore,
token refresh, and Android Keystore encrypted token storage. Do not save passwords
or accept a fixed "password" bypass. Read public SUPABASE_URL and
SUPABASE_PUBLISHABLE_KEY through the existing .env Secrets Gradle configuration.
Remove unused Firebase plugins/dependencies and fix any resulting build errors.

Use public.profiles, public.complaints, and public.complaint_events. Upload a real
captured photo into the PRIVATE complaint-images bucket at
<auth-user-id>/reports/<submission-uuid>.jpg. Submit through submit_complaint with
p_submission_id, p_category, p_photo_path, p_latitude, p_longitude,
p_location_address, and p_description. The RPC returns a row array. Keep an
unmodified draft's UUID on retry so a lost response does not duplicate a complaint.

Fetch only the signed-in citizen's complaints and their status events. Render
private photos through signed URLs. Refresh every 10 seconds while the screen is
active, and provide a Refresh action. Update an open complaint detail from the
latest fetched row so officer status, notes, and after-photo become visible.

Remove random AI outcomes, seeded complaints, synthetic photos, and random GPS
fallbacks. Require a real camera image and device location. If permission, camera,
GPS, or network access fails, explain the error and allow retry. The verification
label is "Manual review; AI screening not configured".

Build the app and resolve actual compiler errors. Check real login, invalid login,
registration with email confirmation, restored session, failed upload/retry, and
logout. The AI Studio Android emulator does not provide real camera capture or
Google Play services, so leave camera/GPS acceptance for a physical phone. Report
what was actually tested and what remains. Do not claim the backend works before
we provide the project configuration and complete the two-device test.
```

The changed source includes `SupabaseApi.kt`, `EncryptedSessionStore.kt`, `CleanTrackRepository.kt`, the ViewModel, login defaults, complaint detail refresh, camera/GPS error handling, backup exclusions, `.env.example`, and Gradle cleanup.

## 4 Officer dashboard

The prepared `officer-dashboard/` folder can run as a static website. Test it locally first. For a Google AI Studio web project, use a separate project so the officer interface is independent of the Android app.

```text
Create a responsive CleanTrack officer WEB dashboard connected to my existing
Supabase project. Use the supplied officer-dashboard files, styles, and SQL API
contract. Keep its complaint queue/detail workflow. If your environment requires
React, adapt the view layer without changing the Supabase schema or RPC contract.

Use Supabase email/password sign-in. After login, fetch public.profiles for the
current user and allow the dashboard only when role = "officer". The database
must also enforce access; do not rely on a frontend role check alone. Ordinary
citizen accounts must be denied access to the officer desk.

Show complaint counts, a searchable queue, category/status filters, private
citizen photo, location link, description, citizen name, assigned officer,
status events, and cleanup evidence. Do not seed production data or invent AI
results. Load all complaint pages, not just the first API page.

Save officer updates ONLY through officer_update_complaint with p_id,
p_expected_updated_at (the original exact server timestamp), p_status, p_note,
p_assigned_to, and p_resolution_photo_path. Validate available transitions and
require a note. Upload a cleanup image at
<officer-auth-id>/resolutions/<random-uuid>.<jpg/png/webp> and require it to resolve.
The RPC returns a row array. Show edit-conflict errors and let the officer reload
before saving. Do not erase a note being edited during periodic refresh.

Use private Storage signed URLs. Use only the public publishable/legacy anon key;
never include sb_secret, service_role, or a Gemini secret. Do not create a citizen
complaint database in browser localStorage. Refresh every 5 seconds while the tab
is visible and stop polling after logout. Label this as periodic refresh, not a
Supabase Realtime subscription. Render citizen text safely.

Build and check desktop/mobile layouts, login errors, citizen access denial,
assignment, valid/invalid status changes, required cleanup photo, conflicting
edits, session expiration, and logout. Connect to the same Supabase project as
my citizen Android app and perform the live two-device test below.
```

## 5 Live acceptance test before the review/demo

| Action | Required result |
| --- | --- |
| Citizen A registers and confirms email | Can sign in using the correct password; wrong password fails |
| Citizen A captures a real photo and GPS and submits | Private image, complaint ID, and first event are saved in Supabase |
| Officer A signs in | Sees the same complaint ID/photo/coordinates |
| Citizen B signs in | Cannot read citizen A's complaint or its signed photo through the API |
| Citizen tries officer RPC or changes their profile role | Database denies the action |
| Officer moves Submitted to In Progress with a note/assignment | Citizen A sees status and note within the refresh interval |
| Officer resolves without cleanup photo | Client and server both refuse |
| Officer uploads cleanup photo and resolves | Citizen A sees Resolved, cleanup evidence, and history |
| Two officers edit the same old version | The second stale update is refused until reloaded |
| Photo/GPS permission is denied or network is lost | Clear error, retry; no simulated evidence is submitted |
| Unmodified submission is retried after a lost response | Same complaint ID, no duplicate report in that wizard session |
| Officer logs out and citizen restarts the app | Officer session clears; citizen session restore uses secure saved tokens |

For RLS tests, use the actual citizen JWT/public key through the API. An administrator SQL query bypasses RLS and does not prove citizen isolation. This pilot authorizes each officer across the whole project queue; ward-based access is not implemented.

## 6 Migration of any existing Firebase data

The shared repository currently uses Room for login/complaint records and has Firebase dependencies. Its seeded records and simulated statuses are not imported into Supabase. Your current AI Studio project may contain newer Firebase integration not present in that export.

If there are real Firebase Auth users, Firestore complaints, or Storage photos, first export them and inspect the fields. Preserve an old-ID-to-new-ID mapping, move photos into private storage, reconstruct truthful status events, and plan account migration/password reset with the actual Firebase Auth configuration. Do not silently discard real records or copy demo records into the pilot. This separate historical-data migration is not included in the prepared code.

## 7 What to say in the CSP review and change in report/PPT

Use this technology wording:

"The citizen app is built in Kotlin and Jetpack Compose using Google AI Studio.
We have selected Supabase Auth, PostgreSQL, and private Storage for the shared
backend. The officer dashboard will use the same complaint records. The migration
code is prepared; project configuration and real-device end-to-end testing are
in progress. AI relevance screening is a later server-side integration."

After the live acceptance test actually passes, replace the middle sentence with:
"A citizen submission reaches the officer dashboard, and officer progress and
resolution evidence return to the citizen app through the shared Supabase project."

Change Firebase Auth to Supabase Auth, Firestore to PostgreSQL, Firebase Storage to
Supabase Storage, and Cloud Functions to Supabase Edge Functions in the technology
and architecture sections. Describe Edge Functions/Gemini as planned until tested.
Keep the Work Plan & Timeline slide as requested. Do not claim AI accuracy,
authority adoption, or community impact without evidence.

For the demo: citizen A submits, officer A marks In Progress, then attaches cleanup
evidence and resolves; citizen A refreshes and shows the same complaint ID/history.
Use clearly labeled test accounts and photos for rehearsal. A test cleanup photo
is not evidence of an actual community cleanup.

## 8 Verification and remaining work

Ten automated API-client tests passed locally. JavaScript syntax checks passed.
The dashboard's browser test was prepared but could not execute because Chromium
is unavailable here. The Android build and SQL/RLS live execution have not been
verified. Supply the project URL/public key, run the schema in Supabase, build in
AI Studio, and complete the physical-phone/dashboard test before deploying.

Future tasks after the core flow passes: server-side Gemini relevance assistance
with manual fallback, notifications, actual Realtime subscriptions, offline draft
handling, ward-based permissions, retention, and documented community feedback.

## Official documentation

- Google AI Studio Android: https://ai.google.dev/gemini-api/docs/aistudio-android
- Google AI Studio Build: https://ai.google.dev/gemini-api/docs/aistudio-build-mode
- Supabase Kotlin: https://supabase.com/docs/guides/getting-started/quickstarts/kotlin
- Supabase API keys: https://supabase.com/docs/guides/getting-started/api-keys
- Supabase row-level security: https://supabase.com/docs/guides/database/postgres/row-level-security
- Supabase Storage policies: https://supabase.com/docs/guides/storage/security/access-control
