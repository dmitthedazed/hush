# Contributing

## Build

```sh
git clone https://github.com/dmitthedazed/hush.git
cd hush
./gradlew testDebugUnitTest assembleDebug
```

Needs JDK 21 and the Android SDK. Modules: `app` (phone), `wear`, `tv`, and `core` with the protocol and shared code.

## Before you change the protocol

[docs/NOTES.md](docs/NOTES.md) describes the MDR protocol, Sony's services and where each piece lives in the decompiled Sound Connect. Read the part you're touching first. Frame encoding is covered by tests in `core/src/test`, keep them passing and add one for anything new.

## Rules of thumb

- No analytics, accounts or trackers. The app only talks to GitHub and Sony's own servers.
- Keep device identifiers (serial, Bluetooth address) hidden by default in the UI.

## Pull requests

Keep them small. Say which headphones and firmware you tested with, and add screenshots for UI changes.
