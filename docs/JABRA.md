# Jabra protocol and support

Hush implements Jabra GNP independently. Support is **experimental**, with physical-device testing on the original Jabra Elite 10 (product ID 11846, firmware 4.6.0) and Elite 85t (9421, firmware 2.0.0). Both have passed configuration writes with confirming reads, EQ restoration, and reconnect after an app update/restart. Coverage is limited to those firmware versions and the checks recorded below. Elite 10 Gen 2 and other products are rejected during initialization.

The references are the user-supplied Sound+ `jabra-soundplus-5-21-0-0-11448-0988ad39a.apk` and [JabraLibre's published Elite 10 findings](https://github.com/pLum0/JabraLibre). JabraLibre reports hardware testing on firmware 4.6.0. Its GPL source is not incorporated into Hush. Vendor binaries, decompiled code, images, configuration XML, and encryption material are not distributed with Hush.

## Transport, framing, and lifetime

Connect to the paired device through the standard Serial Port Profile UUID `00001101-0000-1000-8000-00805f9b34fb`, using insecure then secure RFCOMM. Bounded reflected-channel fallbacks cover Elite 10 channel 29 and the legacy channel 3. Each connect attempt has a five-second deadline, and closing a session also closes an in-progress socket. These fallback channels are not proof of model identity: the product ID must pass the allowlist before any configuration write.

The wire header is:

| Offset | Meaning |
| --- | --- |
| 0 | Destination endpoint, normally 4 for the headphones or 9 for the application |
| 1 | Source endpoint |
| 2 | Request sequence; unsolicited events normally use zero |
| 3 | Upper two bits: event=0, read=1, write=2, response=3; lower six bits: **total packet length** |
| 4 | Command |
| 5 | Subcommand |
| 6 onward | Semantic payload |

Hush describes command and subcommand together as a two-byte **topic**. For example, `04 09 07 88 13 be 01 04` writes ANC using sequence 7; `88` means a write containing eight bytes total. There is a length field; the response bytes `c7`, `c8`, and `d1` are lengths combined with response type, not different serializers. Frames are 6–63 bytes, can span socket reads, and can arrive together. Payload bytes that resemble headers are ordinary payload.

The native SDK's data vector starts at frame offset 5 and therefore includes the subcommand. Its offsets must be reduced by one when translating to Hush's semantic payload. The Java SDK's packet `q()` method already starts at offset 6.

Ordinary replies match reversed endpoints, sequence, and topic. ACK command `ff` echoes the original five-byte header beginning at frame offset 5. NAK command `fe` carries an error code at offset 5, followed by the echoed header. Configuration writes tolerate a missing ACK because firmware may send a state event instead, but they always require a confirming read. An ACK alone never changes the displayed setting.

Each session has its own parser, sequence counter, pending request, and event collector. Writes and their confirming reads form one serialized operation; superseded queued changes to the same setting are discarded. An individual EQ-band edit merges with the most recent confirmed curve inside that operation, and coalescing is per band, so rapid changes to different bands preserve both edits. Disconnect cancels pending operations and delayed reads. Initialization requires an allowed identity and a valid current noise mode. Optional unsupported or invalid reads do not expose writable controls.

## Commands

All payloads below begin at **frame offset 6**. Multibyte numeric fields are big-endian unless explicitly stated otherwise.

| Topic | Read request | Response / write |
| --- | --- | --- |
| `0211` product ID | Empty | Two-byte **little-endian** product ID |
| `0203` firmware, `0201` serial | Empty | One-byte length, followed by UTF-8 text |
| `13be` sound mode | Selector `01` | Off=`01`, HearThrough=`02`, ANC=`04`; write selector then value |
| `13be` sound capabilities | Selector `00` | Mode bitmask, maximum HearThrough raw level, maximum ANC raw level, optional extended fields |
| `13be` HearThrough / ANC strength | Selector `02` / `03` | Raw level; write selector then level |
| `137d` equalizer | `00` for all bands | Described below |
| `137e` equalizer enable | Empty | `00` bypass / `01` enabled |
| `1390` sleep timeout | Empty | Five-minute units: `00` off, `03` 15 min, `06` 30 min, `0c` 60 min, `18` 120 min |
| `133a` announcements | Empty | `00` tones, `01` voice, `02` off |
| `138e` Elite 10 automatic pause | Target `00` | Reply and write: target `00`, then `00` disabled / `01` enabled |
| `1383` Elite 85t automatic pause | Empty | `00` enabled / `01` disabled, following the model-specific Sound+ form |
| `1202` battery | `01` | Described below |
| `0d4c` event subscription | Empty, returning supported 32-bit big-endian mask blocks | Intersect supported bits with desired `00 00 02 96`; write every returned block, zeroing unused extended blocks |

Elite 10 captures show compact mode replies with a single value. The generic Sound+ definition also describes replies echoing the selector; the parser accepts those explicitly bounded variants. Capabilities likewise accept an optional leading selector `00`. Strength raw value zero is strongest, so the UI uses `maximum - raw`. A zero maximum means there is no adjustable strength control. The original Elite 10 is not assigned a fabricated ANC strength range.

The Elite 85t automatic-pause form in its model configuration reverses the polarity described in the generic GNP definition. Hush follows the model form: `00` enables pause/resume, and `01` disables it. Hardware testing on firmware 2.0.0 confirmed pause and resume with `00` when the left earbud was held at least 50 cm away, and continued playback in the right bud with `01` under the same conditions. The model form exposes `NFMI_AUTO_PAUSE_AND_RESUME` (`1383`), with no separate on-head detection setting; Elite 10 instead uses the on-head target of `138e`.

Elite 85t pause depends on the magnetic link between the earbuds. Removing a bud but holding it near the other one may leave that link connected and fail to trigger a pause. Jabra documents an NFMI range of about 20 cm and automatic resume when the earbud is replaced within 60 seconds. For repeatable testing, move the **left** bud well away while listening through the right; when pause is disabled, the right bud should keep playing. See [Jabra's explanation of auto-pause and NFMI](https://www.jabra.com/supportpages/jabra-elite-85t/100-99190003-60/faq/41acf3e3-05b4-4e45-bc84-81d44ffc1993).

After mode writes, firmware can first report the old transitional mode. Hush waits 600 ms and reads the mode again. Button-originated sound events similarly trigger fresh reads; their embedded value cannot overwrite a confirmed setting. Configuration events refresh the optional settings and equalizer.

Event subscription first reads the device's supported masks. Elite 85t firmware 2.0.0 rejected the fixed `00 00 02 96` mask as an illegal parameter. Its actual supported response was `00 00 02 92`, and writing that intersection was acknowledged: the unsupported bit was `04` (on-head status), while the dedicated ANC/HearThrough bit was supported despite its omission from the older model XML. Elite 10 firmware 4.6.0 reported `00 00 02 96` and acknowledged that subscription. Hush uses the device response, requests only the intersection of supported and relevant events, and preserves the complete returned mask length. The model definitions require this read-before-write procedure; the native SDK likewise checks event support before subscribing.

## Equalizer and battery

Equalizer read payload:

```text
count:u8 masterGain:s16
repeat count times: maximumGain:u8 frequency:u16 gain:s16 Q:u16
```

The count is 1–5. Maximum gain is in quarter dB, frequency is in thirds of a hertz, gain/master gain is in sixtieths of a dB, and Q is represented in 1/4096 units. Hush edits only per-band gain, in half-dB UI steps, within the reported symmetric gain limit. It preserves master gain, frequency, and Q exactly.

Equalizer write payload:

```text
count:u8 targetBand:00 masterGain:s16
repeat count times: frequency:u16 newGain:s16 Q:u16
```

There is no invented Sony Clear Bass band or hardware preset slot. Hush's EQ metadata uses the `jabra-gnp` namespace, the actual frequencies and gain bounds, and a separate enable/bypass state. A curve requires matching device layout metadata before application.

On the physically tested Elite 10 firmware 4.6.0, the optional `137e` status read returns `FE FD` (**access denied**), while `137d` returns a valid five-band curve. Sound+'s native `GetEqualizerStatus` also sends the empty `137e` read; a missing request selector is not the issue. Hush keeps the readable curve editable, omits the unavailable bypass control, and does not send enable writes when applying it. A request to restore an explicitly bypassed curve is rejected before any gain write. The initial denial is remembered for that session rather than repeatedly querying the restricted command.

The captured curve reports frequencies 60, 250, 1000, 4000, and 7600 Hz, with per-band limits of 7.5, 6, 6, 6, and 10 dB. Its gains include a non-half-dB value (`-118/60` dB), so restoring a saved hardware state must preserve exact raw gains instead of quantizing them to slider steps.

Elite 85t firmware 2.0.0 supports the separate enable/bypass command. Applying a curve enables EQ in the same serialized operation. Hardware testing confirmed bypass, automatic enable on curve application, and exact restoration of all five gains, including values between slider steps.

Battery payload:

```text
flags percent reserved reserved extraCount
repeat extraCount times: componentId flags percent
```

Main flags: bit 0 charging, bit 1 low battery, bit 7 extra-component records present. Without extra records, the older short form still supplies flags and percentage. Extra records are validated against the declared count. ID `15` identifies the case. IDs `04` and `05` identify earbud components, but their left/right interpretation depends on the connected primary earbud. Sound+ assigns sides using a separate connection-state event; JabraLibre's observed fixed side mapping must not be generalized to both products.

Hush therefore labels the primary and secondary readings **Earbud 1** and **Earbud 2**, or **Earbuds** when there is no secondary reading. It does not infer left/right from Bluetooth address ordering. Invalid percentages remain unknown; zero percent is valid. The compact battery value is the lowest known earbud percentage, excluding the case. The main charging flag belongs only to the primary component; aggregate charging is true only when every reported earbud is known to be charging. Extra-component charging flags are not yet interpreted.

## Evidence, tests, and hardware acceptance

APK references for reproducing the protocol research:

- Java `com/gnnetcom/jabraservice/m.java`: frame encoding, lengths, buffering, offsets, ACK/NAK construction.
- Java `com/gnnetcom/jabraservice/f.java`: SPP transport and legacy channel fallback; `Gb/a.java`: product ID and firmware queries.
- Native `lib/arm64-v8a/libjabralib.so`: `GnpPacket::Deserialize`, `GNBatteryStatus::LoadPayloadInfo`, and equalizer parsing/serialization.
- Bundled `res/raw/device_info.zip`, model directories `11846_01-92_1.2.4` and `9421_01-68_2.0.0`: model forms and `GnpDefinitionComplete.dat` definitions for sound controls, equalizer, automatic pause, sleep, and announcements.
- Java `com/jabra/sdk/api/basic/a.java` and `JabraDeviceEarbudConnectionProxy`: primary/secondary battery side assignment.

Tests under `core/src/test/java/com/occaecat/hush/jabra` use independently constructed fixtures, the published battery sample, and explicitly labeled hardware captures: the Elite 10 EQ payload/status denial and Elite 85t supported event mask. They cover every stream boundary, combined packets, malformed lengths, payloads resembling headers, ACK/error correlation, unknown identities, variable battery counts, conservative charging, EQ parameter preservation, restricted bypass controls, supported event-mask negotiation, model-specific pause encodings, optional features, failed readbacks, rapid requests, cancellation, and physical-button notifications through a fake transport. Constructed fixtures are not claimed as hardware captures.

Physical-device results from 2026-10-29, using the phone debug build on a Pixel 10:

| Check | Elite 10, firmware 4.6.0 | Elite 85t, firmware 2.0.0 |
| --- | --- | --- |
| Transport and identity | Insecure SPP; product ID verified | Insecure SPP; product ID verified |
| ANC / HearThrough / Off | All writes confirmed; audible behavior confirmed by the tester | All writes confirmed; audible behavior confirmed by the tester |
| HearThrough strength | Reported range 0–4; tested 0, 2, 4 | Reported range 0–4; tested 0, 2, 4 |
| ANC strength | No adjustable range reported | Reported range 0–4; tested 0, 2, 4 |
| Automatic pause | Off/on readback and physical pause/resume confirmed | Off/on readback and physical behavior confirmed with NFMI separation |
| Sleep timeout | Off, 15, 60, 120 minutes written/read back; original 30 restored | Off, 15, 60, 120 minutes written/read back; original 30 restored |
| Announcements | Tones, voice, off written/read back | Tones, voice, off written/read back |
| EQ gains | Each of five bands changed by −0.5 dB individually; flat reset and exact restore passed | Each of five bands changed by −0.5 dB individually; flat reset and exact restore passed |
| EQ enable/bypass | Status access denied; curve remains editable and bypass is hidden | Bypass confirmed; applying a curve automatically enabled EQ |
| App update/restart | Automatic connection restored | Automatic connection restored |
| Buds returned to case, then removed | Automatic reconnect passed after a 10-second case cycle | Automatic reconnect passed; original settings retained |
| Android playback and volume | Play/pause and volume lowered one step; original paused state and volume restored | Pause/resume and volume lowered one step then restored |
| Physical button updates in Hush | Supported-mask subscription acknowledged; UI synchronization confirmed | ANC, HearThrough, and Off events followed by confirming reads; UI synchronization confirmed |

All changed settings were restored after each automated run. Original raw EQ gains were `[270, -118, 0, 0, 0]` on Elite 10 and `[351, -118, 0, 118, 240]` on Elite 85t. The Elite 85t wire capture confirmed unchanged frequency, Q, master gain, and reported gain limits across 12 EQ readbacks. While the buds were in the closed case, Elite 10 briefly reported a broken-pipe connection failure and Elite 85t a timeout; both reconnected automatically when removed.

Switching Elite 10 → Elite 85t → Elite 10 preserved the correct device's EQ curve and capabilities, with no settings restored from the previous session. The final verification run passed 62 unit tests, including all seven pre-existing baseline tests, and built the phone, Wear, and TV debug applications successfully.

These checks establish command/readback behavior; a successful sleep-timeout write does not establish that every timer duration was allowed to elapse. Coverage still needs direct comparison with the Sound+ UI, other firmware versions, single-earbud and case-absent battery configurations, battery/charging accuracy across percentages, Sound+ occupying the channel, Bluetooth and permission transitions, extended sleep/reconnect testing, rapid physical-button/UI interactions, and physical Sony/Jabra switching. TV, standalone Wear, phone-linked Wear, widgets, tiles, scenes, and sleep timers also need physical-device acceptance. Jabra playback/volume use Android's active local media route; device power-off is unsupported.

Firmware discovery, experimental installation, and button remapping are now implemented as described below. Proprietary production-test power-off, MySound, spatial audio, and Jabra multipoint connection management remain outside this implementation. Sound+ remains available from the firmware page.

## Optional hardware-test build

A separate application ID lets the phone debug build coexist with an installed Hush release:

```sh
./gradlew :app:assembleDebug -PhushTestApplicationId=com.occaecat.hush.jabratest
```

The resulting app is labeled **Hush Jabra Test**. The normal application ID is unchanged when the property is omitted. Debug builds include a hardware-test content provider at `<applicationId>.hardwaretest`; its calls accept only the ADB shell, root, or the app's own UID. The provider and its manifest entry are absent from release builds. Other apps cannot invoke its controller actions.

After installing the debug APK and connecting through Hush, read the current state with:

```sh
adb shell content call --uri content://com.occaecat.hush.jabratest.hardwaretest --method state
adb logcat -s JabraGnp
```

The provider can also invoke the same mode, strength, EQ, pause, sleep, and announcement actions as the UI. Actions are asynchronous: read `state` again after the operation completes to assess the confirmed result. Debug GNP logs show control-topic frames and redact identity payloads other than the product ID; they omit the Bluetooth device name and address. The provider's explicit `paired` action includes local paired-device names and addresses, so exclude that output from shared test reports.

## MyControls extension

The phone and TV Device screens now include **Button controls** for the original Elite 10 and Elite 85t. Opening this page reads the supported contexts and current assignments. The model Forms define music, incoming calls, and ongoing calls, each with left/right single, double, and triple press assignments. Hold and volume assignments are not exposed by these schemas.

`JabraButtons.kt` implements `13ad` context discovery, `139a` assignment reads/writes, and `139b` context defaults/reset. Each assignment is a four-byte semantic record `[context, side, gesture, action]`, with no count prefix. Hush validates the product identity, requested context, record length, unique slots, side, and gesture. The allowed action menus come from the two model Forms; unknown and reserved action values remain visible and cannot be overwritten or reset.

Before every change, Hush rereads the assignments and relevant reservation state. Elite 10's `13e5` Spotify Tap and `0d4d` assistant-lock reads protect controls reserved by Spotify or Google Assistant. When reservation state cannot be established, assignments remain visible but editing is disabled. Elite 85t's Forms do not advertise Spotify/Bisto, so these extra probes are not a dependency for its button editor. Hush does not change assistant or Spotify settings when assigning a button. Context resets require confirmation, readable compatible defaults, and no reserved controls in that context.

Writes are followed by fresh reads; a rejected write or mismatching readback displays an error instead of publishing the requested assignment. When a valid readback differs from the request, the error carries the observed assignments so the backend can show the device's actual configuration. The pages offer Refresh, retain previously confirmed assignments during operations, and disable editing while disconnected, updating firmware, or awaiting confirmation. Session changes dismiss pending selection/reset dialogs. Button assignments are not included in scenes, watch messages, widgets, or backups.

`JabraButtonsTest.kt` supplies constructed protocol fixtures for both models and covers malformed/duplicate records, unsupported contexts, unknown/reserved actions, action/context mismatches, stale assignments, Spotify/Bisto reservations, failed reservation probes, rejection, readback mismatch, context defaults/reset, identity checks, and cancellation. Three backend tests additionally cover queued button edits, reset ordering, and publishing observed assignments after a mismatching readback. Physical coverage is recorded below; call-context changes, reserved assistant/Spotify configurations, and context resets still need hardware acceptance.

APK evidence: model Forms in `res/raw/device_info.zip` directories `11846_01-92_1.2.4` and `9421_01-68_2.0.0`; their `USER_BUTTONS`, `USER_BUTTONS_DEFAULT`, and `USER_BUTTONS_CONTEXTS` GNP definitions; Java `JabraDeviceMyControlsHandler`, `MyControlsConfigurationModelMapper`, `SpotifyMyControlsBusinessRule`, and `GoogleBistoControlsBusinessRule`. See [the expansion research](JABRA_EXPANSION_RESEARCH.md) for schema details and remaining hardware qualification.

## Experimental firmware transfer

The independent updater under `core/.../jabra/firmware` implements GNP update protocol 7 for the original Elite 10 and Elite 85t. Installation requires an explicit user action; it is not a connection-time task, scene, widget action, or automatic firmware check. The transfer implementation and simulated tests do not establish that installing or recovering an interrupted update has been qualified on physical headphones. No firmware erase, upload, or commit was performed while implementing this feature.

`JabraFirmwareFile.verified` owns a defensive copy of each image and validates the declared CRC before constructing it. `JabraFirmwarePackage.verified` freezes the ordered file list and validates the product, version, language, preload limit, and complete known partition layout: `{0}` for Elite 10 and `{0,1,4,5,6,7,9,254}` for Elite 85t. Changed future layouts are rejected. The updater receives this checked object, not arbitrary paths or mutable download buffers. The checksum is the independent word-order-aware calculation documented in [the research](JABRA_EXPANSION_RESEARCH.md); it is not a cryptographic signature check. An optional signed container's 52-byte excluded CRC header remains part of the transmitted bytes.

`JabraFirmwareUpdater.readReadiness` performs only reads. It verifies the hardware PID, installed version, protocol list (`0214`, one byte per protocol, no count prefix), firmware lock (`0f34`, exactly `00` unlocked or `01` locked), and current voice language (`1308`, little-endian 16-bit). If that language read is rejected, the fallback accepts `0208` only when it reports exactly one available language. It does not guess a language from the phone locale. Battery query `1202 [01]` must report both earbuds and the case, each at least 50%. Missing readings block installation.

The generic schema also defines `0f35`, but neither model profile establishes that capability and Sound+'s protocol-7 updater does not issue it. A read-only Elite 10 4.6.0 check returned protocol `7`, language `0x0409`, an unlocked firmware lock, and component battery levels; `0f35` alone received no reply or NAK while later commands continued working. Hush therefore does not issue this unadvertised query on either current model profile. Required readiness queries still block installation on failure; there is no blanket timeout suppression.

`transfer(package, onProgress)` repeats readiness inside an exclusive transport session and requires a strictly newer target version with the same language. It announces `ERASING` before its first partition-selection write. For every ordered partition it selects `0f2d`, inspects staged version `0f1e`, writes an empty `0f17` erase request, waits for `0f18`, submits CRC/chunk/preload data with `0f19`, and sends `0f1a` event packets containing index LE16, data length, zero, and at most 52 image bytes. The first chunk is sent immediately; each `0f1b` notification authorizes the negotiated preload batch. Its four-byte payload represents the last received chunk's final word, not a numeric credit count.

The updater requires a successful `0f1c` verification after all image bytes, writes the eight-byte staged version (major/minor/patch, language LE16, three zeros), and confirms it by reading again. It reselects and rechecks all required partitions before the empty `0f1d` GO **write**. Only an acknowledged commit returns `AWAITING_CASE`; that means both earbuds must be placed in their case, not that installation has finished. The controller retains the update target for reconnection and installed-version verification. Both model Forms require case completion and specify no explicit reboot command.

`JabraConnection.firmwareSession` holds the command mutex for the complete operation. Update writes require correlated ACKs; ordinary settings' optional-ACK behavior is not used. A dedicated ordered inbox reports overflow as failure instead of dropping events. Erase/status events arriving before command ACKs are buffered. Socket writes are cancellable by closing the socket from the cancellation handler, and command deadlines cover both writing and waiting for responses. An interrupted mutating session closes before releasing its mutex, preventing queued settings from running against a partially staged update.

There is no blind retry, firmware-lock override, guessed rollback, or byte-offset resume. A full package is transferred even when metadata permits partial updates. Malformed, unexpected, rejected, or missing replies stop the operation. Immediately repeated credit frames do not grant an extra batch; normal sequence wrap is accepted. Actual OTA event sequencing, including whether either firmware uses constant event sequence numbers, must still be checked against an official update capture before physical installation qualification. An uncertain final-commit response is not reported as success.

`JabraFirmwareTest` and `GnpFirmwareInboxTest` use constructed fixtures for immutable CRC-checked data, signed-header handling, wrong/incomplete packages, identity/language/lock/protocol/battery gates, all eight Elite 85t partitions, chunk boundaries and flow control, duplicate/wrapped credits, staged readback, strict command failures, missing verification, cancellation, blocked socket-write deadlines, event overflow, and commit uncertainty. The 16-zero-byte nonce CRC (`ffffffff`) is a published firmware checksum fixture; no vendor executable image or SDK source is included.

Additional evidence: Java `Ib/e`, `Ib/b`, and `Ib/k` for metadata, CRC, and update phases; `Qa/C1605m` for protocol-list parsing and `Qa/y` for staged versions; `Ra/C1615f` and `Ra/C1625p` for GO dispatch. Native `GNCurrentLanguage` constructor at `0x2bfeb8` and response callback at `0x2c0274` establish `1308` and little-endian language decoding in the supplied arm64 library. These offsets identify research evidence, not copied implementation code.

## Update lifecycle and extension validation

The phone runs transfers in its connected-device foreground service with a bounded wake lock. The controller journals model, target version, Bluetooth identity, and lifecycle transitions to an atomic file in `noBackupFilesDir` before allowing writes. Downloaded firmware is also excluded from Android cloud backup. A process restart marks an unfinished transfer interrupted; it never resumes offsets or repeats GO. After case completion, verification opens the same Bluetooth address and reads the PID and installed version. Only an exact target-version match reports success. A missed reconnect can be checked manually; waiting for the case is bounded to 30 minutes. A journal failure leaves an interrupted, recoverable in-memory state rather than trapping controls in an active update.

`Phase.UPDATING` gates UI and dispatch, device switching, playback and volume. It invalidates scene/automation restore points and cancels sleep timers. Older watches receive the existing `RECONNECTING` phase plus a firmware reason, while newer watches decode the optional update flag. Firmware initiation is available in the phone's explicit review flow, not the watch, TV, widgets, tiles, or automation.

Validation on 2026-10-29 used the Pixel and isolated Hush Jabra Test application:

| Check | Elite 10, 4.6.0 | Elite 85t, 2.0.0 |
|---|---|---|
| Read supported contexts and all 18 assignments | Passed | Passed |
| Right double press: Next track → Play/Pause | Write/readback passed; user confirmed playback paused | Write/readback passed; user confirmed playback paused |
| Restore original assignment and compare all 18 slots | Exact match | Exact match |
| Firmware protocol, current language, lock | Protocol 7, English `0409`, unlocked | Protocol 7, English `0409`, unlocked |
| Battery gate | Blocked correctly: case 30%, both earbuds 85% | Ready: case 67%, both earbuds 100% |
| Download and production verification on phone | One file, 2,466,080 bytes | Eight files, 1,872,768 bytes |

The phone firmware page correctly reports the installed releases as current. All **118 unit tests** pass, including the original seven Sony frame tests; phone, Wear, and TV debug APKs assemble. The isolated test build is installed on the Pixel. Physical firmware erase/upload/commit, case installation, interrupted-install recovery, TV/Wear acceptance, and button reset/call/reservation behavior remain unqualified; firmware installation stays experimental. No firmware image was flashed during these checks.
