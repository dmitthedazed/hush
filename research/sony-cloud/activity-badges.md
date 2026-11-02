# Sound Connect 13.3.0: Activity / Your Headphones / бейджи

Источники: `jadx-output/sources` (Java), `apktool-output/res` (strings, layout, drawable, raw), телефон (только чтение: `databases/`, `files/*.realm`, `shared_prefs/` — имена ключей, без значений и идентификаторов). Уверенность: [H] прямо в коде/данных, [M] вывод из нескольких фрагментов, [L] догадка / не проверено.

Пути ниже относительно `jadx-output/sources/`, если не указано иное. Ресурсы: `apktool-output/res/`.

---

## 1. Что логируется

### 1.1 Сущности (логическая модель)

Сущности, которые мигрирует Sony (`n1.java` - список секций миграции): `ActivityDetectionCount`, `ActivityDetectionTimeEntry`, `AppLaunchCounter`, `AscCategoryEnterCount`, `Badge`, `CbOperationCount`, `EqOperationCount`, `HeadphoneDaysOfUsage`, `HeadphoneUsageDay`, `HeadphoneUsageDaysInWeek`, `HeadphoneWeeksOfUsage`, `HeadphonesUsageTimeEntry`, `NcAsmOperationCount`, `PlaceDetectionCount`, `PlaceInfo`, `PlaceStayingTimeEntry`.
Путь: `application/yourheadphones/data/n1.java` [H].

Типы счётчиков: `kmp/feature/yourheadphones/data/YhCounterType`-подобный enum `j2objc/application/yourheadphones/data/YhCounterType.java`: `EQ(1)`, `CLEAR_BASS(2)`, `NC_ASM(3)`, `USAGE_DAYS_COUNT(4)`, `USAGE_WEEKS_COUNT(5)`, `ASC_ACTIVITY_DETECTION(6)`, `ASC_PLACE_DETECTION(7)` [H].

| Что | Есть? | Где/как |
|---|---|---|
| Время прослушивания (use time) по устройству | да | `yh_use_time` (start/end, device, `is_temporary_record`) [H] |
| Время по месту (ASC places) | да | `yh_asc_use_time`, `yh_asc_place_info` (place_id, name, display type) [H] |
| Время по действию (ASC: stay / long_stay / walk / run / vehicle) | да | `yh_asc_activity_time` (action_kind, accumulated_time, device) [H]; enum `YhIshinAct`: `Stay, LStay, Walk, Run, Vehicle, None` [H] |
| Счётчики операций: EQ, Clear Bass, NC/ASM, ASC action switch, ASC place switch | да, **только счётчики, не время режима** | `yh_counter(count_type, device_id, count, last_count_time)` [H] |
| Дни использования / недели | да | `yh_headphones_usage_day` (year, month, day, week_number, device), `yh_weekly_use_time_counter` [H] |
| Дни запуска приложения | да | `yh_app_launch_counter(year, day_of_year)` [H] |
| Страны/регионы (Traveler) | да | Realm `YhCountry(countryCode, arrival, deviceInformation)` [H] |
| Устройства | да | `yh_device(device_id, device_name, model_color)` [H] |
| Badges | да | `yh_badge(badge_type, level, status, obtained_time, device_id)` [H] |
| Режим NC/ASM как время (сколько минут в NC) | **нет** | считается только число переключений (`NcAsmOperationCount`) [H] |
| Кодек, громкость, sound pressure | **нет** в Yh. Громкость/SPL есть только в Safe Listening (см. 1.4) | `sl_log_data.accumulated_sound_pressure` [H] |
| Шаги | **нет** (grep по yourheadphones/kmp: 0 совпадений) | — [H] |

### 1.2 Схема БД

**Текущее хранилище (код 13.3.0): SQLDelight** файл `your_headphones.db`, создаётся в `kmp/feature/yourheadphones/YhProcessorProvider.java` (строка ~45). Схема: `kmp/feature/yourheadphones/db/YourHeadphonesQueries.java` и `wh0/a.java` (DDL `CREATE TABLE yh_*`) [H]:

