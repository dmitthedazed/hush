# Jabra Elite 10 and Elite 85t

Hush's Jabra implementation is independent and experimental. It targets the **original** Elite 10 (GNP product ID 11846) and Elite 85t (9421). Elite 10 Gen 2 and other products fail identity verification before controls become available. The GNP frame and semantic parsers live in pure Kotlin under `protocol/gnp`; `devices/jabra` owns Bluetooth RFCOMM, the session, settings, firmware version lookup, and the `DeviceModule` contract. `app` registers the brand. No Jabra SDK, decompiled source, firmware image, or encryption material is included.

The model picker and hero fetch Jabra's own transparent product renders from `assets2.jabra.com` at runtime and cache them on the phone; no vendor artwork is committed. The URLs were resolved from Jabra's official [Elite 10](https://www.jabra.com/supportpages/jabra-elite-10) and [Elite 85t](https://www.jabra.com/supportpages/jabra-elite-85t) support pages.

Protocol research references the user-supplied Sound+ 5.21.0 APK (`jabra-soundplus-5-21-0-0-11448-0988ad39a.apk`) and [JabraLibre's public Elite 10 research](https://github.com/pLum0/JabraLibre). JabraLibre's GPL code is not copied. Within Sound+, `com/gnnetcom/jabraservice/m.java` explains frame offsets and ACK/NAK, `f.java` the SPP path, `Gb/a.java` identity queries, `libjabralib.so` the battery and equalizer structures, and the model definitions in `res/raw/device_info.zip` distinguish the two pause commands. These are references for independently written code; the vendor material remains outside the repository.

## Transport and frames

Connect to the paired device using the standard Serial Port Profile UUID `00001101-0000-1000-8000-00805f9b34fb`, trying insecure and secure RFCOMM. Bounded channel fallbacks include Elite 10 channel 29 and legacy channel 3. Each attempt has a five-second deadline. The product ID response, rather than a Bluetooth name or channel, authorizes the session. Aliases of saved devices remain usable.

| Byte | Meaning |
| --- | --- |
| 0 | Destination endpoint (4 headphones, 9 app) |
| 1 | Source endpoint |
| 2 | Sequence |
| 3 | Upper two bits: event 0, read 1, write 2, response 3; lower six bits: **total frame length** |
| 4–5 | Command and subcommand, together called the topic |
| 6 onward | Semantic payload |

Frames are 6–63 bytes. The parser retains fragments, emits combined frames separately, and resynchronizes past malformed lengths. For example `04 09 07 88 13 be 01 04` requests ANC: `88` is write type plus an eight-byte total length. Replies correlate reversed endpoints and sequence; ACK `ff` echoes the original five-byte header, and NAK `fe` carries an error code before the echoed header. Some writes have no ACK, so every setting change requires a confirming read. Session closure cancels pending commands and closes the socket, including during a blocked connect or write.

## Commands

Payloads begin at frame offset 6. Multibyte numeric fields are big-endian unless noted.

| Topic | Read | Response / write |
| --- | --- | --- |
| `0211` identity | Empty | Two-byte **little-endian** product ID |
| `0203`, `0201` | Empty | Length-prefixed UTF-8 firmware and serial |
| `13be` mode | Selector `01` | Off `01`, HearThrough `02`, ANC `04`; write selector and value |
| `13be` capabilities | Selector `00` | Mode bitmask, maximum HearThrough and ANC raw levels |
| `13be` strengths | Selector `02` / `03` | HearThrough / ANC raw level; zero is strongest, so UI strength is `maximum − raw` |
| `137d` EQ | Selector `00` | Band count, master gain, then band parameters below |
| `137e` EQ enable | Empty | `00` bypass, `01` on; access denied on the tested Elite 10 firmware |
| `1390` sleep | Empty | Five-minute units: off `00`, 15 `03`, 30 `06`, 60 `0c`, 120 `18` |
| `133a` announcements | Empty | `00` tones, `01` voice, `02` off |
| `138e` Elite 10 auto-pause | Target `00` | Target then `00` off / `01` on |
| `1383` Elite 85t auto-pause | Empty | **Inverted:** `00` on / `01` off |
| `1202` battery | `01` | Variable component records below |
| `0d4c` events | Empty | Supported bitmask blocks; subscribe to intersection with `00 00 02 96` |
| `13ad` buttons | Empty | Supported music/call contexts |
| `139a`, `139b` buttons | Context ID | Current/default four-byte records: context, side, gesture, action |

The Elite 85t's supported event mask observed on firmware 2.0.0 was `00 00 02 92`; writing `00 00 02 96` was rejected. Elite 10 firmware 4.6.0 accepted `00 00 02 96`. The tested Elite 10 reports a HearThrough range of 0–4 and an ANC range of zero, so Hush shows a HearThrough strength slider and treats its ANC strength as fixed. Button-originated events trigger a delayed state read, so an old transitional event never replaces a confirmed setting. Spotify Tap and assistant-reserved button records stay visible but cannot be edited.

EQ read layout: `count:u8 masterGain:s16`, then for each band `maximumQuarterDb:u8 frequencyThirdHz:u16 gainSixtiethDb:s16 Q4096:u16`. A write inserts target band `00` after count and preserves master gain, frequency, and Q. The tested Elite 10 reported 60, 250, 1000, 4000, and 7600 Hz with a nonrounded `−118/60 dB` gain; Hush preserves that value on readback/restoration. Its 4.6.0 firmware rejects `137e` but accepts band edits. The tested Elite 85t supports bypass. There are no invented Sony preset slots.

The shared sliders display whole dB values within each band's reported limits. The wire's finer gain is retained when changing a different band. Saved curves carry a layout identity, and imports outside any individual band's limits are rejected. Old records with an incompatible layout remain in storage rather than being interpreted as this device's EQ.

Battery begins with flags and primary percent. If flag bit 7 is set, byte 4 gives a count of three-byte records (`component ID`, flags, percent). ID `15` is the case; IDs `04` and `05` add an earbud reading. The connection-side event needed to assign left/right has not been established for both models, so Hush uses neutral **Earbud 1/2** labels in details. The compact battery is the lowest known earbud level, excluding the case. Unknown percentages are omitted. The primary charging flag does not establish the other earbud's charging state.

Older single-device `sound.json` data is assigned using its recorded protocol namespace (or the saved selected brand), then retained until a device from that module is registered. This keeps Jabra scenes and automation from being adopted by Sony after the modular upgrade. EQ records with old raw layouts are preserved, but cannot be applied unless their layout matches the current device.

The Elite 10's `0d4c` event ID `02` reports one on-head state at a time: `00` left worn, `01` left removed, `02` right worn, `03` right removed. Hush shows each side only after such an event. The Elite 85t's auto-pause uses the link between earbuds: physical testing required moving the left bud more than 50 cm from the right. `00` then paused/resumed playback; `01` kept playing. The Elite 85t does not advertise the Elite 10 on-head event bit, so no on-head reading is inferred from its auto-pause setting.

## Firmware and tests

The firmware capability reads the installed `0203` version and compares it with Jabra's public `firmwareinfo_%04X.xml` manifest after checking the product name and version format. Hush links to Jabra's support instructions when a newer version appears. This module contains **no** firmware transfer session, flashing command, installer, or downloaded binary. Sound+ remains responsible for installation.

`protocol/gnp/src/test` includes hand-constructed stream boundary, malformed frame, ACK/NAK, battery, button, and model-specific encoding cases, plus explicitly identified sanitized Elite 10 EQ and Elite 85t event-mask captures. Constructed records are not represented as hardware captures. The hardware tests from 2026-10-29 verified noise modes and strengths, EQ gains and exact restoration, announcements, sleep readbacks, both auto-pause mappings, button remapping, button-originated mode refresh, case/reconnect, and model switching on Elite 10 firmware 4.6.0 and Elite 85t firmware 2.0.0. On 2026-11-04 the modular phone build was checked with the Elite 10: its five EQ bands and HearThrough slider appeared after the EQ status denial was handled by the shared protocol error type. The Elite 85t modular UI, widgets, watch, TV, and single-earbud battery behavior still need a new physical pass. Support remains experimental until those checks pass.
