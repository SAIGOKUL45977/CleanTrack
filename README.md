# CleanTrack

A Kotlin and Jetpack Compose citizen Android app, a shared Supabase backend, and a separate officer web dashboard. The migration source is prepared. The existing Supabase project is configured; applying this source to the current AI Studio app, compiling an APK, and completing the physical-phone/dashboard test are still required.

**Start with [START_HERE.md](START_HERE.md).** Detailed prompts and acceptance checks are in [docs/GOOGLE_AI_STUDIO_HANDOFF.md](docs/GOOGLE_AI_STUDIO_HANDOFF.md). Project boundaries are recorded in [docs/PROJECT_CONSTRAINTS.md](docs/PROJECT_CONSTRAINTS.md).

## Shared workflow

The citizen signs in, captures a real photo and device GPS coordinates, and submits a report. Supabase stores the private photo, complaint, and audit event. An authorized officer reviews the same record, records an assignment/progress note, and attaches a cleanup photo before resolving it. The citizen retrieves status, notes, and resolution evidence through periodic refresh.

- `app/`: citizen Android interface and Supabase REST integration.
- `supabase/migrations/202610070001_cleantrack.sql`: tables, RLS, private storage, and guarded submission/update RPCs.
- `supabase/promote-officer.sql`: administrator-only provisioning for additional officer accounts.
- `officer-dashboard/`: static officer desk, with no production npm dependencies.

## Existing project status — checked 8 October 2026

The public configuration points to `cleantrack-csp` (`gkdksqpxkzxeqmzurhub`). The `profiles`, `complaints`, and `complaint_events` tables exist with RLS enabled. The `complaint-images` bucket is private, with a 5 MB JPEG/PNG/WebP limit. The profile and audit triggers and guarded RPCs are installed. A confirmed citizen account and a confirmed officer account exist; their roles were checked. No complaint records exist yet.

**For this project, do not repeat the initial migration, bucket creation, or officer promotion.** Those steps are complete. Setup instructions in the detailed guide are retained for a separate fresh project.

The Android configuration names are `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`, without `NEXT_PUBLIC_`. An existing AI Studio `.env` overrides the supplied `.env.example`; check it before building. Public publishable keys belong in clients. Secret/service-role keys and Gemini secrets do not.

## Run the clients

1. Back up the current AI Studio Android export. Apply the source to that existing project, preserving any newer working interface changes. This branch does not automatically update AI Studio.
2. Build and install an APK on a physical phone. The source export has no Gradle wrapper; use AI Studio's managed Android build or Android Studio's Gradle/SDK environment.
3. From the extracted project root, serve the dashboard: `python3 -m http.server 8000 --bind 127.0.0.1 --directory officer-dashboard`. On Windows, use `py` instead of `python3`, or run `start-officer-dashboard.cmd`.
4. Open `http://localhost:8000`, check the prefilled project configuration, and sign in with the existing officer account.
5. Complete the live acceptance checklist in the handoff guide. A source package and configured database are not proof that the installed APK and dashboard already communicate.

The supplied existing Android project is https://ai.studio/apps/25087f4d-5584-4a89-a2ef-d307235a1a68 . For eventual web hosting, serve the dashboard over HTTPS.

## Validation and limits

Ten API-client tests pass: `node --test officer-dashboard/api.test.mjs`. They cover officer gating, refresh/session behavior, authorization headers, transitions, required resolution evidence, conflict timestamps, private-image URLs, pagination, and upload limits.

Database checks used authenticated-role/JWT-claim contexts inside rolled-back SQL transactions. They confirmed the officer role, citizen-only profile visibility, denial of the officer-update RPC to a citizen, and refusal of submission without an identity. These checks do not replace real login, authenticated API requests, private-photo access, or the live two-device test.

The dashboard browser test uses explicitly labeled local fixtures and does not contact the real project. It is supplied but has not passed here: the Chromium download returned an incomplete archive. Android compilation was not performed because this environment lacks Gradle and an Android SDK. Camera/GPS and end-to-end testing remain pending.

Supabase advisors still report guarded `SECURITY DEFINER` function warnings and performance suggestions. The functions deliberately mediate writes while table writes are revoked; their role/identity checks were reviewed. The project is not being presented as a completed security audit or production deployment.

## Pilot scope

Officers can see the full project queue; citizens can read only their own reports and cannot edit/delete them after submission. Assignment currently identifies a responsible officer. Field-team details can be written in the officer note; a structured team directory is not implemented. Android retrieves the latest 100 citizen reports. Refresh is every 10 seconds in the subscribed citizen screen and every 5 seconds in a visible officer tab.

AI verification is not deployed in this version. New reports say **“Manual review; AI screening not configured.”** Random AI outcomes and synthetic camera/GPS fallbacks were removed. Device coordinates are real; reverse-geocoded street addresses are future work. Realtime subscriptions, notifications, offline queues, and ward-level permissions are also future work.

Legacy Room classes remain as unused model compatibility code. Local demo rows and any historical Firebase records are not imported. Back up and map any genuine Firebase data separately before switching real users.

The Review 0 presentation keeps its Firebase framing as requested for that review. This migration package describes the subsequent Supabase work; it does not change the review deck or imply that planned features have been demonstrated.