```
yh_device(device_id PK, device_name, model_color)
yh_counter(count_type, device_id FK, count, last_count_time, PK(count_type, device_id))
yh_app_launch_counter(year, day_of_year, PK(year, day_of_year))
yh_headphones_usage_day(pk PK, year, month, day, week_number, device_id FK)
yh_weekly_use_time_counter(id AI, years, months, count)
yh_asc_place_category_counter(id AI, place_category, year, day_of_year, device_id FK)
yh_use_time(id AI, start_time, end_time, device_id FK, is_temporary_record DEFAULT 0)
yh_asc_activity_time(action_kind, device_id, accumulated_time, PK(action_kind, device_id))
yh_asc_place_info(place_id PK, place_name, place_display_type)
yh_asc_use_time(id AI, start_time, end_time, device_id FK, place_id FK, is_temporary_record)
yh_badge(badge_type, level, status, obtained_time, device_id FK NULL)
yh_visualize_artist_filter(artist PK)
yh_visualize_music_filter(title, artist, PK(title, artist))
```

Запросы агрегации (примеры из `YourHeadphonesQueries.java`): `selectUseTimesInRange` (`end_time > ? AND start_time < ?`), `selectUseTimesInRangeWithoutTemporary` (`is_temporary_record = 0`), `selectUsageDays` (`COUNT WHERE year=?`), `selectWeeklyUseTimeCounter`, `selectBadgesByTypeAndDeviceIdOrderByLevelDesc`, `selectDeviceBadgesByBadgeTypeOrderByDeviceNameAndDeviceId` [H].

**Старое хранилище (Realm, всё ещё на телефоне):** `application/yourheadphones/data/YhRealmComponent.java` - файлы `yh_main_data_repository.realm`, `yh_backup_data_repository.realm`, `yh_restore_data_repository.realm`, `yh_visualization_*.realm`, `sl_*.realm`, `asc_opt_*.realm` в `/data/data/com.sony.songpal.mdr/files/`. Модели `lm/a..n.java` (Realm proxies в `io/realm/*.java`, схема `addColumnDetails`) [H]:

```
YhActivityTimeInfo(actionKind, accumulatedTime, deviceInformation)
YhAppLaunchCounter(year, dayOfYear)
YhAscPlaceCategoryCounter(placeCategory, year, dayOfYear, deviceInformation)
YhBadge(badgeType, level, status, obtainedTime, device)
YhCounter(countType, count, deviceInformation, lastCountTime)
YhCountry(countryCode, arrival, deviceInformation)
YhDevice(deviceId, deviceName, modelColor)
YhHeadphonesUsageDay(pk, year, month, day, weekNumber, deviceInformation)
YhPlaceInfo(placeId, placeName, placeDisplayType)
YhPlaceTimeInfo(entryTime, exitTime, deviceInformation, placeInformation, isTemporaryRecord)
YhUseTime(startTime, endTime, deviceInformation, isTemporaryRecord)
YhWeeklyUseTimeCounter(years, months, count)
```
Имена полей подтверждены из `io/realm/*Proxy` (`u1`=YhBadge, `i2`=YhUseTime, `g2`=YhPlaceTimeInfo и т.д.) [H].

**Миграция Realm → SQLDelight** есть: `application/yourheadphones/data/z1.java` (`YhDatabaseMigrationResult`: MIGRATED/FAILED/SKIPPED), `n1.java` (логирует "Realm - size / SQLDelight - size" по секциям) [H]. На тестовом телефоне файла `your_headphones.db` **нет**, а Realm-файлы живые (`yh_main` обновлялся сегодня), т.е. миграция либо не выполнялась, либо гейтится [M, не проверено почему]. Данные телефона не декодировались (нужна Realm-библиотека); агрегатов по количеству записей не делал.

### 1.3 Как собираются события

