# Функции Sound Connect, которые можно вынести на телефон в Hush

Дата: 2026-10-21. Только чтение, код не менялся.

## Что уже есть в Hush (не дублировать)

- Adaptive Sound Control (`devices/api/.../sound/Automation.kt`): активность, гео-зоны, пресеты шумоподавления/EQ.
- Phone-side EQ для проводных и EarPods (`wired/PhoneEq*`, `apple/earpods/SoftEq.kt`, DynamicsProcessing на session 0).
- AutoEq, Find Your EQ, hearing test (`sony/.../sound/HearingTest.kt`), EQ-библиотека.
- Sleep timer с выключением наушников (`features/SleepTimer.kt`, `SonyController.powerOff`).
- Insights: батарея, сессии, дозиметрия звука (≈дБ, ВОЗ 80 дБ / 40 ч).
- Touch and hold с действиями приложения (`assistant/Assistants.kt`), сцены, Find (с локацией), pause when removed (Sony `CONTROL_BY_WEARING`).

## Источники в декомпиле

- `jadx-output/sources/com/sony/songpal/mdr/j2objc/tandem/features/` — список фич по протоколу: upscaling (DSEE), sarautoplay, sense, smarttalkingmode, soundposition, vpt, wearingjudgement / wearingstatusdetector, safelistening, autopoweroff, quickaccess, karaoke, djcontrol, party, illumination, lea, multipoint, ncoptimizer, facetaptestmode и др.
- `j2objc/devicecapability/tableset1|2` — `FunctionType` по моделям (AutoPowerOffCapability, SafeListeningCapability, SmartTalkingModeCapability, SoundPositionCapability, UpscalingCapability, WearingJudgementCapability, DJControl, Karaoke, NcOptimizer). Полное сопоставление модель→функция в этом отчёте не трассировалось, поэтому колонка "модели" ниже опирается на таблицы и на строки, а не на перебор всех моделей.
- `apktool-output/res/values/strings.xml` — названия и описания (DSEE_*, SmartTalkingMode_*, AutoPowerOff_*, BatteryCare_*, Safelstn_*, SoundPosition_*, VPT_*, Headgesture_*, IA_360RA_*, ESA_*).
- `com/sony/songpal/earcapture/` — съёмка ушей камерой телефона для персонального HRTF 360 Reality Audio (уходит в облако через `IaHttpController`).
- `com/sony/songpal/mdr/j2objc/application/sarautoplay/` — Auto Play: список приложений (`getSARAppSpec`), решение по датчику наушников.

## Кандидаты

Оценка: feasibility — насколько реализуемо на Android; effort — S (до дня), M (несколько дней), L (неделя+).

### 1. Авто-выключение по простою (AutoPowerOff)

- **Sound Connect**: `AutoPowerOff_*` — выключение через 5 / 15 / 30 мин / 1 ч / 3 ч, и "по снятии с ушей" (`AutoPowerOff_RemovedFromEars_*`). Таблица `AutoPowerOffCapability` и `AutoPowerOffWithWearingDetectionCapability`.
- **Наушники**: Sony v1/v2 Bluetooth, где есть `powerOff` (уже используется сном-таймером).
- **Hush**: если нет воспроизведения (MediaSession не playing) и устройство подключено N минут, вызвать `powerOff`. Вариант "по снятии" — через уже существующий `pauseWhenRemovedAvailable`/wearing status.
- **Ограничение**: выключить можно только подключённые наушники; после разрыва линка команду не отправить.
- **Feasibility**: high. **Effort**: S. **Кому**: Sony BT (v1/v2). Для проводных и EarPods неприменимо (нет питания).

### 2. Safe listening: предупреждения и мягкий потолок громкости на телефоне

