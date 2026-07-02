# SmartDevice

Android app to add / control / edit / remove Tuya (ThingClips) smart devices — Jetpack Compose,
Clean Architecture + MVVM. See [`docs/`](docs/) for Tuya SDK integration notes.

## Credentials (kept out of source control)

The Tuya `appKey` / `appSecret` and the account-specific `security-algorithm.aar` are **not**
committed. Provide them locally as follows.

### Local development
1. Copy `local.properties.example` to `local.properties` (gitignored) and fill in:
   ```properties
   THING_APP_KEY=your_tuya_app_key
   THING_APP_SECRET=your_tuya_app_secret
   ```
   The build injects these into `BuildConfig` and the manifest (see `app/build.gradle.kts`).
2. Download your `security-algorithm.aar` and drop it in `app/libs/` — see
   [`app/libs/README.md`](app/libs/README.md).
3. Build: `./gradlew :app:assembleDebug`.

### CI (GitHub Actions)
The workflow in [`.github/workflows/android.yml`](.github/workflows/android.yml) builds on every
push/PR. Add these **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `THING_APP_KEY` | Tuya appKey |
| `THING_APP_SECRET` | Tuya appSecret |
| `THING_SECURITY_AAR_BASE64` | base64 of your `security-algorithm.aar` |

Set them with the `gh` CLI (run from the repo root, after the remote exists):
```bash
gh secret set THING_APP_KEY --body 'your_tuya_app_key'
gh secret set THING_APP_SECRET --body 'your_tuya_app_secret'
base64 -i app/libs/security-algorithm-1.0.0-beta.aar | gh secret set THING_SECURITY_AAR_BASE64
```

> Note: credentials compiled into an APK can still be extracted by decompiling it. Keeping them
> out of the repo prevents casual exposure; it is not a substitute for the account-bound
> `security-algorithm.aar`, which is what actually gates access to your Tuya app.
