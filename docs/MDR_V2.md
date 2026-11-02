# Sony MDR v2 in Hush

Протокол v2 нужен для WH-1000XM5/XM6, WF-1000XM4/5/6, LinkBuds, ULT WEAR, WH-CH720N, WF-C700N и более поздних моделей.
Код лежит в `core/src/main/java/com/occaecat/hush/mdr/v2/`. Исследование: `research/v2/` (SUMMARY.md, decompile.md, github.md).

**Статус: на v2-железе не проверялось.** У нас есть только WH-1000XM4, а это v1. Реализация проверена тремя способами:
- реальные захваты WF-1000XM5 6.1.0 и WF-LC900 (LinkBuds Clip) 2.0.3 из mos9527/SonyHeadphonesClient (MIT), `core/src/test/resources/mdr/WF-*`;
- классы сообщений самого Sound Connect из `tools/sonyref/sound-connect.jar`, `SonyReferenceV2Test`. Каждый запрос Hush и каждый захваченный ответ разбирается фабрикой Sony `mdr.v2.table1/2.c$b`, и ни один не отвергнут;
- симулятор (`HeadsetSimV2Test`): контроллер проходит весь старт сессии и отправляет команды на профиль WF-1000XM5.

## Как устроено

- **Транспорт.** RFCOMM, кадр тот же, что у v1 (`Frame.kt`). `BluetoothMdrLink` выбирает сервис по `BluetoothDevice.getUuids()`, как Sound Connect (`ep0/a`):
  - есть `956C7B26-D49A-4BA8-B03F-B17D393CB6E2` → сначала v2;
  - есть только `96CC203E-…` → v1, как раньше;
  - UUID неизвестны → v1, затем v2.

  У XM4 в SDP только v1 UUID, это проверено по `dumpsys bluetooth_manager`. Поэтому для XM4 ничего не изменилось.
- **Определение поколения.** Оно задаётся сервисом (`MdrStreams.v2` → `MdrConnection.v2` → парсер `MdrV2`). Подстраховка: если на v1-сервисе пришёл 8-байтовый `CONNECT_RET_PROTOCOL_INFO`, сессия переключается на v2, и наоборот (`HeadphonesController.initializeV1/V2`).
- **Модель.** Она общая с v1. `MdrV2.parse` отдаёт те же `MdrEvent`, что и v1, поэтому UI не менялся:
  - `SupportedFunctions` для v2 приходит двумя списками (таблицы 1 и 2). Контроллер склеивает их в `V2Functions` и кладёт в `state.functions` v1-эквиваленты (`V2Functions.v1Equivalents`), так что `has(FunctionType.X)` работает как прежде;
  - сырые коды лежат в `state.v2Functions`.
- **Команды.** Интерфейс `MdrProtocol` (`mdr/Protocol.kt`) с двумя реализациями: `V1Protocol` (прежние билдеры) и `V2Protocol` (собирается на сессию из функций, потому что inquired type зависит от модели). `null` означает, что у поколения такой команды нет.
- **Init** (`v2/Init.kt`, по `vv/e.java` и libmdr):
  1. protocol info;
  2. capability, device info 1–3, а также 4, если версия ≥ 0x03001004 и не 0x03002013/14 (`k(v)` Sound Connect);
  3. support function T1, затем T2, если байт 7 = ENABLE;
  4. для каждой объявленной функции: сначала capability, потом значения.

## Что поддержано

| Функция | v2 | Источник раскладки |
|---|---|---|
| protocol / capability / device info / support function T1+T2 | `00`–`07` | decompile, захват XM5 |
| Батарея single / L-R / кейс (+ threshold-варианты) | `22`/`23` тип 0,1,2 / 8,9,A | libmdr, захват XM5 |
| Кодек, соединение наушников, upscaling indicator | `12`/`13` тип 2,1,3 | libmdr, GB-тест кадра, захват |
| NC/ASM: типы 0x17, 0x19 (NA), 0x16, 0x15, 0x14, 0x13, 0x22, 0x01 | `60/61`, `66/67`, `68/69` | decompile (`sn0/`, `un0/b`), libmdr, ohm LinkBuds S, захват |
| EQ preset / non-customizable / ULT / errorcode, EBB | `50/51`, `56/57`, `58/59` | libmdr, захваты XM5 и LC900 (10 полос, 13 шагов) |
| DSEE (upscaling), режим соединения (качество/стабильность) | `e0`–`e8` тип 1, 0/2 | libmdr, захват |
| Pause when taken off | `f6/f7/f8` тип **1** | Sound Connect `V2ControlByWearingRepository` (KMP): `f8 01 <ProtocolOnOff>` |
| Speak-to-Chat TYPE1/TYPE2 + детали | `f8 0c on preview`, `fc 0c sens out` | GB/SB/BL сходятся, libmdr |
| Auto power off (по времени / с датчиком ношения) | `20/21`, `26/27`, `28` тип 4/5 | libmdr, захват XM5 (тип 5) |
| Назначаемые кнопки (ASSIGNABLE / WITH_LIMITATION) | `f0/f1`, `f6/f7`, `f8` тип 3/0x0E | libmdr, захват XM5 |
| Vibrator | `f8 00` | libmdr |
| General settings 1–4 | `d0`–`d9` | libmdr, захват XM5 (SIDETONE, MULTIPOINT) |
| Плеер: статус, громкость, track/album/artist, управление | `a0`–`a9` | libmdr, захват |
| NC optimizer (personal / barometric / оба) | `80`–`89` тип 0/1/2 | decompile `vn0/`, `DeviceCapabilityTableset2Builder` (секунды: всего, personal, barometric) |
| Fixed-message alerts | `94 00 00`, `98 00 type act`, `99` | libmdr |
| T2: список источников, pairing mode, connect/disconnect/unpair | `30`–`3d` тип 0/2 | libmdr, захват XM5 (тип 2, с CoD) |
| T2: голосовые подсказки on/off, capability (языки), язык (тип 2), громкость −2..+2 (тип 0x20/0x21) | `40`–`49` | libmdr, GB, захват XM5; decompile `ap0/` (r/s/t/u, k0, l0) |
| EQ band info (частоты полос) | `5a/5b` | decompile `jn0/c`, `e0`, `kn0/b` (как v1) |
| Listening mode: Background Music (+ размер комнаты), Cinema, 360 Upmix Cinema/Game/Music, Voice boost, Sound leakage reduction | `e6`–`e9` тип 3/9, 4, 0xA, 6, 7 | decompile `fn0/` (a1, g1, h1, j1, f1; ответы h0, m0, n0, p0, l0) |
| Приоритет соединения с LE Audio (low latency, beta) | `e6`–`e8` тип 5 | decompile `fn0/c1`, `i0`, `QualityPriorValue` |
| Max Volume Control (вкл + уровень 0..4) | T2 `52`–`59` тип 5 | decompile `uo0/` (p, w), `to0/` (g, j) |
| UPDT: category, service id, nation, language, serial | `36/37` тип по FW_UPDATE_* | decompile `ho0/b`, `q`, `r` |

