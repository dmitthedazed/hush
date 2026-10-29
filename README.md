<div align="center">

<img src="docs/icon.png" width="112" alt="Hush icon">

# Hush

**Liberate your headphones from vendor apps.**

No accounts, no tracking, no shady software. Sony MDR v1/v2, experimental Jabra Elite 10 / Elite 85t support, and Apple EarPods (USB-C).

[![Release](https://img.shields.io/github/v/release/dmitthedazed/hush?style=flat-square&color=d6d07a)](https://github.com/dmitthedazed/hush/releases/latest)
![Android 12+](https://img.shields.io/badge/Android-12%2B-3ddc84?style=flat-square&logo=android&logoColor=white)
![Wear OS](https://img.shields.io/badge/Wear%20OS-4285f4?style=flat-square&logo=wearos&logoColor=white)
![Android TV](https://img.shields.io/badge/Android%20TV-34a853?style=flat-square&logo=androidtv&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-lightgrey?style=flat-square)](LICENSE)

**[Download](https://github.com/dmitthedazed/hush/releases/latest)**

<img src="docs/screenshots/listen.png" width="24%" alt="Listen">
<img src="docs/screenshots/player.png" width="24%" alt="Now playing">
<img src="docs/screenshots/sound.png" width="24%" alt="Sound">
<img src="docs/screenshots/device.png" width="24%" alt="Device">

</div>

## Why

Headphone makers ship heavy apps that want an account, log what you do and still hide half of what the hardware can. Hush talks to the headphones directly over the vendor's own protocol, reimplemented from scratch, and puts every setting on three tabs, **Listen**, **Sound** and **Device**, in your wallpaper's colours.

## Supported headphones

| Brand | Protocol | Status |
| --- | --- | --- |
| Sony | MDR v1 | Tested on WH-1000XM4 |
| Sony | MDR v2 | Fully implemented, needs testers |
| Jabra | GNP | Elite 10 (original), firmware 4.6.0, and Elite 85t, firmware 2.0.0: hardware tested; experimental firmware coverage |
| Apple | USB audio | EarPods (USB-C): equalizer on the phone, remote, authenticity check |
| More brands | | Soon |

Got Sony headphones on MDR v2, a Jabra Elite 10 or Elite 85t, or something else? Try Hush and [report how it went](https://github.com/dmitthedazed/hush/issues/new/choose), that's the fastest way to get them supported.

Features depend on the connected model. Jabra supports noise modes and available strengths, five-band hardware EQ, earbud/case battery, automatic pause, sleep timeout, and announcements. Elite 85t also supports EQ bypass; Elite 10 firmware 4.6.0 exposes editable EQ without bypass. See the [tested controls and remaining limits](docs/JABRA.md#evidence-tests-and-hardware-acceptance).

## Listen

- Noise cancelling, ambient or off, with ambient level, voice focus, DSEE Extreme and Speak-to-Chat
- **Scenes** like Commute, Office, Walk and Sleep, or your own: noise mode, EQ, Speak-to-Chat and volume in one tap. Tap again to go back.
- **Now playing** with artwork, seek bar and the headphones' own volume
- **Sleep timer** that fades out, pauses and turns the headphones off

## Sound

- Sony's presets, Manual and Custom 1/2, hold to compare
- **AutoEq**: fit published measurements (oratory1990 and others) to the six bands, and keep them in an EQ library with share codes
- **Find Your Equalizer** (Sony's listening test) and a quick hearing test
- **Adaptive Sound Control** by activity and place, and per-app sound profiles

## Device

- Every setting from the vendor app: CUSTOM button, touch panel, wearing detection, NC optimizer, auto power off, voice guidance, multipoint
- **Touch and hold** can open ChatGPT or Claude voice mode, switch noise modes, step through scenes, start a sleep timer, recognise a song, record a voice note, toggle the flashlight or fire a Tasker intent
- **Insights**: battery history with an honest time-left estimate, weekly sound dose (WHO 80 dB × 40 h), and where you last had them
- **Updates** for Hush itself and for the headphones' firmware, checked weekly
- How-to guide, first-run tour and help articles from the manufacturer
- Backup and restore everything to one JSON file

<p align="center">
  <img src="docs/screenshots/guide.png" width="24%" alt="How to use">
  <img src="docs/screenshots/device-about.png" width="24%" alt="Device details">
  <img src="docs/screenshots/about.png" width="24%" alt="About Hush">
</p>

## Everywhere

Home-screen widgets, a Quick Settings tile, a live notification with mode buttons, a **Wear OS** app with tiles, **Android TV**, and layouts for foldables and tablets. On ColorOS, an optional Xposed module adds Hush to My Devices, the Bluetooth settings and Fluid Cloud.

<p align="center">
  <img src="docs/screenshots/tv/listen.png" width="32%" alt="Android TV: Listen">
  <img src="docs/screenshots/tv/sound.png" width="32%" alt="Android TV: Sound">
  <img src="docs/screenshots/tv/picker.png" width="32%" alt="Android TV: choose headphones">
</p>

## Install

1. Download `hush-<version>-phone.apk` from the [latest release](https://github.com/dmitthedazed/hush/releases/latest). Use `-wear` for the watch and `-tv` for Android TV.
2. Pair the headphones in Android's Bluetooth settings, open Hush and pick them.
3. Close the vendor app (Sony Sound Connect or Jabra Sound+). The headphones talk to one app at a time.

Builds are signed with a development key, so Android asks once about an unknown developer. After that Hush updates itself from GitHub releases (Device → About → Updates).

> [!NOTE]
> Tested on a Sony WH-1000XM4 (firmware 3.0.1) with a realme phone on ColorOS 16, and on Jabra Elite 10 (4.6.0) / Elite 85t (2.0.0) with a Pixel 10. Jabra test coverage is documented in [docs/JABRA.md](docs/JABRA.md).

<details>
<summary>How it works</summary>

For Sony, Hush opens the headphones' RFCOMM control channel and speaks the MDR protocol: frames, ACKs, command tables 1 and 2. Sony's cloud features are reached the way Sound Connect reaches them: the Concierge service for tutorials and help, the model catalogue for per-colour renders, and `info.update.sony.net` for firmware.

All of it is written up, with pointers into the decompiled Sound Connect 13.3.0, in [docs/NOTES.md](docs/NOTES.md).

Jabra support uses an independently implemented Bluetooth Classic GNP backend. It covers ANC/HearThrough/Off, available strength controls, hardware EQ, earbud/case battery, auto-pause, sleep timeout and announcements. Protocol evidence, limitations and the hardware acceptance checklist are in [docs/JABRA.md](docs/JABRA.md). Firmware installation stays with Sound+. Elite 10 Gen 2 and other Jabra models are not yet supported.

</details>

<details>
<summary>Build</summary>

Needs JDK 21. Minimum Android 12 (API 31).

```sh
./gradlew :app:assembleRelease    # phone; also :wear and :tv
./gradlew testDebugUnitTest
```

For a phone debug build that installs alongside the normal app, use `./gradlew :app:assembleDebug -PhushTestApplicationId=com.occaecat.hush.jabratest`. The optional [ADB hardware-test provider](docs/JABRA.md#optional-hardware-test-build) is included only in debug builds and restricts calls to the shell, root, and the app itself.

See [CONTRIBUTING.md](CONTRIBUTING.md).

</details>

## Disclaimer

Hush is an independent project and is not affiliated with, endorsed or supported by Sony, Apple or any other headphone maker. Product names and logos are trademarks of their owners: Sony, WH-1000XM4, Sound Connect, DSEE Extreme and LDAC belong to Sony Group Corporation; Apple and EarPods belong to Apple Inc.

Product images, tutorials, help articles and firmware information belong to their manufacturers. Hush fetches them from the manufacturers' own servers, only for use with headphones you own.

## License

[MIT](LICENSE) © dmitthedazed. Third-party licenses are listed in the app under Device → About Hush → Open-source licenses.
