# Google AI Studio handoff — simple CSP demonstration

Review 0 is complete. The team now wants a small working demonstration, operated by students. Citizen email/password entry should be replaced with name/mobile details. The user's currently installed APK is still the earlier local-storage version.

## Current setup

The existing Supabase project has the private bucket, RLS-enabled tables, officer account, initial migration, and additive guest-details migration configured. Anonymous Sign-Ins are currently disabled: a live request returned `anonymous_provider_disabled`. Enable them at:
https://supabase.com/dashboard/project/gkdksqpxkzxeqmzurhub/auth/providers

The citizens still have private background guest identities. Name/mobile are self-entered contact information; they are not verified identity and must never be used alone to retrieve another installation's complaints. Officer roles come from the protected profiles table, not editable metadata.

## Android prompt

Back up the current AI Studio export. Paste this into the EXISTING Android project, and supply the revised source and SQL contract using the file/code facilities available in that workspace.

```text
Continue my existing CleanTrack native Android citizen app for a college CSP
demonstration. Keep Kotlin, Jetpack Compose, CameraX, my package/application ID,
and newer working UI changes. Do not create a replacement web citizen app.

Apply the supplied revised source. Replace citizen registration/login screens
with Name, Mobile number, and Continue. Require a non-empty name up to 100
characters and a 10-digit Indian mobile number starting with 6, 7, 8, or 9.
Do not collect Aadhaar, email, a citizen password, or an OTP in this prototype.

Create a Supabase anonymous user in the background, with full_name and
mobile_number in signup data. The REST endpoint is POST /auth/v1/signup with
{"data":{"full_name":name,"mobile_number":mobile}} and the public apikey header.
The response contains a session. Persist its tokens using the supplied Android
Keystore store and restore the same guest identity after app restart. Do not
create another guest user every time the app opens. Anonymous Sign-Ins must be
enabled in the project. Explain this setup error if the provider is disabled.

Read SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY through the existing .env Secrets
Gradle setup. Never put service-role, sb_secret, or Gemini secrets in the APK.
Remove unused Firebase plugins/dependencies and fix actual compiler errors.

Use profiles (id, full_name, role, mobile_number), complaints, and complaint_events.
The new profile trigger forces citizen role and stores a valid entered mobile.
The submit_complaint RPC copies name and citizen_mobile_number from that profile.
Do not let the client select citizen_id, promote roles, or write complaint rows
directly. Do not change the officer account or database RLS to public access.

Upload the real camera photo to PRIVATE complaint-images storage at
<auth-user-id>/reports/<submission-uuid>.jpg. Call submit_complaint with
p_submission_id, p_category, p_photo_path, p_latitude, p_longitude,
p_location_address, and p_description. The RPC returns a row array.
Retain an unchanged draft's UUID/path on retry to prevent duplicate complaints.

Fetch only the current guest's complaints and events, display private photos
through signed URLs, refresh every 10 seconds while the citizen screen is active,
and provide Refresh. Update an open detail from the latest row so the officer's
status, note, and after-photo appear. Require a real camera image and actual GPS.
No gallery picker, manual location, synthetic capture/GPS, seeded reports, or
random AI outcomes. Keep exactly the five supplied complaint categories.
Verification says "Manual review; AI screening not configured".

No citizen sign-out button or phone-number-based complaint lookup is needed.
History is retained only through this app installation's saved guest session.
Explain that clearing app data or losing that session loses access. Do not add
cross-device recovery, notifications, rewards, a new officer app flow, or
automatic escalation in this task.

Build the Android app, fix real errors, and report the actual build result.
Check name/mobile validation, disabled guest provider error, guest-session
restoration, submission/retry, and status refresh. Camera/GPS acceptance and the
shared phone/dashboard flow must be checked on a physical phone. Do not claim
those tests passed based on compilation alone.
```

The current project link supplied by the user is:
https://ai.studio/apps/25087f4d-5584-4a89-a2ef-d307235a1a68

GitHub changes do not update that project automatically. The source export lacks a Gradle wrapper; use the managed AI Studio build or Android Studio's Gradle/SDK environment.

## Officer dashboard

Use the supplied static `officer-dashboard/` folder. It already includes login, queue/filter/search, photo/location, entered citizen mobile, assignment, history, and resolution with mandatory evidence. The team signs in with the provisioned officer account.

Run `start-officer-dashboard.cmd` on Windows or:
`python3 -m http.server 8000 --bind 127.0.0.1 --directory officer-dashboard`
Then open `http://localhost:8000`.

An optional separate AI Studio web project may adapt the view layer, but must preserve the existing guarded RPC and private-storage contract. There is no reason to rebuild the dashboard before testing the included version.

## Acceptance checklist

| Action | Required result |
| --- | --- |
| First app launch | Name/mobile/Continue; no citizen email/password screen |
| Blank name or invalid mobile | Clear validation error |
| Continue with valid test details | One private guest session and citizen profile |
| App restart | Same guest's complaints, not a new user |
| Submit real camera/GPS report | Same complaint ID and private evidence in Supabase/officer desk |
| Officer opens report | Correct name and self-entered mobile displayed |
| Officer records In Progress and note | Citizen receives status/note on refresh |
| Resolve without photo | Client and server refuse |
| Resolve with cleanup photo/note | Citizen receives Resolved, after-photo, and history |
| Another installation/guest | Cannot read the first guest's private reports |
| Citizen calls officer RPC/edits role | Database denies access |
| Network, camera, or location failure | Retry/error rather than fabricated evidence |
| Retry after lost submission response | Same complaint ID for unchanged draft |
| Two stale officer edits | Second save rejected until refresh |

Contact numbers are not OTP verified. Re-entering the same mobile on another phone must not restore another guest's records.

## Scope for the next review

Show the full citizen-to-officer-to-citizen loop. Municipal staff participation, community impact, and real cleanup are not established by a team-operated rehearsal. Use clearly labeled test records/photos.

AI image relevance screening and deadline-based escalation are planned. The municipal hierarchy/time limits are unconfirmed. Mention escalation in the future-feature section until it is implemented and tested with clearly labeled demonstration rules. Field-team information can go in officer notes; the current assignment field selects a responsible officer.

Keep the presentation short: one problem/evidence slide, one workflow, feature demonstrations, actual technology/progress, and next steps. The original Review 0 deck records the earlier architecture; the next deck must reflect the demonstrated version.

## Verification record

Ten officer API tests pass. JavaScript syntax, 48 HTML references, and 10 XML files pass. The live database guest-claim tests validate contact storage, forced citizen role, profile isolation, denied role edits/officer updates, and required uploaded evidence, using rolled-back fixtures. No test complaints were seeded.

The live anonymous signup is blocked pending provider activation. Android compilation and physical-phone acceptance remain pending. The supplied fixture-based Playwright test has not passed here because Chromium's download failed. No production-readiness claim is made.

The database advisors still report intentionally guarded authenticated SECURITY DEFINER functions and disabled leaked-password protection:
https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable
https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection

## Official references

- Anonymous sessions: https://supabase.com/docs/guides/auth/auth-anonymous
- RLS: https://supabase.com/docs/guides/database/postgres/row-level-security
- Private storage: https://supabase.com/docs/guides/storage/security/access-control
- AI Studio Android: https://ai.google.dev/gemini-api/docs/aistudio-android
