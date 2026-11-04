# Sony MDR v2: что делает Sound Connect (декомпиляция)

Метка: **[код]** — подтверждено чтением декомпилированного кода (путь относительно `jadx-output/sources/`). **[вывод]** — логический вывод, не проверено на железе. **[нет]** — не удалось определить по коду.

Корень v2 в jadx: `com/sony/songpal/tandemfamily/message/mdr/v2/` (227 файлов). Инициализация v2: `vv/e.java`. Выбор версии: `com/sony/songpal/mdr/platform/connection/connection/h1.java`.

Приложенные таблицы (Приложение A–C) сгенерированы скриптом из `Command.java`, `FunctionType.java` и `*InquiredType.java`, то есть это полные данные, а не выборка.

---

## 1. Как Sound Connect выбирает v1 или v2

### 1.1 Выбор идёт по Bluetooth-сервису, а не по ответу на handshake

- `lm0/i.java`: два набора UUID.
  - **TABLE_SET_1 (v1)** (`lm0/i.f68592a`): `a()` = `96CC203E-5068-46ad-B32D-E316F5E069BA`, `b()` = `BA69E0F5-16E3-2DB3-ad46-68503E20CC96`.
  - **TABLE_SET_2 (v2)** (`lm0/i.f68593b`): `a()` = `956C7B26-D49A-4BA8-B03F-B17D393CB6E2`, `b()` = `E2B63C39-7DB1-3FB0-A84B-9AD4267B6C95`.
  - `lm0/i.f68594c = [v2, v1]`. Порядок проверки: сначала v2. [код]
  - `lm0/i.a()` = `443cce33-e85d-4b85-8d53-6e319ede53ae`. Нигде, кроме определения, не используется. [код] Назначение неизвестно [нет].
- Сопоставление с устройством: `ep0/a.java` (строки 19–60). Берётся `BluetoothDevice.getUuids()` и сравнивается с `a()`/`b()` каждого набора. [код]
- Выбор таблицы: `com/sony/songpal/mdr/platform/connection/connection/l1.java:44-48`. Если найден `f68592a` → `TABLE_SET_1`, иначе `TABLE_SET_2`. Классы-фабрики: `om0/c.java:19` (SET_1) и `om0/d.java:19` (SET_2, `a()` возвращает UUID v2). [код]
- Запуск инициализации: `h1.java:259-267`. `TABLE_SET_1` → `tv.d.g(...)` (v1, см. `docs/MDR_V1.md` раздел 2). Иначе → `vv.e.m(...)` (v2). [код]
- RFCOMM-сокет создаётся в `ep0/c.java:38`: `remoteDevice.createRfcommSocketToServiceRecord(eVar.a())`, где `eVar.a()` — UUID выбранного набора (v1 или v2). Для TABLE_SET_2 менеджер соединения собирается в `y0.java:206` (`pm0.b` + `ph.m`). [код]

**Следствие для Hush.** Hush подключается только к UUID v1 (`hush/core/.../mdr/MdrConnection.kt:57`, `createRfcommSocketToServiceRecord(MDR_UUID)` на строках 187–188). Для v2-наушников Hush получает либо отказ сокета, либо ответ другой длины. Сейчас Hush определяет v2 по длине ответа (`v1/Parser.kt:48-49`, 8 байт). Sound Connect определяет v2 раньше, по UUID, а длину уже не проверяет. [вывод] Для v2 надо подключаться к `956C7B26-…` первым. Какой UUID отдают реальные WH-1000XM5 и WF-1000XM4, не проверено [нет].

### 1.2 Проверка версии протокола

- `vv/e.java:596-605` (`Q(in0.k)`): если версия (4 байта) не в списке `f84924b`, выставляется `UNAVAILABLE_PROTOCOL_VERSION`. [код]
- Допустимые версии (`vv/e.java:63`, 76 значений, все в hex):

```
01000000 02000000 02000001 02001000 03000000 03000001 03000002 03000003 03000004 03000005
03000006 03000007 03000008 03000009 03000010 03000011 03000012 03000013 03000014 03000015
03000016 03000017 03000018 03000019 03000020 03001001 03001002 03001003 03001004 03002000
03002001 03002002 03002003 03002004 03002005 03002006 03002007 03002008 03002009 03002012
03002013 03002014 03002015 03002016 03002017 03002018 03002019 03003000 03003001 03003002
03003003 03003004 03003005 03003006 03003007 03003008 03003009 03003010 03003011 03003012
03003013 03003014 03003015 03003016 03003017 03003018 03003019 03003020 03003021 03003023
03003024 03003025 03003026 03003032 03003033 03003037
```

- Другие предикаты по версии (`vv/e.java`):
  - `M(v)`: `v == 0x03999999` или старший байт = `0x04`. Если да, capability идёт через кэш (`N0(p(new a()))`), иначе `i0()` (`CONNECT_GET_CAPABILITY_INFO`). [код]
  - `k(v)` (`:6693`, лог: `isTutorialContentsSelectOnConciergeSupport`): ложь при `v < 0x03001004` или `v ∈ {0x03002013, 0x03002014}`. Определяет, нужен ли `DEVICE_INFO INSTRUCTION_GUIDE`. [код]
  - `l(v, funcs)`: `v > 0x03002014` и нет `TUTORIAL_CONTENTS_SELECT_ON_CONCIERGE`. [код]
  - `j(v)` (`:6683`, список `f84925c`: `0x03000010-13, 0x03000018, 0x03001004, 0x03002000`): передаётся вторым аргументом в `aVar.b(version, j(version))` (`vv/e.java:637`, `:1570`). Смысл флага не выяснен [нет].

### 1.3 Транспорт и BLE/GATT

- Основной транспорт: RFCOMM, см. 1.1.
- `ph/m.java` импортирует `BluetoothGatt`. Назначение (GATT-туннель для tandem или BLE setup) не разобрано [нет]. В командах есть функции `GATT_CONNECTABLE` (`FunctionType`, код см. Приложение B), `DISCONNECT_TANDEM_GATT_REQUEST`, status `TANDEM_OVER_GATT_CONNECTED` (`audio/param/StatusErrorCodeType`, Приложение C). [код]
- LE Audio: `TWS_SUPPORTS_LEA_UNI_LEA_BROAD`, `LE_AUDIO_CONNECTION_MODE`, `LE_AUDIO_SWITCH_SUPPORTED_COMPATIBILITY` и группа `table2/lea`. [код]

---

## 2. Формат кадра

### 2.1 Кадр на проводе совпадает с v1 и с Hush

Код общий для v1 и v2 (`qm0/b.java`, `qm0/a.java`, `cp0/b.java`, `cp0/f.java`, `lm0/c.java`). [код]

| Поле | Размер | Описание |
|---|---|---|
| START | 1 | `0x3E` |
| type (DataType) | 1 | см. таблицу типов ниже |
| seq | 1 | 0 или 1, переключается; ACK подтверждает тот же seq |
| len | 4 | длина payload, big-endian (`e.i(bArr.length)`, `qm0/b.java:69`) |
| payload | len | |
| sum | 1 | `(Σ байт от type до конца payload) & 0xFF` (`cp0/f.java`: сумма, `update` берёт `& 255`) |
| END | 1 | `0x3C` |

- Экранирование внутри кадра (`cp0/b.java`, константы `qm0/e.java`): `0x3C → 3D 2C`, `0x3D → 3D 2D`, `0x3E → 3D 2E`. То есть `0x3D` + `(b & ~0x10)`. [код]
- ACK без payload: `3E 01 seq 00 00 00 00 seq+1 3C` (`qm0/b.java:32-33`: для seq 0 это `3E 01 00 00 00 00 00 01 3C`). [код]
- Hush `core/.../mdr/Frame.kt` делает то же самое (START/END/ESC, sum8, `ack(seq)`). [код] Значит, кадры v2 можно разбирать существующим `Frame.kt` без изменений.

### 2.2 Типы кадров (DataType)

`com/sony/songpal/tandemfamily/message/DataType.java`. Коды `BSON.*` в jadx развёрнуты в числа (стандартные BSON-значения).

| DataType | код | ACK | Где используется |
|---|---|---|---|
| DATA_MDR | 0x0C | да | v2 table1 (`table1/c.java` `b()`) |
| DATA_MDR_NO2 | 0x0E | да | v2 table2 (`table2/c.java:589`) |
| ACK | 0x01 | нет | подтверждение |
| SHOT_MDR | 0x1C | нет | одноразовые сообщения без подтверждения |
| LARGE_DATA_MDR | 0x2C | да | большие данные (прошивка) |
| DATA_COMMON / SHOT_COMMON | 0x0D / 0x1D | | общие данные |

### 2.3 Повтор и подтверждение

`lm0/c.java:145-190` (`n(DataType, payload, timeout, retries)`): отправка с текущим seq, ожидание ACK с таймаутом, повтор до `retries` раз, затем `IOException("Remote endpoint does not respond to message.")`. Проверка ACK: `lm0/c.java:55-75`, неверный seq логируется. [код]

Отличий v2 от v1 на уровне кадра не найдено. [код]

### 2.4 Что отличается на уровне payload

- Первый байт: opcode (`Command`, Приложение A). Второй байт: inquiry type группы (например, `NcAsmInquiredType`). Это подтверждено проверками вида `bArr[0] == cmd && bArr[1] == type` (`in0/c.java:26`, `in0/k.java:26`, `sn0/u.java:7`).
- `CONNECT_RET_PROTOCOL_INFO` длиной 8 байт (v1: 4 байта, `[01][00][hi][lo]`). См. раздел 4.1.
- Диспетчеризация: RET/NTFY-классы группы сначала читают `bArr[1]` (inquiry type), затем выбирают подкласс через `switch` на enum (пример: `sn0/i0.java`, `sn0/x0.java`). Подкласс отвечает за конкретное поле (`sn0/t0.java` = NC_ON_OFF, `sn0/k0.java` = ASM_SEAMLESS). Общая схема для статусов: `[cmd][type][valueChangeStatus][value...]` (`sn0/k1.java:29-31`, `sn0/i0.java:77`). [код]

Пример: NC on/off (`un0/n.java`): `length == 5`, `bArr[1] == NC_ON_OFF`, значение в `bArr[4]` (`NcAsmOnOffValue`). ASM seamless (`un0/e.java`): `length == 6`, `bArr[1] == ASM_SEAMLESS`, режим в `bArr[4]` (`AmbientSoundMode`), уровень `u8(bArr[5])`. [код]

---

