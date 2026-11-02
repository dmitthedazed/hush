# MDR v1 — all devices

Hush supports every Sony headset that speaks MDR protocol v1, not just the WH-1000XM4. It covers the whole v1 command set and picks features at runtime from what the headset reports. Sections 1–4 are the research behind this; section 5 is how it is built and section 6 what is checked.

## 1. Which devices are "v1"

The decompile has no table that maps models to protocols. The protocol is negotiated at connect time:

- `CONNECT_RET_PROTOCOL_INFO` (`01 00 hi lo`) gives a 16-bit version. Sound Connect accepts `0x1000 0x2000 0x3000 0x4000 0x4010 0x5000 0x6000 0x7000 0x7010` (`tv/d.java:77`, `f81747b`). It rejects any other version with `UNAVAILABLE_PROTOCOL_VERSION`.
- **v2** headsets answer the same request with an **8-byte** payload (`01 00 01 00 …`). Gadgetbridge tells the two apart by payload length (`SonyHeadphonesProtocol.java:133`).

v1 models:

- Confirmed by a real protocol-info reply (Gadgetbridge, mos9527): WH-1000XM3 (`0x4010`), WF-SP800N (`0x7000`), WI-SP600N (`0x4010`).
- Confirmed by our own hardware: WH-1000XM4.
- Run on the v1 implementation in Gadgetbridge: WH-1000XM2, WF-1000XM3, WI-C100.
- Most likely also v1, not verified: other models of the same generation (WH-H910N, WH-XB900N, WI-1000XM2, WH-CH710N…).

The v2 models (8-byte reply) are WF-1000XM4+, WH-1000XM5+, LinkBuds and LinkBuds S/Open.

**Conclusion:** there are no model lists in the code. The device is classified by the length of its protocol info. Within v1, version thresholds turn on some sub-features: `>= 0x4000` UPDATE_METHOD, `>= 0x5000` INSTRUCTION_GUIDE and UNIQUE_ID (`tv/d.java:231-265`). A model only matters for its picture and name.

## 2. How Sound Connect initializes v1 (the reference sequence)

`tv/d.java:880-990` (`call()`). Every step after the handshake depends on `FunctionType` from `CONNECT_RET_SUPPORT_FUNCTION`:

1. `00 00` protocol info → check the version
2. `02 00` capability info (capability counter + display language). The result is cached, and when the counter has not changed the capability requests are skipped.
3. `04 01/02/03` model name / FW / series+colour, plus `04 04` instruction guide when the version is `>= 0x5000`
4. `06 00` support function → a `Set<FunctionType>`
5. Per function:

| FunctionType (hex) | Request in init |
|---|---|
| `41` VPT / `42` SOUND_POSITION | `40 01` / `40 02` (VPT caps) |
| `51` PRESET_EQ / `53` …NONCUSTOMIZABLE / `52` EBB | `50 01` / `50 03` / `50 02` |
| `61` NC / `62` NC+ASM / `63` ASM | `60 01` / `60 02` / `60 03` |
| `81` NC_OPTIMIZER | `80 01` (+ NC_MUSIC `02`) |
| `A1` PLAYBACK_CONTROLLER | `A0 01` |
| `E1` CONNECTION_MODE / `E2` UPSCALING | `E0 01` / `E0 02` |
| `F1` VIBRATOR / `F2` POWER_SAVING / `F4` AUTO_POWER_OFF / `F5` SMART_TALKING / `F6` ASSIGNABLE | `F0 01/02/04/05/06` |
| `F3` CONTROL_BY_WEARING | `F0 03` (handled in KMP, `a.java`: types {3}) |
| `B1` TRAINING_MODE | `B0 01` |
| `D1..D3` GENERAL_SETTING1..3 | `D0 id lang` |
| `14` BLE_SETUP | `1C 01`, `1C 00` (BLE hash, BT address) |
| `38` PAIRING_DEVICE_MANAGEMENT | table 2 `30 01` |
| `39` VOICE_GUIDANCE | table 2 `40 01` + params |
| `30` FW_UPDATE | `36 07/08/09/0A/…` by version |

