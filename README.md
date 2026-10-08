# FLY8 Clone

An independent Android app for creating and managing isolated app sessions.

This repository is a clean product codebase. It contains no dependency on the previous prototype's application package, licensing flow, contact system, or APK.

## Features

- Browse launchable apps installed on the device.
- Create an isolated Android user/profile for an app.
- Install the existing package into that profile.
- Launch, stop, clear data, and remove a session.
- Persist only small session metadata locally.
- Optional Shizuku bridge for privileged profile/package operations.
- Original dark graphite UI with mint/cyan eye-window visual language.
- No activation-key screen.
- No remote license server.
- No Discord/contact integration.

## Important compatibility note

This first independent backend uses Android's own user/profile isolation. That keeps the host app lightweight, but Android and each device vendor decide which profile operations are permitted and whether more than one profile can be foreground at the same time.

The project deliberately does not claim that every phone can run many simultaneous game instances.

## Build

GitHub Actions builds the release APK with JDK 17, Gradle 9.5.1, Android SDK 36, and Shizuku API 13.1.5.

The workflow also scans the repository for legacy prototype branding before building.
