# Sony MDR v2: публичные источники (web research)

Дата сбора: 2026-10-21. Фокус: write-ups, документация, обсуждения, спецификации. GitHub-исходники отдельно не разбирались (только ссылки из write-ups).

Легенда уверенности: **[H]** высокая (первоисточник/прямая цитата), **[M]** средняя (вторичный источник), **[L]** низкая (догадка или противоречие).

## 1. Источники

| # | Источник | URL | Дата | Что дает |
|---|---|---|---|---|
| S1 | mdr-protocol (Andreas Olofsson), README-документация | https://github.com/AndreasOlofsson/mdr-protocol | не указана (2 коммита) | Frame, Tandem, DataTypes, Ack, RFCOMM/UUID. Только v1-модели |
| S2 | SonyHeadphonesClient (Plutoberth), README | https://github.com/Plutoberth/SonyHeadphonesClient | архивирован, дата не указана | Статус XM3 (полностью), XM4 (частично), XB950BT. Протокол не описан |
| S3 | Gadgetbridge, issue #3341 (WF-1000XM5) | https://zff.dev/Freeyourgadget/Gadgetbridge/issues/3341 | 2023-09-27 открыт, коммит 2026-05-23 | Классический BT-MAC, btsnoop, «слегка новее XM4», список имен фич |
| S4 | Gadgetbridge, страница Sony headphones | https://gadgetbridge.org/gadgets/headphones/sony | 2026 (актуальная) | Таблица уровня поддержки по моделям и фич |
| S5 | SonyCompanionApp (whitebeard10), README/FAQ | https://github.com/whitebeard10/SonyCompanionApp | не указана | Деление на v1 и v2, транспорт RFCOMM (AF_BTH) |
| S6 | SoundGuys: Sony Bridge (Mac), Tushar Mehta | https://www.soundguys.com/sony-bluetooth-headphone-control-mac-os-160060/ | 2026-07-14 (по данным страницы) | Списки v1/v2 моделей, фичи v2 |
| S7 | Android Authority: открытое приложение Sony (форк SonyBridge, MIT) | https://androidauthority.com/sony-open-source-headphone-app-3710109 | 2026 (по поиску), страница 403 | Авто-определение версии протокола, DSEE Extreme; текст не прочитан |
| S8 | Routenote / Loud'n Wireless / basic-tutorials.de: firmware WH-1000XM6 3.0.0, WF-1000XM5 6.0.0 | https://routenote.com/blog/sonys-wh-1000xm6-and-wf-1000xm5-get-audio-sharing-in-new-update/ ; https://www.loudnwireless.com/blog/new-firmwares-sony-wh-1000xm6-300-and-wf-1000xm5-600- | 2026 (по поиску) | LE Audio + Auracast, Find Hub, re-pair |
| S9 | Headphonesty: WF-1000XM5 LE Audio support | https://www.headphonesty.com/2024/04/sony-wf-1000xm5-le-audio-support-update/ | 2024-04 (по URL), страница 403 | Только заголовок, текст не прочитан |
| S10 | Sony Help Guide, WH-1000XM6 (2984) | https://helpguide.sony.net/mdr/2984/v1/en/print.pdf | 2026 | Speak-to-Chat, Quick Access, head gestures, Auracast (только TOC) |
| S11 | Sony Help Guide, LE Audio pairing Android | https://helpguide.sony.net/mdr/2963/v1/en/contents/TP1001106279.html | актуальная | LE Audio на WF-1000XM5 (Android) |
| S12 | Sony Sound Connect (JP) support page | https://kunkoku.jp/soundconnect-2.html (вторичн.); Sony support 00369422 | 07/09/2026 (Sony) | Совместимость, переименование приложения |

## 2. Факты о протоколе

### 2.1 Фрейминг MDR v1 (базовый, известный)

- **[H]** Start byte `0x3E` (`<`), stop byte `0x3C` (`>`). Приемник ищет start, мусор вне кадров отбрасывает (S1).
- **[H]** Escape `0x3D`. Экранирование: `0x3C` → `3D 2C`, `3D` → `3D 2D`, `3E` → `3D 2E`. Экранирование = AND `0xEF` (сброс 5-го младшего бита), распаковка = пропуск `3D` и OR `0x10` следующего байта (S1).
- **[H]** Поля кадра: DataType (1 б, off 0x00), Sequence ID (1 б, off 0x01), длина N (4 б big-endian, off 0x02), payload N байт (off 0x03), checksum (1 б, на 0x03+N) (S1).
- **[M]** Checksum: «simple 1-byte wrapping addition of every other byte in the frame (unescaped)». Какие именно байты: не уточнено (S1). Требует проверки на btsnoop.
- **[H]** DataTypes с описанием: `Ack` 0x01, `DataMdr` 0x0C, `DataMdrNo2` 0x0E. Без описания: Data 0x00, DataMcNo1 0x02, DataIcd 0x09, DataEv 0x0A, DataCommon 0x0D, Shot 0x10, ShotMcNo1 0x12, ShotIcd 0x19, ShotEv 0x1A, ShotMdr 0x1C, ShotCommon 0x1D, ShotMdrNo2 0x1E, LargeDataCommon 0x2D (S1).
- **[H]** Sequence ID: 0x00/0x01 переключается на каждый не-ACK кадр. Ответ: пустой Ack с инвертированным SeqID. Без ACK кадр переотправляется с тем же ID (S1).
- **[M]** Tandem: «синхронизация + тег под-протокола». Внутри Tandem-кадра может быть несколько MDR-пакетов, и кадр может быть разорван между RFCOMM-пакетами, поэтому поток трактуется как непрерывный (S1). Автор сам пишет «(I think)».
- **[H]** Транспорт: RFCOMM, канал находится через SDP по UUID `96CC203E-5068-46AD-B32D-E316F5E069BA`. Номер канала зависит от модели (9 для WF-3000XM3, 15 для WH-3000XM3 по S1) (S1).
- **[H]** Формат MDR-пакетов описан в отдельном файле `MDR_packets.md`, в доступной части не включен (S1). Структуры смотреть в libmdr/mdrd (упомянуты в S1).
- **[L]** Init/handshake: в S1 явной последовательности нет, только открытие канала и обмен Ack/SeqID.

