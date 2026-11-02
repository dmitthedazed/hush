Received MDR v2 frames from a WF-1000XM5 (firmware 6.1.0, protocol 0x03003018), from
[mos9527/SonyHeadphonesClient](https://github.com/mos9527/SonyHeadphonesClient) `tests/WF-1000XM5-6.1.0`
at commit 8f4808c (MIT, see LICENSE.mos9527; scrubbed upstream with its `tooling/scrub-capture.py`). Only the
headset's DATA frames are kept; `MdrV2CaptureTest` replays them through Hush's frame parser and v2 decoder.
