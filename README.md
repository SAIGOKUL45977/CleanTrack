# CleanTrack

A Kotlin and Jetpack Compose citizen Android app with a Supabase backend and a separate officer web dashboard. This migration is prepared for configuration and live testing; it is not a deployed service.

## The shared flow

The citizen signs in, captures a real photo and device location, and submits a report. Supabase stores the private photo, complaint, and audit event. An authorized officer reviews the same record, assigns an officer, and records progress. Resolving a complaint requires a cleanup photo. The citizen sees status, notes, and resolution evidence through periodic refresh.

- `app/`: existing citizen interface, now backed by Supabase REST APIs.
- `supabase/migrations/202610070001_cleantrack.sql`: tables, roles, RLS policies, private storage, and validated RPCs.
- `supabase/promote-officer.sql`: administrator-only officer provisioning.
- `officer-dashboard/`: responsive web officer desk; no npm dependencies are needed to run it.
- `docs/GOOGLE_AI_STUDIO_HANDOFF.md`: setup order, prompts, and acceptance checks.

## Setup

The Android defaults and dashboard now contain the public client configuration for `cleantrack-csp` (`gkdksqpxkzxeqmzurhub`). On 7 October 2026, the project's Auth settings API accepted the supplied publishable key: email sign-in and signup are enabled, and email confirmation is required. The Data API returned `PGRST205` for `public.profiles`; the CleanTrack schema is not yet available through that API. Client configuration alone does not create the database or complete a live migration.

1. Open the existing Supabase project. Run the migration in its SQL Editor.
2. Create citizen and officer email/password accounts in Supabase Auth. Confirm their email addresses. Replace the placeholder email in `supabase/promote-officer.sql` and run it for the officer account.
3. Android reads the configured `.env.example` defaults. If your AI Studio project already has a `.env`, update its `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` to match because `.env` overrides the defaults. These are Android Gradle property names; do not use the Next.js `NEXT_PUBLIC_` prefixes here. Never use `service_role`, `sb_secret_*`, or a Gemini secret in either client.
4. Apply the Android changes to your existing Google AI Studio Android project, or open this project in Android Studio. Build and install it on a physical phone. The source export does not include a Gradle wrapper; use AI Studio's managed build or the Gradle/SDK setup provided by Android Studio.
5. Run the dashboard locally: `python3 -m http.server 8000 --directory officer-dashboard`. Open `http://localhost:8000`. Its connection fields are prefilled from `config.mjs`. Check that they still match this project if your browser has an older saved configuration, then sign in as the officer. Serve the folder over HTTPS for a live pilot.
6. Complete the phone/dashboard test in the handoff guide before describing the shared flow as operational.

## Validation completed

`node --test officer-dashboard/api.test.mjs`: 10 tests passed for officer gating, token refresh, logout during refresh, authorization headers, transition validation, required resolution evidence, conflict timestamps, image URL checks, pagination, and upload limits.

The Android APK was not compiled in the preparation environment. The SQL migration and RLS policies were reviewed but were not executed against a Supabase project. Browser layout/interaction checks could not run because Chromium is not installed in that environment. Supabase live testing and physical-phone camera/GPS testing remain required.

## Scope

This is a single-community pilot: authorized officers can see the project's full queue; citizens can read only their own reports. Android displays the latest 100 reports per citizen. The officer queue is paginated. Refresh runs every 10 seconds in the subscribed citizen screen and every 5 seconds in a visible officer tab. Supabase Realtime subscriptions, push notifications, offline queues, ward-based officer access, and server-side Gemini screening are future work.

The old local login bypass, sample complaints, random AI outcomes, and synthetic camera/GPS fallbacks are removed from the active flow. Legacy Room classes remain as unused model compatibility code; local demo rows are not migrated to the shared database. If your live Firebase project contains real records beyond the supplied source, export and map those records in a separate migration before switching users.

The supplied AI Studio project link is https://ai.studio/apps/25087f4d-5584-4a89-a2ef-d307235a1a68 . Changes in this GitHub branch do not automatically update that AI Studio project.