### 2.2 Разделение v1 и v2

- **[M]** S5: v1 = WH-1000XM3, WH-1000XM4. v2 = WH-CH720N, WH-1000XM5, WF-1000XM5, WF-C700N. Транспорт RFCOMM (FAQ), BLE не упоминается.
- **[M]** S6 (Sony Bridge): v2 = WH-1000XM5, WH-1000XM6, WH-XB910N, WH-CH720, WH-CH520, WF-1000XM4, WF-1000XM5, WF-C700N, LinkBuds S. v1 = WH-1000XM4, WH-1000XM3, WH-1000XM2, WH-XB900N, MDR-XB950BT. v2 даёт EQ и точный % батареи; v1 только ANC.
- **[L] Противоречие:** S5 относит WH-1000XM4 к v1, S6 относит WF-1000XM4 к v2. Возможно, v1/v2 различаются по семейству (over-ear vs TWS), а не по поколению. Нужна проверка на устройствах.
- **[H] (S3)** Maintainer Gadgetbridge (joserebelo) по btsnoop WF-1000XM5: «протокол XM5 — слегка новее XM4». Это подтверждает, что v2 — это эволюция того же семейства, а не отдельный протокол.
- **[M] (S3)** В btsnoop с WF-1000XM4 протокол совпадал с XM4 (2023-09-29).

### 2.3 Транспорт для v2

- **[M] (S5)** v2 через RFCOMM (SPP-подобный классический BT).
- **[H] (S3)** Для WF-1000XM5 в Gadgetbridge работает **Bluetooth Classic MAC**. Отдельная запись `LE_WF-1000XM5` — BLE-only, используется только для sensor-данных Service Link, для pairing и управления не подходит. Наблюдалось: Sony переименовывает устройство в `LE_WH-1000XM5` после pairing.
- **[H] (S3)** Ошибка подключения «headphones aren't reporting the expected service on connection» — то есть на устройстве не находится ожидаемый сервис (вероятно, UUID/SDP отличается у v2). Точное имя сервиса не найдено.
- **[M] (S8)** LE Audio и Auracast на WF-1000XM5 и WH-1000XM6 появились в обновлении (firmware WH-1000XM6 3.0.0, WF-1000XM5 6.0.0). Требуется re-pair. Head tracking и Gemini Live работают поверх LE Audio. LE Audio — отдельный транспорт, управление через него не описано.
- **[L]** GATT-сервисы/характеристики для управления в открытых источниках не найдены ни для v1, ни для v2.

### 2.4 Команды и opcodes

- **Ни один найденный источник не публикует таблицу opcodes для v2.** Что известно только по именам фич (S3, из логов пользователей, не из текста протокола): AmbientSoundControl, WindNoiseReduction, AdaptiveVolumeControl, AmbientSoundControlButtonMode, EqualizerWithCustomBands, PowerOffFromPhone, SpeakToChatConfig, SpeakToChatEnabled, SpeakToChatFocusOnVoice, VoiceNotifications. **[M]** Эти имена — вероятно, внутренние имена feature-флагов Sony, полезны как ключи для поиска.

## 3. Feature matrix (Sound Connect)

Источник матрицы: S4 (Gadgetbridge), S6, S8, S10. Колонка «транспорт» — что подтверждено; «[?]» — не найдено.

