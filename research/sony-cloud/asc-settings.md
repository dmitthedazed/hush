# Adaptive Sound Control (ASC): места, настройки, обучение и Settings takeover (STO) в Sound Connect

Пути:
- `SRC` = `/home/dimka/Projects/sound-connect-decompile/jadx-output/sources`
- `J2O` = `SRC/com/sony/songpal/mdr/j2objc/application`
- Телефон читался только на чтение (adb pull во временную папку, сразу удалено). Координаты, MAC-адреса, идентификаторы и токены в отчет не включены.

Метки: **[H]** прямо в коде/данных, **[M]** вывод из кода, **[L]** не проверено или гипотеза.

---

## 1. ASC: модель данных

### 1.1 Места (places)
- Доменная модель: `J2O/adaptivesoundcontrol/Place.java`. Поля: marker (None/Detection/Added/Deleted), `PlaceType`, id, `c0` (геоточка), `GeoFenceRadiusSize`. [H]
- `PlaceType` (внутренний): Unknown, Other, Home, Work, Station. [H] (`J2O/adaptivesoundcontrol/PlaceType.java`)
- `PlaceDisplayType` (что видит пользователь): home, office, school, trainstation, busstation, gym, other. [H] (`J2O/adaptivesoundcontrol/PlaceDisplayType.java`)
- Радиус геофенса: `GeoFenceRadiusSize` = SMALL 50 м, MEDIUM 100 м, LARGE 200 м. [H] (`J2O/adaptivesoundcontrol/GeoFenceRadiusSize.java`)
- Переключение: `PlaceSwitchingType` = auto / manual. [H] (`J2O/adaptivesoundcontrol/PlaceSwitchingType.java`)
- Запись места в хранилище: `SRC/oq/r0.java` (id, displayType, lat, lng, name, radius). [H]
- Геоточка: `SRC/oq/c0.java` (time, lat, lng, accuracy, name). [H]
- Добавление/правка/удаление: `J2O/adaptivesoundcontrol/MyPlaceEditor.java` (`a(PlaceDisplayType, lat, lng, name, radius)`). [H]
- Wi-Fi/SSID/BSSID в ASC и contextlib не найдены (grep). Определение места: геофенс через Google `GeofencingClient` (`SRC/com/sony/songpal/contextlib/location/GeoFenceManager.java`) + собственный детектор `SRC/com/sony/songpal/contextlib/PlaceDetector.java`, нативный UAD `SRC/com/sony/sai/unifiedactivitydetector/` (`Place/PlaceEvent`, `GeofenceEvent`). [H] для геофенса, [M] для роли UAD.

### 1.2 Настройки по месту (per place)
Класс `SRC/oq/f.java`. JSON-ключи (`fromJSON/toJSON`): [H]
- `placeId` (int), `placeEnabled` (bool), `placeDisplayType` (string), `placeSwitchingType` (string, optional)
- `autoNcAsmPersistentData` (объект, см. 1.3)
- `equalizerData` (string, формат не декодирован [L]), `smartTalkingMode` (bool)
- флаги ожидания применения: `needsApplingNcAsm`, `needsApplingEqulizer`, `needsApplingSmartTalkingMode` — значит, настройки применяются телефоном при подключении/срабатывании места. [M]

### 1.3 Настройки по активности (per activity) и NC/ASM
Класс `J2O/autoncasm/a.java`. JSON-ключи (`o()`, `h(JSONObject)`): [H]
`ncAsmEffect`, `ncAsmMode` (0=NC, 1=ASM — `NoiseCancellingAsmMode`), `ncType`, `ncValue` (уровень NC), `asmType`, `asmId`, `asmRangeMin`, `asmRangeMax`, `asmRangeStep`, `asmValue` (уровень ASM), `noiseAdaptiveOnOffValue`, `noiseAdaptiveSensitivity`, `enabled`.
- Контейнер "активность → настройки": `J2O/autoncasm/b.java` (`Map<IshinAct, a>`). [H]
- Пресеты по умолчанию: `J2O/autoncasm/AutoNcAsmPersistentDataFactory.java`. Настраиваемые активности: **LStay, Walk, Run, Vehicle**. `Stay` в этом наборе нет. [H]
- Активности: `J2O/adaptivesoundcontrol/IshinAct.java`: Stay ("stay"), LStay ("long_stay"), Walk ("walk"), Run ("run"), Vehicle ("vehicle"), None. [H]
- Значения NC/ASM в протоколе: `SRC/com/sony/songpal/mdr/j2objc/tandem/features/ncasm/` (NoiseCancellingAsmMode NC=0/ASM=1, NoiseAdaptiveSensitivity STANDARD=0/HIGH=1/LOW=2, NoiseAdaptiveOnOffValue OFF=0/ON=1). [H]

