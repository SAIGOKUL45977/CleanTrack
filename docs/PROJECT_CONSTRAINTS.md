# CleanTrack CSP scope after Review 0

## Purpose and architecture

- Brand: CleanTrack.
- Official title supplied in the handoff: AI-Based Smart Community Waste Monitoring & Complaint Management System.
- Formal wording uses waste; citizen reporting actions can use garbage.
- Native Kotlin/Compose citizen app and a separate officer web dashboard.
- The team operates the prototype for college demonstrations; municipal adoption is not claimed.

## Citizen entry

- Name and mobile number only, with Continue.
- No citizen email/password screen, Aadhaar field, or OTP in this demonstration.
- A private guest identity persists behind the UI so each installation owns its reports.
- Entered contact details are not verified identity. A typed mobile number cannot authorize complaint retrieval.
- Same-installation history only. No cross-device recovery or citizen sign-out button.
- Officer email/password login and administrator-controlled role assignment remain.

## Reporting rules

- Actual live camera capture and device GPS; no gallery/manual location/synthetic evidence.
- Exactly five categories: Overflowing Waste Bin; Plastic Waste Accumulation; Organic/Food Waste; Dry Leaf & Bio Litter; E-Waste Disposal.
- Citizens read only their own reports and cannot edit/delete after submission.
- Officer updates require notes. Resolution requires a stored cleanup photo.
- Assignment identifies a responsible officer; field-team details can be recorded in notes.
- Private complaint-images bucket, guarded RPC writes, and protected role records.

## Planned features

AI checks image relevance only, with future results Verified, Pending Manual Review, or Rejected. The current version has manual review, not a deployed Gemini function. No severity score, confidence percentage, extra hazardous/chemical category, or automatic category detection.

Deadline-based escalation remains planned. Authority hierarchy and time limits are unconfirmed. Later demonstration rules must be labeled as such and must not imply notification of real municipal authorities. Changing a report to In Progress should not restart the resolution deadline.

Reverse-geocoded addresses, notifications, Realtime subscriptions, offline queues, ward-level access, and Firebase-history migration are not implemented. Current GPS text is coordinates and status refresh uses polling.

## Demonstration integrity

No simulated feature should be described as real integration. Test photos/records and dummy interview notes remain labeled. A required after-photo records evidence but does not independently prove cleanup authenticity or location.

CCTV/drone monitoring, IoT, vehicle routing, rewards, and claims of municipal adoption are outside this prototype. Keep the next presentation centered on working features. Review 0 is complete; the next review date is not yet announced.
