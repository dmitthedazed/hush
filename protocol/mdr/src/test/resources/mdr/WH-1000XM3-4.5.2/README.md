Received MDR frames from a WH-1000XM3 (firmware 4.5.2, protocol 0x4010), from
[mos9527/SonyHeadphonesClient](https://github.com/mos9527/SonyHeadphonesClient) `tests/WH-1000XM3-4.5.2`
(MIT, see LICENSE.mos9527; addresses already replaced with placeholders there). Only the headset's
DATA frames are kept; `MdrV1CaptureTest` replays them through Hush's frame parser and v1 decoder.
