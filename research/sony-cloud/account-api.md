# Sound Connect: вход в аккаунт Sony и облачные API

Дата: 2026-10-21. Цель: понять, что Hush может импортировать из аккаунта Sony (история прослушивания, бейджи, ASC-места, настройки).

Обозначения путей:
- `S/` = `/home/dimka/Projects/sound-connect-decompile/jadx-output/sources/`
- `A/` = `/home/dimka/Projects/sound-connect-decompile/apktool-output/`
- Уверенность: [высокая] — прочитан код; [средняя] — вывод по нескольким признакам; [низкая] — гипотеза, не проверена.

Секреты (client_id, x-api-key, токены, email, user id) в отчёте заменены на `<redacted>`. Аутентифицированные запросы не выполнялись. С устройства читались только имена файлов, ключей и таблиц; значения не читались и не сохранялись.

---

## 0. Выводы

1. **Вход.** OAuth2 Authorization Code + PKCE, без client_secret. Сервер `https://v1.api.auth.seeds.services/`. Redirect `headphonesconnect://signin`. Вход идёт во встроенном WebView, а не в Custom Tabs. Технически повторяемо, но client_id, redirect URI и API-ключи принадлежат Sony. Использовать их в Hush значит выдавать себя за Sound Connect. Это вероятно нарушает условия Sony и небезопасно. Не рекомендую.
2. **Облако с данными пользователя.** Два канала:
   - HPC Backup (`hc.sens.bda.ndmdhs.com`): настройки Sound Connect (STO), включая ASC-места. Двусторонний обмен, восстановление возможно, но только при наличии резервной копии в аккаунте и с валидным токеном.
   - "Your Headphones" GraphQL (`v1.api.data-gateway.seeds.services/graphql`, `HPCListenTrendVisualization`): агрегаты прослушивания по годам и сценам. Только чтение, плюс мутация удаления. Сырых событий нет.
3. **Только локально на телефоне:** сырая история прослушивания (Realm `yh_main_data_repository.realm`), бейджи (`YhBadge`), GPS-точки и места (координаты). В облачных запросах их не найдено.
4. **Практичный путь для Hush:** импорт с устройства через root (чтение файлов Realm/SQLite/prefs из `/data/data/com.sony.songpal.mdr/`). Официального экспорта данных в приложении не найдено. Облачный импорт агрегатов и настроек возможен только через учётку пользователя, с риском по ToS и без гарантий.

---

## 1. Вход в аккаунт (login flow)

### 1.1 Серверы и выбор окружения
- Prod: `https://v1.api.auth.seeds.services/`, QA/dev: `https://v1.api.auth.seeds-dev.services/`. [высокая] `S/com/sony/songpal/mdr/j2objc/application/settingstakeover/ScaMdcimBDAInfoImplementation.java:105-111` (метод `s(boolean)`).
- Регион CN: `https://v1.api-auth.seeds.sony.com.cn/` (тот же файл, строка 108). Выбор по `ml/o.java:134-137` (`i()` = locale == "cn"). [высокая]
- Конкретный класс конфигурации: `S/wj0/b.java:40-46` (`l()` = base, `n()` = client_id). ServerConfiguration.PROD задан в `S/al0/a.java`. [высокая]
- Альтернативная регистрация устройства использует тот же хост: `S/com/sony/songpal/mdr/j2objc/feature/productregistration/ProductRegistrationAPIClient.java:116` (`https://v1.api.auth.seeds.services`), путь `/praxis/devices/register` (строка 152). [высокая]