## Решения по расхождениям (SUMMARY §3)

- **Pause when taken off.** Используем SYSTEM тип 1 (`f8 01`), как libmdr и Sound Connect KMP. Вариант Gadgetbridge `26 01` в v2 означает LEFT_RIGHT_BATTERY.
- **Auto power off.** Тип 5 — `AUTO_POWER_OFF_WEARING_DETECTION`, тип 4 — по времени. Выбор идёт по FunctionType (0x24 / 0x25), как в libmdr. XM5 отвечает типом 5.
- **NC.** Тип 0x15 и 0x17 — это разные функции (AUTO и DUAL). Тип выбирается по FunctionType (`NcAsmV2.typeFor`), а не по модели. Вариант Gadgetbridge с WNR-байтом не реализован. Ветер = тип 0x16 с NcValue ON_SINGLE, это `NcAsmState.ncOnValue = 1`, как в v1.
- **EQ.** Число полос и шагов берётся из capability. Ровный уровень `HeadphonesState.eqFlat = (steps−1)/2`: 10 для 6 полос, 6 для 10 полос.
- **OnOffSettingValue в v2 инвертирован:** ON = 0, OFF = 1 (`v2/OnOffSettingValue.java`, `ProtocolOnOff`). То же для GsSettingValue. GsSettingType: BOOLEAN = 0, LIST = 1 (в v1 это 1/2).
- **AlertMessageType в v2 сдвинут:** 0 = connection mode … 3 = assistant, а в v1 это 1…4. Парсер переводит коды в v1 (`Alert.toV1`), `V2Protocol.alertAnswer` переводит обратно. Начиная с 5, коды совпадают.

## Не проверено / не сделано

- Ни одна запись на v2-устройство не проверена вживую. Sound Connect принимает форму payload, но смысл значений проверен только там, где он виден в захватах или совпадает в нескольких проектах.
- NcValue при ambient для типов с MODE (0x15/0x16): Hush шлёт запомненное значение NC. Хвост noise adaptation (0x19) отправляется обратно в том виде, в каком пришёл.
- Не реализовано для v2:
  - adaptive sound (SENSE): в v2 `74 type START_SETTING/END_SETTING` — это транзакция редактирования настроек в приложении, а не v1-шное «начать адаптацию», поэтому `senseStart()` = null;
  - VPT/sound position, power saving, training mode (в v2 таких команд нет);
  - собственно перепрошивка (UPDT transfer): Hush её не делает и для v1. Параметры UPDT читаются, так что серийник, Authenticity и проверка обновлений работают как на v1;
  - Safe Volume Control и журнал звукового давления (safe listening типы 0–4): Sound Connect не запрашивает их статус, только получает уведомления;
  - quick access (сервисы Sony-партнёров на двойное/тройное касание), head gestures, multipoint source switch, Auracast.
- Режимы прослушивания: каждый на наушниках — отдельный переключатель; как в Sound Connect, Hush показывает их одним выбором и сначала выключает старый режим, потом включает новый. Взаимоисключение на стороне наушников не проверено.
- Общий EQ (`SonyEqualizer` → `EqUi.range`) берёт ровный уровень из capability: ±10 для 6 полос, ±6 для 10 полос/13 шагов. Sony-специфичные страницы (AutoEq, Find Your Equalizer, hearing test, `EqShape`) по-прежнему только для 6 полос ±10; AutoEq на других числах полос сам отказывает.
- Self test (`HeadsetProbe`) на v2 только собирает журнал сессии: его проверки пишут v1-команды.
- Фото: `SonyArt` знает рендеры всех 17 v2-моделей каталога по цветам, у TWS ещё левый/правый наушник и кейс (тот же GraphQL-запрос, что для v1, docs/MDR_V1.md §5). Проверено только, что ссылки отдаются.
- Нужен хотя бы час с XM5 или WF-1000XM5: проверить `getUuids()` (отдают ли они ещё и v1 UUID), init, NC, EQ, STC, pause, auto power off и multipoint.
