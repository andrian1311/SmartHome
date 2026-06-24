# app/libs — Tuya security algorithm component

The Tuya/Thing SDK's `libthing_security.so` depends on `libthing_security_algorithm.so`,
which is **not** published on Maven. It ships inside an app-specific **`security-algorithm.aar`**
(a.k.a. the "security image"), generated for your exact **appKey + package name + SHA-256**.

Without it the app crashes at startup with:

```
java.lang.UnsatisfiedLinkError: No implementation found for ...
    SecureNativeApi.doCommandNative ... is the library loaded, e.g. System.loadLibrary?
```

## How to get the file

1. Go to the Tuya IoT Platform → **App** → **App SDK** → your app
   (the one using appKey `***REMOVED***`).
2. Make sure the **Android** package name `com.shaqsid.smart` and the debug **SHA-256**
   are registered for this app.
3. Download the SDK / "Get security image". The bundle contains **`security-algorithm.aar`**
   (sometimes named `security-algorithm-<version>.aar`).
4. Copy that `.aar` file into **this folder** (`app/libs/`).
5. Sync Gradle and rebuild. `build.gradle.kts` already picks up any `*.aar` here via
   `implementation(fileTree("libs") { include("*.jar", "*.aar") })`.

## Version note

The `security-algorithm.aar` should match the `thingsmart` SDK version in
`gradle/libs.versions.toml` (currently `5.8.0`). If the platform gives you a different
version and you hit conflicts, align the `thingsmart` version to match the downloaded SDK.

> This `.aar` is tied to your account/app — keep it out of public repos if the repo is shared.