- **Подключение/выбор устройства (use time)**: `vim/MdrApplication.java` (~стр. 900-910, колбэк device state): `qVarJ2.x(cVarD)`, `qVarJ2.r().b(device)` (старт учёта). Отключение/смена - `onSelectedDeviceChanged` (~стр. 3070-3098) → `us/d.java` (`b` - старт, `c`/`d` - стоп, `e` - обновление). Учёт в `us/d.java` (54 строки, только badge-trigger) [H по вызовам, M по полному потоку].
- Запись use time: `kmp/feature/yourheadphones/f0.java` (`c(entry, temporary)`), `z.java` (фасад `YhData`, `M(start,end,type,device)`, `N(entry)`, `U(entry)`), `YhProcessorProvider.createUseTimeProcessor`. Во время сессии пишется запись с `is_temporary_record=1`, затем финализируется [M].
- Счётчики операций (EQ/CB/NC-ASM): `kmp/feature/yourheadphones/YhCounterSqlDelightProcessorImpl.java` (`incrementEqOperationCount`, `incrementCbOperationCount`, `incrementNcAsmOperationCount`, `incrementActivityDetectionCount`, `incrementPlaceDetectionCount`, `incrementHeadphoneDaysOfUsage`, `incrementHeadphoneWeeksOfUsage`) [H]. Источник - действия пользователя в UI (ncasm/eq фичи), не телеметрия гарнитуры [M].
- ASC действия/места: `SAI` (`com/sony/sai/unifiedactivitydetector*`, native) + `kmp/autoplay/core/sai/SaiUadManagerImpl.java` (состояния sensor / scene / home-destination flows). Phone-side детектор (Stay/Walk/Run/Vehicle) [M]. Был ли вход от гарнитуры (`com/sony/sai/motionactivityheadset`) - не подтверждено [L].
- Места (home/office/school/…): `YhPlaceDisplayType` (`Home, Office, School, TrainStation, BusStation, Gym, Other`) - категории зарегистрированных в ASC локаций [H].

### 1.4 Safe Listening (отдельно, но связано)

- Сырой лог гарнитуры: `application/safelistening/db/*` (таблицы `sl_log_data`, `sl_current_data`, `sl_connection_record`, `sl_device`). Поля: `mdr_type`, `time_stamp`, `rtc_reset_counter`, `accumulated_view_time`, `accumulated_sound_pressure`, `corrected_time_stamp`, `ambient_time` [H, из телефонного `sl_main.db`, локальная схема]. Это накопители, которые гарнитура отдаёт через MDR (`MdrType`, `SlLogDataTwsR`) [M].
- Телефонный `sl_main.db` на тестовом устройстве пустой (0 строк в 4 таблицах) [H].
- Для Hush: SPL/dose получить можно **только** у устройств, которые отдают `accumulated_sound_pressure` (WH-1000XM4 по этой схеме - не проверено) [L].

### 1.5 Агрегация (периоды)

- `charts/DateRangeType`: `DAY, WEEK, MONTH, YEAR` (`j2objc/application/yourheadphones/charts/DateRangeType.java`) [H]. UI: `activity_yh_log_datebased_detail.xml`, MPAndroidChart `BarChart` (`com/github/mikephil/charting`) [H].
- Алгоритм «часы в сутках»: `application/yourheadphones/data/y1.java` - `j(dayStart)` делит сутки на 48 получасовых окон, `i(dayStart, n)` - по дням с переносом переполнения на следующий день (`f32940c` = 1 день, `f32939b` = 30 мин) [H].
- Неделя: `YhHeadphonesUsageDay.week_number` + правило «4 дня в неделю» (строка `Actvty_Bdg_Desc_WeeklyUse`) [H].
- Log-экраны: `yh_log_totalusage_*` (суммарно по устройствам), `yh_log_datebased_*` (по периоду), `yh_log_place_*` (по местам), `yh_log_action_*` (по действиям Stay/Walk/Run/Ride/LongStay), плюс `yh_log_*_travel`-строки (страны) [H по layout/strings].

### 1.6 Облако и обратная загрузка