Then the app reads current values with GET_STATUS / GET_PARAM for each capability.

## 3. The full v1 command set

### Table 1 (`DATA_MDR 0x0C`, `v1/table1/Command.java`)

`Gap` is what Hush lacked before this work (the old `core/.../mdr/Commands.kt`); every group is now covered by `core/.../mdr/v1/`, except the deliberate gaps in section 6.

| Group | Opcodes | In Hush | Gap |
|---|---|---|---|
| CONNECT | 00–07 | protocol, device info, support function | `02/03` capability info |
| COMMON battery | 10/11/13, inq 00/01/02 | 00 only | **01 L/R, 02 cradle** (TWS) |
| COMMON upscaling effect | 14/15/17 | ✓ | |
| COMMON codec | 18/19/1B | ✓ | |
| COMMON BT device info | 1C/1D | — | BLE hash/address |
| COMMON power off | 22 | ✓ | |
| COMMON connection status | 24/25/27 | — | **L/R earbud connection** (TWS) |
| COMMON concierge data | 28/29 | — | |
| COMMON link control (keep-alive) | 2E/2F | — | `TANDEM_KEEP_ALIVE` (23) |
| UPDT | 34/35/36/37 | 36 only (params) | the FW-update state machine (34/35); out of scope |
| VPT / Sound position | 40–49 | — | **surround (VPT) and sound position** (XM2/XM3, WI-SP600N…) |
| EQEBB | 50–5B | preset EQ only | **status 52/53/55, EBB, non-customizable EQ, ext info 5A/5B** |
| NCASM | 60–69 | inq 02 only | **caps 60, status 62/63/65, NC-only (01), ASM-only (03)**, dual/single NC, wind reduction |
| SENSE (Adaptive Sound Control) | 70/71/74 | — | **AUTO_NC_ASM**, built into the headset |
| OPT (NC optimizer) | 80–89 | status/param, start | caps 80, NC_MUSIC_OPTIMIZER |
| ALERT | 90/91/94/98/99 | set status, answer | caps 90, vibrator alert (02) |
| PLAY | A0–A9 | ✓ | |
| SPORTS (training mode) | B0–BD | — | **TRAINING_MODE** (sports models) |
| LOG | C4/C9 | — | action log (C1); can be ignored |
| GENERAL_SETTING | D0–D9 | boolean only | **list type (GsSettingType.LIST)**, e.g. the XM3's NC/AMB key |
| AUDIO | E0–E9 | params | caps E0, status E2/E3/E5 |
| SYSTEM | F0–FD | 03/04/05/06 | **01 vibrator, 02 power saving**, statuses, ASSIGNABLE caps (key/preset tables), EXT for 03/04/06 |
| TEST | FF | — | not needed |

### Table 2 (`DATA_MDR_NO2 0x0E`, `v1/table2/Command.java`)

| Group | Opcodes | In Hush | Gap |
|---|---|---|---|
| PERIPHERAL | 30–3D | status, param, set status, ex param | caps `30` |
| VOICE_GUIDANCE | 40–49 | param 46/48 | caps 40, status 42–45, volume/language as params |

## 4. Sources for structs (by priority)

1. **Decompile**: payload classes are named in `Command.java` (`tm0/*` for table 1, `an0/*` and `bn0/*` for table 2), structs are in `vm0/*`, and enums are in `v1/table1/param/*`. This is the ground truth.
2. **mos9527/SonyHeadphonesClient** (MIT, branch `v1-compat`, last commit 2026-10-20), `libmdr/include/mdr/ProtocolV1T1.hpp` (317 structs) and `ProtocolV1T2.hpp` (48). It describes every v1 payload declaratively, byte by byte, with Sony's names (`THMSGV1T1*`). This is the best map to read alongside the decompile. `tests/WH-1000XM3-4.5.2/` holds a real init capture from an XM3 (`0x4010`), which can be used as a replay test.
   - Device notes: `docs/device-support/WH-1000XM3.md`:
     - the RET reply arrives **before** the ACK of the GET;
     - auto power off is written through `SYSTEM_SET_EXTENDED_PARAM` and gets no notify;
     - GS1 on the XM3 is a LIST type (`ASSIGNABLE_KEY_SETTING`).
