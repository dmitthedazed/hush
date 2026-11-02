# MDR v2 и переносимые функции — сводка исследования (2026-10-21)

Источники (подробности, ссылки, пути к классам — там):
- `decompile.md` — Sound Connect (jadx): UUID, PROTOCOL_INFO, все opcode T1/T2 (прил. A), FunctionType (прил. B), init-последовательность.
- `github.md` — открытые проекты, лицензии, таблица opcode v2, расхождения, квирки моделей, pitfalls.
- `web.md` — статьи/форумы, матрица функций по моделям, открытые вопросы.
- `../portable-features.md` — функции «только для некоторых моделей», которые можно сделать на телефоне для всех.

## 1. Транспорт и определение поколения
- Управление v2 — Classic RFCOMM, как v1. BLE (запись `LE_…`) — только сенсоры/GATT, LE Audio/Auracast — отдельно.
- **Поколение выбирается по SDP UUID, а не по ответу:**
  - v1 (TABLE_SET_1): `96CC203E-5068-46AD-B32D-E316F5E069BA`
  - v2 (TABLE_SET_2): `956C7B26-D49A-4BA8-B03F-B17D393CB6E2`
  - Sound Connect пробует v2 первым (`lm0/i.java`, `ep0/a.java`, `h1.java:259`). Gadgetbridge дополнительно смотрит длину init-ответа (4 байта → v1, 8 → v2).
- Hush сейчас подключается только к v1 UUID (`core/.../mdr/MdrConnection.kt:57`) и сдаётся при v2-рукопожатии (`HeadphonesController.kt:~430`, `protocolV2`).
- Не проверено на железе: какие UUID отдают XM5 / WF-1000XM4 (`BluetoothDevice.getUuids`), отдают ли v2-модели также v1 UUID.

## 2. Фрейм
- Идентичен v1 и нашему `Frame.kt`: `3E | escape(TYPE | SEQ | LEN32BE | PAYLOAD | SUM8) | 3C`, escape `3D` (`& 0xEF` / `| 0x10`).
- TYPE `0x0C` = таблица 1, `0x0E` = таблица 2, `0x01` = ACK (SEQ = 1 − входящий).
- Payload: `[opcode][inquired type]…`.
- CONNECT_RET_PROTOCOL_INFO v2 = 8 байт: `01 00 <version Int32BE @2> <e6 @6> <e7 @7>`; байты 6/7 = флаги «есть таблица 1/2» (`0x00` = enable), от них зависит, запрашивать ли support-function для T1/T2. Список допустимых версий — 76 значений (`vv/e.java:63`).
- Support function RET: `07 00 N (code, value)×N` (у v1 только коды). Смысл `value` не разобран.

## 3. Команды v2 (совпадают у libmdr, Gadgetbridge, SonyBridge, BudsLink)
| Функция | Opcode (get / ret / set / notify) | Примечание |
|---|---|---|
| Батарея | `0x22` / `0x23` | раскладки single / L-R / case — см. github.md §4 |
| NC/ASM | `0x66` / `0x67` / `0x68` / `0x69`, тип `0x17` (у части проектов `0x15`) | seamless ASM подтверждён в декомпиле |
| EQ | `0x56` / `0x57` / `0x58` | 6 или 10 полос, смещение +10 или +6 — расходятся |
| DSEE / LDAC (audio) | `0xE6` / `0xE8` | |
| System (STC, APO, …) | `0xF6` / `0xF8` | Speak-to-Chat: `f8 0c`, бит инвертирован |
Полные таблицы: decompile.md прил. A (T1 ~157, T2 ~85 opcode), github.md §4.

**Расхождения (решать по железу):** тип `0x05` у auto power off (wearing vs by-time); pause-when-taken-off (`26 01` у GB vs `F6 01` у libmdr); NC тип `0x15` vs `0x17`; GB-опкоды `0x96–0x99`, `0xB6–0xB9`; число полос EQ.

**Не разобрано:** точные раскладки battery, power off, EQ-значений, playback, GATT-кадров (`ph/m.java`) — decompile.md §6.3.

## 4. Init (Sound Connect, `vv/e.java` call(), строки 1210–1600)
protocol info → capability → device info → support function (T1, затем T2 по флагам) → по каждой функции цепочка get/capability. Соответствие FunctionType → запрос: decompile.md §4.3.

## 5. Pitfalls
- Ответ может прийти раньше ACK; держать один запрос в полёте; SEQ меняется только по нашему ACK.
- WF-C700N FW 2.0.5 принимает запись NC без эффекта (исправлено в 2.0.7).
- EQ: пресет и полосы в одном кадре нельзя.
- LE Audio блокирует часть функций; приоритет соединения на XM5 FW 2.5.1 требует снятия валидации.