- **Да, синхронизируется.** Строка: «Records can be saved to the account on the server and can be restored on other mobile device» (`Actvty_Setting_TurnOn_Desc`) [H].
- Механизм: **Settings TakeOver (STO)** - `j2objc/application/settingstakeover/StoDataProcessor.java`, `StoController.java`, `v.java`. Загрузка: `startBackupSequence` (стр. ~847), восстановление: `storeYhData` (стр. ~382, `j1VarA.c().length`), метки времени `setYhData`/`saveYhVisualizationRealmDataInfoTime` [H].
- Формат облачного payload: JSON-объекты с ключами `deviceInformation.deviceId/deviceName/modelColor`, `startTime`, `endTime`, `isTemporaryRecord`, `placeInformation.*`, `badgeType`, `level`, `status`, `obtainedTime`, `countType`, `lastCountTime` и т.д. (`n1.java`, `new JSONObject(map).toString()`) [H]. Версии формата: `YhDataUtil.VERSION_1..4` (+ `INVALID`) [H].
- Автосинхронизация включается отдельной настройкой (`Msg_Actvty_InitialSetup_AutoSync_Description`) [H].
- Сервер aggregated listening (GraphQL): см. раздел 3.3. Хост из `shared_prefs/yh_visualize_pref.xml`, ключ `service_url` = `https://v1.api.data-gateway.seeds.services` [H, значение прочитано с телефона, это не персональный идентификатор].
- Скачать обратно вручную: **нет** (нет экспорта в файл/CSV в yourheadphones; `grep export|csv` по пакету не дал UI). Только через восстановление аккаунта на другом телефоне [M].

---

## 2. Бейджи

### 2.1 Полный список типов (enum `BadgeType`)

Файл: `j2objc/feature/badge/domain/BadgeType.java` [H]. Тип: Kind = USER_BASE / DEVICE_BASE; Property = LEVEL / FLAG / YEAR; Rank: BRONZE (1..24), SILVER (25..69), GOLD (70..100), `RANK.getRankFromLevel` [H].

| enum | Kind / Property | Логика (класс) | Порог уровня L | Единица |
|---|---|---|---|---|
| APP_LAUNCH_COUNT | USER / LEVEL | `nw/a.java` `o(L)` | L=1: 1; L≥2: (L−1)·5 | дни запуска приложения |
| STO_AUTO_SYNC_USAGE | USER / FLAG | `nw/r.java` | одноразово | включена авто-синхронизация |
| HEADPHONES_DAILY_USAGE_COUNT | USER / LEVEL | `nw/m.java` `o(L)=L` | L | дни использования (`km/d.java` - сравнение по календарному дню) |
| HEADPHONES_WEEKLY_USAGE_COUNT | USER / LEVEL | `nw/p.java` `o(L)=L` | L | недели, где ≥4 дней использования (текст `Actvty_Bdg_Desc_WeeklyUse`) [M] |
| HEADPHONES_USED_MORE_THAN_LAST_YEAR | USER / YEAR | `nw/o.java` | сравнение с прошлым годом | «новый рекорд» |
| ASC_PLACE_STAYING_TIME_HOME | USER / LEVEL | `nw/f.java` (база `nw/c.java` `p(L)`) | L=1:1; L≥2:(L−1)·5 | дни в месте (текст «used for %d day(s) at Home») [M] |
| …_OFFICE, _SCHOOL, _TRAIN_STATION, _BUS_STATION, _GYM, _OTHER | USER / LEVEL | `nw/g,i,j,d,e,h.java` | то же | то же |
| ASC_ACTIVITY_DETECTION_COUNT | USER / LEVEL | `nw/b.java` | L=1:1; L≥2:(L−1)·5 | число смен действий ASC |
| NC_ASM_OPERATION_COUNT | USER / LEVEL | `nw/q.java` | то же | число операций NC/ASM |
| EQ_OPERATION_COUNT | USER / LEVEL | `nw/l.java` | то же | число операций EQ |
| CB_OPERATION_COUNT | USER / LEVEL | `nw/k.java` | то же | число операций Clear Bass |
| INSTRUCTION_GUIDE_ALL_CONFIRMED | DEVICE / FLAG | `mw/b.java`, вызов `qu/q.java:x()` | одноразово | прочитаны все инструкции устройства |
| IA_SETTING_COMPLETE | DEVICE / FLAG | `mw/a.java`, вызов `qu/q.java:y()` | одноразово | настройка Immersive Audio |
| QA_SETTING_COMPLETE | USER / FLAG | `mw/c.java` | одноразово | Quick Access настроен |

Уровни: максимум **100** (`if (nextLevel > 100) return null`), строка `Actvty_Bdg_Achieve_MaxLevel` «You've reached level 100» [H].