### 1.4 Обучение ("Adaptive Sound Control optimization", ASC OPT)
- Флаг: `is_asc_opt_enabled` (STO) / `ASC_IsOptimizationEnabled` (SETTINGS_DB). [H]
- Запись обучения: Realm-класс `cj.a` (`SRC/cj/a.java`), схема Realm: `class_OptimizeConditionData`. [H]
  Поля: `act` (IshinAct), `placeId`, `ncType` (NcType: UNKNOWN/OFF/NC/ASM), `feedbackTime` (время суток, Date от эпохи 0 UTC), `prevAct`, `prevPlaceId`, `prevNcType`. Шестое поле `X()` (Date) в запросах не используется. [H]
  Смысл: лог ручных переключений NC/ASM с контекстом (место, активность, время суток) и предыдущим состоянием. [M]
- Места для обучения: Realm-класс `cj.b` (`class_PlaceData`): `placeId`, `placeName` (`SRC/cj/b.java`). Координат здесь нет. [H]
- Репозиторий: `SRC/com/sony/songpal/mdr/application/adaptivesoundcontrol/optimization/data/n.java` (записи: вставка с дедупликацией `k()`, выборка `u()`, по месту/предыдущему месту), `q.java` (места).
- Файлы Realm: `asc_opt_main_data_repository.realm`, `asc_opt_backup_data_repository.realm`, `asc_opt_restore_data_repository.realm` (`optimization/data/AscOptRealmComponent.java`). [H]
- Счетчики времени обучения в `asc_opt_preference`: `ASC_OPT_START_TIME`, `ASC_OPT_END_TIME`, `ASC_OPT_TOTAL_USAGE_TIME` (`SRC/dj/a.java`). [H]
- Правило, по которому из записей выводится предложение/авто-применение (порог, окно, частота), не найдено. [L]

### 1.5 Что уходит на гарнитуру, а что остается на телефоне
- Гарнитура получает команды NC/ASM и режима Adaptive: `SRC/com/sony/songpal/tandemfamily/message/mdr/v2/table1/ncasm/` (NoiseAdaptiveMode, NoiseAdaptiveSensitivity, NcAsmMode, NcValue, AmbientSoundMode...). [H]
- Переключение по месту и активности (геофенс, распознавание активности, `SRC/com/sony/songpal/ishinlib/`, `SRC/com/sony/songpal/contextlib/`) выполняет телефон; гарнитура получает уже выбранный режим. [M]
- Протокол не содержит команд "список мест" или "обучение" (по поиску в tandemfamily). [M]

---

## 2. Settings takeover (STO) и облако

### 2.1 Категории и транспорт
- Облачный бэкап: `J2O/settingstakeover/ScaMdcimBDAInfoImplementation.java`. Категория `b()` = **"Backup_Adaptive"**, сервис `c()` = "HPC_Backup", хост PROD/QA выбирается в `d()`. Транспорт — SDK MDCIM (`jp.co.sony.mdcim`). [H]
- Оркестрация: `J2O/settingstakeover/StoController.java`, `StoDataProcessor.java` (сборка файлов ~строки 925-950), `BackupRestoreData.java` (сравнение и выбор версии). [H]
- Триггеры авто-синхронизации ASC: `ASC_OPT_SETTING_CHANGED`, `ASC_OPT_DATA_CHANGED`, `ASC_SETTING_CHANGED` (`StoController.java` ~552-594). [H]
- Аутентификация и эндпоинты API — не разбирались (отдельный агент).

### 2.2 Формат
Пакет STO содержит именованные блобы `yq0.a(name, bytes, meta)`. [H] (`StoDataProcessor.java` ~936-948)
1. **`asc_opt_realm_backup_file`**: сырые байты Realm-файла обучения (`dl0/a.java`, `dl0/g.java`). Восстановление пишет байты в restore-Realm и подменяет основной. Версия схемы: `dl0/a.i()` = 2. [H] для процесса, [M] для версии.
2. JSON верхнего уровня `y` (`J2O/settingstakeover/y.java`): `sto_data_format_version`, `last_update_time`, `asc_settings_data`, `asc_opt_settings_data`, плюс настройки других фич (fw, ia, sbl, sl, svc, yh). [H]
3. `asc_settings_data` = класс `h.java`: `format_version` (по умолчанию 2), `last_modified_time`, `is_asc_enabled`, `is_asc_opt_enabled`, `is_activity_recognition_enabled`, `is_place_learning_enabled`, `is_notification_sound_enabled` (в формате 1 поле иное), `asc_place_array`, `asc_place_settings_persistent_array`, `auto_nc_asm_persistent_data`. [H]
   - Элемент `asc_place_array`: `palce_id` (sic, опечатка в ключе), `place_name`, `place_type` (имя enum, например Home), `latitude`, `longitude`, `geo_fence_radius_size` (метры 50/100/200, по умолчанию 100). [H]
   - Элемент `asc_place_settings_persistent_array`: JSON класса `oq/f` (см. 1.2). [H]
   - `auto_nc_asm_persistent_data`: объект "активность → JSON `autoncasm/a`" (см. 1.3). [H]