### 1.2 Authorize-запрос
Строится в `S/ft0/b.java:h()` (строки ~130-150):
```
{base}user/authorize?client_id=<redacted>
  &redirect_uri=headphonesconnect://signin
  &response_mode=query&response_type=code
  &scope=openid%20profile
  &claims={"id_token":{"idp_identifier":null},"userinfo":{"idp_identifier":null}}
  &country=<код страны>
  [&prompt=login]            // по умолчанию LOGIN, S/wj0/b.java:29-32
  &state=<random>&nonce=<random>
  &code_challenge=<S256>&code_challenge_method=S256
```
- client_id: prod, два UUID-литерала, разные для CN и не-CN. `S/com/sony/songpal/mdr/j2objc/application/settingstakeover/ScaMdcimBDAInfoImplementation.java:91-96` (метод `p(boolean)`). Значения `<redacted>`. [высокая]
- redirect_uri: `S/com/sony/songpal/mdr/j2objc/application/settingstakeover/ScaMdcimBDAInfoImplementation.java:77-78` (`i()`). Манифест: `A/AndroidManifest.xml`, `SignInActivity` (exported, singleTask) с intent-filter `scheme=headphonesconnect host=signin`. [высокая]
- Параметры PKCE и nonce: `S/ft0/b.java`:
  - `c()` — code_verifier, 64 случайных байта, base64url без паддинга.
  - `f(str)` — code_challenge = base64url(SHA-256(verifier)).
  - `d()` — nonce, 32 байта. `e()` — state, 70 байт.
  - Все значения хранятся только в полях объекта (`f57195e/f57196f/f57197g`), на диск не пишутся. [высокая]

### 1.3 UI входа
- WebView, не Custom Tabs. `S/com/sony/songpal/mdr/mdcim/ui/signin/webview/SignInWebViewActivity.java`. [высокая]
- Перехват редиректа: `S/com/sony/songpal/mdr/mdcim/ui/signin/webview/a.java:115-130` (`shouldOverrideUrlLoading`): если URL `headphonesconnect://signin`, создаётся Intent VIEW, который уходит в `SignInActivity`. [высокая]
- Настройки WebView: `webview/a.java:156-168`. `setSavePassword(false)`, `setDomStorageEnabled(true)`, БД в `cacheDir/webStorage`. [высокая]
- Обработка колбэка: `S/com/sony/songpal/mdr/mdcim/ui/signin/SignInActivity.java:335-360` (`onNewIntent`). Берутся `code` и `state`. При `error=interaction_required` и отмене пользователем экран закрывается. Иначе показывается ошибка. [высокая]
- Затем `ft0/b.g(code, state)`: сверка state с сохранённым (иначе ошибка). Далее обмен кода на токены. [высокая]

### 1.4 Обмен кода на токены
- `POST {base}token`, `Content-Type: application/x-www-form-urlencoded`. Тело: `grant_type=authorization_code&code=…&redirect_uri=headphonesconnect://signin&client_id=<redacted>&code_verifier=…`. `S/et0/h.java:executeUseCase` (строка ~187). [высокая]
- Ответ JSON: `access_token`, `refresh_token`. `S/et0/h.java:157-158`. Пустые значения — ошибка. [высокая]
- Из access_token разбирается JWT-payload: `sub`, `aud`, `iss`, `exp`, `iat`, `idp_identifier`, `idp_identifier_id`. `S/et0/j.java:31-49`. Результат — MdcimUserId (`et0/h.java:e()`). [средняя: поля взяты из `getString`-вызовов, проверку подписи не видел]
- Провайдер определяется по подстроке user id (`et0/h.java:c()`, значения Amazon/Google/Apple/FaceBook/SonyDev/SonyProduction). [высокая]

### 1.5 Refresh и отзыв
- Refresh: `POST {base}token`, тело `refresh_token=…&client_id=<redacted>&grant_type=refresh_token`. `S/et0/g.java:172-175` (executeUseCase). Ответ: `access_token` (строка 190). `idp_token_status == "require_login"` требует повторного входа (строки 177-180). [высокая]
- Отзыв (logout): `POST {base}revoke`, тело `token=…&token_type_hint=refresh_token&client_id=<redacted>`. `S/et0/i.java:59-71`. [высокая]
- Страница управления аккаунтом: `{base}account_settings?login_hint=…&country=…`. `S/et0/k.java:27-32`. [высокая]
- Заголовок для API: `Authorization: Bearer <access_token>`. `S/et0/k.java:a()`, `c()`, `d()`. [высокая]
- Access token хранится только в памяти: синглтон `S/et0/e.java:5-27`. После перезапуска процесса нужен refresh. [высокая]