Ступенька (L−1)·5: уровни 1, 5, 10, 15, … , 495 для L=100 [H, формула].

### 2.2 Строки (названия/описания), которые есть в ресурсах, но не в enum

В `strings.xml` есть титулы без соответствующего enum-значения в 13.3.0: `Actvty_Bdg_Title_ASC_LocationSwitch*` (Beginner/Expert/Master), `…_ClearBassSwitch_*`, `…_EQSwitch_*` (Beginner/Expert/Master), `…_TotalTime` (`Always Together With my Audio device`, «N hour(s)»), `…_ASC_TotalTime`, `…_Tutorial`, `…_AppFun` (Sound Connect N days), `…_360RA`, `…_Traveler` (countries/regions, `Actvty_Bdg_Desc_Traveler`), `…_Safelstn` (7 дней ниже лимита ВОЗ, `Actvty_Bdg_Challenge_Safelstn`). Возможно это либо старые/неподключённые бейджи, либо логика, которую я не нашёл (уверенность в «неиспользуемости» - [L]). Traveler и Safelstn связаны с `YhCountry` и Safe Listening соответственно [M].

Полные тексты - `strings.xml` ключи `Actvty_Bdg_Title_*`, `Actvty_Bdg_Desc_*`, `Actvty_Bdg_Achieve_*`, `Actvty_Bdg_Challenge_*`, `Actvty_Bdg_Progress_*` (например `Actvty_Bdg_Progress_NumberOfTimes` = «%d time(s) until the next level») [H].

### 2.3 Как разблокируются

- `BadgeType.startBadgeLogic(...)` → `badgeItem().j(...)`; обработчик `nw/*.java` подписан на счётчики через `YhCounter`/listeners (`jw/c.java` базовый класс, `r()/s()` start/stop) [H].
- Новый бейдж: `yh_badge.status = NEW_OBTAINED (0)` → `OBTAINED (1)` (`YhBadgeStatus`) [H]. Показ: `yh_new_obtained_badge_dialog_layout.xml` + уведомление `Actvty_Notification_Bdg_New` [H].
- Прогресс до следующего уровня: `pw/a.java` (BadgeDisplayInfo) с полями `badgeInfo`, `current`, `next`. Тексты `Actvty_Bdg_Progress_Day` / `…_NumberOfTimes` [H].
- Ранг бронза/серебро/золото по уровню → выбор картинки (`badge/view/a.java` `e(type, level)`: L<25 → `_l_`, 25..69 → `_m_`, ≥70 → `_h_`; L=0 → -1) [H]. Соответствие `l/m/h` ↔ bronze/silver/gold - [M] (по RANK и текстам «Gold_badge_Total_Talkback»).

### 2.4 Рендер: НЕ 3D

- **Нет 3D-моделей** (нет `.glb/.gltf/.obj/.fbx` в apktool-output; единственные анимационные ресурсы - Lottie JSON и MP4 для Party-режима).
- **Статичные PNG (RGBA 368×368 для large)** в `res/drawable-xhdpi|xxhdpi/`: 126 файлов `a_mdr_bdg_*` / `a_digest_badge_*` / `yh_badge_bar_progress`. Схема имён: `a_mdr_bdg_{large|small}_{l|m|h|nul}_{category}`, где category ∈ {asc, busstop, gym, home, hpc, mdr, office, other, safelistening, sca, school, station, time, travel}, а также special: `360ra`, `quick_access`, `sto_autosync`, `tutorial`, `year`, `large_deco_image`, `large_bdg_360ra`. `nul` = заблокированный вариант. Изображение - плоская векторная эмблема (кольцо с зубчатым краем, лента, иконка). Смотрел `a_mdr_bdg_large_h_home.png`: золотое кольцо с зубцами, коричневая лента со звёздами - **2D flat**, без теней/бликов [H, визуально].
- **Lottie** для «блеска»: `res/raw/bdg_deco_bace.json` (250×158, 60 кадров @30fps, 8 слоёв, только shape-слои `gr/sh/fl/tr`, без градиентов) и `res/raw/bdg_deco_twinkl.json` (108 кадров, 8 precomp-слоёв, зацикленный «twinkle», есть вариант `raw-night/`) [H]. Подключены в `yh_new_obtained_badge_dialog_layout.xml` (`LottieAnimationView` `leaf_animation_view` autoPlay=false, `twinkle_animation_view` loop=true) [H].
- **Motion**: `res/xml/yh_badge_detail_header_scene.xml` и `yh_badge_list_fragment_scene.xml` - `MotionLayout` с `OnSwipe dragUp` (разворачивание шапки деталей бейджа) [H].
- **Шейдеры**: в коде/ресурсах AGSL/SKSL не найдено [M].
- Dialog: `yh_new_obtained_badge_dialog_layout.xml` → `ImageView` + `LottieAnimationView` (leaf) + `ImageView badge_image` + `LottieAnimationView twinkle` + текст + `Next badge` / `Close`.