4. `asc_opt_settings_data` = `J2O/settingstakeover/b.java`: только `asc_opt_enabled`, `format_version`, `last_modified_time`. [H]

Шифрование в коде STO не найдено (нет Cipher/GZIP в пакете). Шифрование на транспорте (MDCIM) не разбирался. [M]

### 2.3 Restore/merge
- `BackupRestoreData.java`: для каждой категории сравниваются `last_modified` локальные и внешние. Режимы `RequiredType` = BACKUP / RESTORE / SYNC; для ASC-Realm решение по `f()` (метка времени данных). Побеждает более новая сторона. [H]
- Локальные метки в `STO_PREFERENCE`: `ADAPTIVE_SOUND_SETTINGS_MODIFIED_TIME`, `ADAPTIVE_OPTIMIZATION_SETTINGS_MODIFIED_TIME`, `ADAPTIVE_OPTIMIZATION_DATA_MODIFIED_TIME`, `KEY_LAST_SYNC_TIME`. [H]
- Места и настройки ASC входят в STO (ключи выше), обучение — отдельным Realm-файлом. Значит **места, NC/ASM по месту и по активности, EQ по месту и обучение импортируются из одного облачного бэкапа**. [H]

---

## 3. Локальное состояние на телефоне (root, только чтение)

- `SETTINGS_DB` (`databases/`), таблица `settings(key, value)`: [H]
  - `ASC_IsASCEnabled`, `ASC_IsActivityRecognitionEnabled`, `ASC_IsLocationDetectionEnabled`, `AutoNcASM_IsEnabled`, `ASC_IsNotificationSoundEnabled`, `ASC_IsOptimizationEnabled`. На этом телефоне ASC сейчас выключен (все флаги кроме распознавания активности выключены).
  - `ASC_UserStateSettings_{long_stay,walk,vehicle,run}` — JSON с 13 ключами из 1.3 (все числа и `enabled`). Удобно парсить напрямую. [H]
  - `AutoNcASM_ModelName_Map_<device>` и `BgFwUpdate_*` — с MAC-суффиксом (ключ на устройство, в отчет не выносить).
- Мест локально нет: в данных приложения не найдено ни одной строки с широтой/долготой/`place_name` (grep по всему `/data/data/com.sony.songpal.mdr`); `files/ContextLib` пуст. [M] (возможно, места живут только в облаке/в памяти).
- Realm: `asc_opt_main_data_repository.realm` 8192 байт, `restore` 8192 байт, `backup` 4272 байт. Размер похож на пустую или почти пустую базу. Строки не декодировались. [L]
- Бэкап ОС: `allowBackup="true"`, `dataExtractionRules` в Android 12+ разрешает облако только для двух jpg, device-transfer правил нет. [H] (`apktool-output/res/xml/`)

---

## 4. Соответствие модели Hush

Модель Hush: `hush/devices/api/src/main/java/com/occaecat/hush/scenes/SceneStore.kt`, `sound/Automation.kt`.
- `Place(id: String, name, lat, lng, radius: Float)`
- `Activity` = STILL / WALKING / RUNNING / VEHICLE
- `DeviceScenes(scenes, ascEnabled, activities: Map<Activity, SceneAction>, places: Map<placeId, SceneAction>, appsEnabled, apps)`
- `SceneAction(noise: String? (id режима устройства), level: Int?, voiceFocus: Boolean?, eq: EqPick?, volume, switches)`
- `resolve`: приложение > место > активность, поле за полем.

### 4.1 Места
| Sony | Hush | Вердикт |
|---|---|---|
| `palce_id` (int) | `Place.id` (String) | прямо, с префиксом (например `sony:<id>`) |
| `place_name` | `name` | прямо |
| `latitude`/`longitude` | `lat`/`lng` | прямо |
| `geo_fence_radius_size` (50/100/200) | `radius` (Float) | прямо. Hush по умолчанию 150, Sony 100 |
| `place_type` / `placeDisplayType` (home/office/gym…) | нет | теряется (нужно поле `type`, если важно) |
| `placeSwitchingType` auto/manual | нет | теряется (Hush всегда по геофенсу) |
| `placeEnabled` | нет | при импорте пропускать выключенные места |
| Wi-Fi | нет в Sony | не применимо |