### 1.6 Где хранятся токены на устройстве
- Refresh token зашифрован и лежит в SharedPreferences `mdcim_ready_preference`: ключи `ready_config` (шифротекст AES, Base64), `ready_value` (соль/ключ, тоже шифруется через нативный `BackupNativeCipher`), `ready_id` (user id). `S/wj0/c.java:46-80` (`a(String)`, запись), `:80-105` (`b()`, чтение), `:109-121` (`c()/d()`, ready_id). [высокая]
- Ключевые операции нативные: `libbackupcrypto.so`, методы `nGetMdcimRefreshTokenEncryptionKey`, `nGetBDARefreshTokenEncryptionKey`, `nGetBDAUserCredential`. `S/com/sony/songpal/mdr/crypto/BackupNativeCipher.java:11,29-33`. Ключи не извлекались. [высокая]
- На устройстве подтверждено наличие ключей `ready_value`, `ready_id`, `ready_config` и `account_preference.xml` с полем `provider` (значение `sony`, не секрет). Значения не читались. [высокая по структуре]
- `user_credential` для backup-API производится из нативного слоя: `S/wj0/b.java:51` (`o()` = `BackupNativeCipher.c().b(a().c())`). [средняя]

### 1.7 Можно ли повторить в стороннем приложении?
- Техническая схема: public client, PKCE, без secret. Это повторяемо, если сервер Sony примет `redirect_uri=headphonesconnect://signin` для чужого приложения.
- Схема `headphonesconnect://` не принадлежит Hush. Если бы Hush объявил тот же intent-filter, Android мог бы перехватывать редирект неоднозначно. Сервер, скорее всего, ограничивает client_id и redirect_uri. [низкая, не проверено]
- Использовать client_id и redirect Sound Connect в Hush — это выдача себя за чужое приложение. Вероятно нарушает условия использования Sony [не проверено: условия не читались]. Не рекомендую.
- x-api-key для облачных GraphQL-запросов лежит в нативной библиотеке `libcloudmodelinfo.so` (`S/com/sony/songpal/mdr/cloudmodelinfo/CloudModelInfoNative.java:10-32`). Извлечение и использование ключа — обход контроля доступа. Не рекомендую.

---

## 2. Облачные бэкенды по функциям