### 2.5 Источники и форматы (для справки, НЕ для распространения)

Все ассеты - собственность Sony. Копии 5 PNG и 2 JSON (`a_digest_badge_gold.png`, `a_mdr_bdg_large_{h,m,l}_home.png`, `bdg_deco_{bace,twinkl}.json`) лежат в `hush/research/sony-cloud/badges-samples/` **только для локальной сверки, не включать в сборку**.

Внешних URL для бейджей в коде не найдено (ассеты зашиты в APK) [H].

### 2.6 Шеринг

В пакетах `application/yourheadphones`, `j2objc/application/yourheadphones` нет `ACTION_SEND`/`createChooser`/`ShareCompat` [H]. Шеринга бейджей/статистики в приложении нет.

---

## 3. Экраны «Your Headphones» / Activity

### 3.1 Навигация
- Раздел Activity: `vim/activity/bottomnavi` (нижняя навигация) + «Discover → Activity» (`Actvty_InitialSetup_Dis_to_Act`) [H].
- Активация: «Record Activity» (`Actvty_Setting_TurnOn_Title`) с описанием Yh + Safe Listening (`Actvty_Setting_TurnOn_Safelstn`) [H].
- Шапка «Log»: `Actvty_Log_Title`, `Actvty_Log_TotalTime_Title`, `Actvty_Log_Period_Title`, `Actvty_Log_Period_Week_Label` ("%1$s - %2$s"), `Actvty_Log_Period_Year_Label` [H].

### 3.2 Виды
| Экран | Layout | Содержимое | Источник |
|---|---|---|---|
| Total usage (по устройствам) | `yh_log_totalusage_*`, `activity_yh_log_totalusage_detail*.xml` | суммарное время, разбивка по устройствам | `yh_use_time` |
| Date-based | `yh_log_datebased_*`, `activity_yh_log_datebased_detail.xml` (`yh_date_layout_graph`) | BarChart: День/Неделя/Месяц/Год | `DateRangeType` |
| Place-based (Registered Locations) | `yh_log_place_*`, `activity_yh_log_placebased_detail.xml` | время по категориям мест, «Other Locations» | `yh_asc_use_time` |
| Action-based | `yh_log_action_*` | Stay / Walk / Run / Ride / Stay(long) | `yh_asc_activity_time` |
| Travel | `Actvty_Log_Travel_*` | страны/регионы, Residence, last visit, count | `YhCountry` |
| Badges | `activity_yh_badge_detail.xml`, `yh_badge_list_*`, `yh_badge_history_item_*` | сетка по категориям, уровни, история | `yh_badge` |
| Digest | `LH_Digest_*`, `Log_Digest_*` | «Past 7 days», «Use time over the past 7 days was longer/shorter», «Now», «Recent listening situations» | derived |

Шкалы/периоды: День, Неделя, Месяц, Год (+ «Все» в BadgeHistory - не проверено) [M].

### 3.3 Listening History (облачная часть, отдельная от use time)
- GraphQL через Apollo (`util/ApolloClientController.java`, `zq0/*`), namespace `HPCListenTrendVisualization`. Запросы (строки `zq0/*.java`) [H]:
  - `ListenRankingByYear(sumupType, year)` → `items { name count playingtime }`
  - `ListenRankingByScene(scene, sumupType)` → то же
  - `ListenMusicRankingByYear(year)` / `ListenMusicRankingByScene(scene)` → `tracktitle trackartist count playingtime meta { album { cover_art { img_path } } record { main_genre { id_genre } } }`
  - `ListenedScenes` → `scenes`
  - `DataControlInfo` → `items { dataType year timestamp }`
  - `DeleteRankingStatus` / mutation `DeleteRankingMutation { deleteRanking { status } }`