## 3. Команды v2 по группам

Полная таблица — Приложение A (`table1` + `table2`, 243 строки, каждая с файлом класса). Для каждого opcode указан класс `*_GET_*`/`*_RET_*`/`*_SET_*`/`*_NTFY_*` в `mdr/v2/…` или в пакете с обфусцированным именем (`in0/`, `hn0/` и т.д.).

Общие замечания:
- `GET_TEST` (0x0F), `COMMON_GET_CAPABILITY` (0x10), `COMMON_RET_CAPABILITY` (0x11) и `COMMON_GET_STATUS` (0x12) в Java записаны как `BSON.*`. Значения 15, 16, 17, 18 взяты из стандартной BSON-таблицы. [вывод]
- `_NTFY_` приходит от наушников сам, без запроса (`*_NTFY_STATUS`, `*_NTFY_PARAM`).
- Для SET: request формирует `super.f(type, ...)` в базовом классе группы (`sn0/z0`, `sn0/k1`).

Группы (таблица 1, `table1/Command.java`):

| Группа | Opcodes | Назначение | Ключевые классы |
|---|---|---|---|
| CONNECT | 00–07 | protocol info, capability info, device info, support function | `in0/c,k,a,e,b,f,d,l` |
| COMMON | 0F–19 | concierge, connection status, audio codec, upscaling effect, BLE setup, established time, device special mode, phone+device info, reconnection, display FW | `hn0/a..t`, `go0/a` |
| POWER | 20–29 | battery (total, L/R, cradle, thresholds), power off, auto power off (+wearing), power save, link control, battery safe mode, caring charge, stamina, auto touch backlight off | `yn0/a..d,z0,u0,i,b0`, `x/b`, `g0/b` |
| UPDT | 30–3F | FW update state, params, transfer | `ho0/*` |
| LEA | 40–4D | LE Audio state and params | `nn0/*`, `g/b` |
| EQEBB | 50–5B | preset/custom EQ, EBB, sound effect, ULT button assign | `jn0/*`, `o0/b`, `s/b`, `f0/b`, `r0/b` |
| NCASM | 60–69 | NC on/off, NC mode (dual/single/auto), ASM on/off and level, NC/AMB toggle, NC test mode | `sn0/*`, `un0/n`, `un0/e` |
| SENSE | 70–7B | adaptive sound control (ext info) | `ao0/*`, `h/b`, `table1/d.java` |
| OPT | 80–89 | NC optimizer (personal/barometric), TV sound booster | `vn0/*` |
| ALERT | 90–99 | fixed/flexible message, vibrator alert, voice assistant, LE Audio alert | `en0/*` |
| PLAY | A0–A9 | playback control, music/call volume, mute, play mode | `wn0/*` |
| SAR_AUTO_PLAY | B0–B9 | SAR, auto play | `zn0/*` |
| LOG | C4, C9 | action log | `qn0/a,e` |
| GENERAL_SETTING | D0–D9 | general settings 1–4 (list) | `ln0/*` |
| AUDIO | E0–E9 | connection mode, upscaling (DSEE), BGM mode, upmix cinema/series, voice contents, sound leakage reduction, listening option | `fn0/*`, `table1/d` |
| SYSTEM | F0–FD | quick access, assignable settings, smart talking mode, voice assistant settings, wearing detector, earpiece selection, call settings, reset, auto volume, face tap test | `do0/*`, `h1/b`, `z/b` |

Table 2 (`table2/Command.java`, `DATA_MDR_NO2`):

| Группа | Opcodes | Назначение |
|---|---|---|
| CONNECT | 06, 07 | support function (table2) — `jo0/a,b` |
| POWER | 20–29 | power (table2) — `qo0/a` и др. |
| PERI (peripheral) | 30–3D | pairing device management, peripheral params |
| VOICE_GUIDANCE | 40–4B | voice guidance (status/params/ext) |
| SAFE_LISTENING | 50–5B | safe listening (`table2/safelistening/type`) |
| LEA | 60–69 | LE Audio (table2) |
| PARTY | 70–7D | party (DJ control, illumination, auracast, karaoke, Live Karaoke) |
| SYSTEM | F0–FF | system (table2), extended params |

Полный перечень opcode → класс: Приложение A.

Ключевые payload-структуры, проверенные по коду:

- **Battery** (`POWER_*`, inquiry `BATTERY=0`, `LEFT_RIGHT_BATTERY=1`, `CRADLE_BATTERY=2`, `BATTERY_WITH_THRESHOLD=8`, `LR_BATTERY_WITH_THRESHOLD=9`, `CRADLE_BATTERY_WITH_THRESHOLD=10`): классы в `yn0/`. Точные байты не разобраны [нет].
- **NC/ASM** (см. 2.4): NC_ON_OFF=1, ASM_SEAMLESS=34, NC_AMB_TOGGLE=48, NC_TEST_MODE=64 (`sn0/t0` и `un0/n`, `un0/e`).
- **Power off / auto power off**: POWER_OFF=3, AUTO_POWER_OFF=4, AUTO_POWER_OFF_WEARING_DETECTION=5, POWER_SAVE_MODE=6 (`power/param/PowerInquiredType`). Классы — `yn0/*`. [нет точной раскладки]
- **EQ**: PRESET_EQ=0, EBB=1, PRESET_EQ_NONCUSTOMIZABLE=2, PRESET_EQ_AND_ULT_MODE=3, PRESET_EQ_AND_ERRORCODE=4, SOUND_EFFECT=48, CUSTOM_EQ=49, TURN_KEY_EQ=50, CUSTOMIZABLE_SOUND_EFFECT_*=51–54, ULT_BTN_SOUND_EFFECT_ASSIGN=64. [код]
- **Playback**: PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT=1, …_AND_FUNCTION_CHANGE=2, PLAYBACK_CONTROL_WITH_FUNCTION_CHANGE=3, MUSIC_VOLUME=32, CALL_VOLUME=33, MUSIC_VOLUME_WITH_MUTE=48, CALL_VOLUME_WITH_MUTE=49, PLAY_MODE=64.
- **Audio**: CONNECTION_MODE=0, UPSCALING=1, CONNECTION_MODE_WITH_LDAC_STATUS=2, BGM_MODE=3, UPMIX_CINEMA=4, CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO=5, VOICE_CONTENTS=6, SOUND_LEAKAGE_REDUCTION=7, LISTENING_OPTION_ASSIGN_CUSTOMIZABLE=8, BGM_MODE_AND_ERRORCODE=9, UPMIX_SERIES=10, UPSCALING_AUTO_OFF_WITH_STATUS_DISABLE_REASON=11.
- **Upscaling** (`common/param/UpscalingEffectType`, `audio/param/UpscalingType`): DSEE_HX=0, DSEE=1, DSEE_HX_AI=2, DSEE_ULTIMATE=3. Авто-выключение: OFF=0, AUTO=1.
- **Connect device info**: `DeviceInfoType`: MODEL_NAME=1, FW_VERSION=2, SERIES_AND_COLOR_INFO=3, INSTRUCTION_GUIDE=4. Запрос `[04][type]` (`in0/b.java`), ответ `[05][type][строка]` (`in0/f.java` диспетчеризует на `i.b` для строки модели и `g.b` для FW).

Остальные payload-раскладки (battery, EQ-значения, playback, etc.) — в классах по таблице Приложения A. Их разбор занимает много времени, и для Hush они нужны по одному, по мере реализации фич.

---

## 4. Discovery функций и init

### 4.1 Protocol info (`in0/c`, `in0/k`)

- Запрос: `[00][00]` (`ConnectInquiredType.FIXED_VALUE = 0`, `in0/c.java:26`). [код]
- Ответ (8 байт, `in0/k.java:16-26`): `[01][00][v3][v2][v1][v0][e6][e7]`.
  - `v` = 4 байта, big-endian (`util/e.g`: `bArr[i]<<24 | …`, `com/sony/songpal/util/e.java:51`). Проверка по списку из 1.2.
  - `e6` и `e7` = `EnableDisable` (ENABLE=0, DISABLE=1, OUT_OF_RANGE=-1).
  - `e6 == ENABLE` → в init запрашивается table1 support function (`vv/e.java:603`, поле `f84938e`).
  - `e7 == ENABLE` → в init запрашивается table2 support function (`f84939f`).

### 4.2 Support function (`in0/d`, `in0/l`; table2 `jo0/a,b`)

- Запрос table1: `[06][00]` (`in0/d.java`). Ответ: `[07][00][N][(code_i, value_i) × N]`. Длина = `3 + 2N` (`in0/l.java`: проверка `bArr.length == bArr[2]*2+3`). [код]
- `code_i` = `FunctionType` (таблица `NO_1`), `value_i` = байт (смысл не выяснен [нет]; сохраняется в `cn0.c.b()`). Записи с `NO_USE` пропускаются (`in0/l.java:c()`).
- Table2: `jo0/a` (запрос), `jo0/b` (ответ). Формат аналогичен; классы — в таблице A.
- Полный список `FunctionType` (код → имя), 193 значения: Приложение B.

### 4.3 Что запрашивается после support function

`vv/e.java call()`, строки 1210–1600. Цепочка `if (funcs.contains(FunctionType.X)) requestY()`. Основные соответствия (подтверждено по строкам):