- **Sound Connect**: Safe Listening (`safelistening`, `SafeListeningCapability`), уведомления `Safelstn_NotifyMsg_Desc_Overdose_LoudVol / LongTime / Continuous`, экран опорных уровней дБ (`NSlSoundPressureReferenceActivity`), "Auto volume with limitation" (`AutoVolumeWithLimitationConfirmDialogFragment`).
- **Hush**: дозиметрия уже есть (`Insights`). Добавить: уведомление при превышении порогов (громко / долго / непрерывно) по тем же правилам. Потолок — либо ограничение `setVolume` для Sony, либо DynamicsProcessing limiter на session 0, как в `SoftEq` (работает на EarPods; на A2DP проверить отдельно).
- **Ограничение**: громкость телефона не равна дБ SPL в ухе. Без калибровки потолок будет условным. Для Sony точнее использовать их же таблицу громкость→дБ, если она есть в протоколе.
- **Feasibility**: high для уведомлений, medium для жёсткого лимитера. **Effort**: S (уведомления), M (лимитер + калибровка). **Кому**: все устройства, включая проводные и EarPods (выигрыш выше всего, потому что там нет встроенной функции).

### 3. Auto Play при надевании / подключении (SAR Auto Play)

- **Sound Connect**: `sarautoplay` — воспроизведение при надевании, список поддерживаемых приложений (`getSARAppSpec`, `SARAutoPlaySupportedAutoPlayService`), датчик калибруется (`SARAutoPlaySensorCalibrationState`). Есть отдельный фрагмент для BLE-модуля (`kmp/autoplay`).
- **Hush**: на Sony v2 использовать сигнал о надевании (уже приходит для "pause when removed") и на Android отправить `play()` в активную или последнюю MediaSession через NotificationListener (уже есть `MediaWatcher`). Для EarPods: авто-старт при вставке USB (событие `USB_DEVICE_ATTACHED`, уже есть).
- **Ограничение**: Android 12+ может запрещать старт воспроизведения в фоне; работает только с сессиями, которые уже знает система. Нет датчика у проводных наушников (кроме вставки кабеля). Сенсорная точность Sony-датчика не повторяется.
- **Feasibility**: medium-high. **Effort**: S-M. **Кому**: Sony v2 (надевание), EarPods (вставка), сенсорный вариант для проводных — low.

### 4. Напоминание о зарядке (Battery Care)

- **Sound Connect**: Battery Care (`BatteryCare_Detail`: "зарядка остановится до 100 %"), `Msg_Function_BatteryCare_get_upper_limit`, `TipsDetailCaringChargeActivity`. Сам лимит делает прошивка.
- **Hush**: нельзя остановить зарядку на наушниках. Можно уведомить при достижении 80–90 % во время зарядки (батарея Sony уже читается).
- **Feasibility**: high. **Effort**: S. **Ценность**: низкая. **Кому**: Sony BT.

### 5. Speak-to-Chat на телефоне (детекция голоса по микрофону телефона)

- **Sound Connect**: `smarttalkingmode` (`SmartTalkingMode_*`): при начале речи включается "пропуск голоса" (ASC), выход по таймеру Fast / Standard / Slow / Off, чувствительность детекции.
- **Hush**: AudioRecord с `VOICE_RECOGNITION` в foreground service (тип microphone, Android 14+), VAD по энергии, при речи — пауза/приглушение медиа через MediaSession или ambient у Sony. Работает на любом устройстве, но детекция по микрофону телефона в кармане/на столе ненадёжна (ложные срабатывания от музыки и окружения), а батарея страдает.
- **Feasibility**: low-medium. **Effort**: M-L. **Кому**: любые; для Sony лучше оставить встроенную функцию.

### 6. Пространственный звук / Sound Position / VPT (через Spatializer)

- **Sound Connect**: `soundposition` (Front / FL / FR / RL / RR), `vpt` (Concert Hall, Club, Arena, Outdoor), `upmix` (Cinema / Music / Game, "3D Surround" для BRAVIA).
- **Hush**: `android.media.Spatializer` (API 32+): проверить `isAvailable` и `isAvailableForDevice` для текущего A2DP-устройства, включить spatializer. Пресеты VPT — проприетарные, точной копии не будет; приблизить можно ревербератором/EQ на DynamicsProcessing, но это не то же самое.
- **Ограничение**: spatializer зависит от прошивки телефона (ColorOS может не выставлять его наружу), управление системное, для head tracking нужно аппаратное поддержание. Для проводных и EarPods доступность нужно проверить отдельно.
- **Feasibility**: medium (включение spatializer), low (воспроизведение VPT). **Effort**: M. **Кому**: все, где spatializer доступен.