- Сцены (`j2objc/application/yourheadphones/listeninghistory/YhSceneType.java`): `TREND, GENRE, MORNING, WEEKDAY_AFTERNOON, HOLIDAY_AFTERNOON, NIGHT, MIDNIGHT, HOME, OFFICE, SCHOOL, GYM, COMMUTE_FORWARD` [H]. Строки `LH_Scene_*`: Running, Walking, Transportation, PC/ScreenTime, Home, Office, School, Gym, commute_home, to_office_school и т.д. [H].
- Жанры: `ROCK, RAP, ELECTRO, FRENCH_CHANSON, POPS, COMEDY, FOLK, BLUES, JAZZ, CLASSICAL, SOUL, METAL` [H].
- Агрегация по годам: `YhVisualizeDataType { YEAR, RECENT }`, `YhVisualizeSumupType { ARTIST, GENRE }`, `YhVisualizeImageSize { SMALL, MEDIUM }` [H].
- Кэш: `shared_prefs/yh_visualize_pref.xml` (`cache_expired`, `scenes_previous_fetched`, `get_listen_ranking_by_year_2025_trackartist` и т.п.) [H, только имена].
- Источник треков (что даёт `playingtime`) - не установлен [L]. Есть `YhAppleMusic*` (авторизация, playback status PLAYING/PAUSED/…) в `j2objc/application/yourheadphones/musicplayer/` [M], но Android-реализация не нашлась.
- Push: «Remember what song you listened to the most in %d?» (`LH_Push_Annual_highlights_*`) - годовые «highlights» [H].

---

## 4. Предложение для Hush

### 4.1 Модель данных (Room, на устройстве)

```
device            (id TEXT PK, name, productFamily, transport: CLASSIC|BLE|WIRED,
                   sourceProtocol: SONY_V1|SONY_V2|EARPODS|WIRED_GENERIC, firstSeenMs)
listen_session    (id PK, deviceId FK, startMs, endMs, isOpen, source: LIVE|IMPORT_SC)
mode_segment      (sessionId FK, startMs, endMs, mode: OFF|NC|ASM|ANC_OFF,  --  время режима, не счётчик)
op_counter        (deviceId, type: EQ|CLEAR_BASS|NC_ASM|ASC_ACTION|ASC_PLACE|APP_LAUNCH, dayEpoch, count,
                   PK(deviceId,type,dayEpoch))
place_visit       (id PK, deviceId?, startMs, endMs, category: HOME|OFFICE|SCHOOL|GYM|STATION|BUS|OTHER,
                   source: ASC_IMPORT|GEOFENCE|MANUAL, isOptIn)   -- только с явного согласия, on-device
country_visit     (countryCode, firstMs, lastMs, count)          -- Traveler, геоданные - опционально
day_stat          (dayEpoch, deviceId, listenMs, ncMs, asmMs, sessions, PK(dayEpoch,deviceId))  -- главный rollup
badge_def         (static Kotlin: id, property LEVEL|FLAG|YEAR, thresholdFn, kind USER|DEVICE)
badge_progress    (badgeId, deviceId?, level, obtainedMs, status NEW|SEEN)
import_log        (source, importedAtMs, rowCount, hash)
```

- Week/Month/Year: `GROUP BY` по `day_stat` (индекс на `dayEpoch`), без отдельных таблиц. При росте - rollup `week_stat`.
- Сутки с переходом через полночь: как у Sony (`y1.i`): делить интервал по границам дней, а не класть целиком (проверено логикой Sony) [H].
- Мульти-устройство: группировка по `deviceId`, поле `productFamily` для фильтра «все Sony» / «все наушники».
- Пишем `listen_session` только при активном соединении; `op_counter` - инкремент при каждом действии пользователя в UI Hush.
- SPL / громкость - **не** логировать, если протокол не даёт (у Sony их нет в Yh-логе; SL-накопители - отдельная фича, см. 1.4).