| FunctionType | запрос (inquiry или метод) |
|---|---|
| NOISE_CANCELLING_ONOFF_AND_AMBIENT_SOUND_MODE_ONOFF | `v0(NcAsmInquiredType.NC_ON_OFF_AND_ASM_ON_OFF)` |
| …_LEVEL_ADJUSTMENT | `v0(NC_ON_OFF_AND_ASM_SEAMLESS)` |
| NC_DUAL_SINGLE_OFF_AND_ASM_ONOFF | `v0(NC_MODE_SWITCH_AND_ASM_ON_OFF)` |
| AMBIENT_SOUND_MODE_ONOFF / LEVEL_ADJUSTMENT | `v0(ASM_ON_OFF)` / `v0(ASM_SEAMLESS)` |
| AMBIENT_SOUND_CONTROL_MODE_SELECT | `v0(NC_AMB_TOGGLE)` |
| MODE_NC_ASM_… (auto/dual/single) | `v0(MODE_NC_ASM_…)` (варианты NC-mode switch) |
| PRESET_EQ / PRESET_EQ_NON_CUSTOMIZABLE / PRESET_EQ_AND_ULT_MODE / EBB / SOUND_EFFECT / CUSTOM_EQ / PRESET_EQ_AND_ERRORCODE | `o0(EqEbbInquiredType.…)` |
| CUSTOMIZABLE_SOUND_EFFECT | `n0(CUSTOMIZABLE_SOUND_EFFECT_SELECT)` |
| ULT_SOUND_EFFECT_ASSIGN | `n0(ULT_BTN_SOUND_EFFECT_ASSIGN)` |
| NC_OPTIMIZER_PERSONAL / _BAROMETRIC / _PERSONAL_BAROMETRIC | `o(OptInquiredType…)` через `wv` |
| TV_SOUND_BOOSTER | `w0(OptInquiredType.TV_SOUND_BOOSTER)` |
| PLAYBACK_CONTROLLER_* | `y0/z0(PlayInquiredType.…)` |
| SAR / AUTO_PLAY / INTEGRATED_AUTO_PLAY / GATT_CONNECTABLE | `D0()` / `g0()` / `s0()` / `q0()` |
| UPSCALING_AUTO_OFF(_WITH_STATUS_DISABLE_REASON) | `Y(AudioInquiredType.UPSCALING[…])` |
| BGM_MODE_SMALL_MIDDLE_LARGE(_AND_ERRORCODE) | `Z()` / `a0(BGM_MODE_AND_ERRORCODE)` |
| CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO | `j0()` |
| UPMIX_CINEMA / UPMIX_SERIES / VOICE_CONTENTS / SOUND_LEAKAGE_REDUCTION / LISTENING_OPTION_ASSIGN_CUSTOMIZABLE | `d0()` / `e0()` / `f0()` / `c0()` / `b0()` |
| AUDIO_360_UPMIX_OPTIMIZATION / _HEADTRACKING | `M0()` / `L0()` |
| SMART_TALKING_MODE_TYPE1 / TYPE2 / ASSIGNABLE_SETTING(_WITH_LIMITATION) | `F0(SystemInquiredType.…)` |
| VOICE_ASSISTANT_SETTINGS | `H0()` |
| WEARING_STATUS_DETECTOR | `I0()` |
| QUICK_ACCESS | `C0()` (с проверкой `n0`) |
| EARPIECE_SELECTION | `m0()` |
| BLE_SETUP | `h0()` |
| DISPLAY_FW_VERSION | `l0()` |
| PAIRING_DEVICE_MANAGEMENT_* | `x0(PeripheralInquiredType.…)` |
| VOICE_GUIDANCE_* | `d1/e1(VoiceGuidanceInquiredType…)` |
| LIGHTING_DEFAULT_COLOR | `Q0()` |
| LE_AUDIO_* / QUICK_ACCESS_CANT_BE_USED_WITH_LEA_CONNECTION | `t0`, `u0` (table2 LEA), `N0(this.f84935b.h())` |
| DJ_CONTROL / DJ_CONTROL_WITH_STATUS_DISABLE_REASON | `W(PartyInquiredType.…)` |

Сама цепочка `if` длиннее таблицы; остальные условия читаются из `vv/e.java:1420–1600`. [код]

---

## 5. Порядок init, который отправляет Sound Connect

Порядок из `vv/e.java call()` (1210–1260 и 1420–1600):

1. `CONNECT_GET_PROTOCOL_INFO [00 00]` → `CONNECT_RET_PROTOCOL_INFO` (`B0()`). Если версия не в списке → `UNAVAILABLE_PROTOCOL_VERSION` (`Q`, `:596`).
2. Capability: `CONNECT_GET_CAPABILITY_INFO [02 00]` (`i0()`) → `CONNECT_RET_CAPABILITY_INFO`. Если `M(version)` — путь через кэш (`N0(p(new a()))`).
3. `CONNECT_GET_DEVICE_INFO`: `[04 01]` MODEL_NAME, `[04 02]` FW_VERSION, `[04 03]` SERIES_AND_COLOR_INFO, и `[04 04]` INSTRUCTION_GUIDE если `!k(version)` (`:1228-1234`), или если `l(version, funcs)`.
4. `CONNECT_GET_SUPPORT_FUNCTION` table1 `[06 00]` (`E0`) — если `byte6 == ENABLE`; table2 `jo0.a` — если `byte7 == ENABLE`.
5. Для каждой функции из `Set<FunctionType>`: цепочка из 4.3. Большая часть запросов — `*_GET_CAPABILITY` / `*_GET_STATUS` / `*_GET_PARAM` (`0x??` четной пары).
6. Чтение текущих значений: отдельные `GET_STATUS`/`GET_PARAM` на каждую функцию, после чего приложение ждёт ответы `RET_*` (`CallableC1079e.K/...`). [код частично; точный порядок чтения статусов не разбирал полностью — [нет]]

Отдельные детали, которые нужны для повторного подключения: `wv.a/b/c` (capability store, `tableset2`) — хранит ответы и делает `CAPABILITY_INFO` по счётчику (`vv/e.java:582-595`). [код]

---

## 6. План для Hush

### 6.1 Что переиспользовать из v1

- `core/mdr/Frame.kt`: кадр v2 идентичен, менять не нужно.
- `core/mdr/Request.kt` (Request, Table, Reader): используется как есть.
- `core/mdr/Model.kt`, `Events.kt`: часть типов общая (NcAsmState, Charge, AssignableKey, EqState). Новые типы добавляются рядом.
- `v1/Init.kt`: структура (handshake → info → support → per-function) та же, отличаются только запросы и функции.
- `MdrConnection.kt`: сокет, таймаут, ACK-цикл.

### 6.2 Что добавить

1. `core/mdr/v2/` (новый пакет):
   - `v2/Commands.kt`: enum `Command2Table1` и `Command2Table2` со значениями из Приложения A (таблица 1 и 2); `DataType` для table2 = 0x0E.
   - `v2/FunctionType.kt`: из Приложения B, с разделением по `NO_1`/`NO_2` (коды пересекаются между таблицами, поэтому таблица обязательна).
   - `v2/InquiredTypes.kt`: enum-ы из Приложения C (NcAsm, Common, Power, Eq, Play, Audio, Alert, Connect, DeviceInfo, и т.д.).
   - `v2/Requests.kt`: builders по группам. Формат `[cmd][inquiry]…`, для SET — `[cmd][inquiry][valueChangeStatus][value…]` (см. 2.4).
   - `v2/Parser.kt`: `ProtocolInfo` (8 байт), `SupportFunction` (3+2N), `DeviceInfo`, `CapabilityInfo` (строка), плюс статусы по группам.
   - `v2/Init.kt`: `plan(version, table1Functions, table2Functions)` по разделу 5.
2. `MdrConnection.kt`: сначала пробовать `956C7B26-D49A-4BA8-B03F-B17D393CB6E2`, при неудаче — `96CC203E-…`. Выбранный UUID определяет парсер (v1 или v2), а не длина ответа.
3. `HeadphonesController.kt:430-440`: убрать ранний выход на `protocolV2` и передать управление v2-init. `protocolV2` в `UiState` оставить для сообщения об ошибке, если версия не в списке.
4. `v1/Parser.kt:48-49`: ветка на `size == 8` убирается, потому что выбор версии идёт по UUID.

### 6.3 Что нужно проверить

- Нет захвата v2 из-за пределов Sound Connect. Тесты на декодирование писать нельзя, пока не появится дамп (например, через `btsnoop`, сохранённый при подключении WH-1000XM5 к Sound Connect). Решение о тестах — за владельцем.
- UUID, которые реально отдают WH-1000XM5/WF-1000XM4, не сверял. Это надо проверить на устройстве (`BluetoothDevice.getUuids()`).
- Значения `value_i` в support function, точные раскладки батареи и power off — надо разобрать по классам из Приложения A перед реализацией.
- Для GATT-канала (tandem over GATT) по коду не видно, как упакованы кадры. [нет]
- Надёжность: в v1 `tv/d.java` ответ на RET_PROTOCOL_INFO приходит раньше ACK от GET (см. `docs/MDR_V1.md` раздел 4). Для v2 надо проверить то же самое.

---

## Приложение A. Команды v2 (opcode → класс)

`table` — 1 = `DATA_MDR`, 2 = `DATA_MDR_NO2`. Путь — относительно `jadx-output/sources/`. Префикс `com/sony/songpal/tandemfamily/message/` сокращён до `mdr/v2/…` там, где это класс из пакета v2.

