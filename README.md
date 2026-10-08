# FLY8 Clone

A clean, independent Android beta for managing isolated app instances.

## Current backend

The first backend uses Android's native clone-profile mechanism through Shizuku. Android 15 defines the clone profile as a separate profile type intended to run a second instance of an app; device/OEM support is required. This means this backend is deliberately lightweight and does not embed a full Android virtualization engine.

## Included

- Native dark UI with a graphite/mint visual system.
- App picker from installed launcher apps.
- Create, launch, clear, and remove clone profiles.
- Shizuku permission integration.
- Local clone metadata only.
- No activation-key system.
- No license server.
- No Discord/contact integration.
- No prototype package names.

## Build

GitHub Actions uses JDK 17, Gradle 8.11.1, Android SDK 36, and builds `app-release.apk`.

The workflow also rejects legacy prototype branding/package strings before compiling.

## Limitations

Android's native clone-profile support is not the same thing as an in-process virtualization engine. AOSP describes clone profiles as a single additional clone profile, and OEM support varies. The project intentionally keeps this backend small rather than pretending it provides unlimited simultaneous instances.

## Shizuku

Install and start Shizuku separately, then grant FLY8 access when requested. FLY8 does not bundle the Shizuku manager.
