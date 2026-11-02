# MDR v2: что known open-source проекты на GitHub/Codeberg

Дата: 2026-10-21. Клоны (`--depth 1`, только чтение, не собирались):
`/tmp/claude-1000/-home-dimka--claude-spares-spare-v9hXlM/fa7ef9fa-c442-41c8-b8e1-e54ff4678937/scratchpad/gh/`.
Ниже пути вида `mos9527/...` означают `<scratchpad>/gh/mos9527-клон`, то есть `SonyHeadphonesClient/...`.
Сокращения: **GB** = Gadgetbridge, **SB** = SonyBridge, **BL** = BudsLink, **libmdr** = `SonyHeadphonesClient/libmdr`.

## 0. Главное

- Самый полный и самый проверенный источник: **mos9527/SonyHeadphonesClient** (библиотека **libmdr**, MIT). Там есть типизированные структуры всех команд V2 T1/T2, захваты трафика с реальных наушников и тесты. Опирайтесь на него.
- **GB** (AGPL-3.0) и **BL** (GPL-3.0) читать можно, но код переносить в Hush (MIT) нельзя. Использовать как перекрёстную проверку и для списка моделей.
- **SB** (MIT) и **libmdr** (MIT) можно использовать с указанием авторства. Хеш-версии и opcode в них совпадают с libmdr.
- Все opcode из libmdr, GB, SB и BL совпадают, кроме нескольких мест (раздел 6). Там расхождения, которые нужно проверять захватом.

## 1. Проекты

| Проект | Лицензия | Активность (последний коммит / push) | V2 | Модели, подтверждённые по источникам |
|---|---|---|---|---|
| mos9527/SonyHeadphonesClient (libmdr, libmdr-bt, tests, docs/device-support) — https://github.com/mos9527/SonyHeadphonesClient | MIT (`LICENSE`) | 2026-10-20; 341★ | Полный V2 T1+T2 (`libmdr/src/HeadphonesV2*.cpp`, `libmdr/include/mdr/ProtocolV2*.hpp`) | WH-1000XM5 (FW 2.5.1, classic RFCOMM, README/`docs/device-support/WH-1000XM5.md`), WH-1000XM6 (FW 3.0.0, `docs/device-support/WH-1000XM6.md`), WF-1000XM5 (`docs/device-support/WF-1000XM5.md`), WH-CH720N (`docs/device-support/WH-CH720N.md`), WF-C510 (`docs/device-support/WF-C510.md`), WF-C700N (FW 2.0.5/2.0.7, `docs/device-support/WF-C700N.md`), WF-L910 LinkBuds Open (FW 1.4.1, `docs/device-support/WF-L910.md`), WF-LC900 LinkBuds Clip (FW 2.0.3, `docs/device-support/WF-LC900.md`), WF-LS900N LinkBuds S (`docs/device-support/WF-LS900N.md`, без таблицы). Для сравнения V1: WH-1000XM3 (`docs/device-support/WH-1000XM3.md`) |
| Gadgetbridge — https://codeberg.org/Freeyourgadget/Gadgetbridge | AGPL-3.0 (заголовки файлов) | 2026-10-19 | `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/sony/headphones/protocol/impl/v2/` | Координаторы: WH-1000XM5 (`.../devices/sony/headphones/coordinators/SonyWH1000XM5Coordinator.java`, без батареи L/R), WH-1000XM6 (experimental), WF-1000XM5 (experimental, `SonyWF1000XM5Coordinator.java`), WF-1000XM4, WF-C700N/C710N, WH-CH720N, WH-ULT900N, LinkBuds, LinkBuds S (`SonyLinkBudsSCoordinator.java`, `preferServiceV2()`), WF-1000XM3/WH-1000XM3/XM4 (V1) |
| AmitRajput-Dev/SonyBridge — https://github.com/AmitRajput-Dev/SonyBridge | MIT (`LICENSE`) | 2026-08-14 (код); 90★ | Есть: `Client/Constants.h` (`V2Command`), `Client/Headphones.cpp`, `Client/CommandSerializer.cpp:203` (`serializeNcAndAsmSettingV2`) | Проверено на железе только WH-CH720N и ULT WEAR (README, «Supported headphones»). Ожидаемо работают XM5/XM6/WH-XB910N/WH-CH520; WF-1000XM4/XM5, WF-C700N, LinkBuds S — «battery format differs» (README). Основа — Plutoberth (MIT), поэтому структура C++ похожа на старый клиент |
| maniacx/BudsLink — https://github.com/maniacx/BudsLink | GPL-3.0 | 2026-10-20 (push); 257★ | `src/lib/devices/sony/sonySocketV2.js` (1503 строк), `sonyConfig.js:316` (`PayloadTypeV2T1`, `PayloadTypeV2T2`, `FunctionTypeV2T1`) | Конфиги моделей `src/lib/devices/sony/deviceConfigs/*.js`: WH-1000XM4/5/6, WF-1000XM3/4/5/6, WF-C500/C510/C700N/C710N, WH-CH720N, LinkBuds, LinkBuds S, ULT, WH-XB900N/XB910N, WI-C100, WI-SP600N, WH-1000XM2/3 |
| VyomJain6904/sony-headphones-linux — https://github.com/VyomJain6904/sony-headphones-linux | MIT (`LICENSE`, `licenses/libmdr-LICENSE`) | 2026-08-21; 12★ | Вендорит libmdr (пин `db0ae2574d8f8e1407e8ea28a7ee87db9dde1c24`, `docs/COMPATIBILITY.md`) и `native/src/mdr_backend.cpp` (UUID и выбор поколения, строки 29–33) | WH-1000XM5 FW 2.5.1, classic RFCOMM, MDR v2 — полный тест чтения и записи (`docs/COMPATIBILITY.md`). Есть `SPEC.md`, `SONY_PLUGIN_RESEARCH.md` (список функций Sound Connect). Патч `patches/libmdr-v2-connection-priority-validation.patch` |
| ohm-app/sony-headphones-bluetooth-documentation — https://github.com/ohm-app/sony-headphones-bluetooth-documentation | CC0-1.0 | последний код-коммит 2023-10-28 | Пакеты LinkBuds S (`docs/Devices/LinkBuds S.md`), общая спецификация RFCOMM (`docs/Protocol/Specification.md`) | LinkBuds S (только AmSC и Speak-to-Chat) |
| Plutoberth/SonyHeadphonesClient — https://github.com/Plutoberth/SonyHeadphonesClient | MIT | последний коммит 2025-07-19; 1363★ | Только V1 | WH-1000XM3 (полностью), XM4 (частично) (README) |
| ClusterM/sony-headphones-control — https://github.com/ClusterM/sony-headphones-control | GPL-3.0 | 2024-02-20 | Только V1 (`README.md`: WF-1000XM3, WH-1000XM2/3) | — |
| impankratov/sony-headphones-control-py | **лицензии нет** (GitHub API пуст) | 2026-06-08 | Не разбирался: только ANC-переключение | — (не копировать) |