| Функция | Хост (prod / QA) | Стиль | Авторизация | Источник |
|---|---|---|---|---|
| Sony-аккаунт (вход, refresh, revoke) | `v1.api.auth.seeds.services` / `seeds-dev` (CN: `v1.api-auth.seeds.sony.com.cn`) | REST, form-urlencoded, JSON | client_id в теле | раздел 1 |
| Регистрация устройства | `v1.api.auth.seeds.services/praxis/devices/register` | REST JSON | Bearer | `S/com/sony/songpal/mdr/j2objc/feature/productregistration/ProductRegistrationAPIClient.java:149-152` [высокая] |
| Your Headphones: история прослушивания | `v1.api.data-gateway.seeds.services/graphql` / `seeds-dev` | GraphQL (Apollo) | `Authorization: Bearer` + `X-API-Key` | `S/com/sony/songpal/mdr/j2objc/application/yourheadphones/listeninghistory/YhVisualizeSetting.java` (`b()`), `S/com/sony/songpal/mdr/util/h2.java:678-682,1126` [высокая] |
| Каталог моделей (CloudModelInfo) | `v1.api.data-gateway.seeds.services/graphql` | GraphQL `GetAllCloudModelInfos` | `x-api-key` (нативный) | `S/jp/co/sony/vim/framework/core/device/cloudmodelinfo/CloudModelInfoController.java:27,41,545` [высокая] |
| Строки CloudString | `v1.api.data-gateway.seeds.services/graphql` | GraphQL `getStringInfo` | `x-api-key` | `S/jp/co/sony/vim/framework/core/cloud/string/CloudStringController.java:22,41` [высокая] |
| HPC Backup: настройки STO, ASC, sensitive/adaptive | `hc.sens.bda.ndmdhs.com` / `qa-hc.sens.bdafrt.com` | REST JSON, data/property + media_data | `Bearer`, `x-api-version: v1`; анонимный вариант `x-anon-anoid`, `x-anon-anoid_token`, `x-anon-share` | `S/com/sony/songpal/mdr/j2objc/application/settingstakeover/ScaMdcimBDAInfoImplementation.java:62-65`, `S/vq0/d.java:15-60` [высокая] |
| Сайт-ресурсы, SAR-автоплей | `hpc-assets.data-gateway.seeds.services`, `image.musicmeta.seeds.services`, `musiccenter-cdn.meta.ndmdhs.com` | статика (png/json) | публичные | `A/smali*/…` (строки URL), `S/com/sony/songpal/mdr/j2objc/application/sarautoplay/SARAppFetchTask.java:154,195` (`x-api-key`) [средняя] |
| Аналитика csx.bda (action log) | конфиг: `certs-headphonesconnect-cfgdst-ore-pro.bda.ndmdhs.com` (найден в cert-БД на устройстве) | SDK, одностороннее | внутреннее | `S/com/sony/csx/bda/actionlog/`, `/data/.../databases/com.sony.csx.quiver.core.loader.*cert.db` [низкая: endpoint загрузки логов не найден статически] |
| Согласие на сбор поведения (Privacy Center) | `api.p11r.sony.net` / `api.integ.p11r.sony.net` | REST | — | `S/com/sony/songpal/mdr/kmp/feature/privacycenter/PrivacyCenterRepository.java:188-193` [высокая] (не пользовательские данные) |
| Поддержка / Concierge | `concierge.support.sony.net`, `qa.concierge.support.sony.net` | REST | — | `S/jp/co/sony/support_sdk/server/SolutionsServer.java` [средняя] |
| Чат-бот (digibot) | `https://www.sony.com/electronics/support/digibot/api/` + путь | REST JSON | только Content-Type | `S/com/sony/songpal/mdr/j2objc/feature/chatbot/ChatbotAPIClient.java:71,240` [средняя] |
| Прошивка | `info.update.sony.net` | — | — | уже в `docs/NOTES.md`, не перепроверял |
| Auracast | облака нет, работа с BLE/устройством | — | — | `S/com/sony/songpal/mdr/feature/auracast/` [средняя] |

Прочие GraphQL-операции Apollo (`MusicRankingDataQuery`, `popularMusicRanking`) относятся к глобальному чарту, не к аккаунту: `S/com/sony/songpal/mdr/kmp/platform/seedsql/musicranking/MusicRankingService.java`. [высокая]

Endpoint GraphQL для Your Headphones: в `S/com/sony/songpal/mdr/util/ApolloClientController.java:101` по умолчанию указан `seeds-dev`, но реальный URL передаётся вызывающим кодом (`h2.java:1125-1126`, `bVar.e()`). Prod-URL найден в `A/smali_classes4/jp/co/sony/vim/framework/core/device/cloudmodelinfo/CloudModelInfoController.smali` и других. Какой именно URL получает YH, не подтверждено [средняя].

---

## 3. Данные для импорта

### 3.1 История прослушивания (Your Headphones)

**Облако (GraphQL, все операции найдены в APK-строках и разобраны в `S/zq0/*.java`):**