3. **Gadgetbridge** (codeberg, AGPL — read it, do not copy code), `service/devices/sony/headphones/protocol/impl/v1/SonyProtocolImplV1.java`. Its capabilities are **static per model** (coordinators), which is an anti-pattern for us. It is still useful for quirks: wind noise reduction in the NC/ASM struct, and the "audio settings only on SBC" flag for the XM2.

## 5. How it is built

```
core/mdr/
  Frame.kt, MdrConnection.kt   transport; MdrConnection takes a parser (Table, ByteArray) -> MdrEvent, MdrV1::parse by default
  Request.kt                   Request(table, payload), Table, Reader (u8/s8/u16/prefixed string/list)
  Model.kt                     protocol-independent state types (NcAsmState, NcAsmCapability, Charge, AssignableKey…)
  Events.kt                    MdrEvent: everything a headset can say, decoded
  v1/Commands.kt               Command1 (all 134 table-1 opcodes) and Command2 (table 2), from the decompile
  v1/FunctionType.kt           every FunctionType
  v1/Requests.kt               request builders, one object per command group (Connect, Common, Vpt, EqEbb, NcAsm,
                               Sense, Optimizer, Alert, Play, Sports, GeneralSettings, Audio, SystemSettings,
                               Peripheral, VoiceGuidance, Update)
  v1/Parser.kt                 MdrV1.parse: RET/NTFY payloads of both tables → MdrEvent
  v1/Init.kt                   handshake + plan(version, functions): Sound Connect's tv/d.java sequence
```

- `HeadphonesController.initialize()` does the following:
  1. Sends the handshake.
  2. Waits for PROTOCOL_INFO and SUPPORT_FUNCTION, 3 s each.
  3. Stops on a v2 reply: the session is not retried, and the UI says "Not supported yet".
  4. Otherwise sends `Init.plan`. A headset that never answers SUPPORT_FUNCTION is treated as an XM4 (`XM4_FUNCTIONS`).
- What the UI shows follows the state, so a model only gets the rows it reported:
  - **noise modes:** `HeadphonesState.noiseModes()` gives NC/Off, Ambient/Off or all three. `SonyNoise` takes its level range and Focus on voice from `NcAsmCapability`.
  - **Wind noise reduction:** offered for DUAL_SINGLE_OFF NC with a level slider.
  - **batteries:** L/R/case on true wireless models.
  - **one row per assignable key**, with the presets the headset lists for it.
  - **list general settings:** e.g. the XM3's NC/AMBIENT button.
  - **Sound effects:** VPT, Sound position, Bass boost (EBB).
  - **other toggles:** vibration, power saving, training mode.
  - **EQ:** band labels come from EQEBB extended info. On PRESET_EQ_NONCUSTOMIZABLE the bands are read-only (`EqUi.editable`). The upscaling name and logo follow UpscalingType.
- Models: `SonyModule.V1_MODELS` lists the add-device entries.
- Pictures (`sony/SonyArt.kt`) come from Sound Connect's cloud model list (`CloudModelInfoController`):
  - Request: `POST https://v1.api.data-gateway.seeds.services/graphql` with headers `x-api-key` (returned by `libcloudmodelinfo.so`, readable with `strings`) and `Content-Type: application/json`. Body: `{"query":"query { HPC { getAllCloudModelInfos { model_name model_color_id sca_image_image_url left_image_url right_image_url cradle_image_url } } }"}`. `overview_image_url` and `wp_*_image_url` also exist.
  - Sound Connect looks up a model by `model_name` + `model_color_id` (= ModelColor, `HeadphonesState.modelColor`). Old models have an empty `model_id`/`model_number`.
  - The images are public PNGs at `https://hpc-image.data-gateway.seeds.services/<uuid>.png`. The main one is `sca_image_image_url`, 720² RGBA. Earbuds and case are 512² PNGs and exist only for true wireless models (WF-1000XM3, WF-SP800N).
  - `SonyArt` keeps only the uuids, per model and colour. It does not ship the key: re-run the query to refresh them. If the headset reports an unknown colour, the lowest colour id is used. WH-CH710N is not in the list: its three colours use the product shots from sony.sk, fetched at runtime like the rest (`RemoteRender` sends a browser User-Agent, the shop answers 403 otherwise).
  - `RemoteRender` downloads each file once (`render-sony-<model>-<color>[-left|-right|-case].png`, trimmed). `rememberRender` and `renderBitmap` use it, with the bundled XM4 renders kept. The add-device page gets `Model.imageUrl`. The earbud and case pictures replace the L/R/C letters in `BatteryStatus` (`DeviceController.partImage`).

