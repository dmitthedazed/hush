# Jabra firmware, button remapping, and multipoint research

Research date: 2026-10-29. Scope: original Elite 10 (11846 / `2E46`) and Elite 85t (9421 / `24CD`). This is an implementation assessment, not implemented feature support. No commands were sent to either headset during this research.

Evidence comes from the supplied Sound+ 5.21.0 APK, its model-specific configuration, current Jabra documentation, and downloads from Jabra's public firmware servers. Decompiled files and downloaded vendor binaries remain outside the repository. Protocol descriptions and the checksum verification were produced independently; vendor SDK code is not proposed for inclusion in Hush.

## Findings

| Feature | Elite 10 | Elite 85t | Implementation confidence |
| --- | --- | --- | --- |
| Firmware discovery/download | Public manifest and English image verified | Public manifest and all eight English partitions verified | Ready to implement with identity and package validation |
| Firmware installation | GNP OTA path and case completion identified | GNP OTA path and case completion identified | Requires a dedicated transfer implementation and physical update/recovery validation |
| Button remapping | 18 model-defined slots | 18 model-defined slots | Strong APK/model evidence; reads and reversible writes still need hardware verification |
| Bluetooth multipoint | 10 remembered devices; 2 connected; 1 audio source | 8 remembered devices; 2 connected; 1 audio source | Confirmed by Jabra |
| Headset-side list/connect/disconnect/forget UI | Generic commands found, product support unproven | Generic definitions weaker/protected; product support unproven | Do not expose a full connection manager without successful capability/read probes |

Multipoint limits are documented in Jabra's [Elite 10 FAQ](https://www.jabra.com/en-ie/supportpages/jabra-elite-10/100-99280703-98/faq/2c89b2c0-6a52-4347-94bd-9266a647b9e0) and [Elite 85t FAQ](https://www.jabra.com/en-sg/supportpages/jabra-elite-85t/100-99190700-98/faq/f72585d5-e434-4499-a2c0-01cb083739aa). Multipoint support does not establish that an app can manage the headset's other Bluetooth connections.

## Firmware discovery and package selection

The Bluetooth updater in this APK constructs `https://www.jabra.com/firmwareinfo_%04X.xml`, using the hexadecimal product ID. These endpoints and their referenced package metadata were fetched successfully during research:

