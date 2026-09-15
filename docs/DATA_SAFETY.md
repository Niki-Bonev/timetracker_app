# Data safety working notes

These notes are a starting point for the Google Play Data safety form. Verify them against the exact production build and Firebase configuration before submission.

## Data Tempo itself handles

When the user stays in local mode, project/session data remains on the device except when the user explicitly exports a file.

When the user chooses an account, Firebase Authentication can process account identifiers such as email address, display name, profile image URL and Firebase UID. Firestore stores the user's Tempo project names, session metadata and timing intervals under their UID to provide backup/sync.

## Not included by this project

- Advertising SDKs
- In-app behavioral analytics SDKs
- Location access
- Contacts
- Camera or microphone access
- Health APIs
- Financial/payment collection
- Accessibility APIs

## Security / deletion

Firestore security rules are scoped to the authenticated UID. The Settings screen includes account/cloud-data deletion and device-local data deletion. Export is user-initiated via Android's share sheet.

Firebase/Google services may have their own transport, diagnostic and service data practices; use Google's current documentation when completing Play Console declarations.