| table | opcode | command | payload class (jadx path, under sources/) |
|---|---|---|---|
| 1 | 0x00 | CONNECT_GET_PROTOCOL_INFO | `in0/c.java` |
| 1 | 0x01 | CONNECT_RET_PROTOCOL_INFO | `in0/k.java` |
| 1 | 0x02 | CONNECT_GET_CAPABILITY_INFO | `in0/a.java` |
| 1 | 0x03 | CONNECT_RET_CAPABILITY_INFO | `in0/e.java` |
| 1 | 0x04 | CONNECT_GET_DEVICE_INFO | `in0/b.java` |
| 1 | 0x05 | CONNECT_RET_DEVICE_INFO | `in0/f.java` |
| 1 | 0x06 | CONNECT_GET_SUPPORT_FUNCTION | `in0/d.java` |
| 1 | 0x07 | CONNECT_RET_SUPPORT_FUNCTION | `in0/l.java` |
| 1 | 0x0F | GET_TEST | `go0/a.java` |
| 1 | 0x10 | COMMON_GET_CAPABILITY | `hn0/a.java` |
| 1 | 0x11 | COMMON_RET_CAPABILITY | `hn0/j.java` |
| 1 | 0x12 | COMMON_GET_STATUS | `hn0/b.java` |
| 1 | 0x13 | COMMON_RET_STATUS | `hn0/n.java` |
| 1 | 0x15 | COMMON_NTFY_STATUS | `hn0/i.java` |
| 1 | 0x18 | COMMON_SET_PARAM | `hn0/t.java` |
| 1 | 0x19 | COMMON_NTFY_PARAM | `hn0/c.java` |
| 1 | 0x20 | POWER_GET_CAPABILITY | `yn0/a.java` |
| 1 | 0x21 | POWER_RET_CAPABILITY | `x/b.java` |
| 1 | 0x22 | POWER_GET_STATUS | `yn0/c.java` |
| 1 | 0x23 | POWER_RET_STATUS | `g0/b.java` |
| 1 | 0x24 | POWER_SET_STATUS | `yn0/z0.java` |
| 1 | 0x25 | POWER_NTFY_STATUS | `yn0/i.java` |
| 1 | 0x26 | POWER_GET_PARAM | `yn0/b.java` |
| 1 | 0x27 | POWER_RET_PARAM | `yn0/b0.java` |
| 1 | 0x28 | POWER_SET_PARAM | `yn0/u0.java` |
| 1 | 0x29 | POWER_NTFY_PARAM | `yn0/d.java` |
| 1 | 0x30 | UPDT_GET_CAPABILITY | `ho0/a.java` |
| 1 | 0x31 | UPDT_RET_CAPABILITY | `ho0/m.java` |
| 1 | 0x32 | UPDT_GET_STATUS | `ho0/c.java` |
| 1 | 0x33 | UPDT_RET_STATUS | `ho0/u.java` |
| 1 | 0x34 | UPDT_SET_STATUS | `ho0/b0.java` |
| 1 | 0x35 | UPDT_NTFY_STATUS | `ho0/k.java` |
| 1 | 0x36 | UPDT_GET_PARAM | `ho0/b.java` |
| 1 | 0x37 | UPDT_RET_PARAM | `ho0/q.java` |
| 1 | 0x38 | UPDT_SET_PARAM | `ho0/w.java` |
| 1 | 0x39 | UPDT_NTFY_PARAM | `ho0/e.java` |
| 1 | 0x3E | UPDT_TRANSFER_DATA | `ho0/c0.java` |
| 1 | 0x3F | UPDT_NTFY_MESSAGE | `ho0/d.java` |
| 1 | 0x40 | LEA_GET_CAPABILITY | `nn0/a.java` |
| 1 | 0x41 | LEA_RET_CAPABILITY | `nn0/k.java` |
| 1 | 0x42 | LEA_GET_STATUS | `nn0/d.java` |
| 1 | 0x43 | LEA_RET_STATUS | `nn0/q.java` |
| 1 | 0x45 | LEA_NTFY_STATUS | `g/b.java` |
| 1 | 0x46 | LEA_GET_PARAM | `nn0/c.java` |
| 1 | 0x47 | LEA_RET_PARAM | `nn0/p.java` |
| 1 | 0x48 | LEA_SET_PARAM | `nn0/v.java` |
| 1 | 0x49 | LEA_NTFY_PARAM | `nn0/f.java` |
| 1 | 0x4A | LEA_GET_EXTENDED_PARAM | `nn0/b.java` |
| 1 | 0x4B | LEA_RET_EXTENDED_PARAM | `nn0/o.java` |
| 1 | 0x4C | LEA_SET_EXTENDED_PARAM | `nn0/u.java` |
| 1 | 0x4D | LEA_NTFY_EXTENDED_PARAM | `nn0/e.java` |
| 1 | 0x50 | EQEBB_GET_CAPABILITY | `jn0/a.java` |
| 1 | 0x51 | EQEBB_RET_CAPABILITY | `jn0/v.java` |
| 1 | 0x52 | EQEBB_GET_STATUS | `jn0/e.java` |
| 1 | 0x53 | EQEBB_RET_STATUS | `o0/b.java` |
| 1 | 0x55 | EQEBB_NTFY_STATUS | `s/b.java` |
| 1 | 0x56 | EQEBB_GET_PARAM | `jn0/d.java` |
| 1 | 0x57 | EQEBB_RET_PARAM | `f0/b.java` |
| 1 | 0x58 | EQEBB_SET_PARAM | `r0/b.java` |
| 1 | 0x59 | EQEBB_NTFY_PARAM | `jn0/f.java` |
| 1 | 0x5A | EQEBB_GET_EXTENDED_INFO | `jn0/c.java` |
| 1 | 0x5B | EQEBB_RET_EXTENDED_INFO | `jn0/d0.java` |
| 1 | 0x60 | NCASM_GET_CAPABILITY | `sn0/a.java` |
| 1 | 0x61 | NCASM_RET_CAPABILITY | `sn0/u.java` |
| 1 | 0x62 | NCASM_GET_STATUS | `sn0/c.java` |
| 1 | 0x63 | NCASM_RET_STATUS | `sn0/x0.java` |
| 1 | 0x64 | NCASM_SET_STATUS | `sn0/n1.java` |
| 1 | 0x65 | NCASM_NTFY_STATUS | `sn0/s.java` |
| 1 | 0x66 | NCASM_GET_PARAM | `sn0/b.java` |
| 1 | 0x67 | NCASM_RET_PARAM | `sn0/i0.java` |
| 1 | 0x68 | NCASM_SET_PARAM | `sn0/z0.java` |
| 1 | 0x69 | NCASM_NTFY_PARAM | `sn0/d.java` |
| 1 | 0x70 | SENSE_GET_CAPABILITY | `ao0/a.java` |
| 1 | 0x71 | SENSE_RET_CAPABILITY | `ao0/j.java` |
| 1 | 0x72 | SENSE_GET_STATUS | `mdr/v2/table1/d.java` |
| 1 | 0x73 | SENSE_RET_STATUS | `mdr/v2/table1/d.java` |
| 1 | 0x74 | SENSE_SET_STATUS | `ao0/q.java` |
| 1 | 0x75 | SENSE_NTFY_STATUS | `h/b.java` |
| 1 | 0x76 | SENSE_GET_PARAM | `mdr/v2/table1/d.java` |
| 1 | 0x77 | SENSE_RET_PARAM | `mdr/v2/table1/d.java` |
| 1 | 0x78 | SENSE_SET_PARAM | `ao0/o.java` |
| 1 | 0x79 | SENSE_NTFY_PARAM | `ao0/e.java` |
| 1 | 0x7A | SENSE_GET_EXT_INFO | `ao0/c.java` |
| 1 | 0x7B | SENSE_RET_EXT_INFO | `ao0/l.java` |
| 1 | 0x80 | OPT_GET_CAPABILITY | `vn0/a.java` |
| 1 | 0x81 | OPT_RET_CAPABILITY | `vn0/l.java` |
| 1 | 0x82 | OPT_GET_STATUS | `vn0/c.java` |
| 1 | 0x83 | OPT_RET_STATUS | `vn0/w.java` |
| 1 | 0x84 | OPT_SET_STATUS | `vn0/a0.java` |
| 1 | 0x85 | OPT_NTFY_STATUS | `vn0/j.java` |
| 1 | 0x86 | OPT_GET_PARAM | `vn0/b.java` |
| 1 | 0x87 | OPT_RET_PARAM | `vn0/q.java` |
| 1 | 0x88 | OPT_SET_PARAM | `vn0/y.java` |
| 1 | 0x89 | OPT_NTFY_PARAM | `vn0/d.java` |
| 1 | 0x90 | ALERT_GET_CAPABILITY | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 1 | 0x91 | ALERT_RET_CAPABILITY | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 1 | 0x92 | ALERT_GET_STATUS | `en0/a.java` |
| 1 | 0x93 | ALERT_RET_STATUS | `en0/i.java` |
| 1 | 0x94 | ALERT_SET_STATUS | `en0/r.java` |
| 1 | 0x95 | ALERT_NTFY_STATUS | `en0/g.java` |
| 1 | 0x98 | ALERT_SET_PARAM | `en0/l.java` |
| 1 | 0x99 | ALERT_NTFY_PARAM | `en0/b.java` |
| 1 | 0xA0 | PLAY_GET_CAPABILITY | `wn0/a.java` |
| 1 | 0xA1 | PLAY_RET_CAPABILITY | `wn0/n.java` |
| 1 | 0xA2 | PLAY_GET_STATUS | `wn0/c.java` |
| 1 | 0xA3 | PLAY_RET_STATUS | `wn0/w.java` |
| 1 | 0xA4 | PLAY_SET_STATUS | `wn0/e0.java` |
| 1 | 0xA5 | PLAY_NTFY_STATUS | `wn0/i.java` |
| 1 | 0xA6 | PLAY_GET_PARAM | `wn0/b.java` |
| 1 | 0xA7 | PLAY_RET_PARAM | `wn0/r.java` |
| 1 | 0xA8 | PLAY_SET_PARAM | `wn0/b0.java` |
| 1 | 0xA9 | PLAY_NTFY_PARAM | `wn0/d.java` |
| 1 | 0xB0 | SAR_AUTO_PLAY_GET_CAPABILITY | `zn0/a.java` |
| 1 | 0xB1 | SAR_AUTO_PLAY_RET_CAPABILITY | `zn0/j.java` |
| 1 | 0xB2 | SAR_AUTO_PLAY_GET_STATUS | `zn0/c.java` |
| 1 | 0xB3 | SAR_AUTO_PLAY_RET_STATUS | `zn0/q.java` |
| 1 | 0xB5 | SAR_AUTO_PLAY_NTFY_STATUS | `zn0/g.java` |
| 1 | 0xB6 | SAR_AUTO_PLAY_GET_PARAM | `zn0/b.java` |
| 1 | 0xB7 | SAR_AUTO_PLAY_RET_PARAM | `zn0/o.java` |
| 1 | 0xB8 | SAR_AUTO_PLAY_SET_PARAM | `zn0/t.java` |
| 1 | 0xB9 | SAR_AUTO_PLAY_NTFY_PARAM | `zn0/d.java` |
| 1 | 0xC4 | LOG_SET_STATUS | `qn0/e.java` |
| 1 | 0xC9 | LOG_NTFY_PARAM | `qn0/a.java` |
| 1 | 0xD0 | GENERAL_SETTING_GET_CAPABILITY | `ln0/a.java` |
| 1 | 0xD1 | GENERAL_SETTING_RET_CAPABILITY | `ln0/h.java` |
| 1 | 0xD2 | GENERAL_SETTING_GET_STATUS | `ln0/c.java` |
| 1 | 0xD3 | GENERAL_SETTING_RET_STATUS | `ln0/n.java` |
| 1 | 0xD5 | GENERAL_SETTING_NTFY_STATUS | `ln0/g.java` |
| 1 | 0xD6 | GENERAL_SETTING_GET_PARAM | `ln0/b.java` |
| 1 | 0xD7 | GENERAL_SETTING_RET_PARAM | `ln0/k.java` |
| 1 | 0xD8 | GENERAL_SETTING_SET_PARAM | `ln0/o.java` |
| 1 | 0xD9 | GENERAL_SETTING_NTNY_PARAM | `ln0/d.java` |
| 1 | 0xE0 | AUDIO_GET_CAPABILITY | `fn0/a.java` |
| 1 | 0xE1 | AUDIO_RET_CAPABILITY | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 1 | 0xE2 | AUDIO_GET_STATUS | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 1 | 0xE3 | AUDIO_RET_STATUS | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 1 | 0xE4 | AUDIO_SET_STATUS | `mdr/v2/table1/d.java` |
| 1 | 0xE5 | AUDIO_NTFY_STATUS | `fn0/j.java` |
| 1 | 0xE6 | AUDIO_GET_PARAM | `fn0/b.java` |
| 1 | 0xE7 | AUDIO_RET_PARAM | `fn0/g0.java` |
| 1 | 0xE8 | AUDIO_SET_PARAM | `fn0/z0.java` |
| 1 | 0xE9 | AUDIO_NTFY_PARAM | `fn0/s.java` |
| 1 | 0xF0 | SYSTEM_GET_CAPABILITY | `do0/a.java` |
| 1 | 0xF1 | SYSTEM_RET_CAPABILITY | `do0/i0.java` |
| 1 | 0xF2 | SYSTEM_GET_STATUS | `do0/d.java` |
| 1 | 0xF3 | SYSTEM_RET_STATUS | `h1/b.java` |
| 1 | 0xF4 | SYSTEM_SET_STATUS | `do0/g2.java` |
| 1 | 0xF5 | SYSTEM_NTFY_STATUS | `z/b.java` |
| 1 | 0xF6 | SYSTEM_GET_PARAM | `do0/c.java` |
| 1 | 0xF7 | SYSTEM_RET_PARAM | `do0/x0.java` |
| 1 | 0xF8 | SYSTEM_SET_PARAM | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 1 | 0xF9 | SYSTEM_NTFY_PARAM | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 1 | 0xFA | SYSTEM_GET_EXT_PARAM | `do0/b.java` |
| 1 | 0xFB | SYSTEM_RET_EXT_PARAM | `do0/r0.java` |
| 1 | 0xFC | SYSTEM_SET_EXT_PARAM | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 1 | 0xFD | SYSTEM_NTFY_EXT_PARAM | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 1 | 0xFF | UNKNOWN | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 2 | 0x06 | CONNECT_GET_SUPPORT_FUNCTION | `jo0/a.java` |
| 2 | 0x07 | CONNECT_RET_SUPPORT_FUNCTION | `jo0/b.java` |
| 2 | 0x20 | POWER_GET_CAPABILITY | `qo0/a.java` |
| 2 | 0x21 | POWER_RET_CAPABILITY | `qo0/k.java` |
| 2 | 0x22 | POWER_GET_STATUS | `qo0/c.java` |
| 2 | 0x23 | POWER_RET_STATUS | `qo0/q.java` |
| 2 | 0x24 | POWER_SET_STATUS | (нет прямого класса в скрипте, см. switch в table*/c.java) |
| 2 | 0x25 | POWER_NTFY_STATUS | `g/b.java` |
| 2 | 0x26 | POWER_GET_PARAM | `qo0/b.java` |
| 2 | 0x27 | POWER_RET_PARAM | `qo0/o.java` |
| 2 | 0x28 | POWER_SET_PARAM | `qo0/u.java` |
| 2 | 0x29 | POWER_NTFY_PARAM | `qo0/d.java` |
| 2 | 0x30 | PERI_GET_CAPABILITY | `oo0/b.java` |
| 2 | 0x31 | PERI_RET_CAPABILITY | `oo0/o.java` |
| 2 | 0x32 | PERI_GET_STATUS | `oo0/d.java` |
| 2 | 0x33 | PERI_RET_STATUS | `oo0/v.java` |
| 2 | 0x34 | PERI_SET_STATUS | `oo0/d0.java` |
| 2 | 0x35 | PERI_NTFY_STATUS | `oo0/m.java` |
| 2 | 0x36 | PERI_GET_PARAM | `oo0/c.java` |
| 2 | 0x37 | PERI_RET_PARAM | `oo0/q.java` |
| 2 | 0x38 | PERI_SET_PARAM | `oo0/a0.java` |
| 2 | 0x39 | PERI_NTFY_PARAM | `oo0/h.java` |
| 2 | 0x3C | PERI_SET_EXTENDED_PARAM | `oo0/x.java` |
| 2 | 0x3D | PERI_NTFY_EXTENDED_PARAM | `oo0/e.java` |
| 2 | 0x40 | VOICE_GUIDANCE_GET_CAPABILITY | `ap0/a.java` |
| 2 | 0x41 | VOICE_GUIDANCE_RET_CAPABILITY | `ap0/q.java` |
| 2 | 0x42 | VOICE_GUIDANCE_GET_STATUS | `ap0/d.java` |
| 2 | 0x43 | VOICE_GUIDANCE_RET_STATUS | `ap0/c0.java` |
| 2 | 0x44 | VOICE_GUIDANCE_SET_STATUS | `ap0/m0.java` |
| 2 | 0x45 | VOICE_GUIDANCE_NTFY_STATUS | `ap0/l.java` |
| 2 | 0x46 | VOICE_GUIDANCE_GET_PARAM | `ap0/c.java` |
| 2 | 0x47 | VOICE_GUIDANCE_RET_PARAM | `ap0/x.java` |
| 2 | 0x48 | VOICE_GUIDANCE_SET_PARAM | `ap0/h0.java` |
| 2 | 0x49 | VOICE_GUIDANCE_NTFY_PARAM | `ap0/g.java` |
| 2 | 0x4A | VOICE_GUIDANCE_GET_EXTENDED_PARAM | `ap0/b.java` |
| 2 | 0x4B | VOICE_GUIDANCE_RET_EXTENDED_PARAM | `ap0/v.java` |
| 2 | 0x50 | SAFE_LISTENING_GET_CAPABILITY | `ro0/a.java` |
| 2 | 0x51 | SAFE_LISTENING_RET_CAPABILITY | `ro0/b.java` |
| 2 | 0x52 | SAFE_LISTENING_GET_STATUS | `uo0/a.java` |
| 2 | 0x53 | SAFE_LISTENING_RET_STATUS | `uo0/l.java` |
| 2 | 0x54 | SAFE_LISTENING_SET_STATUS | `uo0/u.java` |
| 2 | 0x55 | SAFE_LISTENING_NTFY_STATUS | `uo0/b.java` |
| 2 | 0x56 | SAFE_LISTENING_GET_PARAM | `to0/a.java` |
| 2 | 0x57 | SAFE_LISTENING_RET_PARAM | `to0/f.java` |
| 2 | 0x58 | SAFE_LISTENING_SET_PARAM | `to0/i.java` |
| 2 | 0x59 | SAFE_LISTENING_NTFY_PARAM | `to0/b.java` |
| 2 | 0x5A | SAFE_LISTENING_GET_EXTENDED_PARAM | `so0/a.java` |
| 2 | 0x5B | SAFE_LISTENING_RET_EXTENDED_PARAM | `so0/b.java` |
| 2 | 0x60 | LEA_GET_CAPABILITY | `ko0/a.java` |
| 2 | 0x61 | LEA_RET_CAPABILITY | `ko0/i.java` |
| 2 | 0x62 | LEA_GET_STATUS | `ko0/c.java` |
| 2 | 0x63 | LEA_RET_STATUS | `ko0/m.java` |
| 2 | 0x65 | LEA_NTFY_STATUS | `ko0/f.java` |
| 2 | 0x66 | LEA_GET_PARAM | `ko0/b.java` |
| 2 | 0x67 | LEA_RET_PARAM | `ko0/k.java` |
| 2 | 0x68 | LEA_SET_PARAM | `ko0/p.java` |
| 2 | 0x69 | LEA_NTFY_PARAM | `ko0/d.java` |
| 2 | 0x70 | PARTY_GET_CAPABILITY | `mo0/a.java` |
| 2 | 0x71 | PARTY_RET_CAPABILITY | `mo0/p.java` |
| 2 | 0x72 | PARTY_GET_STATUS | `mo0/c.java` |
| 2 | 0x73 | PARTY_RET_STATUS | `mo0/y.java` |
| 2 | 0x74 | PARTY_SET_STATUS | `mo0/n0.java` |
| 2 | 0x75 | PARTY_NTFY_STATUS | `mo0/j.java` |
| 2 | 0x76 | PARTY_GET_PARAM | `mo0/b.java` |
| 2 | 0x77 | PARTY_RET_PARAM | `t/b.java` |
| 2 | 0x78 | PARTY_SET_PARAM | `i0/b.java` |
| 2 | 0x79 | PARTY_NTFY_PARAM | `mo0/e.java` |
| 2 | 0x7C | PARTY_SET_EXTENDED_PARAM | `mo0/e0.java` |
| 2 | 0x7D | PARTY_NTFY_EXTENDED_PARAM | `mdr/v2/table2/d.java` |
| 2 | 0xF0 | SYSTEM_GET_CAPABILITY | `wo0/a.java` |
| 2 | 0xF1 | SYSTEM_RET_CAPABILITY | `wo0/i0.java` |
| 2 | 0xF2 | SYSTEM_GET_STATUS | `wo0/h.java` |
| 2 | 0xF3 | SYSTEM_RET_STATUS | `j1/b.java` |
| 2 | 0xF4 | SYSTEM_SET_STATUS | `wo0/e2.java` |
| 2 | 0xF5 | SYSTEM_NTFY_STATUS | `wo0/d0.java` |
| 2 | 0xF6 | SYSTEM_GET_PARAM | `wo0/g.java` |
| 2 | 0xF7 | SYSTEM_RET_PARAM | `z0/b.java` |
| 2 | 0xF8 | SYSTEM_SET_PARAM | `q1/b.java` |
| 2 | 0xF9 | SYSTEM_NTFY_PARAM | `n/b.java` |
| 2 | 0xFA | SYSTEM_GET_EXTENDED_PARAM | `wo0/d.java` |
| 2 | 0xFB | SYSTEM_RET_EXTENDED_PARAM | `wo0/t0.java` |
| 2 | 0xFC | SYSTEM_SET_EXTENDED_PARAM | `wo0/o1.java` |
| 2 | 0xFD | SYSTEM_NTFY_EXTENDED_PARAM | `wo0/i.java` |
| 2 | 0xFF | UNKNOWN | (нет прямого класса в скрипте, см. switch в table*/c.java) |

