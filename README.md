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
push/PR. On pushes to `main` (and manual runs) it also publishes the APK to **GitHub Releases**,
named `SmartHome-<DDMMYYYY><Nth build that day>.apk` (e.g. `SmartHome-030720261.apk`). Add these
**repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `THING_APP_KEY` | Tuya appKey |
| `THING_APP_SECRET` | Tuya appSecret |
| `THING_SECURITY_AAR_PASSPHRASE` | passphrase used to encrypt the committed `.aar.enc` |

The `.aar` is too large for a GitHub secret (48 KB limit), so an **AES-256 encrypted copy**
(`app/libs/security-algorithm-1.0.0-beta.aar.enc`) is committed and decrypted in CI with the
passphrase secret. To (re)generate the encrypted file after replacing the `.aar`:
```bash
# pick/keep a strong passphrase and store it as the THING_SECURITY_AAR_PASSPHRASE secret
openssl enc -aes-256-cbc -pbkdf2 -iter 100000 -salt \
  -in  app/libs/security-algorithm-1.0.0-beta.aar \
  -out app/libs/security-algorithm-1.0.0-beta.aar.enc \
  -pass pass:'your_passphrase'
```

> Note: credentials compiled into an APK can still be extracted by decompiling it. Keeping them
> out of the repo prevents casual exposure; it is not a substitute for the account-bound
> `security-algorithm.aar`, which is what actually gates access to your Tuya app.