Дополнительно: `dustbin1415/WinUI-SonyHeadphonesClient` (MIT) и `artistro08/ConnectFlyout` (MIT) используют клиентские библиотеки выше; их протокольный код не разбирал.

## 2. Фрейм и отличие от v1

Фрейм одинаковый для v1 и v2 (подтверждено тремя независимыми реализациями):

```
3E | escape( TYPE | SEQ | LEN(int32 BE) | PAYLOAD | SUM8 ) | 3C
```

- Экранирование: байты `3C`, `3D`, `3E` внутри заменяются на `3D 2C`, `3D 2D`, `3D 2E` (то есть байт & `0xEF`, сброс бита 0x10). GB: `.../headphones/protocol/Message.java:` `escape`/`unescape` (маска `MESSAGE_ESCAPE_MASK = 0b11101111`); libmdr: `libmdr/src/Command.cpp:14-37` (`Escape`/`Unescape`).
- `SUM8` — сумма байт от TYPE до конца PAYLOAD по модулю 256 (`libmdr/src/Command.cpp:74-96`; GB `Message.java`, `calcChecksum`).
- TYPE: `0x0C` = DATA_MDR (таблица 1), `0x0E` = DATA_MDR_NO2 (таблица 2), `0x01` = ACK, `0x00` = DATA (в libmdr `libmdr/include/mdr/Protocol.hpp:37-55`).
- Пример из теста GB (`gadgetbridge/app/src/test/.../impl/v2/SonyProtocolImplV2Test.java:116-119`): GET_AUDIO_CODEC = `3e:0c:01:00:00:00:02:12:02:23:3c`. Проверка: 0C+01+00+00+00+02+12+02 = 0x23.
- ACK: TYPE=`0x01`, SEQ = `1 - SEQ_входящего`, пустой PAYLOAD (GB `SonyHeadphonesProtocol.java:426-428`; libmdr `Headphones.cpp:311-315`, `SendACK`). Пример: ACK на SEQ=0 → `3E 01 01 00 00 00 00 02 3C`.
- Отличие от v1 не во фрейме, а в payload: opcode те же (частично), но у v2 больше таблиц, команды Table 2 (`0x0E`) активны, и структуры payload зависят от inquired-type. Версия определяется по ответу на `CONNECT_GET_PROTOCOL_INFO` (раздел 3).

## 3. Handshake и определение поколения

### 3.1 Выбор поколения

- **По UUID сервиса SDP** (основной способ, libmdr и SB и BL и GB):
  - v1: `96CC203E-5068-46AD-B32D-E316F5E069BA`
  - v2: `956C7B26-D49A-4BA8-B03F-B17D393CB6E2`
  - GB: `SonyHeadphonesSupport.java:44-45` (константы), `:176-200` (`getSupportedService`: если есть V2-UUID, берёт V2, иначе V1).
  - sony-headphones-linux: `native/src/mdr_backend.cpp:29-33` (`protocolGeneration`: XM5-UUID → 2, legacy → 1).
  - libmdr: семейство задаётся вызывающим кодом (`mdrConnectionConnect`, `libmdr/src/Headphones.cpp:1407`: `MDR_PROTOCOL_V2 → ProtocolFamily::V2`), а не по payload.
- **По длине ответа на init** (GB): `SonyHeadphonesProtocol.java:133-146`. Длина 4 байта → v1 (`01 00 40 10`), длина 8 → v2 (`01 00 03 00 00 00 00 00`). Для других длин GB пишет error и ничего не делает.
- SB: пробует v1, затем v2, и запоминает, что сработало (README, раздел «How it works»).

### 3.2 Ответ на CONNECT_GET_PROTOCOL_INFO (v2)

Из `libmdr/include/mdr/ProtocolV2T1.hpp:3747-3760` (`ConnectRetProtocolInfo`, `#pragma pack(1)`, 8 байт):

| Смещение | Поле | Значение |
|---|---|---|
| 0 | command | `0x01` (`CONNECT_RET_PROTOCOL_INFO`) |
| 1 | type | `0x00` (FIXED_VALUE) |
| 2–5 | protocolVersion | Int32 BE |
| 6 | supportTable1 | `0x00` = ENABLE, `0x01` = DISABLE |
| 7 | supportTable2 | `0x00` = ENABLE, `0x01` = DISABLE |

Значения версии из документации и комментариев:
- WF-C700N FW 2.0.5/2.0.7: `0x03000015` (`docs/device-support/WF-C700N.md:4`)
- WF-LC900 FW 2.0.3: `0x03003015` (`docs/device-support/WF-LC900.md:4`)
- WF-1000XM5 2.0.1: payload `01:00:03:00:10:04:00:00` → `0x03001004` (GB `SonyHeadphonesProtocol.java:138-144`, комментарий)
- LinkBuds S 2.0.2: `01:00:03:00:00:07:00:00` → `0x03000007` (там же)
- LinkBuds 1.0.3: `01:00:02:00:10:00:00:00` → `0x02001000` (там же)
- WH-1000XM5 1.1.3: `01:00:03:00:00:00:00:00` (там же)
- v1: 4-байтовый ответ `01 00 hi lo`; WH-1000XM3 `0x4010`, WH-1000XM2 `0x4000` (`docs/device-support/WH-1000XM3.md`, `Hush/docs/MDR_V1.md` раздел 1)