## Оценены и отклонены (phone-side нереализуемо или не нужно)

- **Head gestures** (`headgesture`, `Headgesture_*`, кивок/поворот головы для ответа/пропуска): телефон не знает положения головы. Датчики в кармане не дают этого. Не реализуемо.
- **Ear fit test / EFDM** (`wearingstatusdetector`, `wearingjudgement`, `ESA_*`, `EarphoneShape`): проверка уплотнения ушного вкладыша делается внутренним микрофоном и динамиком наушников. У проводных и EarPods внутри уха микрофона нет. Низкая.
- **DSEE / DSEE HX / DSEE Extreme AI** (`upscaling`, `DSEE_*`): ИИ-апскейлинг декодированного потока в прошивке. На телефоне приближается только мультиполосным экспайтером (DynamicsProcessing), это не DSEE. Низкая ценность.
- **360 Reality Audio персональный HRTF** (`earcapture`, `IA_360RA_*`, `IaHttpController`): съёмка ушей камерой телефона, расчёт в облаке Sony. Офлайн не реализовать. Generic binaural через Spatializer возможен, но это отдельная задача. Низкая.
- **NC optimizer / Sound leakage reduction** (`ncoptimizer`, `SOUND_LEAKAGE_REDUCTION`): калибровка по микрофонам наушников. Не реализуемо.
- **Voice focus / call noise reduction**: обработка звонка в системе телефона и в наушниках. Hush не может влиять на звонок.
- **Party mode, DJ-эффекты, Karaoke, Voice changer, Illumination, Auracast receive на XM4**: аппаратные функции наушников (LE Audio / Auracast нет у Sony v1/v2). Auracast можно поддерживать позже через `BluetoothLeBroadcastAssistant` для будущих LE Audio устройств. Не приоритет.
- **Quick Access / touch and hold**: уже реализовано (touch and hold с действиями Hush).
- **Find my headphones**: уже реализовано (Find с локацией).
- **Listening history / badges (Your Headphones, `Actvty_Bdg_*`)**: частично покрыто Insights. Геймификация не нужна.
- **Adaptive volume по шуму** (автогромкость от окружения): в строках Sound Connect нет такой функции. `AutoVolume` встречается только в диалоге безопасного прослушивания. Идея Hush: микрофон телефона измеряет шум и поднимает громкость медиа. Feasibility medium-low (телефон в кармане слышит не то, что ухо), effort M. Не рекомендуется без экспериментов.

## Рейтинг по ценности и трудоёмкости

| # | Функция | Feasibility | Effort | Кому помогает | Ценность |
|---|---|---|---|---|---|
| 1 | Авто-выключение по простою | high | S | Sony BT | высокая |
| 2 | Safe listening: уведомления + лимитер | high / medium | S-M | все, в т.ч. проводные и EarPods | высокая |
| 3 | Auto Play при надевании / вставке | medium-high | S-M | Sony v2, EarPods | средняя |
| 4 | Напоминание о зарядке 80-90 % | high | S | Sony BT | низкая |
| 5 | Speak-to-Chat по микрофону телефона | low-medium | M-L | все | средняя, но ненадёжная |

Следующие по приоритету: Spatializer (6), адаптивная громкость по шуму (отдельный эксперимент).

## Ограничения исследования

- Модели для каждой функции взяты из таблиц `devicecapability` и строк, а не из полного перебора всех моделей.
- Поведение на устройстве (ColorOS 16, realme RMX5060) не проверялось. Для Spatializer, лимитера и Auto Play нужна проверка на телефоне.
