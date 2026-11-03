# Extending Hush

Hush is built so that a brand, a model or a feature is added in one place and every surface picks it up: the phone
app, widgets, the quick settings tile, alerts, the watch and the Features page. The shared code never asks which
brand it is talking to; it asks a `DeviceController` what it can do.

## The map

```
protocol/<name>      Pure Kotlin (JVM): frames, commands, parsers, state model. No Android. Tested on the JVM.
devices/api          The contract: DeviceModule, DeviceController, capabilities, Contract checks, phone fallbacks
                     (phone EQ, Android's battery reading, phone sleep timer), scenes, insights, AutoEq.
devices/<brand>      One module per brand: finds its devices, maps its protocol onto the contract, brings its own
                     settings rows, pages and legal notice.
devices/<brand>-core Optional: the brand's engine without UI, when the watch or TV also talks to the headphones.
link                 The phone ↔ watch contract, in no brand's terms.
ui                   Shared Compose components (rows, pages, theme pieces). No device knowledge.
app, wear, tv        Shells. The phone app lists brands in one place: Brands in app/…/DeviceModules.kt.
```

A dependency only ever points down this list. A brand module never depends on another brand, and `app` reaches a
brand only through `Brands.all`.

## Add a brand

1. **Protocol.** If the headphones talk over a control channel, add `protocol/<name>` as a `kotlin.jvm` module:
   requests, parsers and a state model, with tests against captured traffic. `protocol/mdr` is the reference.
   Headphones with no control channel (wired EarPods) skip this.
2. **Module.** Add `devices/<brand>` (an Android library depending on `:devices:api`) with an `object` implementing
   `DeviceModule`:
   - `id` (lowercase, it prefixes every device key), `brand`, and `notice` for trademarks and data sources.
   - `models`: one `Model` per product you know, each with a `howTo` the add screen shows.
   - `candidates` (units seen but not added) and `devices` (added units and whether they are in use).
   - `controller(device)`: the `DeviceController` for one unit. `hasDemo`/`demoController` give it a demo.
3. **Controller.** Implement what the hardware does and leave the rest `null`; see the next section.
4. **List it.** One line in `Brands.all` and one `implementation(project(":devices:<brand>"))` in `app`.
5. **Check.** `./gradlew testDebugUnitTest` runs `DeviceModulesContractTest` over every brand, and a debug build
   refuses to start while `Contract.check(module)` finds a problem. Controller problems are logged under the
   `Contract` tag as soon as a device opens.

`devices/apple` is the smallest complete brand: no protocol, USB detection, a phone EQ, an authenticity check.

## Add a feature to a device

Every universal feature is a capability with a source (`devices/api/…/Capabilities.kt`):

| Source | Meaning | Examples |
|---|---|---|
| `DEVICE` | The headphones do it | noise cancelling, a hardware EQ, the headset's battery |
| `HYBRID` | The phone decides, the headphones do the work | AutoEq fitted onto hardware bands, a sleep timer that fades and switches the headphones off, scenes switching noise control |
| `PHONE` | The phone stands in for missing hardware | an EQ on the output mix, Android's battery reading, the phone's volume |

So a feature can be hardware, software, or software driving hardware, and the same feature can be any of the three
on different headphones. Report hardware from the controller (`noise`, `equalizer`, `sleepTarget`, `firmware`,
`batteries`…); `withPhoneFallbacks` fills what is missing with the phone's version, and `capabilities` works out
the rest. A capability that is phone logic on the device's hardware declares `providedBy = Source.HYBRID`.

What follows from that for free:

- **Listen / Sound / Device pages** draw the noise card, the equalizer, the sleep timer and so on from the
  capabilities, and mark anything that is not plain hardware ("Runs on this phone, on everything it plays").
- **Features page** lists every feature under "On your headphones", "Phone and headphones together", "On this
  phone" or "Not on these headphones".
- **Widgets, tile, watch, alerts** read `quick`, `noise`, `equalizer` and `state` of the current device.
- **Scenes and automation** drive noise, EQ, volume and `sceneSwitches` of every connected device.

Features only one brand has (Speak-to-Chat, button remapping, multipoint) are not capabilities: they are
`SettingsGroup` rows from `settings()` (each group names its tab), `quick.toggles` for the widgets and the watch,
and `sceneSwitches` for scenes. Bigger ones bring their own section through `listenSections`, `soundSections` or
`deviceSections`.

## Add a universal feature

When most headphones could have it, make it a capability:

1. Add an interface in `devices/api` extending `Capability` and an optional property on `DeviceController`
   (default `null`).
2. Add a `Feature`, map it in `capabilities`, and give it notes for `PHONE` and `HYBRID` in `Feature.note`.
3. If the phone can stand in, add the fallback to `PhoneFallbacks`.
4. Draw it once in `app` from the capability, never from a brand. Add its icon to `FeaturesPage`.
5. Extend `Contract.check(controller)` with whatever must always hold for it, and test it in `CapabilitiesTest`.

## Add a model

Add a `Model` to the brand's `models` (id, name, transport, how to pair, picture). If the model needs different
handling, the controller reads it from the device's `modelId`. Insights take the rated battery life from the
brand's `Insights.Profile`. Nothing outside the brand module changes.
