# FLY8 Clone

An independent, lightweight Android session manager built from scratch for managing supported isolated app profiles.

## Product direction

FLY8 uses the supplied prototype only as a reference for useful session-management ideas and information density. The application itself has its own package, storage model, UI architecture, branding and backend boundary.

The current visual system is built around a near-black and deep-blue palette with the supplied eye/window artwork used as the FLY8 launcher artwork.

## Current UI

- Dashboard with device resources, backend state, recovery summary and bounded activity history.
- Sessions grid with compact session cards, app icons, state, start/stop controls and a session action menu.
- Apps surface for installed launcher-app selection.
- Recovery surface with per-session enablement, scheduled restart interval and stored network/IP recovery preferences.
- Settings surface with low-resource and profile-refresh preferences.
- About surface with FLY8 branding, usage notes, privacy information, project notes and dependency notices.
- No activation account, remote license server or old prototype branding.

## Backend

The first session backend uses Android's native clone-profile/secondary-user primitives. Control access is selectable: Wireless debugging (ADB), Shizuku, or Root. FLY8 does not embed a full Android virtualization engine.

Android/OEM behavior varies. Native clone support does not imply unlimited simultaneous instances, and some devices expose only one additional clone profile.

The backend boundary is:

UI -> session state -> CloneBackend -> Shizuku/system services

This leaves room for another backend later without bringing an old virtualization framework into FLY8.

## Recovery

Scheduled recovery uses Android alarms instead of a permanently running polling loop. A configured interval can relaunch a session when an alarm fires.

Network-change and public-IP recovery preferences are stored in the same per-session model, but reliable event-driven public-IP detection without continuous network polling is intentionally not enabled by default.

## Performance

The app avoids continuous screenshot capture, large bitmap caches and constant UI rebuilding. Resource statistics are sampled when screens are rendered or refreshed rather than polled continuously.

## Control connections

FLY8 supports three independent control methods. Wireless debugging uses Android's modern ADB-over-Wi-Fi pairing flow; the app can manually pair and test the connection and can discover `_adb-tls-pairing._tcp.` and `_adb-tls-connect._tcp.` endpoints with Android mDNS. Shizuku is an optional local privileged bridge. Root is an optional direct `su` bridge on rooted devices. FLY8 does not expose an arbitrary shell console.

Wireless debugging pairing stores control configuration locally. The connection port can change after a reboot, so rediscovery or manual entry may be needed.

## Build

GitHub Actions uses JDK 17, Gradle 8.11.1 and Android SDK 36. The release APK is signed with the repository's debug signing configuration for beta testing.