| Операция | Аргументы | Ответ | Файл |
|---|---|---|---|
| `query DataControlInfo` | — | `getDataControlInfo { items { dataType year timestamp } }` | `S/zq0/a.java` (класс `e`: String, Integer, Integer) [высокая] |
| `mutation DeleteRankingMutation` | — | `deleteRanking { status }` | `S/zq0/b.java` [высокая] |
| `query DeleteRankingStatus` | — | `getDeleteRankingStatus { … }` | `S/zq0/c.java` [высокая] |
| `query ListenMusicRankingByScene` | `$scene` | `getListenMusic…` | `S/zq0/d.java` [средняя, поля не разобраны] |
| `query ListenMusicRankingByYear` | `$year: Int!` | `getListenMusic…` | `S/zq0/e.java` [средняя] |
| `query ListenRankingByScene` | `$scene, $sumupType` | `getListenRankingByScene { items { name count playingtime } yearTableInfo {…} }` | `S/zq0/f.java` [высокая] |
| `query ListenRankingByYear` | `$sumupType: String!, $year: Int!` | `getListenRankingByYear { items { name count playingtime } yearTableInfo { dataType year timestamp } }` | `S/zq0/g.java` (классы `e`, `f`, `c`) [высокая] |
| `query ListenedScenes` | — | `getListenedScenes { scenes }` (List<String>) | `S/zq0/h.java` [высокая] |

- Пагинации нет: ни `first/after`, ни cursor. Выборка идёт по `sumupType` + `year` или `scene`. [высокая]
- Направление: только download (queries) и delete (mutation). Загрузки прослушиваний через GraphQL не найдено. [высокая]
- Гранулярность: агрегаты `count` и `playingtime` по имени (трек/жанр/устройство, семантика `name` не проверена), по году или сцене. Сырых событий и временных меток по каждому прослушиванию нет. [высокая по схеме]
- Единицы `playingtime` не установлены (вероятно секунды или минуты) [низкая].
- Заголовки: `Authorization: Bearer` (access token из памяти, нужен вход) и `X-API-Key` (нативный ключ). `S/com/sony/songpal/mdr/j2objc/application/yourheadphones/listeninghistory/YhVisualizeSetting.java`. [высокая]
- Источник данных в облаке (кто и как загружает счётчики) статически не найден. Вероятно, это телеметрия приложения через csx.bda. [низкая]

**Локально (только на телефоне, root):**
- Realm-файлы: `/data/data/com.sony.songpal.mdr/files/yh_main_data_repository.realm` (~200 КБ), `yh_backup_data_repository.realm` (~105 КБ), `yh_visualization_*.realm`, `yh_restore_*.realm`. Существование подтверждено через `ls` (имена и размеры). [высокая]
- Схема Realm (классы таблиц, имена колонок из строк файла, значения не читались): `class_YhActivityTimeInfo`, `YhAppLaunchCounter`, `YhAscPlaceCategoryCounter`, `YhBadge`, `YhCounter` (countType, count, год/месяц), `YhCountry`, `YhDevice` (deviceId, deviceName, modelColor), `YhHeadphonesUsageDay` (dayOfYear, year), `YhPlaceInfo` (placeId, placeName, placeCategory, placeDisplayType), `YhPlaceTimeInfo` (entryTime, exitTime, long_stay), `YhUseTime` (startTime, endTime, accumulatedTime), `YhWeeklyUseTimeCounter` (weekNumber, year). Источник: `strings` на устройстве. [высокая по именам]
- Классы Realm в коде: `S/com/sony/songpal/mdr/application/yourheadphones/data/YhRealmComponent.java:21` (модуль с `lm.a`…`lm.n`). Имена полей в `S/lm/*.java` обфусцированы, поэтому схему лучше брать из файла. [высокая]
- Шифрование Realm для YH: ключ в пакете YH не найден [средняя]. Строки в файле читаются открыто, что согласуется с отсутствием шифрования.
- Схема SQLDelight `S/com/sony/songpal/mdr/kmp/feature/yourheadphones/db/YourHeadphonesQueries.java` (`your_headphones.db`) на устройстве не найдена в `databases/`. Скорее всего, не используется в рантайме [средняя]. Не импортировать эту БД без проверки.

