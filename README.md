# CleanTrack CSP demonstration

This package prepares a simple connected college prototype: a citizen enters a name and mobile number, captures a real photo and GPS, and submits a complaint. The team signs in to the officer web dashboard, records progress, and attaches a cleanup photo to resolve it. The citizen sees the update on the same phone.

**Read [START_HERE.md](START_HERE.md) first.** The updated AI Studio prompt is in [docs/GOOGLE_AI_STUDIO_HANDOFF.md](docs/GOOGLE_AI_STUDIO_HANDOFF.md).

## Changes after Review 0

- Citizen email/password registration and login screens are replaced by **Name**, **Mobile number**, and **Continue**.
- A persistent Supabase guest identity operates in the background. Private complaint ownership and officer authorization remain enforced.
- The mobile number is entered by the citizen, not OTP verified. It is displayed to authorized officers for follow-up and copied into each new complaint.
- Aadhaar is not collected. It is unnecessary for this demonstration.
- The guest session restores on the same installation. There is no citizen sign-out button, cross-device recovery, or lookup by a typed mobile number.
- Officer login remains. The team operates the dashboard for the college demonstration.

## Existing backend status — 8 October 2026

The project is `cleantrack-csp` (`gkdksqpxkzxeqmzurhub`). Its RLS-enabled tables, private `complaint-images` bucket, guarded RPCs, and officer account are already configured. The additive `guest_citizen_details` migration was applied: `profiles.mobile_number` and `complaints.citizen_mobile_number` now exist. No test complaint rows were inserted.

**One setting remains:** enable Anonymous Sign-Ins in this project's Authentication providers. A live signup check returned `anonymous_provider_disabled`. This setting cannot be changed through the installed Supabase connector's available operations; the guide links to the exact project page. Do not open tables or photo storage to public access.

The source has not been applied to the user's current AI Studio project, and a rebuilt APK has not been installed. The old local-storage app does not connect to this backend.

## Source folders

- `app/`: Kotlin/Compose citizen app and REST integration.
- `officer-dashboard/`: static team-operated web dashboard.
- `supabase/migrations/`: initial schema plus additive guest-details migration.
- `supabase/tests/guest_access.sql`: rolled-back access/validation checks.
- `docs/PROJECT_CONSTRAINTS.md`: current product boundaries.

For a new Supabase project, run both migrations in filename order and provision an officer with `supabase/promote-officer.sql`. For the existing project, those SQL steps are complete; only the Auth provider setting remains.

## Validation

Ten officer API-client tests pass: `node --test officer-dashboard/api.test.mjs`. JavaScript syntax, 48 HTML element references, and 10 XML files pass checks. Authenticated guest-claim SQL tests pass for stored contact details, forced citizen role despite malicious metadata, own-profile visibility, rejection of role edits/officer actions, and refusal of a report without an uploaded photo. Test fixtures were rolled back.

Android compilation and the real phone-to-dashboard-to-phone flow remain pending. The Playwright browser test is supplied, but Chromium installation failed earlier with an incomplete download; no browser pass is claimed. The guest HTTP signup is currently blocked by the provider setting.

Performance advisors retain existing index/RLS execution suggestions, outside this small demonstration change. The database advisors still flag intentionally guarded `SECURITY DEFINER` RPCs and disabled leaked-password protection. The former implement controlled writes while table writes are revoked. Existing role/identity guards remain in place. This is a college demonstration, not a completed production security audit. References: [RPC advisory](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable) and [password protection](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).

## Features and limits

Keep exactly five categories, real camera capture, actual GPS, immutable citizen submissions, officer notes, complaint history, and a mandatory resolution photo. GPS supplies coordinates; street-address lookup is not implemented. Status updates use polling, not Realtime subscriptions.

AI screening is not deployed: reports honestly show manual review. Automatic escalation is planned, with deadlines and authority hierarchy unconfirmed. Municipal adoption, real cleanup, notifications, OTP, cross-device recovery, offline queues, and historical Firebase-data migration are not claimed.

The prepared source is based on the shared export. Preserve any newer working UI changes in the user's AI Studio project. No code here automatically updates that project or the APK. The Review 0 deck remains an archival record of that review; a later deck should describe only features actually demonstrated.
