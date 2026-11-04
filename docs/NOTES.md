# Hush — technical notes

How Hush talks to the headphones and to Sony's services, with pointers into the decompiled Sound Connect. For what the app does, see the [README](../README.md).

A small Kotlin + Jetpack Compose (Material 3) controller for the Sony WH-1000XM4. It speaks Sony's MDR protocol directly. The protocol was reconstructed from Sound Connect 13.3.0 (`../jadx-output`). The UI uses Material You dynamic colors from the wallpaper, and Sound Connect's own color tokens are the fallback when dynamic colors are unavailable.

## Build

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew testDebugUnitTest assembleDebug
```

Pair the headphones in Android Bluetooth settings first, then pick them in the app. Only one app can hold the control channel at a time, so close Sony Sound Connect before using Hush.

## Protocol (MDR v1, command table 1)

The whole v1 command set, the capability-driven start and support for other v1 models are described in [MDR_V1.md](MDR_V1.md). Code: `core/.../mdr/v1/`.

| Layer | Detail | Source in the decompile |
|---|---|---|
| Transport | RFCOMM, UUID `96CC203E-5068-46ad-B32D-E316F5E069BA` | `lm0/i.java` |
| Frame | `3E` · esc(type · seq · len32BE · payload · sum8) · `3C`; escape `3C/3D/3E` → `3D, b & 0xEF` | `qm0/a.java`, `qm0/b.java`, `cp0/b.java`, `cp0/f.java` |
| Data types | `0x0C` DATA_MDR, `0x0E` DATA_MDR_NO2, `0x01` ACK | `tandemfamily/message/DataType.java` |
| Reliability | One outstanding frame; ACK carries the next seq; we ACK received data with `1 - seq`; resend every 750 ms, up to 10× | `lm0/c.java`, `cp0/d.java`, `om0/e.java` |
| Commands | `v1/table1/Command.java`, payloads in `tm0/*`, structs in `vm0/*`, enums in `v1/table1/param/*` | |

| Feature | Request | Response / notify |
|---|---|---|
| Model name / FW | `04 01` / `04 02` | `05 01 len str` |
| Battery | `10 00` | `11/13 00 level charging` |
| Codec | `18 00` | `19/1B 00 codec` (`10` = LDAC) |
| NC/ASM | `66 02`; set `68 02 effect ncType ncVal asmType voice level` | `67/69 02 …` |
| EQ caps | `50 01 01` | `51 01 bands steps n (id len name)…` |
| EQ | `56 01`; set `58 01 preset n bands…` (`A0` = custom, band 10 = flat) | `57/59 01 preset n bands…` |
| DSEE | `E6 02`; set `E8 02 00 on` | `E7/E9 02 00 on` |
| Speak-to-Chat | `F6 05`; set `F8 05 01 on` | `F7/F9 05 00 on` |
| Speak-to-Chat detail | `FA 05`; set `FC 05 00 sens voice time` | `FB/FD 05 00 sens voice time` |
| Pause when removed | `F2 03` (available?), `F6 03`; set `F8 03 00 on` | `F3 03 status`, `F7 03 00 on` (KMP: `xi0/*`) |
| Auto power off | caps `F0 04`; `F6 04`; set `F8 04 01 active selectTime` | `F1 04 n ids…`, `F7 04 01 active select` |
| CUSTOM button | `F6 06`; set `F8 06 01 preset` (`00` ASC, `31` Google, `32` Alexa) | `F7 06 n presets…` |
| Sound quality mode | `E6 01`; set `E8 01 00 v` (1 = stable connection) | `E7 01 00 v` |
| NC optimizer | `82 01`, `86 01`; start/cancel `84 01 00 01/00` | `85 01 00 status` (1→2→10→11), `87/89 01 pT pV bT atm×10` |
| General settings | caps `D0 id 01`, `D6 id`; set `D8 id 01 on` (`D1` touch panel, `D2` multipoint) | `D1 id fmt len TITLE …`, `D7 id 01 on` |
| Supported functions | `06 00` | `07 00 n FunctionType…` (XM4: 11 12 13 14 21 22 30 39 51 62 71 81 A1 C1 D1 D2 E1 E2 F3 F4 F5 F6) |
| Playback controller | caps `A0 01`; status `A2 01`; `A6 01 type` (0 track, 1 album, 2 artist, 20 volume); set volume `A8 01 20 v`; control `A4 01 00 c` (1 pause, 2 next, 3 prev, 7 play) | `A1 01 steps` (31), `A3/A5 01 00 status`, `A7/A9 01 type status len text` / `… 20 v` |
| Pairing mode (table 2) | `32 01`; set `34 01 mode 00` (1 = discoverable) | `33/35 01 mode status`; the XM4 only obeys with multipoint on |
| Paired sources (table 2) | `36 01`; `3C 01 00 action addr[17]` (0 disconnect, 1 connect, 2 unpair) | `37/39 01 n (addr[17] order nameLen name)… playback`, `3D 01 00 result addr` — not on the XM4 (no function `38`) |
| Voice guidance (table 2) | `46 01 01` / `46 01 02`; set `48 01 01 on` | `47 01 01 on`, `47 01 02 lang` |

For NC/ASM, the set command echoes back the setting-type bytes the headset reported. The effect byte is `0x10` while a slider is being dragged and `0x11` when the change is committed.

Verified on hardware (2026-10-12, firmware 3.0.1): connecting, model/firmware/battery/codec reads, EQ preset names and bands, and NC ↔ Ambient switching (each change is ACKed and confirmed by a notify). All settings in Sound Connect's "All device settings" list are read correctly from the XM4, and the NC optimizer has been run end to end. Not yet exercised on hardware: the other write commands (EQ bands, switches, choice dialogs) and power-off. Voice guidance language is read-only, because changing it means downloading a voice pack.

## Protocol v2 (2026-10-21, untested on hardware)

Full notes: `docs/MDR_V2.md`. Summary:
- `mdr/v2/`: `Commands`, `FunctionType` (T1+T2, v1-equivalents), `Requests`, `Parser` (`MdrV2`), `Init`, `Protocol` (`V2Protocol`).
  The controller talks to `MdrProtocol` (`V1Protocol` / `V2Protocol`) instead of the v1 builders.
- Generation = RFCOMM service from `getUuids()` (v2 UUID first); an 8-byte protocol reply on the v1 service switches to v2.
  The XM4 advertises only the v1 UUID (checked in `dumpsys bluetooth_manager`), so its path is unchanged.
- v2 quirks: OnOff ON=0, GsSettingType BOOLEAN=0, AlertMessageType is v1−1 for 1..4 (translated), battery is POWER `22`,
  pause when taken off is SYSTEM type 1.
- Tests: `MdrV2CaptureTest` (WF-1000XM5, WF-LC900 captures from mos9527, MIT), `MdrV2RequestsTest`, `MdrV2FuzzTest`,
  `SonyReferenceV2Test` (Sound Connect's own v2 classes from `tools/sonyref/sound-connect.jar` read every request and reply),
  `HeadsetSimV2Test` (controller end to end with `SimHeadset(v2 = true)`, `Profiles.wf1000xm5()`).
- Catalog: v2 models in `SonyModule.V2_MODELS`, "Not tested on hardware".
- Not done: optimizer, SENSE, firmware/serial, VG language write, listening modes, LE Audio, 10-band EQ UI scale, self test on v2.

## Home screen widgets (Jetpack Glance 1.2)

Four widgets in `widget/`, from smallest to largest, each `SizeMode.Responsive` within its range:

| Widget | Default → max | Shows |
|---|---|---|
| Battery | 1×1 → 2×2 | battery and mode icon; tap cycles NC → Ambient → Off (or reconnects) |
| Status | 2×1 → 4×1 | render, name, battery · mode; the wide size adds mode buttons |
| Controls | 2×2 → 3×3 | render, battery, NC / Ambient / Off buttons sized to the width |
| Dashboard | 4×2 → 5×4 | render, name and status with codec, battery ring on the right, labelled mode buttons; from 4×3 (height ≥ 180dp) a larger header with the current mode and DSEE / Speak-to-Chat toggle chips |

Widgets read `HeadphonesController` directly. A live Glance session recomposes on its own, and `HushWidgets.updateAll` refreshes widgets whose session has ended. Colors follow `GlanceTheme` (Material You).

Previews:
- the widget picker gets generated previews from `providePreview` with sample data, published once per app version by `HushWidgets.publishPreviews` (Android 15+; the API is rate limited, so a limited call is retried on a later launch);
- `res/drawable-nodpi/widget_preview_*.png` are real on-device snapshots used as `previewImage` on older versions;
- `widget/WidgetPreviews.kt` has Android Studio `@Preview`s at the edges of each size range, connected and disconnected.

## Window sizes and foldables

`ui/Adaptive.kt` turns the window size and fold posture (material3-adaptive) into a `HushLayout`, which the tab
pages read through `LocalLayout`:

- **Phones, portrait:** bottom bar, one column. Below 700dp of height the app bar starts medium instead of large; columns under 340dp get a smaller battery ring and optimizer badge.
- **600dp wide and up:** navigation rail. **840dp and up, or a landscape phone:** two columns in a staggered grid, each capped at 600dp. Under 480dp of height the app bar stays small and the EQ sliders shrink.
- **Fold held like a book (vertical hinge, half open):** rail, plus two columns cut at the hinge (`HingeCells`).
- **Fold lying flat (horizontal hinge, half open):** a status pane above the hinge (battery, NC mode, a "Switch mode" button) and the tabs below it.

To check layouts without headphones, a debug build shows a connected XM4:

```
adb shell am start -S -f 0x10008000 -n com.occaecat.hush/.MainActivity --ez demo true
```

On 2026-10-15 this was checked on emulators: small_phone (360×640dp), Pixel 10 Pro XL, and Pixel 10 Pro Fold
(open, half open in book and tabletop postures, closed on the outer screen, landscape), plus Android Desktop (API 34).

## Android TV / Google TV (`:tv`)

A separate TV-only app module built with Compose for TV (`androidx.tv:tv-material` 1.1.0, the May 2026 stable;
Leanback is deprecated). It talks to the headset directly over the TV's Bluetooth through the shared
`HeadphonesController`, like the phone app. `Labels` and `demoState` moved to `:core` so phone and TV share them.

- **Manifest:** `LEANBACK_LAUNCHER`, `leanback` required (keeps it off phones), touchscreen not required, 320×180
  xhdpi banner (`drawable-xhdpi/banner.png`, generated from the XM4 render), landscape only.
- **Layout:** the 960×540dp TV canvas with the guide's 48dp side and 27dp top/bottom safe margins (`Tv` in `Theme.kt`).
  A modal navigation drawer (icons collapsed, labels over a scrim when focused) with Listen / Sound / Device, a status
  pane with the render, battery, codec and mode, and the section list.
- **Theme:** dark only, the Sony night palette mapped onto tv-material roles; focus inverts to `inverseSurface` and
  scales 1.02–1.05×.
- **D-pad:** launch focuses the content. ▶ from the drawer enters the content through a focus restorer: the last row in
  the same section, else the first row. Back goes content → drawer → exit. Sliders don't exist on TV: ambient level is a
  ◀ ▶ stepper, and an EQ band is edited by pressing OK, then ▲ ▼, then OK or Back.
- **Back and targetSdk 36:** Back arrives through the predictive-back dispatcher, not as a key event, so per-widget
  Back handling has to be a nested `BackHandler`, not `onPreviewKeyEvent(Key.Back)`.
- **Pairing:** Google TV has no `Settings.ACTION_BLUETOOTH_SETTINGS` activity. "Pair headphones" opens
  `com.google.android.intent.action.CONNECT_INPUT` (TV Settings › Remotes & accessories), falling back to the
  Bluetooth settings action and then Settings.

```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :tv:assembleDebug
adb shell am start -S -n com.occaecat.hush/.tv.MainActivity --ez demo true   # debug: connected XM4 demo
```

On 2026-10-15 this was checked on the Google TV 16 emulator (API 36 `google-tv` x86_64, AVD `Hush_google_tv`,
tv_1080p): demo screens, drawer, EQ edit mode, the Back chain, dialogs, the permission prompt, the picker and the
pairing screen. Screenshots are in `research/tv-screenshots/`. The image won't boot with less than 6 GB of userdata and
the emulator wants about 7.4 GB free to create it. It has not run on a real TV with the headset yet.

## ColorOS integration (realme UI 7 / ColorOS 16.1)

The headset session lives in `HeadphonesController` (one per process) and `HushService` keeps it in the foreground while connected. On top of it:

- **Quick settings tile** (`HushTileService`): tap cycles NC → Ambient → Off, or reconnects. No root needed.
- **Notification** with battery and mode buttons. No root needed.
- **My Devices card** (`DeviceCardProvider`, `DeviceCard`): My Devices discovers device apps by a provider with the `oplus.devicecards.action.DEVICE_INFO` filter and reads `device_info_table_new`, whose `device_data` JSON is Gson-parsed into `DeviceModel`. It also calls the provider (`connect`, `disconnect`, `delete_device`, `card_show`, `card_hide`) and expects a reply on the `callBinder` (`com.heytap.deviceinfo.DeviceAppCallbackInterface`, transaction 1, code 196611, `result_code` 100 = ok).
- **Xposed module** (`xposed/HushModule`, libxposed API 102, scope `com.heytap.mydevices`, tested with Vector 2.2):
  - adds Hush to the built-in allowlist (`DeviceAppConfigManager`);
  - forwards provider changes to the per-package observer (`AppAgentManager`);
  - marks the card as linked, so the plain Bluetooth card for the same headset steps aside. The card id is `md5(upper-case MAC)`, the same id My Devices gives the Bluetooth card;
  - in `:cards`, routes card taps to `DeviceCardPanelActivity`, because the tap controller registry has no entry for unknown packages.
  - in `com.oplus.wirelesssettings`, adds real COUI switches to the headset's Bluetooth page: Noise cancelling, Ambient sound, and a row that opens the panel (`BluetoothSettingsHook`). They are inserted into `BlockingPrefWithSliceController`, the AOSP class that renders the Fast Pair slice. On ColorOS that class only turns slice rows into links, so the switches have to be added by the hook. The rows appear only when the headset has that slice (Fast Pair metadata 16).
- **Quick device connect card** (`xposed/ConnectPopup.kt`, scope `com.heytap.accessory`). When a session first comes up, Hush sends a broadcast guarded by a signature permission. The hook in accessory's main process receives it and calls PantaConnect's plugin dialog API (`SceneService`, `com.heytap.accessory.plugin.discovery.action.DIALOG`, type 0 then 2). Details that matter:
  - each card uses a fresh `02:…` placeholder address, because PantaConnect remembers the tag of every card it has shown and filters repeats;
  - the request cannot come from My Devices, because that process is frozen in the background and misses Hush's changes;
  - `DialogActivity` runs in `:ui`, where the hook supplies the XM4 render for a fake drawable id, skips the first automatic close and routes "Controls" to the panel.
- **Notifications**: the controls (battery, mode, NC / Ambient / Off) are in a plain silent notification (channel `controls`, LOW). The ColorOS Fluid Cloud capsule is used only for events (`LiveAlerts.kt`): connected (battery), disconnected (planned reconnects excluded) and battery at or below 10% (once per drop). Each is a promoted live update (`android.requestPromotedOngoing` and `android.shortCriticalText` extras, channel `events`) that removes itself after a few seconds through `setTimeoutAfter`. Promotion needs the app's Live alerts switch (appop `POST_PROMOTED_NOTIFICATIONS`).
- **Control center tile**: Hush offers it once via `StatusBarManager.requestAddTileService`. Hush also asks to be exempt from battery optimization, because otherwise Android does not let the tile, the card or Settings start the foreground service from the background.
- **Quick panel** (`DeviceCardPanelActivity` + `ui/HeadphonesPanel`): a translucent sheet that opens over My Devices or Settings. It shows the render, battery, noise modes, ambient level and voice focus.

Checked against the factory My Devices **17.4.10** (`pm uninstall-system-updates com.heytap.mydevices`). The 17.5.1 update is saved in `research/host-apks/` and has not been tested. Methods are found by shape, but a store update can still break the hooks.

Enable with `su -c "/data/adb/lspd/cli scope set com.occaecat.hush com.heytap.mydevices/0 com.oplus.wirelesssettings/0 com.heytap.accessory/0; /data/adb/lspd/cli modules enable com.occaecat.hush"`, then force-stop My Devices.

## Equalizer (Sound Connect parity)

- **Preset slots** follow Sound Connect (`EqResourceMap`, `xn/l.java`): `A0` is "Manual", `A1`–`A5` are "Custom 1–5".
  A band edit is sent with the *active* preset id when that is a writable slot, so Custom 1/2 keep their own
  levels; on a factory preset the edit lands in Manual (`HeadphonesController.setEqBand` / `writeEqPreset`).
  Reset (the round button in the EQ header) flattens the active writable slot.
- **Find Your Equalizer** (`ui/FindYourEq.kt`) is Sound Connect's `feature/eqlisteningcomparison` for the standard
  six bands: `EqBandStepsStandard` (Clear Bass offsets ±3/6/9; 2.5k/6.3k/16k offsets 0..2 / 1..4 / 2..6, 400 Hz and
  1 kHz untouched) and the round tables from `y.java` — round 1 is the four bass×treble quadrants around flat, round
  2 narrows in around the first pick. Every candidate is written to Custom 2 and selected while auditioning (as in
  `u.java`); cancelling writes Custom 2's previous levels back and reselects the preset that was active; Before/After
  then Save leaves the result in Custom 2. A hint shows while no music is playing (`AudioManager.isMusicActive`).
  Verified on hardware 2026-10-17: candidates are applied live, cancel restores both Custom 2 and the old preset.
- Not ported: Creators' EQ / artist presets (QR-code download, newer models only), EQ ULT modes and the 10-band
  variant of Find Your Equalizer (`EqBandSteps10band`), which the XM4 doesn't have.

## Device tab additions (2026-10-17)

- **Now playing** (Listen tab): track / artist / album, previous / play-pause / next and the headset's own volume
  (0–30) through PLAYBACK_CONTROLLER (`tm0/w`, `k2`, `k3`, `l2`, `l3`, `vm0/e0`, `g0`). Verified on the XM4.
  On a skip the headset only sends `a9 01 00` ("track changed", no text); like Sound Connect (`c40/b`) Hush then
  re-reads track/artist/album, retrying a few times if the title comes back empty because the metadata is not there yet.
- **How to use** (Device → Buttons): Sound Connect's instruction guide. The headset lists its GuidanceCategory codes
  (`04 04` → `05 04 n codes…`; XM4: touch panel 0x30, Quick Attention 0x50, CUSTOM button 0x60). For each code Hush
  asks Sony's Concierge (`POST https://concierge.support.sony.net/api/v1/browseSolutions`, partnerId
  `08cd1c37-3f86-3263-a94b-d5c1e28cc343`, appInfo.metadata `actionType=DIRECT`, `directId=PTOUR_<code>`,
  `targetModelName`) — the request of Sound Connect's `pj/v` + `ConciergeWrapper`. The answer's `url` redirects to
  `helpguide.sony.net/tutorial/<id>/<lang>/pageNN.html`: `<h1>` title, `<picture>` GIF with a dark `<source>`,
  a `<p>` or a gesture table. `guide/InstructionGuide.kt` parses that and `ui/GuidePage.kt` lays it out natively;
  GIFs are cached in `cacheDir/guide`. Models without a page get a 404 and the topic is skipped.
- **Official renders**: `04 03` → `05 03 series color` gives Sony's ModelColor. Sound Connect maps model + colour to an
  image through its cloud model list (GraphQL `GetAllCloudModelInfos` on `v1.api.data-gateway.seeds.services`,
  cached in `shared_prefs/cloud_model_info_preference.xml`, field `sca_image_image_url`). The four XM4 images
  (black 0x00/0x01, white 0x02, silver 0x03, blue 0x05) are bundled as `xm4_sony_*` (trimmed) and picked by
  `renderFor(state.modelColor)`; unknown colours fall back to `xm4_angle`.
- **Serial number**: table 1 `UPDT_GET_PARAM` (0x36) type 6 `SERIAL_NUMBER` → `37 06 len ASCII`, the value Sound
  Connect sends with its firmware-update check (`n20/a`, together with category / service / nation / language).
  The XM4 pads it to 16 digits (`000000000NNNNNNN`); Hush shows it without the leading zeros only in Device details,
  hidden as dots until tapped, with a copy button.
- **Device details** (Device → About): every UPDT_GET_PARAM type (`36 02…0B`). XM4 answers: category `HP002`,
  service id `MDRID294301`, nation code `CE7`, language empty, BLE TX power −53 dBm (signed byte), battery to start /
  keep updating 20 % / 20 %, UpdateMethod `0x25` (MediaTek, background, resumable), device-binding id (16 hex,
  hidden like the serial); `36 01` (FW_UPDATE_MODE) gets no answer. Plus series (0x30 Premium), colour, protocol
  version 0x7000 and the supported-function list.
- **Firmware update check** (Device → About → Updates), Sound Connect's AutoMagic (`com.sony.songpal.automagic.e`,
  `kh.d`): GET `https://info.update.sony.net/<category>/<service id>/info/info.xml` (XM4: `HP002/MDRID294301`).
  Header lines `eaid:ENC0003` (AES-128-ECB, zero padding, key `4fa27999ffd08b1fe4d260d57b6d3c17`; ENC0002 is 3DES with
  a zero key), `daid:HAS0003` (SHA-1), `digest:` = sha1(hex(sha1(decrypted body)) + serviceId + category), then a
  blank line and the body: an `InformationFile` XML whose `ApplyCondition`s have `Rule`s on Model / SerialNo /
  FirmwareVersion and `Distribution`s — the `binary` (UPG_x_enc.bin) and a `notice` XML with release notes per locale
  (`<Notice Locale="Russia">`…). `update/FirmwareCheck.kt` decrypts, verifies, evaluates the rules and pulls the
  "[Update details in version …]" block in the phone's language; the result is cached 12 h. Installing is left to Sound
  Connect (the page has a button for it). As of 2025-10-08 the XM4's latest is 3.0.1.
- **Speak-to-Chat state**: SYSTEM_RET_PARAM is `f7 05 00 <on>` (ON_OFF), but notifies are `f9 05 <type> <on>` with type 1 =
  the setting and type 2 = *preview mode*. Turning it on from the headset sends `f9 05 01 01` then `f9 05 02 00`; reading
  the second as the setting made Hush show "off". Type 2 is now ignored (`ym0/c`, `ym0/d`). `f5 05 …` is the status
  (enabled, talking-now).

## Motion (`ui/Motion.kt`)

On top of `MotionScheme.expressive()`, using two springs (under-damped for presses and hops, stiffer for exits):

- `squishy()` — scale down while pressed, overshoot on release (noise-control buttons, list rows, scene chips, EQ presets).
- `hopOnSelect()` — an icon squashes and springs past full size when it becomes selected (nav bar, modes, scenes).
- `morphingBlob()` — the Listen hero's backdrop: a `MaterialShapes` polygon that `Morph`s to the noise mode's shape
  (ANC `Cookie12Sided` / primaryContainer, Ambient `Flower` / tertiaryContainer, Off `Circle` / secondaryContainer)
  with an overshooting spring, a kick, a slow spin and a breath.
- `predictiveBack()` — `PredictiveBackHandler` with the M3 predictive-back transform (scale to 90%, shift
  width/20 − 8dp towards the swipe edge, rounded corners). Cancel bounces back; commit either flings the page out
  and closes it (full-screen pages, tour) or steps back and springs the page in place (tab → Listen, Find Your
  Equalizer rounds, AutoEq preview → search). Compose dialogs have their own back dispatcher, so the handler sits
  inside the dialog content.

## Everyday features (`features/`)

- **Scenes** (Listen → Scenes): one tap applies noise control + EQ (any `SoundAction`), Speak-to-Chat and the headset
  volume; long press edits, "+" adds. Stored in `sound.json` (`scenes`), defaults Commute / Office / Walk / Sleep.
  Tapping the active scene turns it off and restores what the headset was doing before the first scene went on
  (noise control, EQ preset, Speak-to-Chat, volume). Library curves are written into Custom, so Custom's old bands
  come back only if Custom was the active preset. A manual mode change clears the scene and the saved state.
- **Sleep timer** (moon on the player): 15 min…2 h or end of track; fades the headset volume over 45 s, pauses, then
  powers the headset off (or restores the volume). `features/SleepTimer.kt`.
- **Touch and hold** now also does in-app actions: switch noise mode, next scene, 30-min sleep timer, recognize song
  (Shazam `START_TAGGING`, else Google `MUSIC_SEARCH`), voice recorder, flashlight, or a broadcast
  `com.occaecat.hush.action.HOLD` for Tasker.
- **Insights** (Device → Insights): battery history with drain measured only inside unbroken connections (so time with
  another device isn't counted as idle; with multipoint the laptop's drain is seen), per NC on/off, Sony's rating
  (30 h / 38 h) until 2 h are measured; listening minutes and a weekly sound dose (volume → ≈dB, WHO 80 dB × 40 h);
  where/when the headset last disconnected (needs location, "all the time" for screen-off disconnects).
  `insights.json`, 30 days.
- **Firmware watch**: a persisted weekly JobScheduler job (id 4711, any network) re-runs `FirmwareCheck` for the last
  headset and notifies once per new version ("Update in Sound Connect"; tapping opens Updates). The same job checks
  Hush's own releases.
- **Updates** (Device → About → Updates, `ui/UpdatesPage.kt`): one page for Hush and the headset firmware; the row's
  summary names whatever can be updated. Hush's part is `update/AppUpdate.kt`: GET
  `api.github.com/repos/dmitthedazed/hush/releases/latest` (no token; 404 = no releases yet; cached 6 h), version from
  `tag_name` (`v0.16.0`), the APK is the asset ending in `-phone.apk`, `SHA256SUMS.txt` is checked when the release
  has one. The APK is downloaded to `cacheDir/updates`, its package name checked, then installed through a
  `PackageInstaller` session (`InstallReceiver` gets the status and opens Android's confirmation;
  `USER_ACTION_NOT_REQUIRED` makes later updates silent once Hush is the installer of record). Needs
  `REQUEST_INSTALL_PACKAGES` and the per-app "Install unknown apps" switch, which the page asks for. Android rejects
  an APK signed with another key, so releases must be signed with the same key as the installed build.
  `scripts/release.sh <version>` builds, signs, writes `SHA256SUMS.txt` and creates the GitHub release.
- **Help** (Device → About → Help): the model's full Help Guide (`helpguide.sony.net/mdr/<model>/v1/<lang>/`), the
  tour and How to use, and Concierge articles — HELP `dashboard` / `connectionErrorDialog`, DIRECT
  `2DEVICE_MULTIPOINT_GUIDE`, `HPC_FW_UPDATE_COMPATIBLE_INFO`, `SETTING_COMPATIBLE_DEVICE_LIST`. Sony's site rejects
  non-browser clients (403), so they open in the browser. The not-connected placeholder and the "controls are on …"
  card link the connection / multipoint articles.
- **Tour**: shown once after the first connection of a headset with an instruction guide (pager over the guide topics),
  again from Help.
- **Backup & restore**: one JSON (`format: hush-backup`) with `sound.json`, the hold action, sleep-timer option, an
  optional insights history and a headset snapshot; restoring can re-apply the headset settings that don't force a
  reconnect (CUSTOM button, multipoint and sound-quality mode are recorded but not applied).
- **Connection**: multipoint switch, "Pair a new device" (offers to turn multipoint on first, since the XM4 ignores
  pairing mode otherwise; the row follows the headset's notify), and — on headsets with
  PAIRING_DEVICE_MANAGEMENT — the paired-source list with connect / disconnect / remove (`an0/*`, `q30/a`, `q30/b`).
  The XM4 doesn't report its other connections, so the list never shows there.
- **AI assistant** (`assistant/Assistants.kt`): "Voice button opens" Phone default / ChatGPT / Claude. Choosing an
  AI enables `VoiceCommandHandler` (VOICE_COMMAND, sent by Android when the headset asks for voice recognition —
  touch and hold on the XM4 while the CUSTOM button isn't Google Assistant/Alexa) which opens
  `https://chatgpt.com/voice` in ChatGPT or `VOICE_ASSIST` in Claude (its voice overlay). "Ask now" buttons and the
  launcher shortcuts (`res/xml/shortcuts.xml`) go through `VoiceCommandActivity`. Both targets were opened on the
  phone; the headset button path itself still needs a hold on the ear cup to confirm.

## Sound customization beyond Sound Connect (`sound/`)

Everything is phone-side and stored in `filesDir/sound.json` (`SoundStore`).

- **Presets** are split into *Sony presets* (factory) and *Your presets*: the headset's Manual / Custom 1–2 plus the
  EQ library. A library curve is written into Manual when picked (it shows as active while Manual holds its levels).
- **EQ library**: unlimited named curves — save the current EQ, import/share `HUSHEQ1:<dB,…>|<name>` codes, save
  to Custom 1/2, rename, delete. AutoEq ("Save to library"), Find Your Equalizer (on Save) and the hearing test
  (on Apply) also add their results.
- **Hold to compare** (button in the EQ header): selects Off while pressed, restores the previous preset (writable
  slots with their levels) on release. The press handler is keyed on `Unit` and the button never leaves composition,
  otherwise the recomposition caused by selecting Off cancels it before it can restore.
- **Hearing test** (`HearingTest`): pulsed tones at 80 Hz / 400 / 1k / 2.5k / 6.3k / 12.5 kHz (one per band) rising
  4 dB per burst from −80 dBFS with the EQ set to Off; thresholds relative to 1 kHz minus ISO 226 relative
  thresholds give the loss, half of it (>3 dB, ≤ +6 dB) becomes the band gain. Writes Manual, keeps a library copy.
- **Adaptive Sound Control** (`SoundAutomation`): Sound Connect's activity + My Places switching, done with Play
  services Activity Transition API (still / walking / running+bicycle / vehicle) and geofences (current location,
  150 m). Each trigger has a `SoundAction` (noise control mode, ambient level, voice focus, EQ preset or library curve).
- **App profiles**: a `NotificationListenerService` (`MediaWatcher`) reports the package of the playing media
  session; a rule for that app applies its action.
- Merging is field by field, app > place > activity, debounced 1.5 s, and only changes are sent, so manual changes
  stick until the situation changes. The EQ automation replaced is restored when no trigger asks for one any more.
  The engine starts with `HushService`; Play services broadcasts (`AutomationReceiver`) also start it.
- Verified on the phone 2026-10-17: library save/delete, preset split, compare (Bright → Off → Bright), all pages
  render. ASC and app profiles were not switched on (they need the user's permission grants); their runtime
  behaviour is untested.

## AutoEq

Sound → AutoEq searches the [AutoEq](https://github.com/jaakkopasanen/AutoEq) profile index (`results/INDEX.md`,
cached for a week in `cacheDir`, stale copy used offline) with the connected model pre-filled. Picking a profile
downloads its `GraphicEQ.txt` correction and fits it onto the headset's six bands (`autoeq/AutoEq.kt`):

- bands are modelled as RBJ biquads at 48 kHz: Clear Bass as a low shelf (100 Hz, Q 0.7), 400 / 1k / 2.5k / 6.3k / 16k
  as peaking filters (Q 1.05). Sony doesn't publish the real shapes, so these are approximations;
- ridge-regularised least squares over 20 Hz–16 kHz with a free level offset (the headset has no preamp), bands that
  exceed ±10 dB are pinned and the rest refitted, then rounded to 1 dB steps;
- the sheet plots the target vs. the resulting response and the RMS error; Apply writes the levels to the active
  Manual / Custom slot, or Manual (`HeadphonesController.applyEqBands`). The applied profile name is kept in the `autoeq` shared preferences
  and dropped as soon as a band is moved by hand.

For oratory1990's WH-1000XM4 measurement the fit is Clear Bass −2, +1, +2, +5, +2, +2 (RMS 1.5 dB vs 3.1 dB flat).

## Device modules

Hush is one app with one UI; what differs between devices is what they can do. Every brand is a Gradle module
under `devices/` that reports state and capabilities; the app draws the same pages for all of them.

```
:app            shell (DeviceModules, MainActivity with the device switcher) + the shared pages:
                DeviceUi (Listen / Sound / Device), AutoEqSheet, UpdatesPage, DeviceDetailsPage, AboutPage
:ui             design kit: theme, rows, sheets, TabScaffold, DeviceShell (tabs), ProductHero, EqualizerHero,
                PresetChips, NowPlayingCard + NowPlaying (phone media session), FullScreenPage, StatusCard
:devices:api    DeviceModule + DeviceController contract, DeviceSwitcher, AutoEq, AppUpdate, appIntent()
:devices:sony   SonyModule / SonyController (+ SonyEqualizer, SonyFirmware) and Sony-only sections and features;
                sony/wired: Sound Connect's 22 passive wired models (SonyWired, SonyWiredController)
:devices:apple  AppleModule / EarPodsController: EarPods (USB-C)
:core           Sony MDR protocol and session, shared with :wear and :tv
```

- `DeviceController` = `state: StateFlow<DeviceState>` (name, connection, battery, hero chips, track, volume, card
  facts, details…) + optional capabilities (`equalizer: Equalizer?`, `firmware: Firmware?`, `setVolume`) + slots for
  what only that device has (`listenSections` / `soundSections` / `deviceSections`, `CardActions`, `aboutRows`,
  `PlayerBadge`, `Overlays`, `Gate` for permission/picker screens, `statusPane`). A missing capability = that part of
  the shared page isn't shown.
- Shared pages: Listen = hero (render, battery or status label, chips) + device sections + player (phone media
  session, device track/volume as fallback). Sound = EQ hero (bands, compare, reset, optional on/off) + preset groups
  + AutoEq + device sections. Device = device card (+ device actions) + device sections + About (Updates: Hush and
  the device's firmware; device's own rows; Device details; About Hush).
- AutoEq: `Equalizer.autoEq: AutoEqTarget` says how a correction lands — Sony fits it onto six bands, the EarPods'
  phone-side EQ takes the curve as is.
- Device sections are `LazyStaggeredGridScope` extensions; Sony reads its state through a Compose `mutableStateOf`
  snapshot so the grid rebuilds when the headset changes.
- Modules can't see `:app` classes (`appIntent(context)` opens the app). Resources are non-transitive:
  `com.occaecat.hush.ui.R` for shared ones, `com.occaecat.hush.sony.R` for Sony's. Kotlin packages of the moved Sony
  code stayed `com.occaecat.hush.*`; keep file names unique per package across modules (same-name files clash at dex).
- Shared app features (any device): the EQ library (`EqLibrary`: curves per equalizer layout, dB, `HUSHEQ2` share
  codes, old `HUSHEQ1` XM4 codes still import; Sony's old `sound.json` library migrates on start), the sleep timer
  (`SleepTimer` + `SleepTarget`: the device's own volume and power, or the phone's music volume), Insights (per device
  key, battery only for devices with one; old XM4 data migrates to key `sony`; Hearing is merged — one weekly dose
  for the listener, bars stacked by device, and one minute-tick for all devices so a minute goes to the device that
  plays (it says so, else the newest connection), never to two; Find my devices is one page with a card per
  registered device), home screen widgets (show
  `DeviceModules.current`, modes and switches from `QuickControls`), Updates, About, AutoEq, device details.
- Shared device capabilities (`devices/api/.../DeviceControls.kt`), drawn by the shared pages in
  `devices/api/.../ui/DeviceSettings.kt`, so a new brand fills in data rather than drawing UI:
  - `NoiseControl`: modes (kind, button glyph, widget glyph), level and Focus on voice under a mode with `hasOptions`;
    the Listen card, the ColorOS quick panel and the widgets' modes (`quickModes`) all come from it.
  - `DeviceState.batteries`: one `BatteryLevel` or several (left / right / case); `battery` / `charging` are the
    "how long does it last" view (lowest bud, never the case). The hero and the device card show all of them.
  - `settings()`: `SettingsGroup`s of `Setting.Toggle` / `Choice` / `Action` for Listen, Sound or Device. Choice
    sheets, "reconnect?" confirmations (`Confirm`), notes under a group and running actions (spinner + button) are
    the shared UI's. Sony's DSEE / Speak-to-Chat switches, Buttons, Wearing, Conversation and Power & prompts are
    this now (`ui/SonySettings.kt`); only rows that need their own layout (Sony's connection / peer list) stay sections.
- Way forward (from Gadgetbridge, Galaxy Buds Client and OpenFreebuds): a module per brand (protocol), a model as
  data (which capabilities, ranges, presets), refined at runtime by what the device reports (Sony's support-function
  list); add a capability to `DeviceControls.kt` when a second brand needs the same thing.
- Devices are registered (`DeviceRegistry`, `files/devices.json`): the switcher lists every registered device,
  connected or not, plus "Add device". Modules report live devices (`devices`, for "connected"), what they can see
  (`candidates`: paired Sony headsets by name or alias, plugged-in EarPods), their `models` for adding by hand, and
  hooks: `scan`, `onOpen` (Sony connects to the opened headset), `onRemove` (Sony forgets it), `remembered` (moved into
  the registry once on upgrade). With nothing registered the add screen is the app. A found unit that isn't registered
  gets one "New device found" prompt; "Not now" is remembered. Add screen: Found, then brand → model → how to get it
  ready, its found units, and "Add" for models that need no unit (EarPods).
- Microphone test (any device with `DeviceController.microphone`): records 5 s from the device's mic (or the phone's,
  to compare) and plays it back, with peak and background levels (`features/MicTest.kt`, `ui/MicTestPage.kt`).
  Bluetooth mics exist only on the call profile: the headset becomes the communication device while recording (music
  pauses, 16 kHz), then the route is dropped and playback goes over A2DP. USB picks the input/output directly (48 kHz).
- Scenes and automation (any device with noise control or an EQ; `devices/api/.../scenes`, `sound/Automation.kt`,
  `app/.../ui/SceneChips.kt`, `AutomationPages.kt`): per device key, scenes (noise mode + level + voice, an EQ preset /
  library curve / Off, volume, `SceneSwitches` such as Sony's Speak-to-Chat), Adaptive Sound Control (activities, places)
  and app profiles. Places are shared by all devices, what happens there is per device. Everything is applied through
  `NoiseControl.apply`, `Equalizer` (+ `snapshot`/`restore` to put the EQ back) and `setVolume`; the app feeds
  `DeviceModules.targets` (connected, ready devices). Devices without saved settings get defaults built from their
  modes. Hush 0.17's Sony `sound.json` migrates into `scenes.json` (`legacySony`, taken by the first Sony headset);
  `AutomationReceiver` / `MediaWatcher` kept their class names so geofences and notification access survive.
- Wired headphones (`devices/api/.../wired`): `PhoneEq` is the one phone-side EQ effect (DynamicsProcessing on session
  0; the device that applied it last owns it, so one going away doesn't switch off another's), `PhoneEqualizer` is a
  device's settings + the shared `Equalizer` / widget switch / AutoEq row on top of it, `PhoneEqService` keeps the
  process alive while it runs, `WiredAudio` watches the jack and USB audio outputs (self-identifying accessories
  `claim` theirs — EarPods claim USB while attached; the rest is `free`), `RemoteRender` caches makers' renders.
  `Transport.WIRED` = passive headphone; `Transport.icon` / `found` for the shared screens.
- Sony wired (`devices/sony/.../sony/wired`): the models Sound Connect lists as wired — its 360 Reality Audio device
  list (`GET api.iac.meta.ndmdhs.com/v1/device/list`, no auth, type `passive`; `IaDeviceModel`, `jm0.n`
  "Search for wired headphones from a list"). Renders: `image.iac.meta.ndmdhs.com/device/<name>/icon.png`
  (transparent, trimmed). Added by hand (brand page groups "Wired · over-ear / in-ear"); since they can't be told
  apart, the registered wired model opened last is the one on the free wired output. Each gets its own phone EQ
  (`wired_eq_<id>`), AutoEq ("Sony <model>"), Insights (`sony:wired-<id>`, phone volume), sleep timer and, when the
  cable has a mic line, the mic test. Sound Connect itself only offers 360RA registration for them (needs a Sony
  account, ear photos and a partner app) — not ported.
- Still Sony-only (tied to its protocol): Find Your Equalizer and the hearing
  test (Sony's six-band tables), help, backup, the tile, notification,
  ColorOS card, Xposed.
- New brand: `devices/<brand>` shaped like `devices/apple`, include it in `settings.gradle.kts`, depend on it from
  `:app`, add it to `DeviceModules.all`.

### Demo devices

A model page has "Add as demo" when its module says `hasDemo(model)` (Sony: every Bluetooth model). It registers
"<model> (demo)" with the address `demo:<model id>` (`Device.isDemo`, `demoCandidate`). `DeviceModules` asks the
module for `demoController` instead of `controller`, and never calls `onOpen` / `onRemove` for it. The switcher shows
"Demo"; a demo is never `active`, so it doesn't take the screen from a real device and scenes don't target it.

Sony's demo is a `SonyController` over `HeadphonesController.demo(state)`: a second controller that starts from a
canned state (`ui/Demo.kt`; models without their own get one of the same kind, renamed so the model's picture shows),
has no Bluetooth, watchers or saved address, and only updates its own state. `HeadphonesState.demo` turns off the
self test, which would otherwise probe the real headset.

## Apple EarPods (USB-C)

`:devices:apple` (`com.occaecat.hush.apple`). USB 05AC:110B, firmware 26.81 on the reference pair.
Three configurations: UAC2, UAC3 BADD Headset (subclass 0x24) and full UAC3, announced through BOS Configuration
Summary capabilities; 48 kHz only (16/24-bit stereo out, mono mic); HID consumer remote (play/pause, vol±, voice
command) plus an Apple vendor HID channel (usage page 0xFF00, 63-byte reports, protocol unknown, not used). Feature
units only do mute/volume, and there is a hardware sidetone mixer (ALSA "Sidetone Mixing") that Android doesn't expose.

- **Listing**: `AppleModule` lists the EarPods only while plugged in, so plugging them in switches the app to them.
  `UsbAttachedActivity` carries the `USB_DEVICE_ATTACHED` filter (`xml/usb_earpods.xml`): "Always open Hush" there
  also grants USB access; otherwise "Allow USB access" asks.
- **Authenticity** (`apple/usb/EarPodsCheck.kt`): compares IDs, names, serial format, device descriptor, the three audio
  configurations, HID report lengths (31/42), endpoint packet sizes, power and the BOS BADD headset summary with the
  reference pair (fixtures in `devices/apple/src/test/resources/usb/`, `EarPodsCheckTest`). A fingerprint, not crypto.
- **Equalizer** (`apple/earpods/SoftEq.kt`): `DynamicsProcessing` on session 0, 31 third-octave pre-EQ bands from the user's 10
  bands (±12 dB) plus an optional AutoEq GraphicEQ correction, input lowered by the largest boost, limiter on.
  `EarPodsService` (FGS) keeps the process alive while plugged in with the EQ on. Verified on ColorOS 16 2026-10-19: the
  effect sits on the mixer thread that is routed to `AUDIO_DEVICE_OUT_USB_HEADSET` during playback.
- Apple links (user guide PDF, manuals page 301005, checkcoverage, Platform Security "Verifying accessories",
  apple.com/sound, warranty, store) are in `AppleLinks`.