### 3.2 Бейджи

- Модель: `S/com/sony/songpal/mdr/j2objc/feature/badge/application/BadgeInfo.java:12-18` (badgeType, level, achievedDate, status, deviceInfo). Отображение: `S/com/sony/songpal/mdr/j2objc/feature/badge/domain/BadgeType.java:182`. [высокая]
- Вычисляются локально из счётчиков YH: `S/com/sony/songpal/mdr/kmp/feature/yourheadphones/YhProcessorProvider.java` (`createBadgeProcessor`). [высокая]
- В облачных GraphQL-операциях бейджей нет. Вывод: **бейджи только локальные** (Realm-таблица `YhBadge`, поля `badgeType`, `level`, `status`). [средняя]

### 3.3 ASC-места и learned adaptation

- Локально: `asc_opt_main_data_repository.realm` (~8 КБ), `asc_opt_backup_data_repository.realm` (~4 КБ), `asc_opt_restore_data_repository.realm` (~8 КБ) в `files/`. Наличие подтверждено. Содержимое не читалось. [высокая по наличию]
- Места ASC в облаке: ключи STO-JSON `asc_place_array`, `asc_place_settings_persistent_array`, `asc_settings_data`, `asc_opt_settings_data`, `auto_nc_asm_persistent_data`, `is_place_learning_enabled`, `is_asc_enabled`, `is_asc_opt_enabled`. Поля места: `place_name`, `place_type`, `latitude`, `longitude`, `geo_fence_radius_size`, `palce_id` (опечатка в коде). Файлы: `S/com/sony/songpal/mdr/j2objc/application/settingstakeover/` (grep по `put("…")`). [высокая]
- Восстановление в приложение: `S/com/sony/songpal/mdr/j2objc/application/settingstakeover/g.java:250-270`. Из данных создаётся `Place(...)` и вызывается `vVarL.l(...)`. [высокая]
- Направление и облако: через HPC Backup (см. 3.4). Retrieve возможен, если резервная копия существует [средняя]. Координаты мест — чувствительные данные. Usage-ключ для них — вероятно `Backup_Sensitive` [низкая].

### 3.4 Настройки (STO / HPC Backup)

- Верхний уровень STO-JSON: `S/com/sony/songpal/mdr/j2objc/application/settingstakeover/y.java:132-140` (`l()`): `sto_data_format_version`, `last_update_time`, `fw_update_settings_data`, `ia_setting_data`, `asc_settings_data`, `asc_opt_settings_data`, `yh_settings_data`, `sl_settings_data`, `svc_settings_data`, `sbl_settings_data`. [высокая]
- Флаги синхронизации на устройстве: `com.sony.songpal.mdr.application.settingstakeover.StoPreference.STO_PREFERENCE.xml`. Найдены ключи `AUTO_SYNC`, `KEY_LAST_SYNC_TIME`, `*_MODIFIED_TIME`, `FIRST_DEVICE_REGISTERED`, `STO_SIGNIN_APPEAL_DISPLAYED`. Значения не читались. [высокая по именам]

**Backup/Retrieve API (`S/wq0/*.java` + `S/vq0/d.java`):**