### 4.2 Импорт истории Sound Connect

- На устройстве без рута данных нет. Варианты: (a) разовый desktop-скрипт (пользователь сам, на своём телефоне с root) читает `yh_main_data_repository.realm` через Realm-библиотеку (схема выше), пишет JSON → импорт в Hush; (b) если пользователь включит Sony auto-sync, данные лежат на облаке STO (формат JSON, раздел 1.6) - но API STO не документирован и доступ к нему не предлагаю без явного решения (проверить условия Sony).
- Маппинг: `YhUseTime` → `listen_session`; `YhCounter`/`YhAppLaunchCounter` → `op_counter`; `YhBadge` → `badge_progress` (уровни пересчитать по нашим порогам); `YhPlaceTimeInfo`/`YhPlaceInfo` → `place_visit` (спросить согласие, названия мест не импортировать без согласия); `YhCountry` → `country_visit`.
- Флаг `isTemporaryRecord=1` - пропускать или дополнять.

### 4.3 Бейджи: свои, «3D-ощущение», без копирования Sony

Не копировать: PNG/Lottie/названия/пороги 1-5-10…-495 (пороги - факт, не охраняемо; сами ассеты и тексты - охраняемо, проект должен использовать собственные дизайн и тексты).

Варианты (от дешёвого к дорогому):
1. **Compose Canvas + слои**: зубчатый `Path` (кольцо с 48–72 зубцами), 2–3 `Brush.sweepGradient`/`radialGradient` (металл), внутренняя полоса, эмблема `ImageVector`. Бликовая «3D»: вторичный градиент-оверлей с `BlendMode.Screen`, тень через `drawIntoCanvas` + `Paint.setShadowLayer`.
2. **AGSL `RuntimeShader`** (API 33+, fallback на п.1): анимированный specular sweep по координате/времени (`uniform float2 size; uniform float t;`), подсветка по углу - выглядит «объёмно», дешёво по батарее. Для TV/Wear - fallback.
3. **Pre-render из Blender**: моделируем свой медальон (bevel + пластик/металл), рендер 2–3 ракурсов в WEBP 512 px (или 48 кадров sprite-sheet для вращения), кладём в `res/drawable-nodpi` (~80–150 KB на бейдж). Лицензия - собственная модель, без чужих ассетов.
4. **Lottie** (своя анимация в LottieFiles / After Effects): конфетти/блеск 1–2 с, loop=false, reduced-motion → статичный кадр (`Settings.Global.ANIMATOR_DURATION_SCALE`).

Уровни: тиры bronze/silver/gold (1–24 / 25–69 / 70–100) - **своя** палитра и форма (например, не зубцы Sony, а восьмиугольник/щит), иконка категории - свой набор.

Рекомендация: п.1 + п.2 (быстро, в коде, тесты в Paparazzi/Compose preview); п.3 только для «hero»-бейджа при первом получении.

### 4.4 Что сделать дальше (минимум)
1. Таблицы выше + `day_stat` rollup + `BadgeDef` из Kotlin-таблицы порогов (наши формулы, не Sony-строки).
2. Экран Activity: сегменты День/Неделя/Месяц/Год/Всё, стек по устройствам, карточки «Последнее место/сцена».
3. Бейджи: сетка 4 колонки, прогресс «N дн. до следующего уровня», диалог с Lottie/AGSL.
4. Импорт Sony: desktop-скрипт (опционально, позже).

---

## 5. Открытые вопросы / ограничения
- Не декодировал данные Realm на телефоне (нужна Realm-библиотека); распределение значений по бейджам неизвестно [L].
- Почему миграция в SQLDelight не случилась на тест-телефоне - не проверено [L].
- Источник `playingtime`/треков для Listening History [L].
- Какой хост у `HPCListenTrendVisualization` кроме `v1.api.data-gateway.seeds.services` (его берёт `service_url`) - подтверждено только этим ключом [M].
- Безопасность: в scratchpad копии телефонных файлов (Realm, `sl_main.db`, `Devices.db`) были скачаны для анализа и **удалены** после чтения. Из `Devices.db` и `yh_*` ничего, кроме схем, в отчёт не попало.
