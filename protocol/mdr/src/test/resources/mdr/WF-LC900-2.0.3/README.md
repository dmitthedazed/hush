Received MDR v2 frames from a WF-LC900 LinkBuds Clip (firmware 2.0.3, protocol 0x03003015), from
[mos9527/SonyHeadphonesClient](https://github.com/mos9527/SonyHeadphonesClient) `tests/WF-LC900-2.0.3`
at commit 8f4808c (MIT, see LICENSE.mos9527; scrubbed upstream with its `tooling/scrub-capture.py`). Only the
headset's DATA frames are kept; `MdrV2CaptureTest` replays them through Hush's frame parser and v2 decoder.