| Класс | Метод | Путь (относительно `hc.sens.bda.ndmdhs.com`) | Назначение |
|---|---|---|---|
| `wq0/n` | POST | `anoid` | создание анонимного id, body `user_credential` [средняя] |
| `wq0/k` | GET | `anoid?usage=…` | получить анонимный id [средняя] |
| `wq0/l` | POST | `data/property` | создать запись настроек, body `data_usage`, `data_id` и др. [высокая] |
| `wq0/p` | PUT | `data/property/{id}` | обновить запись [высокая] |
| `wq0/c` | GET | `data/property?data_usage=…` | список `data_ids` [высокая] |
| `wq0/d` | GET | `data/property/{id}` | получить запись [высокая] |
| `wq0/a` | (DELETE?) | `data/property/{id}` | удалить или получить [низкая] |
| `wq0/m` | POST | `media_data` | создать blob-запись [высокая] |
| `wq0/e` | GET | `media_data` | список blob [средняя] |
| `wq0/h` | GET | `media_data/{id}/upload_url` | presigned upload [высокая] |
| `wq0/f` | GET | `media_data/{id}/download_url` | presigned download [высокая] |
| `wq0/g` | GET | `media_data/{id}/meta_data` | метаданные blob [высокая] |
| `wq0/o` | PUT | `media_data/{id}/meta_data` | обновить метаданные [высокая] |
| `wq0/j`, `wq0/n` | POST | `share/recover`, `share_user`, `anoid` | восстановление/шаринг между устройствами [низкая] |

- Соответствие verbs: `dt0/a.java:210-228` (`t` = GET, `x` = POST, `z` = PUT), `dt0/b.java:79` (GET), `:100` (POST), `:139` (PUT). `r` = DELETE [низкая].
- Использование в бэкапе: `S/jp/co/sony/backup/b.java:842` (wq0.m), `:898` (wq0.l), `:910` (wq0.o), `:941` (wq0.h), `:947` (wq0.p).
- Использование в восстановлении: `S/jp/co/sony/retrieve/d.java:773` (wq0.e), `:834` (wq0.d), `:840` (wq0.c, `j()` = `Backup_Sensitive`), `:846` (wq0.f), `:883` (wq0.g).
- Ключи данных: `Backup_Adaptive`, `HPC_Backup`, `Backup_Sensitive`, `internal_key`, `meta_data_key_value`. `S/com/sony/songpal/mdr/j2objc/application/settingstakeover/ScaMdcimBDAInfoImplementation.java:53-83`. [высокая]
- Заголовки: `Authorization: Bearer <access>`, `x-api-version: v1`, `Content-Type: application/json` (`S/vq0/d.java:15-40`). Анонимный вариант: `x-anon-anoid`, `x-anon-anoid_token` (`:55-60`). [высокая]
- Шифрование содержимого: `user_credential` берётся из нативного слоя (`wj0/b.java:51`). Факт шифрования самого payload не проверен [низкая].
- Пагинация: не видна. Список возвращает массив `data_ids` [средняя].
- Направление: двустороннее. Upload при входе или AUTO_SYNC, download в восстановлении. [высокая]
- Доступность: только при существующей резервной копии в аккаунте и валидном токене. Гарантий нет.

### 3.5 Устройства и регистрация

- Список устройств: `Devices.db` на телефоне (локально). Облачная регистрация — см. раздел 2. [высокая]

---

## 4. Локальные источники на телефоне (root, только структура)

Путь: `/data/data/com.sony.songpal.mdr/`. Значения не читались.

