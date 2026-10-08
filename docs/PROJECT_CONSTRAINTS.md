# CleanTrack project constraints

This file records the agreed product scope and distinguishes the prepared migration from features that still need implementation or live testing. It supersedes stale setup claims in the imported Claude handoff, not the team's review documents.

## Fixed purpose

- Brand: **CleanTrack**.
- Official title supplied in the project handoff: **AI-Based Smart Community Waste Monitoring & Complaint Management System**.
- Formal project wording uses **waste**; citizen reporting actions can use **garbage**.
- Community/municipal complaint reporting and accountable handling, with citizen status tracking.
- Citizen app: native Android, Kotlin/Jetpack Compose, self-registration as a citizen only.
- Officer interface: separate web dashboard, administrator-provisioned accounts only.

## Reporting and access rules

- Live camera capture and actual device GPS; no gallery import, manual location, or synthetic evidence in the citizen reporting flow.
- Exactly five categories: Overflowing Waste Bin; Plastic Waste Accumulation; Organic/Food Waste; Dry Leaf & Bio Litter; E-Waste Disposal.
- Citizens can read their own reports and cannot edit/delete submitted complaints.
- An officer must provide a note for an update. Resolution requires a stored cleanup photo.
- Private photo storage and guarded database writes; no client-selectable officer role.
- A required after-photo provides an evidence record. The current app does not independently prove its authenticity or that cleanup happened at the original coordinates.

## AI boundary

The intended AI feature checks whether a photo genuinely shows garbage. Its eventual user-facing results are **Verified**, **Pending Manual Review**, or **Rejected**. No severity score, confidence percentage, automatic category detection, or hazardous/chemical category belongs in this scope.

The current Supabase migration uses **manual review**. It removes the original random AI result and does not contain a deployed Gemini Edge Function. Do not imply that selecting Supabase activates AI. Add server-side screening, safe manual fallback, and tests after the shared complaint flow works.

## Current implementation boundaries

- The configured backend is `cleantrack-csp`; its actual schema uses bigint complaint IDs, a separate `complaint_events` table, `full_name` profiles, guarded RPCs, and the private `complaint-images` bucket. Do not substitute the imported handoff's older UUID schema, public `complaint-photos` bucket, or `status_history` JSON contract.
- Assignment identifies a responsible officer. Field-team details may be written in notes; a structured team directory is future work.
- Android currently records GPS coordinates as location text. Reverse-geocoded street addresses are future work.
- Status refresh uses polling, not Supabase Realtime subscriptions. Push/SMS/email notifications, offline queues, and ward-specific officer permissions are future work.
- Legacy local demo data and historical Firebase records have not been migrated.
- Source checks and database-role tests are complete; the installed Android APK and officer dashboard have not yet passed the live two-device test.

## Excluded scope

CCTV/drone monitoring, continuous surveillance, garbage-truck routing, IoT infrastructure, and rewards/points are outside this project. Do not add them as shortcuts to an AI or “smart city” claim.

## Review evidence

The Review 0 deck retains the user's requested Firebase framing and states prototype limitations. This Supabase package is subsequent development work. Field photos and approved demo/dummy interview placeholders must remain clearly distinguished from verified interview data. Do not claim municipal adoption, community impact, AI accuracy, or a connected production system without evidence.