## Приложение B. FunctionType v2 (код → таблица → имя)

Коды заданы в static-блоке `FunctionType.java` (`new FunctionType(name, ordinal, table, code)`). Один код может быть в разных таблицах, поэтому столбец table обязателен. NO_1 и NO_2 взяты из `Table.NO_1` и `Table.NO_2` (`FunctionType.java:238`, `:385`).

| code | table | FunctionType |
|---|---|---|
| 0x10 | NO_1 | CONCIERGE_DATA |
| 0x11 | NO_1 | CONNECTION_STATUS |
| 0x12 | NO_1 | CODEC_INDICATOR |
| 0x13 | NO_1 | UPSCALING_INDICATOR |
| 0x14 | NO_1 | BLE_SETUP |
| 0x15 | NO_1 | TUTORIAL_CONTENTS_SELECT_ON_CONCIERGE |
| 0x16 | NO_1 | CONNECTION_ESTABLISHED_TIME |
| 0x17 | NO_1 | UNNECESSARY_AUTO_RECONNECTION |
| 0x18 | NO_1 | DEVICE_SPECIAL_MODE |
| 0x19 | NO_1 | PHONE_AND_CONNECTED_DEVICE_INFOMATION_FOR_CLASSIC |
| 0x1A | NO_1 | TANDEM_RECONNECTION_REQUEST |
| 0x1B | NO_1 | DISPLAY_FW_VERSION |
| 0x1C | NO_1 | DISCONNECT_TANDEM_GATT_REQUEST |
| 0x1D | NO_1 | RESTRICT_APP_BACKGROUND_FUNCTIONS |
| 0x20 | NO_1 | BATTERY_LEVEL_INDICATOR |
| 0x20 | NO_2 | AUTO_STANDBY |
| 0x21 | NO_1 | LEFT_RIGHT_BATTERY_LEVEL_INDICATOR |
| 0x21 | NO_2 | CHARGE_IN_USE |
| 0x22 | NO_1 | CRADLE_BATTERY_LEVEL_INDICATOR |
| 0x22 | NO_2 | CARING_CHARGE_WITH_THRESHOLD |
| 0x23 | NO_1 | POWER_OFF |
| 0x24 | NO_1 | AUTO_POWER_OFF |
| 0x25 | NO_1 | AUTO_POWER_OFF_WITH_WEARING_DETECTION |
| 0x26 | NO_1 | POWER_SAVING_MODE_ON_OFF |
| 0x27 | NO_1 | TANDEM_KEEP_ALIVE |
| 0x28 | NO_1 | BATTERY_LEVEL_WITH_THRESHOLD |
| 0x29 | NO_1 | LR_BATTERY_LEVEL_WITH_THRESHOLD |
| 0x2A | NO_1 | CRADLE_BATTERY_LEVEL_WITH_THRESHOLD |
| 0x2B | NO_1 | BATTERY_SAFE_MODE |
| 0x2C | NO_1 | CARING_CHARGE |
| 0x2D | NO_1 | BT_STANDBY |
| 0x2E | NO_1 | STAMINA |
| 0x2F | NO_1 | AUTOMATIC_TOUCH_PANEL_BACKLIGHT_TURN_OFF |
| 0x30 | NO_1 | FW_UPDATE_TANDEM |
| 0x30 | NO_2 | PAIRING_DEVICE_MANAGEMENT_CLASSIC_BT |
| 0x31 | NO_2 | SOURCE_SWITCH_CONTROL |
| 0x32 | NO_1 | FW_UPDATE_MTK_TRANSFER_WITHOUT_DISCONNECTION |
| 0x34 | NO_1 | FW_UPDATE_MTK_TRANSFER_WITHOUT_DISCONNECTION_AUTO_UPDATE |
| 0x35 | NO_1 | FW_UPDATE_MTK_TRANSFER_WITH_REPAIR_MODE |
| 0x36 | NO_1 | FW_UPDATE_MTK_TRANSFER_WITH_AC_CONNECTION_CHECK |
| 0x37 | NO_1 | FW_UPDATE_TANDEM_TRANSFER_USING_COMMON_TABLE |
| 0x38 | NO_1 | FW_UPDATE_USING_MC_APP |
| 0x40 | NO_1 | TWS_SUPPORTS_A2DP_LEA_UNI_LEA_BROAD_WITH_CTKD |
| 0x40 | NO_2 | VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_NOT_SUPPORT_LANGUAGE_SWITCH |
| 0x41 | NO_1 | HBS_SUPPORTS_A2DP_LEA_UNI_LEA_BROAD_WITH_CTKD |
| 0x41 | NO_2 | VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_SUPPORT_LANGUAGE_SWITCH |
| 0x42 | NO_1 | CLASSIC_ONLY_LE_CLASSIC_SETTING |
| 0x42 | NO_2 | VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_SUPPORT_LANGUAGE_SWITCH_AND_VOLUME_ADJUSTMENT |
| 0x43 | NO_1 | TWS_SUPPORTS_LEA_UNI_LEA_BROAD |
| 0x43 | NO_2 | VOICE_GUIDANCE_VOLUME_SETTING_MTK_FIXED_TO_5_STEPS |
| 0x44 | NO_1 | CHANGE_TANDEM_CONNECTION_PROFILE_FOR_ANDROID |
| 0x44 | NO_2 | VOICE_GUIDANCE_SETTING_SUPPORT_LANGUAGE_SWITCH |
| 0x45 | NO_1 | BGM_MODE_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x45 | NO_2 | VOICE_GUIDANCE_SETTING_ONLY_ON_OFF_SWITCH |
| 0x46 | NO_1 | HEAD_TRACKER_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x46 | NO_2 | VOICE_GUIDANCE_BATTERY_LEVEL_VOICE |
| 0x47 | NO_1 | PAIRING_DEVICE_MANAGEMENT_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x47 | NO_2 | VOICE_GUIDANCE_POWER_ON_OFF_SOUND |
| 0x48 | NO_1 | SOUND_AR_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x48 | NO_2 | VOICE_GUIDANCE_SOUND_EFFECT_ULT_BEEP_ON_OFF |
| 0x49 | NO_1 | AUTO_PLAY_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x4A | NO_1 | GATT_CONNECTABLE_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x4B | NO_1 | SOUND_AR_OPTIMIZATION_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x4C | NO_1 | QUICK_ACCESS_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x4D | NO_1 | CONNECTION_MODE_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x4E | NO_1 | VOICE_ASSISTANT_SETTINGS_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x4F | NO_1 | VOICE_ASSISTANT_WAKE_WORD_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x50 | NO_1 | PRESET_EQ |
| 0x50 | NO_2 | SAFE_LISTENING_HBS_1 |
| 0x51 | NO_1 | EBB |
| 0x51 | NO_2 | SAFE_LISTENING_TWS_1 |
| 0x52 | NO_1 | PRESET_EQ_NON_CUSTOMIZABLE |
| 0x52 | NO_2 | SAFE_LISTENING_HBS_2 |
| 0x53 | NO_1 | PRESET_EQ_AND_ULT_MODE |
| 0x53 | NO_2 | SAFE_LISTENING_TWS_2 |
| 0x54 | NO_1 | SOUND_EFFECT |
| 0x54 | NO_2 | SAFE_VOLUME_CONTROL |
| 0x55 | NO_1 | CUSTOM_EQ |
| 0x55 | NO_2 | MAX_VOLUME_LEVEL_LIMIT |
| 0x56 | NO_1 | TURN_KEY_EQ |
| 0x57 | NO_1 | PRESET_EQ_AND_ERRORCODE |
| 0x58 | NO_1 | ULT_SOUND_EFFECT_ASSIGN |
| 0x59 | NO_1 | CUSTOMIZABLE_SOUND_EFFECT |
| 0x5A | NO_1 | LR_BALANCE_CORRECTION |
| 0x5B | NO_1 | TURN_KEY_EQ_CONTROLLER |
| 0x60 | NO_2 | LE_AUDIO_CONNECTION_STATE_NOTIFICATION |
| 0x61 | NO_1 | NOISE_CANCELLING_ONOFF |
| 0x61 | NO_2 | LE_AUDIO_SWITCH_SUPPORTED_COMPATIBILITY |
| 0x62 | NO_1 | NOISE_CANCELLING_ONOFF_AND_AMBIENT_SOUND_MODE_ONOFF |
| 0x62 | NO_2 | LE_AUDIO_CONNECTION_MODE |
| 0x63 | NO_1 | NOISE_CANCELLING_DUAL_SINGLE_OFF_AND_AMBIENT_SOUND_MODE_ONOFF |
| 0x63 | NO_2 | GET_IDENTITY_RESOLVING_KEY |
| 0x64 | NO_1 | NOISE_CANCELLING_ONOFF_AND_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT |
| 0x64 | NO_2 | PAS_SUPPORTS_A2DP_LEA_UNI_LEA_BROAD_WITH_CTKD |
| 0x65 | NO_1 | NOISE_CANCELLING_DUAL_SINGLE_OFF_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT |
| 0x66 | NO_1 | AMBIENT_SOUND_MODE_ONOFF |
| 0x67 | NO_1 | AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT |
| 0x68 | NO_1 | MODE_NC_ASM_NOISE_CANCELLING_DUAL_AUTO_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT |
| 0x69 | NO_1 | AMBIENT_SOUND_CONTROL_MODE_SELECT |
| 0x6A | NO_1 | MODE_NC_ASM_NOISE_CANCELLING_DUAL_SINGLE_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT |
| 0x6B | NO_1 | MODE_NC_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT |
| 0x6C | NO_1 | MODE_NC_NCSS_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT_WITH_TEST_MODE |
| 0x6D | NO_1 | MODE_NC_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT_NOISE_ADAPTATION |
| 0x6F | NO_2 | LINK_AUTO_SWITCH_CANT_BE_USED_WITH_LEA_CONNECTION |
| 0x70 | NO_1 | AUTO_NCASM |
| 0x70 | NO_2 | DJ_CONTROL |
| 0x71 | NO_1 | ADAPTIVE_CONTROL_WITH_PARAMETER_NOTIFICATION |
| 0x71 | NO_2 | ILLUMINATION |
| 0x72 | NO_1 | HEART_RATE_SENSOR_SETTING |
| 0x72 | NO_2 | KARAOKE |
| 0x73 | NO_1 | HEART_RATE_PROFILE_SETTING |
| 0x73 | NO_2 | DJ_CONTROL_WITH_STATUS_DISABLE_REASON |
| 0x74 | NO_1 | HEART_RATE_SENSOR_TEST |
| 0x74 | NO_2 | KARAOKE_WITH_STATUS_DISABLE_REASON |
| 0x75 | NO_1 | HEART_RATE_SENSOR_GREEN_LIGHT |
| 0x75 | NO_2 | LIVE_KARAOKE |
| 0x76 | NO_1 | HEART_RATE_SENSOR_INFORMATION |
| 0x76 | NO_2 | AURACAST_RECEIVER_ASSISTANT |
| 0x77 | NO_2 | AURACAST_RECEIVER_ASSISTANT_CAN_NOT_BE_USED_WITH_CLASSIC_ONLY |
| 0x80 | NO_1 | NC_OPTIMIZER_PERSONAL_BAROMETRIC |
| 0x81 | NO_1 | NC_OPTIMIZER_PERSONAL |
| 0x82 | NO_1 | NC_OPTIMIZER_BAROMETRIC |
| 0x83 | NO_1 | SOUND_FIELD_OPTIMIZATION |
| 0x84 | NO_1 | TV_SOUND_BOOSTER |
| 0x90 | NO_1 | FIXED_MESSAGE |
| 0x91 | NO_1 | VIBRATOR_ALERT_NOTIFICATION |
| 0x92 | NO_1 | FIXED_MESSAGE_WITH_LR_SELECTION |
| 0x93 | NO_1 | VOICE_ASSISTANT_ALERT_NOTIFICATION |
| 0x94 | NO_1 | LE_AUDIO_ALERT_NOTIFICATION |
| 0xA1 | NO_1 | PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT |
| 0xA2 | NO_1 | PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT_AND_MUTE |
| 0xA3 | NO_1 | PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT_AND_FUNCTION_CHANGE |
| 0xA4 | NO_1 | PLAYBACK_CONTROLLER_WITH_FUNCTION_CHANGE |
| 0xB0 | NO_1 | SAR |
| 0xB1 | NO_1 | AUTO_PLAY |
| 0xB2 | NO_1 | GATT_CONNECTABLE |
| 0xB3 | NO_1 | SAR_OPTIMIZATION_COMPASS_ACCEL_TYPE |
| 0xB5 | NO_1 | HEAD_TRACKER_COMPASS_ACCEL_TYPE |
| 0xB6 | NO_1 | SAR_OPTIMIZATION_ACCEL_TYPE |
| 0xB7 | NO_1 | HEAD_TRACKER_ACCEL_TYPE |
| 0xB8 | NO_1 | INTEGRATED_AUTO_PLAY |
| 0xC1 | NO_1 | ACTION_LOG_NOTIFIER |
| 0xC2 | NO_1 | TIME_SERIES_OPERATIONLOG_NOTIFIER |
| 0xC3 | NO_1 | SOUND_DROPOUT_NOTIFIER |
| 0xD1 | NO_1 | GENERAL_SETTING_1 |
| 0xD2 | NO_1 | GENERAL_SETTING_2 |
| 0xD3 | NO_1 | GENERAL_SETTING_3 |
| 0xD4 | NO_1 | GENERAL_SETTING_4 |
| 0xE1 | NO_1 | CONNECTION_MODE_SOUND_QUALITY_CONNECTION_QUALITY |
| 0xE2 | NO_1 | UPSCALING_AUTO_OFF |
| 0xE3 | NO_1 | CONNECTION_MODE_SOUND_QUALITY_SOUND_WITH_LDAC_STATUS_QUALITY_CONNECTION_QUALITY |
| 0xE4 | NO_1 | BGM_MODE_SMALL_MIDDLE_LARGE |
| 0xE5 | NO_1 | UPMIX_CINEMA |
| 0xE6 | NO_1 | LISTENING_OPTION |
| 0xE7 | NO_1 | CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO |
| 0xE8 | NO_1 | VOICE_CONTENTS |
| 0xE9 | NO_1 | SOUND_LEAKAGE_REDUCTION |
| 0xEA | NO_1 | LISTENING_OPTION_ASSIGN_CUSTOMIZABLE |
| 0xEB | NO_1 | BGM_MODE_SMALL_MIDDLE_LARGE_AND_ERRORCODE |
| 0xEC | NO_1 | UPMIX_SERIES |
| 0xED | NO_1 | UPSCALING_AUTO_OFF_WITH_STATUS_DISABLE_REASON |
| 0xEE | NO_1 | AUDIO_360_UPMIX_OPTIMIZATION |
| 0xEF | NO_1 | AUDIO_360_UPMIX_HEADTRACKING |
| 0xF0 | NO_1 | VIBRATOR_ON_OFF |
| 0xF0 | NO_2 | WEARING_STATUS_CHECKER |
| 0xF1 | NO_1 | PLAYBACK_CONTROL_BY_WEARING_REMOVING_HEADPHONE_ON_OFF |
| 0xF1 | NO_2 | REPEAT_TAP_TRAINING_MODE |
| 0xF2 | NO_2 | QUICK_ACCESS_EASY_SETTING |
| 0xF3 | NO_2 | AUTO_VOLUME_OPTIMIZER |
| 0xF4 | NO_2 | AUTO_VOLUME_WITH_LIMITATION |
| 0xF5 | NO_1 | VOICE_ASSISTANT_WAKE_WORD_ON_OFF |
| 0xF5 | NO_2 | SONY_VOICE_ASSISTANT |
| 0xF6 | NO_1 | WEARING_STATUS_DETECTOR |
| 0xF6 | NO_2 | WEARING_POSITION |
| 0xF7 | NO_1 | EARPIECE_SELECTION |
| 0xF7 | NO_2 | LINK_AUTO_SWITCH_FOR_SPEAKER |
| 0xF8 | NO_1 | CALL_SETTINGS |
| 0xF8 | NO_2 | LINK_AUTO_SWITCH_FOR_HEADSETS |
| 0xF9 | NO_1 | RESET_SETTINGS |
| 0xF9 | NO_2 | MIC_ON_OFF_BY_HEADPHONE_OPERATION |
| 0xFA | NO_1 | AUTO_VOLUME |
| 0xFA | NO_2 | FUNCTION_CHANGE |
| 0xFB | NO_1 | FACE_TAP_TEST_MODE |
| 0xFB | NO_2 | USB_BROWSER |
| 0xFC | NO_1 | SMART_TALKING_MODE_TYPE2 |
| 0xFC | NO_2 | LIGHTING_MODE |
| 0xFD | NO_1 | QUICK_ACCESS |
| 0xFD | NO_2 | VOICE_ASSISTANT_WITH_SPECIFIC_SETUP_LINK_SUPPORT |
| 0xFE | NO_1 | ASSIGNABLE_SETTING_WITH_LIMITATION |
| 0xFE | NO_2 | LIGHTING_DEFAULT_COLOR |
| 0xFF | NO_1 | HEAD_GESTURE_ON_OFF_TRAINING |
| 0xFF | NO_2 | WEARING_POSITION_WITHOUT_FITTING_SUPPORTER |

