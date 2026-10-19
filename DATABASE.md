# DATABASE

Схема БД (Room/SQLite). Версия схемы: 1. Любое изменение сопровождается миграцией и тестом миграции.

Соглашения: `id` это автоинкрементный `INTEGER PRIMARY KEY`, время хранится как миллисекунды Unix (UTC), строки поиска хранятся в нормализованном виде (без регистра, лишних пробелов).

## Таблицы

### artist
| Поле | Тип | Описание |
|---|---|---|
| id | INTEGER PK | |
| name | TEXT NOT NULL | Основное отображаемое имя |
| name_key | TEXT NOT NULL UNIQUE | Нормализованное имя для сравнения |
| sort_name | TEXT | Имя для сортировки (необязательно) |
| photo_uri | TEXT | Путь к фото исполнителя, добавленному пользователем вручную: уменьшенная копия в личной папке приложения (необязательно). Сканирование это поле не трогает |

Индекс: уникальный по `name_key`.

### artist_alias
| Поле | Тип | Описание |
|---|---|---|
| id | INTEGER PK | |
| alias_key | TEXT NOT NULL UNIQUE | Нормализованный вариант написания |
| alias_display | TEXT NOT NULL | Как вариант выглядел в тегах |
| artist_id | INTEGER NOT NULL | FK -> artist.id, ON DELETE CASCADE |

Сюда пишутся решения кнопки "Проверка" и старые написания после переименования или объединения исполнителя в профиле (задача 0.4.4, схема версии 4). При сканировании ключ имени сначала ищется здесь. Удаление исполнителя удаляет его алиасы.

### album
| Поле | Тип | Описание |
|---|---|---|
| id | INTEGER PK | |
| title | TEXT NOT NULL | |
| title_key | TEXT NOT NULL | |
| year | INTEGER | |
| cover_hash | TEXT | Не используется: ключ кэша обложек считается из `uri`, размера, `date_modified` трека и размера миниатюры (`ARCHITECTURE.md`, раздел 3) |
| accent_color | INTEGER | Вычисленный акцентный цвет по обложке (ARGB). `NULL`: ещё не посчитан или сброшен после смены файла; `0`: посчитан, подходящего цвета нет (нет обложки, серая, почти чёрная или белая), используется красный по умолчанию (`docs/decisions/010-accent-extraction.md`) |

Индекс по `title_key`. Альбом определяется тройкой (`title_key`, `year`, набор исполнителей альбома). Исполнители альбома берутся из тега «Исполнитель альбома» (`ALBUMARTIST`, `TPE2`, `aART`), а если его нет, из исполнителей трека. Версия 3 схемы ничего не меняет в таблицах, она обнуляет `track.date_modified`, чтобы все файлы перечитались с новым правилом (задача 0.3.5a).

### album_artist
| Поле | Тип | Описание |
|---|---|---|
| album_id | INTEGER NOT NULL | FK -> album.id, ON DELETE CASCADE |
| artist_id | INTEGER NOT NULL | FK -> artist.id, ON DELETE CASCADE |
| position | INTEGER NOT NULL | Порядок исполнителей альбома |

PK: (`album_id`, `artist_id`).

### track
| Поле | Тип | Описание |
|---|---|---|
| id | INTEGER PK | |
| media_store_id | INTEGER | Идентификатор в MediaStore |
| uri | TEXT NOT NULL UNIQUE | URI файла |
| relative_path | TEXT | Путь относительно хранилища |
| title | TEXT NOT NULL | |
| title_key | TEXT NOT NULL | Нормализованное название для поиска |
| artist_tag_values | TEXT NOT NULL | Значения тега «Исполнитель» как прочитано из файла (JSON-массив строк), нужно для пересчёта разбиения без чтения файлов |
| album_id | INTEGER | FK -> album.id, ON DELETE SET NULL |
| track_number | INTEGER | |
| disc_number | INTEGER | |
| year | INTEGER | |
| duration_ms | INTEGER NOT NULL | |
| format | TEXT | mp3, flac, m4a, ogg и т.д. |
| bitrate | INTEGER | |
| file_size | INTEGER | |
| date_added | INTEGER | |
| date_modified | INTEGER | Для инкрементального сканирования |
| has_cover | INTEGER NOT NULL | 0/1 |
| has_lyrics | INTEGER NOT NULL | 0/1 |
| is_missing | INTEGER NOT NULL DEFAULT 0 | Файл пропал из MediaStore |
| is_damaged | INTEGER NOT NULL DEFAULT 0 | Файл уронил нативное чтение тегов и в карантине (`NativeCallGuard`): теги взяты из MediaStore. Версия схемы 2 |
| rating_tenths | INTEGER | Оценка 0-100 (шкала 0.0-10.0 с шагом 0.1, например 67 = 6.7), NULL = не оценён. Только в БД, в файлы не пишется |
| is_favorite | INTEGER NOT NULL DEFAULT 0 | |

