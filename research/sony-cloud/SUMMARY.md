# Sony аккаунт, активность, бейджи, ASC, 360RA — сводка (2026-10-21)

Подробности: `account-api.md`, `activity-badges.md`, `asc-settings.md`, `360ra.md` (пути jadx, метки уверенности H/M/L).

## Аккаунт и облако
- Вход: OAuth2 Code + PKCE, `v1.api.auth.seeds.services`, redirect `headphonesconnect://signin`, встроенный WebView. Refresh-токен зашифрован (`libbackupcrypto.so`).
- client_id / redirect / API-ключи — Sony. Повторить можно, но это выдача себя за Sound Connect (вероятно против ToS) — **не делаем**.
- В облаке: HPC Backup `hc.sens.bda.ndmdhs.com` (STO: настройки, ASC-места, YH-данные при автосинке); GraphQL `HPCListenTrendVisualization` — только агрегаты по годам/сценам.
- Только локально (root): сырая история, бейджи, места.
- **Вывод: импорт — через root из локальных баз Sound Connect** (Realm `yh_*`, `asc_opt_*`, SQLDelight `your_headphones.db`, SQLite `sl_*`, `SETTINGS_DB`). Без логина в Sony. Альтернатива — GDPR-выгрузка с web-аккаунта (формат неизвестен).

## Активность (Your Headphones)
- Логируется: время по устройствам, по местам и действиям ASC (Stay/Walk/Run/Vehicle/LStay), счётчики EQ / Clear Bass / NC-ASM / смен ASC, дни запуска и использования, страны. SPL — только Safe Listening (`sl_log_data`).
- **Не логируется:** время в NC/ASM, кодек, громкость, шаги — Hush должен писать это сам.
- UX Sony: Total / Date (день/неделя/месяц/год) / Place / Action / Travel / Badges.
- Модель для Hush (Room): device, listen_session, mode_segment, op_counter, place_visit, day_stat (rollup), badge_progress. Запись — для всех устройств (Sony v1/v2, EarPods, проводные), Insights уже собирает часть.

## Бейджи
- 18 типов, уровни 1–100; порог: L1 = 1, далее (L−1)×5 для счётчиков, daily/weekly = L. Ранги bronze 1–24, silver 25–69, gold 70–100.
- У Sony **не 3D**: плоские PNG 368×368 (126 шт.) + Lottie-блеск. Ассеты Sony не используем (образцы в `badges-samples/` — только для сверки).
- Свои: Compose Canvas + AGSL-шейдер (объём, блик, металл по рангу) или pre-render из Blender + своя Lottie.

## ASC (места, адаптация)
- Места: lat/lng/имя/радиус 50/100/200 м → прямо в Hush `Place`; тип места и auto/manual теряются. Wi-Fi нет.
- NC/ASM и EQ по месту/активности конвертируются; noise-adaptive и Speak-to-Chat — нет. long_stay → STILL.
- «Адаптация» = лог ручных переключений (Realm `cj.a`), правило обучения не найдено → переносить только агрегатом.
- На этом телефоне ASC выключен, мест локально нет (или Realm пуст) — проверить чтением Realm. Полный набор — в STO `Backup_Adaptive` (облако).

## 360 Reality Audio
- Фото ушей → облако → бинарный HRTF (формат неизвестен) → применяет приложение, не наушники; системно — только через `jp.co.sony.threesixtyra` (на ColorOS нет).
- **NO-GO** для профиля Sony. Возможный GO: свой бинауральный рендер в Hush на публичном HRIR (1–2 нед.) — отложено.

## Предлагаемый порядок
1. Хранилище активности в Hush (Room) + запись для всех устройств + вкладка Activity (день/неделя/месяц/год/всё, по устройствам, местам, сценам).
2. Свои бейджи (AGSL) по тем же майлстоунам.
3. Импорт из Sound Connect через root (декод Realm → Hush), разово.
4. Импорт ASC-мест (если найдутся локально).
