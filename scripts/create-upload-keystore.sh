#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$HOME/.tempo-keys"
keytool -genkeypair -v -keystore "$HOME/.tempo-keys/tempo-upload.jks" -alias tempo-upload -keyalg RSA -keysize 2048 -validity 10000
printf '\nCreated %s\nCopy keystore.properties.example to keystore.properties and fill in the values.\n' "$HOME/.tempo-keys/tempo-upload.jks"