Индексы: по `album_id`, `title_key`, `date_modified`.

### track_artist
| Поле | Тип | Описание |
|---|---|---|
| track_id | INTEGER NOT NULL | FK -> track.id, ON DELETE CASCADE |
| artist_id | INTEGER NOT NULL | FK -> artist.id, ON DELETE CASCADE |
| position | INTEGER NOT NULL | Порядок исполнителей в теге |

PK: (`track_id`, `artist_id`). Индекс по `artist_id` для страницы исполнителя и статистики.

### genre и track_genre
`genre(id, name, name_key UNIQUE)` и `track_genre(track_id, genre_id, position)` с PK (`track_id`, `genre_id`). Несколько жанров на трек поддерживаются с самого начала.

### listen_event
| Поле | Тип | Описание |
|---|---|---|
| id | INTEGER PK | |
| track_id | INTEGER | FK -> track.id, ON DELETE SET NULL |
| started_at | INTEGER NOT NULL | Время начала, мс |
| played_ms | INTEGER NOT NULL | Сколько реально проиграно |
| duration_ms | INTEGER NOT NULL | Длительность трека на момент прослушивания |
| counted | INTEGER NOT NULL | 1, если прослушивание засчитано по порогу |
| skipped | INTEGER NOT NULL | 1, если пропущен |
| title_snapshot | TEXT NOT NULL | Снимок названия |
| artists_snapshot | TEXT NOT NULL | Снимок исполнителей (JSON-массив имён) |

Индексы: по `started_at`, по `track_id`.

### artist_compound
Составные имена: исполнители, в псевдониме которых есть запятая («Tyler, The Creator»). Используются при разборе тега (раздел 4 в `ARCHITECTURE.md`).

| Поле | Тип | Описание |
|---|---|---|
| id | INTEGER PK | |
| display_name | TEXT NOT NULL | Как имя должно читаться, например `Tyler, The Creator` |
| match_key | TEXT NOT NULL UNIQUE | Нормализованные фрагменты по порядку, соединённые `|`, например `tyler|the creator` |
| builtin_id | TEXT UNIQUE | Стабильный идентификатор записи встроенного списка, NULL у пользовательских |
| enabled | INTEGER NOT NULL DEFAULT 1 | Для встроенных вместо удаления: 0 = пользователь отключил |
| created_at | INTEGER NOT NULL | |

Пользовательские записи удаляются физически, встроенные отключаются. При обновлении приложения встроенные записи добавляются по `builtin_id`, существующие строки и их `enabled` не трогаются.

Разделители не настраиваются: фиксированы `;` и `,`.

### Заготовки на следующие версии
- `playlist(id, name, created_at)` и `playlist_track(id, playlist_id, track_id, position)` (версия 1.1). Один трек может входить в несколько плейлистов и повторяться в одном, поэтому у `playlist_track` собственный `id`, а не составной ключ. Добавление и удаление отдельного трека делается из меню действий трека.
- `smart_playlist(id, name, rules_json)` (версия 1.4).
- `playback_queue_item(position, track_id)` и таблица состояния позиции (версия 0.2, либо DataStore).

## Основные запросы (ориентиры для DAO)

- Все треки исполнителя: `track_artist` JOIN `track` по `artist_id`.
- Топ исполнителей за период: `listen_event` (counted = 1) JOIN `track_artist` по `track_id`, группировка по `artist_id`. Для событий без `track_id` используется `artists_snapshot`.
- Карта активности по часам и дням недели: группировка `started_at` с учётом часового пояса пользователя.
- Дубликаты: группировка по (`title_key`, набор `artist_id`, округлённый `duration_ms`).

## Правила миграций

1. Каждая новая версия схемы это новый номер и файл миграции.
2. Для каждой миграции есть тест на `MigrationTestHelper`.
3. Таблицы, которые невозможно восстановить сканированием (`artist_alias`, `artist_compound`, `listen_event`, `playlist*`), не пересоздаются при деструктивных миграциях.
4. Кэш-таблицы (`track`, `album`, `artist` без алиасов) допустимо пересоздавать, но только с последующим повторным сканированием и сохранением пользовательских данных.
