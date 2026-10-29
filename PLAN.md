# Add Jabra Elite 10 and Elite 85t support

## Summary

Support the original Elite 10 and Elite 85t across Hush’s phone, TV, and Wear apps, including widgets, tiles, scenes, and automation. Implement noise controls, hardware EQ, battery reporting, identity, and supported device settings.

Use the supplied Sound+ APK and [JabraLibre’s research](https://github.com/pLum0/JabraLibre) as protocol references, with an independent implementation that preserves Hush’s MIT license.

## Shared architecture and protocol

- Introduce Sony and Jabra backends behind `HeadphonesController`, retaining its existing application-facing actions. Backends own protocol commands, initialization, capabilities, and state updates.
- Recognize devices using original Bluetooth names, aliases, and saved identity. Verify Jabra product IDs during initialization: **11846 for Elite 10**, **9421 for Elite 85t**. Preserve existing Sony selections.
- Implement Jabra GNP over RFCOMM using the standard SPP service; retain bounded, model-specific channel fallbacks, including Elite 10 channel 29.
- Parse GNP’s six-byte header correctly: packet type occupies the upper two bits of byte 3; total frame length occupies the lower six bits. Handle fragmented and combined reads, malformed packets, errors, and sequence correlation.
- Require successful identity and initial state reads before exposing connected controls. Cancel pending commands on disconnect or device change; publish confirmed settings after readback.
- Document protocol layouts, APK source references, model differences, and fixtures in `docs/JABRA.md`. Keep decompiled vendor material outside the repository.

## Features and interfaces

| Area | Implementation |
|---|---|
| Noise | ANC, HearThrough, and Off through `13be`; discover available modes and level ranges. Invert strength controls because wire level zero means maximum strength. |
| Equalizer | Read configuration through `137d` and enable state through `137e`. Preserve reported frequencies, master gain, and Q when changing band gains. |
| Battery | Decode `1202` with variable component counts. Show case and earbud levels; use neutral earbud labels until connection-side information establishes left/right reliably. |
| Identity | Read model/product ID and firmware; keep serials and addresses hidden by default. |
| Sleep timeout | Offer Off, 15, 30, 60, and 120 minutes through `1390`. |
| Announcements | Preserve all three `133a` choices: tones, voice, and off. |
| Automatic pause | Use Elite 10’s `138e` command and Elite 85t’s `1383` mapping. Verify the 85t’s inverted enable value against Sound+. |
| Playback | Use Android audio/media controls for Jabra playback and volume on the active local audio route. |

- Add brand, explicit capabilities, component batteries, and device-specific setting choices to shared state. Retain aggregate battery for existing compact surfaces, using the lowest known earbud level and excluding the case.
- Generalize EQ capabilities to describe band frequencies, ranges, neutral values, and gain scaling. Jabra exposes enable/bypass, flat reset, editable bands, and saved curves without fabricated Sony preset slots.
- Add EQ layout identity to libraries, scenes, automation, backups, and share codes. Decode existing records as Sony; incompatible curves remain stored but cannot be applied. Clear pending restores when the selected device changes.
- Adapt AutoEq to reported Jabra frequencies and limits, presenting its result as an approximation without Sony-specific response claims.
- Extend the watch contract with optional brand, capabilities, battery components, and EQ metadata. Preserve legacy Sony decoding.
- Gate controls in both UI and command dispatch. Use Jabra labels and generic earbuds artwork; suppress unsupported Sony controls, guides, and calibrated estimates. Sleep timers pause and restore volume when device power-off is unavailable.

## Validation

- Preserve the existing Sony baseline: **all seven core tests currently pass**.
- Add protocol fixtures covering frame boundaries, fragmentation, combined packets, malformed lengths, errors, battery counts, EQ scaling, and model-specific setting encodings.
- Test delayed confirmation, rejected writes, reconnects, rapid commands, device switching, and unsupported-command rejection.
- Test legacy settings/backup/share-code migration, incompatible EQ rejection, and old/new watch messages.
- Run unit tests and assemble all three applications.
- Validate both physical models against Sound+: modes, strength, EQ, announcements, sleep timeout, auto-pause, battery readings, earbud removal, button-originated changes, reconnects, and Sony/Jabra switching. Mark support experimental until these checks pass.

## Agreed boundaries

The first release targets these two models; other Jabra models and Elite 10 Gen 2 remain unsupported. Firmware pages show the installed version and link to Sound+ or Jabra support. Firmware checks/installations, MySound, spatial audio, and Jabra-specific button remapping are deferred. Private production-test power-off commands will not be exposed.