| Модель | Поколение протокола | Транспорт (подтв.) | Фичи (подтв. управляемые через приложение) | Пробелы в Gadgetbridge |
|---|---|---|---|---|
| WH-1000XM4 | v1 (S5) | RFCOMM [H] (S1) | ANC/Ambient, EQ, touch, button action, speak-to-chat on/off, DSEE | App power-off, CUSTOM button, dual-device |
| WH-1000XM5 | v2 (S5, S6) | RFCOMM [M] (S5) | ANC/Ambient, Focus on Voice, EQ 5-band + presets, DSEE, battery per side/case, speak-to-chat | Multipoint, adaptive NC, app power-off, upsampling, ambient button mode, touch toggle, Spotify trigger, voice assistant |
| WH-1000XM6 | v2 (S6) | RFCOMM [M] (S6); LE Audio + Auracast после FW 3.0.0 [M] (S8) | Battery, ambient, speak-to-chat, auto power-off, pause on removal, Quick Access (2–3 taps, Spotify/Endel), head gestures (call answer, Auto Play), Auracast sharing, multipoint, audio switch [M] (S10) | Upsampling (DSEE Extreme), voice notifications, touch sensor, EQ |
| WH-CH720N | v2 (S5, S6) | RFCOMM [M] (S5) | Battery, ambient, EQ (presets + custom), power off | Ambient button mode, timed auto power-off, volume |
| WH-ULT900N | не указано | [?] | Battery, ambient, quick access, voice notif., NC/ambient button config, upsampling, LDAC, auto power-off, touch, 2 devices, service link | EQ не работает, volume |
| WF-1000XM4 | v2 по S6, v1/v2 неясно | [M] Classic (S3 ref. device) | Полный список из Gadgetbridge (highly) | — |
| WF-1000XM5 | v2 (S5, S6) | Classic [H] (S3); BLE только sensor (S3); LE Audio после FW 6.0.0 [M] (S8) | ANC/Ambient, EQ, Find Hub (через Classic, S8), Auto Switch, Auracast/LE Audio sharing, head tracking (LE Audio) | Ambient off, earbud volume, touch |
| WF-C700N / C710N | v2 (S5, S6) | RFCOMM [M] (S5) | Battery (case + each), EQ, upsampling, power off, ambient, button modes | — |
| LinkBuds S | v2 по S6 | [?] | — | Multipoint, adaptive NC, app power-off |
| LinkBuds | не указано | [?] | Spatial sound optimization отсутствует в Gadgetbridge | Spatial, factory reset |
| INZONE Buds | не найдено | [?] | — | — |

Фичи, которые искались по запросу, но в открытых источниках не подтверждены (по моделям): **Speak-to-Chat** (есть на XM6, XM5 частично), **Quick Access** (XM6, ULT), **head gestures** (XM6), **Auracast/LE Audio** (XM6, WF-XM5 с FW), **360RA, safe listening, ear tip test, auto play** — упомянуты в помощи Sony только косвенно, детали не открыты.

## 4. Юридические / ToS заметки

- **[L]** Ни один найденный источник не разбирал ToS Sony относительно reverse engineering. Практика сообщества (S1, S2, S5, S6, S7) — открытые проекты, MIT-лицензия у S5/S7 (форк SonyBridge), явный дисклеймер «не аффилирован с Sony» у S2.
- **[M]** Проект Plutoberth архивирован; автор говорит, что приложение продолжит работать для поддерживаемых моделей (S2).
- **[M]** Gadgetbridge (S3, S4) использует btsnoop-захваты, которые предоставляют пользователи, это стандартная практика сообщества.
- Вывод: для Hush надежнее опираться на собственные btsnoop-захваты и на протоколы из открытых проектов как на справку, не копируя код без проверки лицензии.

## 5. Открытые вопросы

1. **Frame v2:** сохраняется ли Tandem-обертка (`0x3E`…`0x3C`, escape `0x3D`) и тот же checksum, или v2 использует другой фрейм? Не найдено ни в одном источнике. Проверить btsnoop с XM5.
2. **Opcodes/DataType для v2:** таблица не опубликована. Вероятный путь: сравнить btsnoop XM4 vs XM5 при одинаковых действиях (ANC, EQ, speak-to-chat), как предлагал maintainer в S3.
3. **Версионирование протокола:** как приложение определяет версию (S7 пишет «auto-detect»)? Вероятно, по SDP/service record или по ответу на первый запрос — не подтверждено.
4. **Service UUID v2:** отличается ли от `96CC203E-...` v1? S3 упоминает отсутствие ожидаемого сервиса, значит, вероятно да.
5. **Противоречие v1/v2 для XM4 и WF-XM4** (см. 2.2).
6. **LE Audio/Auracast:** управляется ли через GATT отдельно, или только audio-транспорт? Не найдено.
7. **Тексты S6 и S7** содержат даты 2026-07-14, которые не подтверждены вторым источником. Проверить.
8. **Ограничение источников:** Android Authority (S7) и Headphonesty (S9) вернули 403, текст не прочитан, только поисковые сниппеты.
9. **Sony Bluetooth SIG listings:** не проверялись (не нашлись в поиске). Нужен отдельный поиск по `bluetooth.com` / qualified listings для XM5/XM6.
10. **Пункт «Find My Device»** на WF-XM5 работает через Classic (S8), не BLE, это важно для Hush.

## 6. Рекомендации для Hush (кратко)

- Для v2 брать Classic RFCOMM, UUID искать через SDP на устройстве, а не хардкодить.
- Для v1 использовать S1 как спецификацию фрейма (она подтверждена хотя бы одним источником).
- Для v2 получить btsnoop с XM5/WF-XM5 при переключении ANC, EQ, speak-to-chat, и сравнить с XM4.
- Имена фич из S3 использовать как ключи для поиска в логах.