Если `supportTable1 = DISABLE`, libmdr прерывает init (`libmdr/src/HeadphonesV2.cpp:8-30`: «Device doesn't support MDR V2 Table 1»). Table 2 запрашивается только если `supportTable2 = ENABLE`.

BL читает версию по смещению 1..4 (`budslink/src/lib/devices/sony/sonySocketV2.js:103-116`), то есть на байт раньше, чем libmdr. Для логов это не критично, но не копировать.

### 3.3 Порядок init (libmdr `libmdr/src/HeadphonesV2.cpp:8-120`)

1. `CONNECT_GET_PROTOCOL_INFO` = `00 00` (T1). Ждать RET `01`.
2. `CONNECT_GET_CAPABILITY_INFO` = `02` (T1), ответ `03`.
3. `CONNECT_GET_DEVICE_INFO` = `04 <type>`: type `01` model name, `02` FW version, `03` series/color, `04` instruction guide (не отвечает на некоторых моделях, см. раздел 8). Ответ `05`. GB/SB: `04 02` = FW (`SonyBridge/Client/Constants.h` (`FW_GET`)).
4. `CONNECT_GET_SUPPORT_FUNCTION` = `06` на T1 (ответ `07`) и на T2 (DATA_MDR_NO2). Таблица функций: до 256 бит (`DetailsV2.hpp:` `SupportStates`).
5. Если поддерживается, GS (`GENERAL_SETTING`, inquired type `0xD1..0xD4`) GET_CAPABILITY и GET_PARAM; DSEE (`AUDIO_GET_CAPABILITY`, type `01`); assignable controls (`SYSTEM_GET_CAPABILITY`); `ALERT_SET_STATUS` (включить fixed-message alerts); кодек `COMMON_GET_STATUS` type `02`; play-параметры (`PLAY_GET_PARAM`/`PLAY_GET_STATUS`).

Каждая команда отправляется только после ACK на предыдущую (`SendCommandACK` в `HeadphonesV2.cpp`, раздел 8).

## 4. Таблица opcode

Колонки: opcode (hex) → имя из libmdr (`libmdr/include/mdr/ProtocolV2T1.hpp:376-…`, `ProtocolV2T2.hpp`). «Совпадает с» — подтверждение в GB (`service/devices/sony/headphones/protocol/impl/v2/PayloadTypeV2.java`, `v1/PayloadTypeV1.java`), SB (`Client/Constants.h`), BL (`sonyConfig.js:316-…`).

### 4.1 Таблица 1 (DATA_MDR, TYPE 0x0C)

| Opcode | Имя (libmdr) | Совпадает с | Payload / примечания |
|---|---|---|---|
| 0x00 / 0x01 | CONNECT_GET / RET_PROTOCOL_INFO | GB, SB, BL | `00 00` / `01 00 <i32 ver> <t1> <t2>` (3.2) |
| 0x02 / 0x03 | CONNECT_*_CAPABILITY_INFO | BL | `02 00` |
| 0x04 / 0x05 | CONNECT_*_DEVICE_INFO | GB, SB, BL | `04 <01 model / 02 FW / 03 series-color / 04 guide>` |
| 0x06 / 0x07 | CONNECT_*_SUPPORT_FUNCTION | BL | T1 и T2, `06 00` |
| 0x12 / 0x13 | COMMON_GET / RET_STATUS | GB (`AUDIO_CODEC_REQUEST/REPLY`), SB, BL | `12 02` → `13 02 <codec>` (codec-enum в GB `v1/params/AudioCodec.java`) |
| 0x15 | COMMON_NTFY_STATUS | BL | |
| 0x18 / 0x19 | COMMON_SET_PARAM / NTFY_PARAM | — | не разбирал |
| 0x22 / 0x23 | POWER_GET / RET_STATUS | GB (BATTERY_LEVEL_REQUEST/REPLY), SB, BL | `22 <type>` → `23 <type> ...` (раздел 5.1) |
| 0x24 / 0x25 | POWER_SET / NTFY_STATUS | GB (`POWER_SET`), BL | Выключение: `24 03 01` (GB `SonyProtocolImplV2.java` `powerOff()`) |
| 0x26 / 0x27 | POWER_GET / RET_PARAM | GB, SB, BL | `26 04` = AUTO_POWER_OFF; `26 05` = AUTO_POWER_OFF_WEARING_DETECTION (GB, SB) |
| 0x28 / 0x29 | POWER_SET / NTFY_PARAM | GB, SB, BL | `28 05 <c0> <c1>` (GB `setAutomaticPowerOff`, SB `APO_SET`) |
| 0x30–0x39 | UPDT_* (прошивка) | — | **Не реализовывать** (`sony-headphones-linux/SONY_PLUGIN_RESEARCH.md`: firmware excluded) |
| 0x40–0x4D | LEA_* (LE Audio) | — | Таблица LEA-флагов в T1 `0x45–0x4F` (`*_CANT_BE_USED_WITH_LEA_CONNECTION`) |
| 0x50–0x5B | EQEBB_* (эквалайзер, EBB) | GB, SB, BL (`0x56/57/58/59`) | `56 00` GET; `57 00 <preset> 06 <bass+10> <b1..b5+10>` RET; `58` SET (5.4) |
| 0x60–0x69 | NCASM_* (NC/ASM) | GB, SB, BL (`0x66/67/68/69`) | `66 <type>` GET, `68 <type> ...` SET (5.2) |
| 0x70–0x7B | SENSE_* | — | Назначение не установлено. Не разбирал |
| 0x80–0x89 | OPT_* (NC optimizer) | — | Упомянут в mos9527 V1 `NOISE_CANCELLING_OPTIMIZER_*`; на V2 GB не реализован (`SonyProtocolImplV2.java:191`) |
| 0x90–0x99 | ALERT_* (уведомления об операциях) | — | `ALERT_SET_STATUS 0x94`, `ALERT_SET_PARAM 0x98`, `ALERT_NTFY_PARAM 0x99`. См. раздел 6 про конфликт с GB (`0x98`) |
| 0xA0–0xA9 | PLAY_* (медиа) | GB (V1 0xA6/A7/A8/A9 = VOLUME) | `a6 20` = MUSIC_VOLUME (`PlayInquiredType`, `0x20`). `PlaybackControl`: PAUSE=0x01, TRACK_UP=0x02, TRACK_DOWN=0x03, PLAY=0x07 (`ProtocolV2T1.hpp`, enum `PlaybackControl`) |
| 0xB0–0xB9 | SAR_AUTO_PLAY_* | GB (`SERVICE_LINK 0xB6/B7/B8/B9`) | Auto Play / Link Auto Switch, см. раздел 6 |
| 0xC4 / 0xC9 | LOG_SET_STATUS / NTFY_PARAM | — | |
| 0xD0–0xD9 | GENERAL_SETTING_* | GB (`d8 d2` = touch panel; `d8 d1` = WAT/CTD), SB — нет | Inquired types `0xD1..0xD4` (`GsInquiredType`). GS2 = TOUCH_PANEL_SETTING (`docs/device-support/WH-1000XM3.md`, Notes) |
| 0xE0–0xE9 | AUDIO_* | GB, SB, BL (`e6/e8`) | `e6 <type>` GET, `e8 <type> <v>` SET. Types: `00` CONNECTION_MODE, `01` UPSCALING (DSEE), `02` CONNECTION_MODE_WITH_LDAC_STATUS, `03` BGM_MODE, `04` UPMIX_CINEMA, `05` CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO, `06` VOICE_CONTENTS, `07` SOUND_LEAKAGE_REDUCTION, `08` LISTENING_OPTION_ASSIGN |
| 0xF0–0xF9 | SYSTEM_* (PARAM) | GB, SB, BL (`0xF6/F7/F8/F9`) | `f6 <type>` GET, `f8 <type> <v>` SET (типы, раздел 5.6) |
| 0xFA–0xFD | SYSTEM_*_EXT_PARAM | GB (`0xFA–0xFD`, ambient button mode, `subtype 0x03`), BL | |
| 0xFF | UNKNOWN | — | |

### 4.2 Таблица 2 (DATA_MDR_NO2, TYPE 0x0E)

| Opcode | Имя | Совпадает с | Примечание |
|---|---|---|---|
| 0x06 / 0x07 | SUPPORT_FUNCTION (T2) | libmdr, BL | Ответ = `DetailsV2::SupportStates::table2Functions` |
| 0x20–0x29 | POWER_* (дубль T1) | BL | |
| 0x30–0x39 | PERI_* (устройства, мультиточка) | BL (`sonySocketV2.js:1007-1127`) | `34 <connType> <1/0> 01` = PERI_SET_STATUS; `3C <connType> <sub> <MAC 6 байт>` = PERI_SET_EXTENDED_PARAM. Подтипы по имени функции: `01` connect, `00` disconnect, `02` unpair (**предположительно**, см. раздел 6) |
| 0x3C / 0x3D | PERI_SET_EXT_PARAM / NTFY_EXT | BL, libmdr (поля `PairedDeviceConnectMac`, `...DisconnectMac`, `...UnpairMac`, `DetailsV2.hpp`) | |
| 0x40–0x49 | VOICE_GUIDANCE_* | GB (`COMMAND_2` 0x46–0x49, `VOICE_NOTIFICATIONS_*`), BL (`0x46/48/49`) | GB: `46 01` on/off (0x00=on), `46 20` громкость со знаком (-2..+2). **Совпадает** |
| 0x53–0x5B | SAFE_LISTENING_* (Sound Pressure) | — | |
| 0x60–0x69 | LEA_* | — | |
| 0x70–… | PARTY_*, … | — | |

Таблица 2 есть на WF-C700N, WH-1000XM5, WF-LC900 и т.д. (`docs/device-support/*.md`). На WH-1000XM3 (V1) это тоже работает, но opcode другие. См. раздел 6.

## 5. Форматы payload ключевых команд

### 5.1 Батарея

Запрос: `22 <type>`. Ответ: `23 <type> ...`.

Типы в libmdr (`PowerInquiredType`, `ProtocolV2T1.hpp`): `00` BATTERY, `01` LEFT_RIGHT_BATTERY, `02` CRADLE_BATTERY, `08` BATTERY_WITH_THRESHOLD, `09` LR_BATTERY_WITH_THRESHOLD, `0A` CRADLE_BATTERY_WITH_THRESHOLD.

| Ответ | Структура (libmdr) | Совпадение |
|---|---|---|
| `23 00 <lvl> <chg>` | `PowerRetStatusBatteryBase` | GB `handleBattery`, SB `BATTERY_RET`, BL — совпадает |
| `23 01 <Llvl> <Lchg> <Rlvl> <Rchg>` | `PowerRetStatusLeftRightBatteryBase` | GB: тип `0x01` = DUAL2, payload[2]/[3]/[4]/[5] — совпадает |
| `23 08 …` / `23 09 …` / `23 0A …` | threshold-варианты, добавляют байт порога | GB использует `0x09` = DUAL и `0x0A` = CASE; поскольку у них есть threshold-байты, разбор длины важен (раздел 8) |

Charging (`BatteryChargingStatus`): `00` NOT_CHARGING, `01` CHARGING, `02` UNKNOWN, `03` CHARGED.

Из GB (`SonyWF1000XM5Coordinator.java`): 3 батареи (case, left, right). WH-1000XM5 — single.

### 5.2 NC/ASM (шумоподавление / ambient)

Запрос: `66 <type>`. Установка: `68 <type> ...`. Уведомление: `69`.

Типы (`NcAsmInquiredType`, `ProtocolV2T1.hpp`), которые встречаются в проектах:
- `0x13` NC_ON_OFF_AND_ASM_SEAMLESS
- `0x14` NC_MODE_SWITCH_AND_ASM_SEAMLESS
- `0x15` MODE_NC_ASM_AUTO_NC_MODE_SWITCH_AND_ASM_SEAMLESS
- `0x17` MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS (`ProtocolV2T1.hpp:5266`, `:6024`)
- `0x21` ASM_ON_OFF, `0x2x` ASM_SEAMLESS и др.

Установка для `0x17` (`NcAsmSetParamDual…` в `ProtocolV2T1.hpp:6024-6040`, 7 байт):

```
68 17 <valueChangeStatus: 00=changing, 01=changed> <ncAsmTotalEffect: 00=off, 01=on>
   <ncAsmMode: 00=NC, 01=ASM> <ambientSoundMode: 00=NORMAL, 01=VOICE> <ambientSoundLevel u8>
```

Пример из `docs/...` ohm LinkBuds S (тот же семейный формат): `68 17 01 01 01 00 01` = ASM ON, режим ASM, normal, level 1; `68 17 01 00 00 00 08` = OFF.

GB (`SonyProtocolImplV2.java:86-117`) и SB (`CommandSerializer.cpp:203-…`) пишут тот же 7-байтный вариант без WNR. GB при `supportsWindNoiseCancelling()` добавляет байт `02/03` (NC/ASM/WNR) → 8 байт. Для моделей без WNR это 7 байт, как в libmdr.

Уровень ASM: `1..20` (`ambientSoundLevel u8`). Подтверждено записью 20 → 12 → 20 на WH-1000XM5 FW 2.5.1 (`sony-headphones-linux/docs/COMPATIBILITY.md`).

Focus on Voice = `ambientSoundMode = VOICE` (`01`) (libmdr). В GB: отдельный бит `isFocusOnVoice()`.

Для NC-only (`0x13`/`0x14` и т.д.) структуры отличаются порядком полей; см. `ProtocolV2T1.hpp:6002-6010` (`NcAsmSetParamAsmSeamless`: `68 <type> <changed> <total> <mode> <level>`) и `:6242` (`NcAsmSetParamNcOnOffAsmSeamless`).

### 5.3 Speak-to-Chat

Включение/выключение (GB `setSpeakToChatEnabled`, SB `BTNMODE_SET`, BL `sonySocketV2.js:479-481`): `f8 0c <0 = ON, 1 = OFF> 01`. Инверсия: **байт 0 = включено**. Совпадает во всех трёх.

Чувствительность и тайм-аут: GB `SPEAK_TO_CHAT_CONFIG_SET` (V1 `0xFC`, subtype `0x0C`), `<sensitivity> <timeout>`. libmdr: `DetectSensitivity` (AUTO=0, HIGH=1, LOW=2), `ModeOutTime` (FAST=0, MID=1, SLOW=2, NONE=3) (`ProtocolV2T1.hpp`).

### 5.4 Эквалайзер

Запрос: `56 00`. Ответ: `57 00 <presetId> <N> <band...>`. Установка: `58 00 <presetId> 00` для пресета; `58 00 A0 <N> <bass+off> <b1..bN+off>` для custom.

**Количество полос зависит от модели:**
- 6 полос (bass + 5): WH-1000XM4/5, WF-1000XM5, WH-CH720N, WF-C700N и т.д. — GB `SonyWH1000XM5Coordinator` (`EqualizerWithCustomBands`), SB `Constants.h:41`, BL `equalizerSixBands`. Данные `N = 0x06`.
- 10 полос: LinkBuds Clip (WF-LC900) — `band 10, step 13`, 8 пресетов (`docs/device-support/WF-LC900.md:73-76`).

**Смещение значений:**
- 6 полос: `bass` и каждая полоса = `value + 10` (`SB Constants.h:41`, GB `setEqualizerCustomBands`, libmdr `HeadphonesV2T1.cpp:958-967`: `bandSteps[0] - 10` = clear bass, `[1..5] - 10`; полосы 400 Гц, 1 кГц, 2.5 кГц, 6.3 кГц, 16 кГц).
- 10 полос: `value + 6` (`HeadphonesV2T1.cpp:968-…`: 31 Гц…; диапазон ±6, 13 шагов).

Пресеты (`EqPresetId`, `ProtocolV2T1.hpp`): `00` OFF, `01–07` ROCK, POP, JAZZ, DANCE, EDM, R&B/HIP-HOP, ACOUSTIC (**только libmdr**), `10` BRIGHT, `11` EXCITED, `12` MELLOW, `13` RELAXED, `14` VOCAL, `15` TREBLE, `16` BASS, `17` SPEECH (совпадает с SB `EQ_PRESET`). Для WH-1000XM6 и WF-LC900 в libmdr: HEAVY/CLEAR/HARD/SOFT = `0x30–0x33`, USER_SETTING1/2 и `CUSTOM = 0xA0` (`docs/device-support/WH-1000XM6.md`, `WF-LC900.md`). CUSTOM = `0xA0` совпадает с GB и SB.

### 5.5 DSEE (upscaling) и LDAC/режим соединения

- DSEE: GET `e6 01`, SET `e8 01 <0/1>` (SB `DSEE_GET/SET`, GB `setAudioUpsampling`, libmdr `AudioInquiredType::UPSCALING`). Совпадает.
- Приоритет «качество звука / стабильное соединение»: GET `e6 02`, SET `e8 02 <00|01>`. `00` = SOUND_QUALITY_PRIOR (LDAC), `01` = CONNECTION_QUALITY_PRIOR (SBC/стабильное). GB `setAudioLDAC` (`SonyProtocolImplV2.java:235-…`) — совпадает с libmdr `PriorMode`.
- На WH-1000XM5 FW 2.5.1 значения вне `CONNECTION_QUALITY_PRIOR` приходили в ответе, и валидация libmdr их отбрасывала. Патч `sony-headphones-linux/patches/libmdr-v2-connection-priority-validation.patch` снимает эту проверку.

### 5.6 Системные параметры (SYSTEM_*, F6/F7/F8)

`f6 <type>` GET, `f7` RET, `f8 <type> <v>` SET. Типы (`SystemInquiredType`, `ProtocolV2T1.hpp`):
- `0x01` PLAYBACK_CONTROL_BY_WEARING — pause when taken off (**по libmdr**; GB использует `26 01`, см. раздел 6)
- `0x03` ASSIGNABLE_SETTINGS — назначение кнопок (GB `button modes` `f8 03 02 <L> <R>`, `ambient button mode` `fa/fc 03 …`)
- `0x04` VOICE_ASSISTANT_SETTINGS (GB `f8 04 <mode>`)
- `0x08` CALL_SETTINGS, `0x09` RESET_SETTINGS (фабричный сброс; в GB `FACTORY_RESET_SET 0xF8` — проверить тип)
- `0x0A` AUTO_VOLUME — adaptive volume (GB `f8 0a 00/01`, SB `SUB_ADAPTIVE_VOLUME`, BL)
- `0x0C` SMART_TALKING_MODE_TYPE2 — Speak-to-Chat (GB/SB/BL `f8 0c`, раздел 5.3)
- `0x0D` QUICK_ACCESS (GB `f8 0d 02 <double> <triple>`)
- `0x0F` HEAD_GESTURE_ON_OFF, `0x10` HEAD_GESTURE_TRAINING (только libmdr, на XM5 отмечено как «advertised, not requested» в libmdr docs)

Практически все типы GB для PAUSE/AUTO_VOLUME/QUICK_ACCESS/VOICE_ASSISTANT совпадают с libmdr-типами — это хорошее подтверждение того, что GB использует те же opcode, только в старом именовании.

### 5.7 Автоотключение (APO)

- `26 04` — AUTO_POWER_OFF (таймер). Значения (`PowerRetParamAutoPowerOff`): 5/30/60/180 мин, off (`docs/device-support/WH-1000XM3.md`).
- `26 05` — AUTO_POWER_OFF_WEARING_DETECTION (GB `AutomaticPowerOffWhenTakenOff`, SB `APO_GET`, BL `sonySocketV2.js:890-932`: BL использует тот же `05` для «by time»). **Конфликт названия** (раздел 6).
- `28 05 <c0> <c1>` — установка (GB, SB). Пары кодов из `AutomaticPowerOff.getCode()`.

## 6. Расхождения между проектами

1. **Тип 0x05 у APO**: libmdr — `AUTO_POWER_OFF_WEARING_DETECTION`; BL — «by time» (`sonySocketV2.js:890-932`). Нужен захват.
2. **Pause when taken off**: GB `getPauseWhenTakenOff()` = `26 01`. libmdr: `26 01` = `LEFT_RIGHT_BATTERY` (POWER_GET_PARAM), а pause = `f6 01` (SYSTEM, `PLAYBACK_CONTROL_BY_WEARING`). Один из вариантов неверен, либо GB работает с другим набором на конкретных моделях. Проверить на WH-1000XM5 / WF-1000XM5.
3. **NC/ASM тип 0x15 против 0x17**: GB и SB используют `0x17` (с WNR в GB) и `0x15` для ряда моделей (GB `SonyProtocolImplV2.java:80`, `:90`). В libmdr `0x15` = `MODE_NC_ASM_AUTO_NC_MODE_SWITCH_AND_ASM_SEAMLESS`, `0x17` = `..._DUAL_NC_MODE_SWITCH...`. SB для v2 шлёт только `0x17`. Проверить по моделям.
4. **Структура NC/ASM 0x17 с WNR** (GB, 8 байт) против libmdr (7 байт). Для WNR-моделей GB добавляет байт `02/03`, которого нет в libmdr `NcAsmSetParam…` для `0x17`. Скорее всего отдельный тип для WNR; не установлено.
5. **Общая шина GB SYSTEM_CONTROL `0x96–0x99`** (`reboot = 98 00 16 01`, `connect two devices = 98 00 06 01`): libmdr назначает `0x98` = `ALERT_SET_PARAM`, и `0x96/0x97/0x99` в T1 не определены. Для GB `0x98` — это «commit». Что делает `98 00 06 01`/`98 00 16 01` в реальности, не подтверждено. **Не копировать** без захвата.
6. **SERVICE_LINK `0xB6–0xB9`** (GB): libmdr — `SAR_AUTO_PLAY_*`. Сопоставление «Auto Play / Link Auto Switch» по смыслу (`docs/device-support/WF-C700N.md`: оба ❌). Не выводить функцию из opcode.
7. **GENERAL_SETTING_1 (`d8 d1`)**: GB использует как WAT / connect-two-devices. libmdr: GS1 на WF-C700N = `MULTIPOINT_SETTING`, на XM3 = `ASSIGNABLE_KEY_SETTING` (`docs/device-support/WH-1000XM3.md`, Notes). Конфликт назначения, но GS2 (`d8 d2`) = TOUCH_PANEL в обоих совпадает.
8. **EQ-пресеты**: libmdr знает 0x01–0x07 (ROCK…ACOUSTIC) и 0x30–0x33; GB и SB их не знают. Для 6-полосного custom GB, SB, libmdr совпадают; для 10-полосного (LC900) только libmdr.
9. **EQ-смещение**: 6 полос `+10`, 10 полос `+6` (libmdr). GB/SB не знают про 10-полосный вариант; BL указывает «10 bands» по флагу `equalizerTenBands`, но в конфигах XM5/XM6 использует шесть (`budslink/src/.../deviceConfigs/WH-1000XM5.js`: `equalizerSixBands: true`).
10. **Speak-to-Chat (0x0C)**: в libmdr это `SMART_TALKING_MODE_TYPE2`; совпадение по значению, но имя другое. Нормально.
11. **Версия протокола**: BL читает со смещения 1 (`sonySocketV2.js:103-116`), libmdr и GB — со смещения 2 (libmdr `ConnectRetProtocolInfo`). Проверить на захвате.
12. **Выбор поколения**: GB по длине ответа, libmdr и SB по UUID (SB — сначала v1). Для Hush: UUID плюс проверка длины ответа.
13. **Конфликт с ohm-документацией LinkBuds S**: в ней описан внешний заголовок RFCOMM-payload (`0x40` + 4 байта «weird bytes» + BE16 длина) перед `3E…3C` (`docs/Devices/LinkBuds S.md`, `docs/Protocol/Specification.md`). В GB, libmdr и SB такого заголовка нет (`Message.java`, `Command.cpp`). Вероятно это артефакт захвата или другой транспорт (BLE/GATT). Для классического RFCOMM на Android проверить на собственном захвате.

## 7. Квирки по моделям

Сведено по `docs/device-support/*.md` (mos9527), конфигам GB, BL и SB.

| Модель | Поколение / транспорт | Подтверждение | Функции и квирки |
|---|---|---|---|
| WH-1000XM5 | v2, classic RFCOMM | FW 2.5.1, полный тест чтения/записи (`sony-headphones-linux/docs/COMPATIBILITY.md`) | Single-батарея (`22 00`). NC/ASM (`0x17`), Speak-to-Chat (`f8 0c`), Focus on Voice, ASM level 1–20 (записи подтверждены). EQ 6-полос (`58 00 A0 06…`). DSEE, Quick Access, Pause when taken off, auto power off, Voice Guidance ON/OFF. **Multipoint (`PERI_*`) не тестирован** (COMPATIBILITY.md). Connection priority (quality/stable) через AUDIO `0x02`, FW 2.5.1 отдаёт не-QUALITY значения → нужен patch. GB: `SonyWH1000XM5Coordinator.java` — батарея single, TODO по touch sensor, upsampling, button modes |
| WH-1000XM6 | v2 | FW 3.0.0 (`docs/device-support/WH-1000XM6.md`) | Speak-to-Chat ✅, Listening Mode ✅, Multipoint ✅, Capture Voice During a Phone Call ✅, Voice Guidance Volume ✅, NC/AMB с auto ambient level. EQ: только `0x30–0x33` (Heavy/Clear/Hard/Soft), `0xA0` (Custom) и user-settings; остальные пресеты отвергаются. GB: experimental, батарея и EQ «probably not working» (`SonyWH1000XM6Coordinator.java`) |
| WF-1000XM5 | v2 | `docs/device-support/WF-1000XM5.md` | L/R + case батарея (`22 09`/`22 0a` в BL/GB; в libmdr `23 01` LR). Touch Sensor Gesture ✅, Speak-to-Chat ✅, EQ ✅, Multipoint (device change) ✅, Voice Guidance Volume ✅. GB: experimental (NC не на 100%, громкость с наушников не работает). SB: «battery format differs» |
| WF-1000XM4 | v2 | SB: «v2 earbuds, battery format differs» | Есть в GB и BL. Подробностей по квиркам не нашёл |
| WF-C510 | v2 | `docs/device-support/WF-C510.md` (issue #5) | Speak-to-Chat ❌, Capture Voice ❌, Sound Pressure ❌. Touch ✅, Multipoint ✅, EQ ✅ |
| WF-C700N | v2, `0x03000015` | FW 2.0.5 и 2.0.7 (`docs/device-support/WF-C700N.md`) | **FW 2.0.5 принимает NC/ASM-запись, подтверждает её NTFY, но не меняет режим; FW 2.0.7 исправил** (раздел 8). DSEE ✅, Auto power off ✅ (по времени), Bluetooth connection quality ✅, Multipoint ✅, Fix playback device ✅. Не поддерживаются: Listening modes, Speak-to-Chat, Head Gesture, pause when removed, Adaptive Volume, Quick Access, Auto Play, Link Auto Switch. `AUTO_NCASM` объявлена, но не читается. GB: `SonyWFC700NCoordinator` |
| WF-L910 (LinkBuds Open) | v2 | FW 1.4.1 (`docs/device-support/WF-L910.md`) | Нет выбора playback device при 2 устройствах; Currently Playing работает только если аудио с другого устройства; «Ambient Sound Control» для tap отсутствует |
| WF-LS900N (LinkBuds S) | v2 | `docs/device-support/WF-LS900N.md` без таблицы; GB `SonyLinkBudsSCoordinator` (`preferServiceV2`); SB: v2 earbuds; BL `LinkBudsS.js` | Формат AmSC — см. `ohm …/docs/Devices/LinkBuds S.md`: `3E 0C ... 68 17 01 …` (совпадает с libmdr). Speak-to-Chat: `f8 0c` (`ohm`, LinkBuds S) |
| WF-LC900 (LinkBuds Clip) | v2, `0x03003015` | FW 2.0.3, захваты в `tests/WF-LC900-2.0.3*` (`docs/device-support/WF-LC900.md`) | **Нет NC/ASM, Speak-to-Chat, Head Gesture и Auto Power Off** (open-ear). Listening modes: `BGM_MODE_SMALL_MIDDLE_LARGE_AND_ERRORCODE`, `VOICE_CONTENTS`, `SOUND_LEAKAGE_REDUCTION` (взаимоисключающие). EQ и DSEE выключаются при активном listening mode (`EQEBB_NTFY_STATUS PRESET_EQ DISABLE`, `AUDIO_NTFY_STATUS UPSCALING DISABLE`) |
| WH-CH720N | v2 | Проверен SB (единственный «Verified» в SB), mos9527 issue #7 (`docs/device-support/WH-CH720N.md`) | Battery single, NC/ASM ✅, EQ ✅ (без выбора пресетов), Speak-to-Chat ❌, Touch ❌, Multipoint ~ (объявлен официально, не реализован), Power Off ✅, Capture Voice ? |
| ULT WEAR (WH-ULT900N) | v2 | SB «Verified», GB `preferServiceV2`, BL `Sony-ULT.js` | Подробностей по квиркам не нашёл |
| WH-XB910N, WH-CH520 | v2 | SB: «Expected» | Не проверено |
| WH-1000XM3 | **v1**, `0x4010` | FW 4.5.2 (`docs/device-support/WH-1000XM3.md`) | Для сравнения: `0x22` на v1 = POWER_OFF, на v2 = BATTERY (`Hush/docs/MDR_V1.md` раздел 7) |

## 8. Pitfalls

1. **Ответ может прийти раньше ACK на свой GET.** Sony шлёт `*_RET_*` до ACK на `*_GET_*` (XM3: `docs/device-support/WH-1000XM3.md` Notes). Если init ждёт ACK, RET теряется, и сессия отваливается после handshake (там же). Сопоставлять ответы по opcode, а не по порядку ACK.
2. **Один запрос в полёте.** libmdr и GB отправляют следующую команду только после ACK (`libmdr/src/HeadphonesV2.cpp` `SendCommandACK`; GB `SonyHeadphonesProtocol.java:464-481`, `pendingAcks`).
3. **SEQ меняется только по нашему ACK.** Нельзя менять исходящий SEQ, глядя на входящие кадры (Hush уже так делает, `Hush/docs/MDR_V1.md` раздел 7). libmdr: `HandleAck` (`Headphones.cpp:316-325`) принимает только `seq == 1 - mSeqNumber`, остальное «stale ACK» и игнорирует.
4. **Входящий COMMAND-кадр ACK-ается сразу**, до разбора payload (GB `SonyHeadphonesProtocol.java:183`; libmdr `Headphones.cpp:` `case DATA_MDR: SendACK(seq)` перед `Handle…`).
5. **Table 1 до CONNECT_RET_PROTOCOL_INFO нельзя.** libmdr бросает ошибку «before CONNECT_RET_PROTOCOL_INFO» (`Headphones.cpp:` `Handle`, ветка DATA_MDR). То же для T2.
6. **Таймауты и повторы**: GB задерживает connect на 500 мс, потому что «connecting too fast fails with IOException» (`SonyHeadphonesSupport.java:87-89`), и повторяет init 2 раза (`:60`). libmdr использует `Await(type, timeoutMS)` (`Details.hpp:278`).
7. **Поток: плохой кадр не должен съедать следующий.** libmdr при BAD_CHECKSUM и BAD_OTHER удаляет до конца буфера (`Headphones.cpp:219-…`, `MoveNext`). SB PR #6 (упомянут в `Hush/docs/MDR_V1.md`): голый `3E` перезапускает парсер.
8. **Записи, которые устройство «принимает», но не выполняет.** WF-C700N FW 2.0.5: NC/ASM SET подтверждается NTFY с запрошенным состоянием, но режим не меняется (`docs/device-support/WF-C700N.md`, Notes). Чинится FW 2.0.7. **Проверка записи только по ACK/NTFY недостаточна**, нужно читать состояние (GET) или слушать событие на физическом изменении.
9. **EQ-пресет и полосы в одном кадре нельзя.** Для пресета полосы не передаются, иначе кадр отвергается (`docs/device-support/WF-LC900.md:78-79`; для XM4 в V1 такой же случай, `Hush/docs/MDR_V1.md` раздел 7). Для 10-полосного EQ порядок: сначала пресет CUSTOM `0xA0`, затем полосы.
10. **EQ и DSEE выключаются при listening mode** (LC900). После смены режима надо явно запросить `EQEBB` и `AUDIO` (`docs/device-support/WF-LC900.md`, Notes; libmdr `applying a listening mode asks for both statuses`).
11. **LE Audio блокирует функции**: T1 флаги `*_CANT_BE_USED_WITH_LEA_CONNECTION` (`0x45–0x4F` в `ProtocolV2T1.hpp` и BL `FunctionTypeV2T1`). Если устройство на LE Audio, эти функции не показывать.
12. **Connection priority на XM5 FW 2.5.1**: значения, отличные от CONNECTION_QUALITY_PRIOR, вызывали ошибку валидации. Нужно принимать `SOUND_QUALITY_PRIOR`/`CONNECTION_QUALITY_PRIOR`/`LOW_LATENCY_PRIOR_BETA` (`sony-headphones-linux/patches/libmdr-v2-connection-priority-validation.patch`).
13. **Ответ на некоторые GET не приходит никогда**: INSTRUCTION_GUIDE, COMMON_GET_UPSCALING_EFFECT и часть UPDT_GET_PARAM (XM3, `docs/device-support/WH-1000XM3.md`). Не ждать на них в init.
14. **Multipoint/PERI**: `PERI_SET_EXTENDED_PARAM` (`0x3C`) с MAC. Подтверждён только WF-C700N/WF-1000XM5 (BL, libmdr). Для XM5 multipoint не тестирован (COMPATIBILITY.md).
15. **Battery threshold-варианты** (`08/09/0A`) имеют дополнительные байты; GB читает `0x09` как DUAL с фиксированной длиной. Проверять длину кадра, иначе сдвиг.
16. **Прошивка**: одинаковый opcode может меняться по FW (WF-C700N 2.0.5 vs 2.0.7; XM6 3.0.0 отвергает часть EQ-пресетов). Логировать `CONNECT_GET_DEVICE_INFO`.

## 9. Что из этого следует для Hush

- Для v2 надо брать фрейм из libmdr (тот же, что у Hush v1), handshake из `libmdr/src/HeadphonesV2.cpp`, структуры из `ProtocolV2T1.hpp` / `ProtocolV2T2.hpp` (MIT, можно с атрибуцией).
- Тестовые захваты для юнит-тестов: `mos9527/SonyHeadphonesClient/tests/WF-1000XM5-6.1.0/`, `tests/WF-LC900-2.0.3*/`, `tests/WH-1000XM3-4.5.2/`. Это MIT, но в захватах есть MAC и метаданные; проверять `tooling/scrub-capture.py`, как в README тестов.
- Сверка: для каждой команды сверить opcode и payload с libmdr и GB; расхождения из раздела 6 не реализовывать, пока не будет захвата.
- Не копировать код из GB (AGPL) и BL (GPL). Для сверки использовать только факты протокола.

Отчёт не проверен на железе. Все утверждения основаны на исходниках и документации перечисленных проектов.
