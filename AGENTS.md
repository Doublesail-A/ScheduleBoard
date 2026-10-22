# ScheduleBoard development

- Base work on the current GitHub main branch; preserve user changes.
- Use official Compose Material 3 and Android 12 dynamic colors. Primary device: Android 12, landscape 1280×720.
- For EVERY application update, increase BOTH versionCode and versionName in app/build.gradle.kts, add an accurate CHANGELOG.md entry, and publish through .github/workflows/android.yml.
- A task that updates the app is not finished until GitHub Actions succeeds and a matching GitHub Release contains the signed APK and SHA256SUMS.txt. Report external setup blockers honestly; do not claim a local APK or a temporary Actions artifact is a published release.
- Never overwrite a published version/tag/binary or use a random runner signing key for distribution. Retain the fixed certificate pinned by scripts/verify_signing.py so upgrades and Google OAuth continue to work.
- Signing keys belong only in repository Actions secrets or the developer's private local keystore. Never commit credentials, tokens, real task/course data, or signing key files (including encoded copies).
- Run meaningful host tests, native Compose rendering and Android Lint; inspect the resulting 1280×720 screenshots. Real-account/ADB verification must be reported separately from mocked API tests.
- Google Tasks uses native Google Play services authorization, scoped to the selected account/list. Preserve offline edits and unrelated remote fields. Never silently upload local-only tasks to a cloud account.