## Приложение C. Inquiry-типы

Значения из enum-файлов `mdr/v2/table*/*/param/`. `OUT_OF_RANGE=-1` — служебное значение для неизвестного байта.

**`table1/alert/AlertInquiredType.java`**  
FIXED_MESSAGE=0, VIBRATOR_ALERT_NOTIFICATION=1, FIXED_MESSAGE_WITH_LEFT_RIGHT_SELECTION=2, VOICE_ASSISTANT_ALERT_NOTIFICATION=3, APP_BECOMES_FOREGROUND=4, LE_AUDIO_ALERT_NOTIFICATION=5, FLEXIBLE_MESSAGE=6, OUT_OF_RANGE=-1

**`table1/audio/AudioExclusiveFunctionType.java`**  
EQUALIZER=0, DSEE=1, HEAD_TRACKER=2, LE_AUDIO=3, IMMERSIVE_AUDIO=4, SAR_OPTIMIZATION=5, GOOGLE_ASSISTANT=6, SPATIAL_AUDIO=7, BGM_MODE=8, UPMIX_CINEMA=9, SONY_VOICE_ASSISTANT=10, TENCENT_XIAOWEI=11, VOICE_CONTENTS=12, SOUND_LEAKAGE_REDUCTION=13, BRAVIA_3D_SURROUND=14, UPMIX_SERIES=15, OUT_OF_RANGE=-1