### 4.2 Настройки по месту (`oq/f` → `DeviceScenes.places[id]`)
| Sony | Hush | Вердикт |
|---|---|---|
| `ncAsmMode` 0=NC / 1=ASM | `noise` (id режима устройства) | конверсия: нужен список режимов устройства (CANCELLING/AMBIENT) |
| `ncValue` (NC) / `asmValue` (ASM) | `level` | конверсия, клэмп по `levelRange` |
| `ncAsmEffect` (вкл/выкл NC/ASM) | отсутствует как отдельное поле | частично: `noise` пустой, если выключено [M] |
| `asmType`, `asmId`, `asmRange*` | нет | теряется |
| `noiseAdaptiveOnOffValue`, `noiseAdaptiveSensitivity` | нет | теряется |
| `smartTalkingMode` | `voiceFocus` | приблизительно [L]. Это не то же самое |
| `equalizerData` + EQ preset id | `eq: EqPick` (Preset/Library/Off) | конверсия. Custom-EQ лучше класть в EqLibrary (см. `importLegacy`), формат `equalizerData` не декодирован [L] |
| `needsApplingX` | нет | не импортировать (служебное) |
| `volume`, `switches`, apps | нет в Sony | Hush-only, не импортируется |

### 4.3 Настройки по активности (`auto_nc_asm_persistent_data` → `DeviceScenes.activities`)
| Sony `IshinAct` | Hush `Activity` | Вердикт |
|---|---|---|
| `long_stay` | STILL | наилучшее соответствие [M], но конфликтует с `stay` |
| `stay` | STILL | в Sony не настраивается по умолчанию (нет в наборе фабрики) |
| `walk` | WALKING | прямо |
| `run` | RUNNING | прямо |
| `vehicle` | VEHICLE | прямо |
| `none` | нет | не импортировать |
Одна активность Hush (STILL) не может хранить и `stay`, и `long_stay`. Если важно различие, модель Hush нужно расширить.

### 4.4 Глобальные флаги
| Sony | Hush | Вердикт |
|---|---|---|
| `is_asc_enabled` | `DeviceScenes.ascEnabled` | прямо |
| `is_activity_recognition_enabled` | нужен permission на распознавание | карта, без поля |
| `is_asc_opt_enabled`, `is_place_learning_enabled` | нет | теряется |
| `is_notification_sound_enabled` | нет | теряется |

### 4.5 Обучение (`asc_opt_realm_backup_file`, `cj.a`)
- В Hush нет обучения: правила статические. Нормальный перенос невозможен.
- Возможная конверсия: агрегировать записи по (placeId, act) и по большинству выбрать `ncType` → кандидаты в `places`/`activities` Hush. Время суток (`feedbackTime`) и `prev*` теряются. Требует декода Realm. [L]

---

## 5. Пути импорта (по приоритету)

**(a) Облако Sony через аккаунт (STO "Backup_Adaptive") — самый полный.** Дает места с координатами и радиусом, NC/ASM и EQ по месту и по активности, флаги, Realm обучения.
- Нужен декод: JSON (`asc_settings_data`, `asc_opt_settings_data`) — просто; Realm (`asc_opt_realm_backup_file`) — нужен Realm-ридер (схема v2 [M]).
- Аутентификация и API — другой агент. [H] что это единственный путь к местам с сервера, [L] что сервер отдает полный набор для этого аккаунта.

**(b) Root-чтение локального хранилища Sound Connect.** Работает уже сейчас, без сети, но данных мало:
- Читаемо: `SETTINGS_DB` `ASC_UserStateSettings_*` (NC/ASM по активности, JSON) и флаги. [H]
- Мест локально нет (см. раздел 3). [M] Realm обучения на этом телефоне пуст или почти пуст. [L]
- Чтобы получить места, нужно сначала восстановить их в приложении Sound Connect из облака — это меняет телефон, поэтому не делалось.

**(c) adb backup / Android backup.** `allowBackup=true`, но правила Android 12+ не включают данные приложения в device-transfer. Работоспособность `adb backup` [L]. Содержимое тех же файлов, что и в (b), без root — низкий приоритет.

**(d) Вручную.** Места (имя, lat, lng, радиус) и NC/ASM по активности вводятся вручную в Hush. Обучение вручную не восстанавливается.

---

## 6. Открытые вопросы (проверить)
1. Декод `equalizerData` и связь EqPresetId с EQ Hush. [L]
2. Правило обучения: порог, окно, выбор режима. [L]
3. Есть ли место в облаке при `is_asc_enabled=false` (выключенная функция). [L]
4. Формат версий 1 и 2 для `asc_settings_data`. [M]
5. Что означает `ncAsmEffect` относительно `ncAsmMode` при выключенном NC/ASM. [L]
