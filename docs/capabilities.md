# Capabilities

What Hush can do, which devices do it themselves, and what the phone does when they can't. The shared UI only ever
asks a `DeviceController` for capabilities (`devices/api`); it never checks a brand or a protocol. A capability the
phone provides instead of the device is marked `Source.PHONE` and the UI says so ("On this phone").

Devices: **XM4** = Sony WH-1000XM4 (MDR v1 over RFCOMM), **EarPods** = Apple EarPods USB-C (plain USB Audio, no
control channel), **TWS** = planned earbuds (Sony WF over MDR v2, generic earbuds over HFP/A2DP only), **Jabra** =
external GNP module over RFCOMM (not ported yet; from its public feature set).

## Audit

| Feature | XM4 | EarPods | TWS | Jabra |
|---|---|---|---|---|
| ANC / ambient / voice focus | HW (level 1–20, voice) | — | HW on WF, — on generic | HW (ANC, HearThrough) |
| EQ presets + bands | HW, 6 bands + Clear Bass, 2 custom slots | — | HW on WF, — on generic | HW, 5 bands |
| AutoEq correction | fitted onto 6 HW bands | phone EQ | HW fit or phone EQ | HW fit (5 bands) |
| Battery | HW (one level) | — (bus-powered) | HW L/R/case on WF; HFP report on generic | HW |
| Codec / sound quality / DSEE | HW report + switch | fixed 48 kHz | WF reports | report only |
| Volume | HW (absolute volume) | phone stream | phone stream | phone stream |
| Sleep timer (fade, then off) | fade + HW power-off | fade, then pause | fade (+ off on WF) | fade (+ off) |
| Wear detection / auto-pause | HW (pause when removed) | — | HW on WF | HW on Elite |
| Speak-to-Chat | HW | — | HW on WF | — |
| Button remap (custom button, assignable keys) | HW | — (inline remote fixed) | HW on WF | partial HW |
| Multipoint / peers | HW | — | HW on WF | HW |
| Auto power-off, voice guidance, vibration | HW | — | HW on WF | partly HW |
| Firmware version / update check | HW + Sony server | descriptor version only | HW on WF | HW |
| Volume limit | HW (max volume) | — | — | — |
| Scenes / automation | phone (drives the rest) | phone | phone | phone |
| Insights (battery, listening, last seen) | phone | phone | phone | phone |
| Mic test | phone (HFP route) | phone (USB input) | phone | phone |
| Authenticity check | — | USB descriptors | — | — |

## Decisions

| Feature | Decision | Interface | Fallback when the device can't |
|---|---|---|---|
| Noise control | universal | `NoiseControl` | none: the phone can't cancel noise |
| EQ + AutoEq | universal | `Equalizer`, `AutoEqTarget` | phone EQ (`PhoneEqualizer`): `DynamicsProcessing` on session 0, marked PHONE |
| Battery | universal | `DeviceState.batteries` | Android's own reading of the headset (HFP battery report), marked PHONE |
| Sleep timer | universal | `SleepTarget` | phone music volume fade, then pause (`PhoneSleepTarget`), marked PHONE |
| Volume | universal | `DeviceController.setVolume` / `SleepTarget` | phone music stream |
| Scenes / automation | universal, phone-side | `SceneTarget` over noise, EQ, volume, `SceneSwitches` | is the phone already |
| Insights | universal, phone-side | `Insights.Profile` | is the phone already |
| Mic test | universal, phone-side | `Microphone` | is the phone already |
| Firmware | universal shape, device data | `Firmware` | none |
| Quick controls (widgets, tiles) | universal | `QuickControls` (derived from noise/EQ) | from the EQ switch when there is no noise control |
| Device switches (Speak-to-Chat…) | device-only, generic rows | `SceneSwitches`, `SettingsGroup` | none |
| Wear detection / auto-pause | device-only | `SettingsGroup` row | none (no sensor on the phone) |
| Button remap, multipoint, auto-off, voice guidance, codec | device-only | `SettingsGroup` rows | none |
| Volume limit | device-only for now | `SettingsGroup` row | possible later (see below), not built |
| Authenticity, guides, help | device-only sections | `deviceSections` / `aboutRows` | — |

### What Android actually allows (checked against developer.android.com, 2026-11)

- **EQ, global.** Attaching insert effects (`Equalizer`, `DynamicsProcessing`) to session 0 — the output mix — is
  *deprecated* but still works on stock Android and ColorOS; vendors may ignore it or chain it after their own
  effects. It is what PhoneEq does, and it can't be per-device: there is one output mix, so the last device to apply
  it owns it. There is no public API to put an effect on another app's output.
- **EQ, per-app.** The public way is per audio session: players broadcast
  `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` with their session id and an app may attach to it. Only
  players that send it (many do, Spotify and YouTube Music don't reliably) get the EQ. Not built; session 0 covers
  more players.
- **Battery.** `BluetoothDevice.getBatteryLevel()` and `ACTION_BATTERY_LEVEL_CHANGED` are not public SDK (hidden;
  reachable by reflection on current releases, may be blocked later). The public signal is
  `BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT` (Apple `+IPHONEACCEV`, `+XEVENT=BATTERY`), needs
  `BLUETOOTH_CONNECT`, and only fires when the headset sends it. Hush reads the hidden getter first and listens for
  both broadcasts; the result is marked PHONE ("reported by Android").
- **Sleep timer.** Fully possible on the phone: `AudioManager.setStreamVolume` for the fade and a media key /
  audio focus request to pause. Turning the device off needs the device.
- **Auto-pause.** Without a wear sensor there is nothing to detect. Pausing on disconnect is already Android's
  (`ACTION_AUDIO_BECOMING_NOISY`, handled by players).
- **Volume limit.** Possible as an emulation: watch `STREAM_MUSIC` and clamp it, or lower the phone EQ's
  `DynamicsProcessing` limiter. Clamping fights the user's volume keys; left for later.
- **Scenes / automation.** Activity recognition, geofences and the media session listener are public (with their
  permissions); they only drive the capabilities above.

## Layers

```
protocol/mdr      pure Kotlin (JVM): MDR frames, v1/v2 commands, parsers, model. No Android, no UI. JVM tests.
core              Android: MDR transport (RFCOMM socket, A2DP watcher) and the Sony session (HeadphonesController).
devices/api       the contract: DeviceController + capabilities (Source, Capabilities), phone fallbacks
                  (PhoneEqualizer, BluetoothBattery, PhoneSleepTarget), scenes, insights, AutoEq.
devices/<brand>   one module per brand: maps its protocol onto the capabilities; device-only sections.
app, ui           draw from capabilities only.
```

A new brand (Jabra/GNP) adds `protocol/gnp` (pure, tested on the JVM) and `devices/jabra` (transport + controller).