**`table1/audio/AudioInquiredType.java`**  
CONNECTION_MODE=0, UPSCALING=1, CONNECTION_MODE_WITH_LDAC_STATUS=2, BGM_MODE=3, UPMIX_CINEMA=4, CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO=5, VOICE_CONTENTS=6, SOUND_LEAKAGE_REDUCTION=7, LISTENING_OPTION_ASSIGN_CUSTOMIZABLE=8, BGM_MODE_AND_ERRORCODE=9, UPMIX_SERIES=10, UPSCALING_AUTO_OFF_WITH_STATUS_DISABLE_REASON=11, OUT_OF_RANGE=-1

**`table1/audio/UpscalingType.java`**  
DSEE_HX=0, DSEE=1, DSEE_HX_AI=2, DSEE_ULTIMATE=3, OUT_OF_RANGE=-1

**`table1/audio/UpscalingTypeAutoOff.java`**  
OFF=0, AUTO=1, OUT_OF_RANGE=-1

**`table1/common/CommonInquiredType.java`**  
CONCIERGE=0, CONNECTION_STATUS=1, AUDIO_CODEC=2, UPSCALING_EFFECT=3, BLE_SETUP=4, CONNECTION_ESTABLISHED_TIME=5, DEVICE_SPECIAL_MODE=6, SMART_PHONE_AND_CONNECTED_DEVICE_INFORMATION=7, TANDEM_RECONNECTION_REQUEST=8, DISPLAY_FW_VERSION=9, OUT_OF_RANGE=-1

**`table1/common/RequestType.java`**  
UNDER_CONFIRMATION=0, EXECUTE=1, OUT_OF_RANGE=-1

**`table1/connect/ConnectInquiredType.java`**  
FIXED_VALUE=0, OUT_OF_RANGE=-1

**`table1/connect/DeviceInfoType.java`**  
MODEL_NAME=1, FW_VERSION=2, SERIES_AND_COLOR_INFO=3, INSTRUCTION_GUIDE=4, OUT_OF_RANGE=-1

**`table1/eqebb/EqBandInformationType.java`**  
NO_INFORMATION=0, HZ=1, KHZ=2, SPECIFIC_INFORMATION=16