## 6. Код, который можно использовать
- **mos9527/SonyHeadphonesClient (libmdr), MIT** — лучший источник: полные структуры v2 T1/T2, тесты на захватах (WF-1000XM5, WF-LC900, WH-1000XM3), проверено на WH-1000XM5 2.5.1 и WF-C700N. Можно брать с атрибуцией (Hush — MIT).
- SonyBridge (MIT), sony-headphones-linux (MIT, вендорит libmdr) — с атрибуцией.
- Gadgetbridge (AGPL-3.0), BudsLink (GPL-3.0) — **только читать**, не копировать.
- Захваты из тестов libmdr годятся как фикстуры для `core/src/test` (по аналогии с `resources/mdr/WH-1000XM4-3.0.1`), если лицензия позволяет.

## 7. Модели
- v1 (Hush уже поддерживает): WH-1000XM2/XM3/XM4, WF-1000XM3, WF-SP800N, WI-SP600N, WI-C100, WI-1000XM2, WH-H910N, WH-XB900N, WH-CH710N.
- v2: WH-1000XM5/XM6, WF-1000XM4/XM5/XM6, LinkBuds/S/Fit/Open/Clip, ULT Wear, WH-CH720N, WF-C700N/C710N/C510, INZONE Buds и др. Принадлежность WF-1000XM4 в источниках спорная — проверять по UUID.
- LE Audio / Auracast пришли прошивками WH-1000XM6 3.0.0 и WF-1000XM5 6.0.0; управление ими не описано.
- **Железа v2 у нас нет** (есть только XM4 = v1). Реализация v2 проверяется тестами на чужих захватах; нужен хотя бы час с XM5 / WF-1000XM4/5.

## 8. План реализации v2 в Hush
1. `MdrConnection`: выбор UUID по `getUuids()` (v2 первым, как Sound Connect), fallback на v1.
2. `core/mdr/v2/`: `Commands`, `FunctionType`, `InquiredTypes`, `Requests`, `Parser`, `Init` — выдают те же `MdrEvent`/модель (`mdr/Model.kt`), что v1, чтобы UI не менялся.
3. `HeadphonesController`: убрать ранний выход для v2, выбирать v1/v2 стек по версии/UUID.
4. Тесты на захватах (libmdr) + `SimHeadset`-профиль v2.
5. Каталог моделей в `SonyModule` (сейчас v2-модели — «Other Sony / not supported»), `SonyCheck` уже понимает `protocolV2`.

## 9. Функции, переносимые на все наушники (portable-features.md)
1. Автовыключение Sony по простою (есть power off из sleep timer) — S.
2. Safe listening на телефоне: уведомления + мягкий лимитер (DynamicsProcessing, как SoftEq) — S–M, все устройства.
3. Auto Play при надевании (v2 wearing) / вставке USB (EarPods) — S–M.
4. Напоминание о зарядке до 80–90 % (Battery Care) — S, низкая ценность.
5. Speak-to-Chat по микрофону телефона (VAD) — M–L, ненадёжно.
Нереализуемо на телефоне: head gestures, ear fit test, DSEE/AI-апскейлинг, персональный HRTF 360RA, NC optimizer, voice focus в звонках.

## 10. Состояние кода на момент сводки (не закоммичено)
- Authenticity для Sony BT: `core/.../diag/SonyCheck.kt` (+ `SonyCheckTest`, 12 тестов), общая карточка `ui/.../ui/Authenticity.kt` (EarPods переведены на неё), `devices/sony/.../ui/SonyAuthenticity.kt`, подключена в `DeviceTab.kt`. `FirmwareCheck` теперь хранит Model-правила и различает 404 (`NoSuchService`). На реальных XM4: 10/10, «Looks genuine». Проводные Sony проверить нечем.
- Паритет моделей: фото XM4 в уведомлениях и на экранах подключения заменено на рендер модели (`ModelDisk`, `notificationIcon` в `SonyComponents.kt`); AutoEq-метка привязана к адресу наушников (`AutoEq.Applied.owner`); демо-адрес → Sony OUI. Последние два не проверены на телефоне.
- Остались: Wear/TV иконки и список CUSTOM-кнопки от XM4 (`tv/.../DeviceScreen.kt:111`), фолбэк `XM4_FUNCTIONS` (`HeadphonesController.kt:443`), значок в выборе устройства (`EntryScreens.kt:119`).
- На телефоне стоит **debug**-сборка — вернуть release (см. HANDOFF.md).