| Model | Manifest | Current published version | Package metadata |
| --- | --- | --- | --- |
| Elite 10 | [firmwareinfo_2E46.xml](https://www.jabra.com/firmwareinfo_2E46.xml) | 4.6.0, 2026-01-23 | [info.xml](https://www.jabra.com/Firmware_Data/Jabra_Elite_10-v4.6.0/info.xml) |
| Elite 85t | [firmwareinfo_24CD.xml](https://www.jabra.com/firmwareinfo_24CD.xml) | 2.0.0, 2021-03-30 | [info.xml](https://www.jabra.com/Firmware_Data/Jabra_Elite_85t-v2.0.0-vector/info.xml) |

Both match the versions observed during Hush's earlier hardware tests. The Elite 10 version/date also match its [official release notes](https://www.jabra.com/support/release-notes/release-note-jabra-elite-10).

The outer XML provides product name, version, public release date, download URL, release-notes URL, minimum updater version, and language IDs. For the ZIP packages above, Sound+ can fetch `info.xml` and selected component files from the URL formed by removing `.zip` and appending `/`. Downloading every language in the ZIP is unnecessary.

The inner `buildVector` describes target PIDs, component filenames, partition numbers, language IDs, versions, CRCs, preload limits, and whether partial upload is allowed. Both inspected packages specify `partialUploadAllowed=FALSE`, `maxPreloadCount=10`, and `maxBlePreloadCount=6`. These are package parameters, not universal protocol constants.

Elite 10's internal product name is **Jabra Stella**. Its inner build date is 2025-10-21, while the outer publication date is 2026-01-23. Elite 85t's corresponding dates are 2021-03-22 and 2021-03-30. Match packages by verified PID and applicability fields; do not reject them merely because internal names or build dates differ from the public manifest.

A Hush implementation should preserve the device's language, select only the matching files, validate every target and download before opening a transfer, and distinguish network failure from "up to date." The minimum updater version is a vendor compatibility requirement; comparing it directly with Hush's unrelated app version would be incorrect. The APK also contains native `firmware.jabra.com/v4` and newer `firmware-api.jabra.com` clients; their presence alone does not make them the correct source for these two models.

### Download verification performed

All English (`0x0409`) payloads below were downloaded from the package directories and their CRCs matched the published values:

| Model | Partition | Bytes | Published and calculated CRC |
| --- | ---: | ---: | --- |
| Elite 10 | 0 | 2,466,080 | `469b9359` |
| Elite 85t | 0 | 16 | `ffffffff` |
| Elite 85t | 1 | 496 | `01143931` |
| Elite 85t | 4 | 4,000 | `a800b956` |
| Elite 85t | 5 | 572,112 | `27402db6` |
| Elite 85t | 6 | 17,264 | `754e713e` |
| Elite 85t | 7 | 759,472 | `4b832198` |
| Elite 85t | 9 | 519,152 | `af0800c7` |
| Elite 85t | 254 | 256 | `2cf50d6d` |

Elite 10 uses `Jabra_Stella_V4.6.0_EN.bin`. Elite 85t uses eight `.gnv` files, totaling 1,872,768 bytes, including nonce, image header, application/configuration/filesystem partitions, and footer. Preserve the package-defined ordering and upload bytes unchanged.

This CRC is not ZIP/zlib CRC32. It uses a table generated from polynomial `0xDB710641`, an initial accumulator of zero, reversed 16-bit word order while retaining byte order within each word, and four prefixed `ff` bytes processed after the file data in that traversal. Each byte updates the accumulator as `((crc << 8) XOR table[(crc >> 24) & 255] XOR byte) & 0xffffffff`. The inspected payloads all have even lengths; reject unsupported input forms rather than guessing padding.

The 85t nonce file consists of 16 zero bytes: `ffffffff` is its valid calculated CRC, not a "skip validation" marker. Sound+'s parser additionally supports signed-file forms that exclude a 52-byte header from the CRC while transferring the entire file; none of these nine selected entries uses that form. CRC validation detects corruption and is not cryptographic authentication. Image-signature enforcement and recovery guarantees have not been established for either model by this research.

## Firmware transfer and installation

The APK's Bluetooth path is `com.jabra.sdk.impl.E0` -> the Bluetooth service -> `Ib/e` (manifest/package manager) and `Ib/k` (OTA state machine). It uses GNP OTA topics, not ordinary setting writes:

| Topic | Role identified in Sound+ |
| --- | --- |
| `0214` | Empty read returns supported update protocols; this updater requires protocol 7 |
| `0f2d` | Select the component's partition |
| `0f1e` | Read/write staged partition version information |
| `0f17` | Start the transfer/erase stage |
| `0f18` | Erase-completion notification |
| `0f19` | Submit CRC, chunk count, and preload parameters |
| `0f1a` | Transfer image chunks as GNP **event** packets |
| `0f1b` | Transfer flow-control/preload notifications |
| `0f1c` | Verify-result notification |
| `0f1d` | Empty WRITE finalization/GO request after staged-partition checks |

The chunk payload contains a two-byte little-endian index, one-byte data length, a reserved zero byte, and up to 52 bytes of image data. A full chunk fits within GNP's six-bit frame-length limit. The SDK sends an initial chunk, then further batches authorized by `0f1b` events. These are not ordinary setting writes, and chunk transmission must preserve those flow-control rules.

The ten-byte `0f19` payload orders the CRC bytes as bits 16..23, 24..31, 0..7, 8..15, followed by chunk count LE16, preload count LE16, and two zero bytes. A staged-version write to `0f1e` contains major/minor/patch, language LE16, and three zero bytes. Verification event `0f1c` uses zero for success and nonzero for failure. Confirm the entire state machine on hardware before using it to erase or commit an image.

The intended operation is:

```mermaid
flowchart LR
    A[Verify identity and package] --> B[Check update protocol and readiness]
    B --> C[Select partition and inspect staged version]
    C --> D[Erase, transfer, verify, record version]
    D -->|More partitions| C
    D -->|All transferred| E[Verify all required partitions]
    E --> F[Request finalization]
    F --> G[User places earbuds in case]
    G --> H[Reconnect and verify installed version]
```

Both model configurations require case placement to complete the update and specify `NeedsExplicitRebootAfterOta=false`. Do not substitute a guessed power-off/reboot command. Jabra documents case-driven completion and recovery steps in its [Elite 85t update FAQ](https://www.jabra.com/supportpages/jabra-elite-85t/100-99190000-02/faq/c5df028d-5b70-44b6-a24e-5e1fbbd69a16) and [Elite 10 manual](https://www.jabra.com/_/media/Jabra_VXi_Product-Documentation/Jabra-Elite-10/User-Manuals/RevA/EN_Jabra-Elite-10_UM_RevB.pdf).

Hush currently has no headset firmware flasher. Its Sony firmware feature checks availability and opens vendor information. The Jabra setting transport also tolerates missing write acknowledgements after 350 ms and uses best-effort buffered events. Neither behavior is suitable for firmware transfer credits, erase completion, or verification.

Readiness checks include the firmware-update lock (`0f34`), complete and applicable files, and sufficient battery. Sound+'s production helper uses a 50% threshold, but its choice to include the case depends on a caller flag whose model-specific policy was not traced. Elite 10's generic definition additionally describes `0f35` preconditions for low battery, busy link, and an existing update connection; availability on the physical firmware remains untested. Do not fabricate those capabilities on Elite 85t or bypass an update lock.

Add a dedicated updater with command-specific responses/timeouts, lossless OTA event delivery, raw event transmission, exclusive session ownership, and persistent progress. Suspend setting commands, automatic event refreshes, scenes, automation, and sleep actions during transfer. Treat case installation and expected link loss as explicit update states rather than the current short A2DP reconnect path. Report installation success only after reconnecting to the same device and verifying the installed version.

Still required before enabling flashing: verify protocol negotiation on each physical model; capture an official update's event ordering and finalization behavior; validate battery/earbud-presence/firmware-lock checks; test cancellation before commit, disconnects, and interrupted-update recovery. No arbitrary byte-offset resume or rollback guarantee was found. The package's `partialUploadAllowed=FALSE` must be respected. A complete updater is feasible from the evidence, but download/CRC success alone does not qualify it for hardware use.

## Button remapping

Both model-specific Forms define MyControls, corroborated by Jabra's [Elite 10 description](https://www.jabra.com/en-sg/supportpages/jabra-elite-10/100-99280702-98/faq/35e46920-9622-45cc-ac15-01024a65bd74) and [Elite 85t manual](https://www.jabra.com/_/media/Jabra_VXi_Product-Documentation/Jabra-Elite-85t/User-Manuals/RevA/Jabra-Elite-85t-User-Manual.pdf).

The model schemas expose 18 slots: three contexts (music, incoming call, ongoing call), two buttons (left/right), and three gestures (single/double/triple press). Sound+'s mapper explicitly excludes HOLD from the editable assignment map.

All payloads here are semantic payloads beginning after GNP's six-byte header:

| Topic | Read | Write / response |
| --- | --- | --- |
| `13ad` | Empty request | Supported context-byte list |
| `139a` | `[context]`, or `[context, button, gesture]` | Repeated four-byte `[context, button, gesture, action]` records; no count prefix |
| `139b` | `[context]` for defaults; reply uses the same four-byte records as `139a` | Write `[context]` to reset that context; empty write resets all contexts |

Context IDs are `00` music, `01` incoming, `02` ongoing. Button IDs are `00` left and `01` right. Gesture IDs are `00` single, `01` double, `02` triple.

| Context | Actions offered by both inspected model Forms |
| --- | --- |
| Music | `00` none, `01` play/pause, `02` next, `03` previous, `04` voice assistant, `05` sound modes |
| Incoming call | `00` none, `06` reject, `07` accept/end current, `08` accept/hold current, `0b` mute, `0c` sidetone |
| Ongoing call | `00` none, `09` end/resume held, `0a` hold/resume held, `0b` mute, `0c` sidetone |

Newer generic definitions also contain locked-assistant and Spotify values. Their presence is not permission to offer them as ordinary writable choices. Sound+ has additional Spotify and Google voice-assistant business rules that reserve particular slots. Query or preserve those modes and assignments; do not silently change assistants or Spotify settings while remapping a button. Relevant generic read schemas include Spotify `13e5` and voice-assistant `0d4d`, but those need model-capability checks and hardware verification as well.

Implement a typed `context/side/gesture -> assignment` model with per-slot allowed actions and lock reasons. Read supported contexts and current assignments, preserve unknown values, write one supported slot, and publish only after readback. Read/reset defaults per context. Reuse Hush's settings rows and dialogs while keeping Sony's CUSTOM-button preset integers separate. Any later backup support should retain a Jabra schema/model identity to prevent incompatible restoration.

## Multipoint connection management

Both products support two simultaneous Bluetooth connections. The unresolved feature is controlling their remembered/connected source list from Hush.

Sound+'s `DeviceToggle.PairingList` enables its paired-device management screen only for `SAMWISE_UC` and `SAMWISE_MS` (product IDs 9455/9457), not Elite 10 or Elite 85t. Both earbuds' model Forms omit the pairing-list support flag. The generic `isConnectionModeSupported` setting concerns shared/personal speakerphone use; it is not evidence of an earbud multipoint enable switch.

The newer full GNP definition bundled with Elite 10 contains these candidate commands. This is a research map, not a verified earbud API:

| Topic | Generic function | Relevant caution |
| --- | --- | --- |
| `0d28` | Enumerate database records using a 16-bit big-endian index and returned next index | Definition explicitly permits access denial in public mode |
| `0d32` | Read source names by database index | Availability on either earbud unverified |
| `0d26` | Read connection status by transport/address | Capability and response variants need probing |
| `0d24` / `0d25` | Connect / disconnect a transport/address | Do not enable from multipoint marketing support alone |
| `0d29` | Database-change notification | Subscription support unverified |
| `0d2a` | Delete a remembered database entry | Public-mode access restriction; destructive action if supported |

The newer record layout carries next index, connection status, flags, transport type, address, GNP endpoint, device type, and identifier. The corresponding Elite 85t generic definitions are older and often mark these commands undefined/protected. Do not reuse the Elite 10 layout on the 85t without successful bounded reads.

Start with read-only capability and connection/database queries on the two models. A public-mode rejection should leave the feature unavailable, without switching to private production or test modes. If listing succeeds, validate each of connect, disconnect, forgetting, and pairing independently with a second source. If the APIs remain unavailable, Hush can explain multipoint and open this phone's Bluetooth settings, but it cannot honestly present the phone's bonded-device list as the earbuds' remembered-source list.

Hush's existing Sony peer list can supply presentation components. Its protocol IDs, playback order, result codes, and LDAC restrictions cannot be reused as Jabra semantics. Introduce explicit action capabilities and an `isCurrentController` field: comparing a source address with `HeadphonesState.address` compares it with the **headset's** address. An intentional controller-source disconnect also needs a policy that prevents automatic reconnect from undoing the action.

## Original proposed implementation order

1. Add a Jabra firmware manifest provider, package validation, release notes, and download-only verification; generalize the current Sony-only firmware-check result.
2. Add MyControls read/write/reset with capability and lock handling; verify reversible changes on both models against Sound+.
3. Run bounded, read-only multipoint-management probes and expose only demonstrated capabilities.
4. Implement the isolated OTA state machine, simulation/fixture tests, and physical update/recovery qualification before exposing installation.

Extend tests for wrong PID/language/partition packages, malformed metadata, CRC vectors, interrupted downloads, unknown button records, locked assignments, rejected readbacks, stale sessions, OTA credits and duplicate events, missing verification, and disconnect/reconnect behavior. Keep firmware writes separate from widgets, scenes, and automation.

## APK evidence index

These paths refer to the supplied APK's reconstructed files, not files to distribute with Hush:

- `Ib/e.java`: `firmwareinfo_%04X.xml`, package XML parsing, language selection, component ordering, downloads, CRC verification.
- `Ib/b.java`: firmware CRC calculation and optional signed-header exclusion.
- `Ib/k.java`: OTA protocol identification, partition/version processing, erase/upload/verify state machine, chunk format, preload parameters.
- `Qa/C1605m.java`, `Qa/y.java`: protocol-7 support and staged-version decoding; `FirmwareUpdateBatteryLevelHelper.java` and `BatteryStateKt.java`: update battery policy.
- `com/jabra/sdk/impl/E0.java`: Bluetooth firmware-update entry, firmware-lock check, completion and cancellation path.
- `com/gnnetcom/jabraservice/OtaProgress.java`: progress/error states; `Ra/C1615f.java` and `Ra/C1625p.java`: finalization dispatch and GO request.
- Model directories `11846_01-92_1.2.4` and `9421_01-68_2.0.0`: Forms, localized labels, and full GNP definitions. Elite 10 MyControls definitions occur around lines 11210/11319/11751; Elite 85t around 7544/7651/8081 in the decoded XML.
- `com/jabra/sdk/api/mycontrols/IMyControls.java`, `JabraDeviceMyControlsHandler.java`: slot enums and Forms-based setting writes.
- `ui/mycontrols/model/MyControlsConfigurationModelMapper.java`: HOLD exclusion; `ui/mycontrols/rules/SpotifyMyControlsBusinessRule.java` and `GoogleBistoControlsBusinessRule.java`: reserved assignments.
- `meta/DeviceToggle.java`, `JabraDevicePairingListHandler.java`, `DeviceProductId.java`: pairing-screen product gate.

Decompiled control flow is evidence to be checked against schemas and captures, not source code to copy. See the extension validation in [JABRA.md](JABRA.md) for the subsequent reversible button tests and read-only firmware checks. Firmware installation remains physically unqualified. Multipoint management was not selected for this implementation.

## Implementation status

The firmware discovery, download verification, and phone update flow described above are now implemented. `app/update/JabraFirmware.kt` reads the public product-specific manifests and package metadata independently of the Sony updater. It requires the verified Elite 10 or Elite 85t product ID, checks the public model name, target PIDs and version, preserves the device-reported language, and requires each partition in the supported model's complete partition set exactly once. Component ordering follows `updateOrder`, preserving XML order for equal values. Unknown partition layouts or a package requiring a newer updater metadata profile fail closed; the current compatibility profile is the researched `5.0.0.0`, not Hush's app version.

Downloads accept HTTPS from `jabra.com` and `www.jabra.com` only, revalidate every redirect, and bound metadata, file sizes, and redirect count. XML external declarations are rejected, filenames cannot escape the package directory, and signed-header metadata selects the documented checksum offset while retaining the complete transferred file. The cache is private to Hush, binds entries to the package source, writes complete files atomically, and revalidates metadata and each file's CRC before producing the core's immutable verified package. No manufacturer firmware or decompiled implementation is distributed with Hush.

The phone Updates page and weekly background checks now support Jabra alongside the existing Sony path. Background work only checks availability. Preparing an update reads protocol, language, lock, readiness, and battery state, downloads the selected files, and opens an explicit installation review. The install action requires a newer firmware version for the same selected device and a running foreground service. The controller repeats readiness and applicability checks before any erase. Firmware progress survives leaving the page; cancellation is offered only while the core reports it is safe before erase. The UI explains charging-case completion and provides a read-only installed-version check after the case update light has stopped flashing. Installation is reported successful only after the controller verifies the target version on the original device.

The installer is presented as experimental. Implemented transfer and recovery logic still requires physical update qualification; previous hardware tests exercised existing controls, not a firmware installation. Pure parser tests cover model/version/language mismatches, missing and duplicate partitions, ordering, unsafe URLs and filenames, XML declarations, and unsupported metadata profiles. Optional local evidence tests parse the previously downloaded public metadata for all six languages and run all nine English files through the production CRC/package factories when those research files are available outside the repository. These tests neither redistribute firmware nor send Bluetooth commands.