**`table1/eqebb/EqEbbInquiredType.java`**  
PRESET_EQ=0, EBB=1, PRESET_EQ_NONCUSTOMIZABLE=2, PRESET_EQ_AND_ULT_MODE=3, PRESET_EQ_AND_ERRORCODE=4, SOUND_EFFECT=48, CUSTOM_EQ=49, TURN_KEY_EQ=50, CUSTOMIZABLE_SOUND_EFFECT_SELECT=51, CUSTOMIZABLE_SOUND_EFFECT_RESET=52, CUSTOMIZABLE_SOUND_EFFECT_CUSTOM=53, CUSTOMIZABLE_SOUND_EFFECT_EXTERNAL_UPDATE=54, ULT_BTN_SOUND_EFFECT_ASSIGN=64, OUT_OF_RANGE=-1

**`table1/eqebb/SoundEffectType.java`**  
SOUND_EFFECT_OFF=0, SOUND_EFFECT_ULT=1, SOUND_EFFECT_ULT1=2, SOUND_EFFECT_ULT2=3, SOUND_EFFECT_CUSTOM=4, SOUND_EFFECT_FLAT=5, SOUND_EFFECT_LIVE=6, SOUND_EFFECT_NONE=-1, OUT_OF_RANGE=-2

**`table1/generalsetting/GsInquiredType.java`**  
GENERAL_SETTING1=-47, GENERAL_SETTING2=-46, GENERAL_SETTING3=-45, GENERAL_SETTING4=-44, OUT_OF_RANGE=-1

**`table1/lea/LEAInquiredType.java`**  
TWS_SUPPORTS_LEA_UNI_LEA_BROAD=2, BGM_MODE_CANT_BE_USED_WITH_LEA_CONNECTION=-11, HEAD_TRACKER_CANT_BE_USED_WITH_LEA_CONNECTION=-10, PAIRING_DEVICE_MANAGEMENT_CANT_BE_USED_WITH_LEA_CONNECTION=-9, SOUND_AR_CANT_BE_USED_WITH_LEA_CONNECTION=-8, AUTO_PLAY_CANT_BE_USED_WITH_LEA_CONNECTION=-7, GATT_CONNECTABLE_CANT_BE_USED_WITH_LEA_CONNECTION=-6, SOUND_AR_OPTIMIZATION_CANT_BE_USED_WITH_LEA_CONNECTION=-5, QUICK_ACCESS_CANT_BE_USED_WITH_LEA_CONNECTION=-4, CONNECTION_MODE_CANT_BE_USED_WITH_LEA_CONNECTION=-3, VOICE_ASSISTANT_SETTINGS_CANT_BE_USED_WITH_LEA_CONNECTION=-2, VOICE_ASSISTANT_WAKE_WORD_CANT_BE_USED_WITH_LEA_CONNECTION=-1, OUT_OF_RANGE=11

**`table1/log/LogInquiredType.java`**  
ACTION_LOG_NOTIFIER=0, TIME_SERIES_OPERATIONLOG_NOTIFIER=1, SOUND_DROPOUT_NOTIFIER=2, OUT_OF_RANGE=-1

**`table1/ncasm/NcAsmInquiredType.java`**  
NC_ON_OFF=1, NC_ON_OFF_AND_ASM_ON_OFF=17, NC_MODE_SWITCH_AND_ASM_ON_OFF=18, NC_ON_OFF_AND_ASM_SEAMLESS=19, NC_MODE_SWITCH_AND_ASM_SEAMLESS=20, MODE_NC_ASM_AUTO_NC_MODE_SWITCH_AND_ASM_SEAMLESS=21, MODE_NC_ASM_DUAL_SINGLE_NC_MODE_SWITCH_AND_ASM_SEAMLESS=22, MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS=23, MODE_NC_NCSS_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS=24, MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS_NA=25, ASM_ON_OFF=33, ASM_SEAMLESS=34, NC_AMB_TOGGLE=48, NC_TEST_MODE=64, OUT_OF_RANGE=-1

**`table1/opt/OptInquiredType.java`**  
NC_OPTIMIZER_PERSONAL_BAROMETRIC=0, NC_OPTIMIZER_PERSONAL=1, NC_OPTIMIZER_BAROMETRIC=2, SOUND_FIELD_OPTIMIZATION=48, TV_SOUND_BOOSTER=49, OUT_OF_RANGE=-1

**`table1/playback/PlayInquiredType.java`**  
PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT=1, PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT_AND_FUNCTION_CHANGE=2, PLAYBACK_CONTROL_WITH_FUNCTION_CHANGE=3, MUSIC_VOLUME=32, CALL_VOLUME=33, MUSIC_VOLUME_WITH_MUTE=48, CALL_VOLUME_WITH_MUTE=49, PLAY_MODE=64, OUT_OF_RANGE=-1

**`table1/power/PowerInquiredType.java`**  
BATTERY=0, LEFT_RIGHT_BATTERY=1, CRADLE_BATTERY=2, POWER_OFF=3, AUTO_POWER_OFF=4, AUTO_POWER_OFF_WEARING_DETECTION=5, POWER_SAVE_MODE=6, LINK_CONTROL=7, BATTERY_WITH_THRESHOLD=8, LR_BATTERY_WITH_THRESHOLD=9, CRADLE_BATTERY_WITH_THRESHOLD=10, BATTERY_SAFE_MODE=11, CARING_CHARGE=12, STAMINA=14, AUTOMATIC_TOUCH_PANEL_BACKLIGHT_TURN_OFF=15, OUT_OF_RANGE=-1

**`table1/sarautoplay/SARAutoPlayInquiredType.java`**  
SAR=0, AUTO_PLAY=1, INTEGRATED_AUTO_PLAY=2, GATT_CONNECTABLE=16, SAR_OPTIMIZATION_COMPASS_ACCEL_TYPE=32, SAR_OPTIMIZATION_ACCEL_TYPE=33, OUT_OF_RANGE=-1

**`table1/sense/SenseInquiredType.java`**  
ADAPTIVE_CONTROL=0, ADAPTIVE_CONTROL_WITH_PARAMETER_NOTIFICATION=1, OUT_OF_RANGE=-1

**`table1/system/SystemInquiredType.java`**  
VIBRATOR=0, SMART_TALKING_MODE_TYPE1=2, ASSIGNABLE_SETTINGS=3, VOICE_ASSISTANT_SETTINGS=4, WEARING_STATUS_DETECTOR=6, EARPIECE_SELECTION=7, CALL_SETTINGS=8, RESET_SETTINGS=9, AUTO_VOLUME=10, FACE_TAP_TEST_MODE=11, SMART_TALKING_MODE_TYPE2=12, QUICK_ACCESS=13, ASSIGNABLE_SETTINGS_WITH_LIMITATION=14, HEAD_GESTURE_ON_OFF=15, HEAD_GESTURE_TRAINING=16, OUT_OF_RANGE=-1

**`table1/updt/UpdtInquiredType.java`**  
FW_UPDATE_MTK_TRANSFER_WO_DISCONNECTION=2, FW_UPDATE_MTK_TRANSFER_WO_DISCONNECTION_AUTO_UPDATE=4, FW_UPDATE_MTK_TRANSFER_WITH_REPAIR_MODE=5, FW_UPDATE_MTK_TRANSFER_WITH_AC_CONNECTION_CHECK=6, FW_UPDATE_USING_MC_APP=7, FW_UPDATE_TANDEM_PART1=16, FW_UPDATE_TANDEM_PART2=17, FW_UPDATE_TANDEM_PART3=18, FW_UPDATE_TANDEM_PART4=19, OUT_OF_RANGE=-1

**`table2/connect/ConnectInquiredType.java`**  
FIXED_VALUE=0, OUT_OF_RANGE=-1

**`table2/lea/LEAInquiredType.java`**  
LE_AUDIO_CONNECTION_MODE_WITH_BT_RECONNECTION=2, LINK_AUTO_SWITCH_CANT_BE_USED_WITH_LEA_CONNECTION=-2, OUT_OF_RANGE=-1

**`table2/party/PartyInquiredType.java`**  
DJ_CONTROL=0, ILLUMINATION=1, KARAOKE=2, DJ_CONTROL_WITH_STATUS_DISABLE_REASON=3, KARAOKE_WITH_STATUS_DISABLE_REASON=4, LIVE_KARAOKE=5, OUT_OF_RANGE=-1

**`table2/peripheral/PeripheralInquiredType.java`**  
PAIRING_DEVICE_MANAGEMENT_CLASSIC_BT=0, SOURCE_SWITCH_CONTROL=1, PAIRING_DEVICE_MANAGEMENT_WITH_BLUETOOTH_CLASS_OF_DEVICE=2, MUSIC_HAND_OVER_SETTING=3, OUT_OF_RANGE=-1

**`table2/power/PowerInquiredType.java`**  
AUTO_STANDBY=0, CARING_CHARGE_WITH_THRESHOLD=1, USB_SUBMERSION=2, OUT_OF_RANGE=-1

**`table2/system/SystemInquiredType.java`**  
WEARING_STATUS_CHECKER=0, REPEAT_TAP_TRAINING_MODE=1, QUICK_ACCESS_EASY_SETTING=2, AUTO_VOLUME_OPTIMIZER=3, AUTO_VOLUME_WITH_LIMITATION=4, SONY_VOICE_ASSISTANT_SETTING_MTK_TRANSFER_SUPPORT_LANGUAGE_SWITCH=5, SONY_VOICE_ASSISTANT_COMMAND=6, WEARING_DEVICE_INFORMATION=7, WEARING_POSITION_JUDGMENT_BY_SENSOR=8, LINK_AUTO_SWITCH_FOR_SPEAKER=9, LINK_AUTO_SWITCH_FOR_HEADSETS=10, FUNCTION_CHANGE=12, USB_BROWSER=13, LIGHTING_MODE=14, VOICE_ASSISTANT_WITH_SPECIFIC_SETUP_LINK_SUPPORT=15, LIGHTING_DEFAULT_COLOR_COLOR_TYPE=16, LIGHTING_DEFAULT_COLOR_CUSTOM_COLOR=17, OUT_OF_RANGE=-1

**`table2/voiceguidance/VoiceGuidanceInquiredType.java`**  
MTK_TRANSFER_WO_DISCONNECTION_NOT_SUPPORT_LANGUAGE_SWITCH=0, MTK_TRANSFER_WO_DISCONNECTION_SUPPORT_LANGUAGE_SWITCH=1, SUPPORT_LANGUAGE_SWITCH=2, ONLY_ON_OFF_SETTING=3, VOLUME=32, VOLUME_SETTING_FIXED_TO_5_STEPS=33, BATTERY_LV_VOICE=48, POWER_ONOFF_SOUND=49, SOUNDEFFECT_ULT_BEEP_ONOFF=50, OUT_OF_RANGE=-1
