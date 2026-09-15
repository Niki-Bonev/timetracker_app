# Google Play closed-testing checklist

## Before generating the AAB

- [ ] Set the final app name, package name and versioning.
- [ ] Add production Firebase configuration if cloud accounts will ship.
- [ ] Configure Firebase Authentication providers and Firestore rules.
- [ ] Generate an upload keystore; keep it outside Git.
- [ ] Copy `keystore.properties.example` to `keystore.properties` and fill in the local paths/passwords.
- [ ] Run `gradle test lint bundleRelease` (or `./gradlew test lint bundleRelease` after generating the optional wrapper launchers described in the README).
- [ ] Install/test a release build on at least one real Android device.
- [ ] Test timer survival across backgrounding, process recreation, reboot, timezone changes, midnight, and loss of connectivity.
- [ ] Test email sign-up/sign-in/reset and Google sign-in against the production Firebase project.
- [ ] Test export/import and account deletion.
- [ ] Replace the privacy-policy template with a hosted final policy URL.

## Play Console

- Create the app and complete App content, Data safety, content rating, target audience and ads declarations.
- Enable Play App Signing.
- Upload the `app-release.aab` from `app/build/outputs/bundle/release/`.
- Add closed-test testers / Google Group and publish the closed-testing release.
- Review pre-launch reports and Android vitals before widening distribution.

## Current SDK configuration

Tempo is configured with `targetSdk = 36` and `compileSdk = 37`. As of the project build date (September 2026), API 36 meets Google Play's requirement for new apps/updates while API 37 lets the project use current Compose tooling.

## Release-key note

Never send or commit your signing passwords or private keystore. Play Console and Firebase certificate fingerprints can be configured from the public fingerprints without exposing the private key.
