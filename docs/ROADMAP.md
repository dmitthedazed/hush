# Roadmap

Ideas in rough order; nothing here is built yet unless it says so.

## Hush network: several devices, one pair of headphones

Headphones keep their control channel with only one host, and multipoint holds only two or three audio connections.
Hush instances (phone, TV, watch, later a computer) can cooperate to hide both limits behind one UI.

**Two features on one transport**

1. **Remote control.** The host that holds the control channel (the *channel owner*) relays commands from the other
   Hush instances and publishes the device state to them. A `RemoteDeviceController` implements the same
   `DeviceController` contract, so Listen / Sound / Device draw it without knowing it is remote.
2. **Semi-automatic switching** (AirPods-like, without Apple's private link). Each node publishes whether it is
   connected to the headphones and whether it is playing. The node that starts playing takes the audio; when no
   multipoint slot is free the longest-idle host is asked to let go. When the new player stops, the interrupted host
   resumes, only if it was playing and only within a short window.

**Steps**

1. **Multipoint capability** in `devices/api`: connected hosts, how many fit, disconnect host X. Sony implements it
   with MDR (works from any connected host). A "Connected devices" page needs no network, so it ships first.
2. **Message contract** in `:link`, in terms of capabilities (noise, equalizer, settings, batteries), never of a
   brand's protocol. `:link` already does this for the watch; generalise it.
3. **Transport and trust:** discovery on the local network (NSD/mDNS, BLE as fallback), pairing by QR, an encrypted
   session on a shared key. Power-off and firmware are never proxied.
4. **Channel owner and `RemoteDeviceController`:** ownership follows playback; the old owner closes its channel, the
   new one opens it, clients reconnect; the UI says which node the control goes through.
5. **Switching policy** on top of presence (`AudioPlaybackConfiguration`, `MediaSession`) and the resume window.
6. **Desktop client** (Linux/Windows) reusing `:protocol:mdr`; the largest piece, last.

**Limits to design around**

- Android has no public API to disconnect another app's A2DP link: use MDR from the headphones' side, and root/Vector
  where available.
- Reconnecting audio takes 1–3 s; it will not be instant like AirPods.
- Only headphones with multipoint can switch; wired and single-point ones can still be controlled remotely.
- If the owner is asleep or gone, control stops until another node takes the channel (on request).

## Activity

- **Activity tab:** listening statistics per headphone, a log of all time, the places where listening happened, and
  use of Ambient Sound Control in scenes. Ranges: day / week / month / year / all time.
- **Switch for the whole feature.** Offline first: nothing leaves the phone, and any connection to an online service
  needs explicit consent.
- **Badges and achievements** in the style of Sound Connect: nice 3D badges with different milestones.
- **Import activity from a Sony account** (with consent).

## Sony

- **360 Reality Audio:** try to pull the ear data from Sony and carry the personalisation over; if it turns out to be
  too much trouble, drop it.
- **Authenticity check over Bluetooth** for Sony headphones (the EarPods already have one over USB).
- **Find tab:** a link to Google Find Hub for Sony headphones.

## App

- **"Close the app completely" button** that releases the control channel, so another controlling app can take it.

## Plugins

- **A plugin system** for optional integrations that don't belong in the core app.
- **Move the Xposed hook into it** as the first plugin (ColorOS My Devices / PantaConnect integration through
  libxposed), and **cut Xposed out of the main app**: no hook classes, no Xposed manifest entries or dependencies
  in `:app`.

## Other

- Jabra (Elite 10 / 85t) on the module system, contributed in the open pull request: reads everything the headphones
  report, firmware *check* only, never install.
- Original product renders for non-Sony models; earbud and case glyphs are the fallback (`BatteryGlyphs`).