## 6. What is checked

- Unit tests: `core/src/test/.../mdr/v1/MdrV1Test.kt` covers the struct layouts. `MdrV1CaptureTest` replays a real WH-1000XM3 capture (`core/src/test/resources/mdr/WH-1000XM3-4.5.2`, from mos9527, MIT), and every frame in it decodes.
- WH-1000XM4 (FW 3.0.1), 2026-10-21:
  - the new initialization connects and reads everything as before;
  - the headset declares function 0x38, so its source list shows up;
  - Wind noise reduction was switched on and off, and both values were read back after an app restart.
- Demo (debug build): `adb shell am start -n com.occaecat.hush/.MainActivity --es demo WH-1000XM3` (also `WF-1000XM3`, `WF-SP800N`, `WI-C100`; `--ez demo true` is the XM4). The states are in `core/.../ui/Demo.kt`: the XM3 matches its real capture, and the others are typical capability sets for their kind of model. All four were checked on the phone on 2026-10-21, on Listen, Sound and Device.
- Not tested on hardware: other models (none on hand), and the writes for VPT, sound position, EBB, vibrator, power saving, training mode, assignable keys on earbuds and list general settings.
- Gaps on purpose:
  - firmware update (UPDT 34/35);
  - the action log (LOG);
  - TEST_COMMAND;
  - the concierge data blob;
  - the "eco timer" keep-alive. It has a builder, `Common.keepAlive`, but nothing sends it.

## 7. Lessons from other clients (checked 2026-10)

- WH-1000XM3 4.5.2 sends `*_RET_*` before the ACK of its `*_GET_*` (mos9527 PR #66, docs/device-support/WH-1000XM3.md).
  Hush matches replies as events, not per request, so this is fine.
- The transmit sequence must change only on our ACKs, never follow inbound frames (mos9527 PR #64); Hush does.
- A V1 EQ write carries a preset or a curve, never both: the XM4 ACKs and drops a combined frame (mos9527 PR #64).
- `0x22` is POWER_OFF on v1 (BATTERY_LEVEL_REQUEST on v2): a v2 client powered XM4s off (SonyBridge #1).
  A v1 headset also powers off when no valid v1 traffic arrives soon after connect.
- XM3 never answers INSTRUCTION_GUIDE, COMMON_GET_UPSCALING_EFFECT and some UPDT_GET_PARAM; init must not wait on them.
- An EQ preset change on the XM3 can raise ALERT DISCONNECT_CAUSED_BY_CONNECTION_MODE_CHANGE; changing multipoint raises
  DISCONNECT_CAUSED_BY_CHANGING_MULTIPOINT and holds the change until answered.
- mos9527 writes the XM3's auto power off with SYSTEM_SET_EXTENDED_PARAM; Sound Connect uses SYSTEM_SET_PARAM
  (q00/b.java), as Hush does.
- A frame cut off by a link glitch must not swallow the next one: a bare 0x3E restarts the parser (SonyBridge PR #6).
- WH-1000XM2 answers the handshake with `01 00 40 00` (protocol 0x4000), also before its ACK (mos9527 #61; test
  `xm2RepliesBeforeAck`).
- WF-1000XM4 (`01 00 01 00 00 00 00 00`), WH-1000XM5 and LinkBuds S answer with the 8-byte v2 form (Gadgetbridge
  SonyHeadphonesProtocol.java).
- An earbud at level 0 is docked or out of reach, not flat: Gadgetbridge and SonyBridge skip it, and so does Hush.