| Файл | Что внутри (по именам) | Тип |
|---|---|---|
| `files/yh_main_data_repository.realm` | Your Headphones: счётчики, используемые часы, места, бейджи | Realm, ~200 КБ |
| `files/yh_backup_data_repository.realm` | то же, для бэкапа | Realm, ~105 КБ |
| `files/yh_visualization_*.realm` | визуализация YH | Realm |
| `files/yh_restore_*.realm` | восстановление YH | Realm |
| `files/sl_main_data_repository.realm` | Safe Listening (основной) | Realm, ~16 КБ |
| `files/sl_backup_data_repository.realm`, `sl_restore_*` | Safe Listening (бэкап) | Realm |
| `files/asc_opt_*.realm` | ASC optimization | Realm, 4-8 КБ |
| `databases/sl_main_data_repository.db`, `sl_backup_data_repository.db` | Safe Listening, SQLite | SQLite |
| `databases/Devices.db`, `IA_DB`, `SETTINGS_DB`, `tips_database.db` | устройства, IA, настройки, подсказки | SQLite |
| `databases/com.sony.csx.quiver.analytics.log.db` | очередь аналитики | SQLite |
| `shared_prefs/mdcim_ready_preference.xml` | токены: `ready_value`, `ready_id`, `ready_config` | prefs (шифровано) |
| `shared_prefs/account_preference.xml` | `provider` | prefs |
| `shared_prefs/com.sony.songpal.mdr.application.settingstakeover.StoPreference.STO_PREFERENCE.xml` | флаги STO | prefs |
| `shared_prefs/com.sony.songpal.mdr.application.yourheadphones.*` | `IS_ENABLED_RECORD` и др. | prefs |
| `shared_prefs/cloud_model_info_preference.xml` | кэш каталога моделей (~250 КБ) | prefs |

Замечания:
- Файлы с координатами (PointRaw, PoIWeekly, place_info_table, location_table, place_context_table) есть в SQL-схемах из APK (`A/`), но в `databases/` на телефоне не найдены. Вероятно, это схемы SDK, которые в рантайме не используются или лежат в других файлах. Перед импортом проверить. [низкая]
- `backup` может содержать пользовательские данные. Не копировать содержимое в облако или в сторонние сервисы.

---

## 5. Альтернативы, если облачный импорт не подходит

**A. Импорт с устройства через root (рекомендуется, если цель — данные именно этого телефона).**
- Hush копирует Realm-файлы YH и ASC (`files/*.realm`) и SQLite Safe Listening через `su`. Формат Realm читается SDK Realm с той же версией схемы. Схему можно вывести из `strings`-листинга (раздел 3.1) или из `S/lm/*.java` (поля обфусцированы).
- Приложение `allowBackup="true"` (`A/AndroidManifest.xml`). Значит, `adb backup` теоретически доступен. На Android 12+ поведение зависит от устройства. Не проверено.
- Ограничения: импорт только локальных данных. Токены не переносятся и не нужны.

**B. Официальный экспорт данных (GDPR).**
- В приложении экспорта не найдено. Поиск по `S/` и строкам: `Gdpr`, `DataExport`, `exportData` — только в связанных с логированием классах `S/com/sony/songpal/mdr/j2objc/actionlog/param/Dialog.java`, `UIPart.java`. [средняя]
- Запрос данных по GDPR идёт через web-страницу аккаунта (`account_settings`, раздел 1.5). Формат выгрузки неизвестен. [низкая]

**C. Облачный API через учётку пользователя.**
- Технически возможен: GraphQL-агрегаты (3.1), HPC Backup (3.4). Нужны вход пользователя по официальному flow и `x-api-key` (нативный).
- Риск: использование ключа и client_id Sound Connect вне приложения. Не рекомендую без явного разрешения Sony.

---

## 6. Открытые вопросы (проверить на устройстве или по ToS)

1. Полный разбор полей `ListenMusicRankingBy*` (`S/zq0/d.java`, `e.java`), семантика `playingtime`.
2. Verbs для `wq0/a`, `wq0/b`, `wq0/i`: проверить код вызова dt0 и поведение сервера (без запросов).
3. Шифрование payload в `data/property` и значение `Backup_Adaptive` (какой класс данных он несёт).
4. Какой Realm-файл содержит ASC-места (`asc_opt_*` или `yh_*`). Проверить `strings` без копирования значений.
5. Куда csx.bda отправляет логи (endpoint загрузки), и не отправляются ли туда счётчики YH.
6. Условия использования Sony Account и Sound Connect (запрет на сторонние клиенты).
7. Версия Realm, которой собраны файлы (для выбора SDK в Hush).
