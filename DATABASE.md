# DATABASE

Схема БД (Room/SQLite). Версия схемы: 11. Любое изменение сопровождается миграцией и тестом миграции.

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
| accent_color | INTEGER | Вычисленный акцентный цвет по обложке (ARGB). `NULL`: ещё не посчитан или сброшен после смены файла; `0`: посчитан, обложки нет, используется красный по умолчанию; у бесцветной обложки хранится нейтральный серый (с версии схемы 11, `docs/decisions/010-accent-extraction.md`) |

Индекс по `title_key`. Альбом определяется тройкой (`title_key`, `year`, набор исполнителей альбома). Исполнители альбома берутся из тега «Исполнитель альбома» (`ALBUMARTIST`, `TPE2`, `aART`), а если его нет, из исполнителей трека. Версия 3 схемы ничего не меняет в таблицах, она обнуляет `track.date_modified`, чтобы все файлы перечитались с новым правилом (задача 0.3.5a). Версия 6 добавляет `track.track_total` и `track.disc_total` и так же обнуляет `track.date_modified`, чтобы они заполнились (задача 0.4.11). Версия 7 добавляет столбцы ReplayGain (`track.rg_*`) и тоже обнуляет `track.date_modified` (задача 0.5.3). Версия 11 ничего не меняет в таблицах, она сбрасывает `album.accent_color` в `NULL`, чтобы акценты пересчитались по новым правилам для бесцветных обложек (задача 1.2.2).

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
| track_total | INTEGER | Всего треков на диске (тег `TRACKTOTAL`, `TRCK` «3/10», `trkn`), нужно для раздела «Singles» (версия схемы 6, задача 0.4.11) |
| disc_total | INTEGER | Всего дисков (версия схемы 6) |
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
| is_favorite | INTEGER NOT NULL DEFAULT 0 | Избранное. Только в БД |
| favorited_at | INTEGER | Когда трек добавлен в избранное (мс), порядок «Favorites» от последних добавленных. NULL, если не в избранном; 0, если момент неизвестен (избранное из старой резервной копии, идёт в конце). Версия схемы 10, задача 1.1.6 |
| rg_track_gain | REAL | ReplayGain трека в дБ (`REPLAYGAIN_TRACK_GAIN`, в Opus `R128_TRACK_GAIN` + 5 дБ), NULL = нет тега. Версия схемы 7, задача 0.5.3 |
| rg_track_peak | REAL | Пик трека (`REPLAYGAIN_TRACK_PEAK`, 1.0 = полная шкала) |
| rg_album_gain | REAL | ReplayGain альбома в дБ (`REPLAYGAIN_ALBUM_GAIN`, в Opus `R128_ALBUM_GAIN` + 5 дБ) |
| rg_album_peak | REAL | Пик альбома (`REPLAYGAIN_ALBUM_PEAK`) |

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
| playlist_id | INTEGER | FK -> playlist.id, ON DELETE SET NULL. Плейлист, из которого запущена очередь (`PlayerController.play(..., playlistId)`); NULL для остальных. Версия схемы 9, задача 1.1.5 |

Индексы: по `started_at`, по `track_id`, по `playlist_id`.

Схема версии 8 (задача 0.6.1, миграция 7→8). `artists_snapshot` кодируется так же, как `track.artist_tag_values`. Сканер обновляет строки `track` на месте, поэтому `track_id` обнуляется только при настоящем удалении трека из БД. Пропавший файл (`is_missing = 1`) связь сохраняет.

### playlist
| Поле | Тип | Описание |
|---|---|---|
| id | INTEGER PK | |
| name | TEXT NOT NULL | Имя (`PlaylistNames.clean`: без краевых пробелов, пробелы схлопнуты, до 100 символов); может повторяться |
| created_at | INTEGER NOT NULL | Момент создания, мс; вместе с именем узнаёт плейлист при импорте копии |
| last_played_at | INTEGER | Когда последний раз запущен с карточки плейлиста, мс; NULL до первого запуска. Место в «Continue listening» |

### playlist_track
| Поле | Тип | Описание |
|---|---|---|
| id | INTEGER PK | Своя запись: трек может стоять в плейлисте несколько раз |
| playlist_id | INTEGER NOT NULL | FK -> playlist.id, ON DELETE CASCADE |
| track_id | INTEGER NOT NULL | FK -> track.id, ON DELETE CASCADE |
| position | INTEGER NOT NULL | Порядок, 0 первый; добавление в конец, перестановка нумерует заново |

Индексы: по (`playlist_id`, `position`), по `track_id`.

Схема версии 9 (задача 1.1.5, миграция 8→9, вместе с `listen_event.playlist_id`). Записи пропавших файлов (`track.is_missing = 1`) остаются, но не показываются и не считаются; при возвращении файла они снова видны. Перестановка видимых записей не трогает места скрытых (`PlaylistOrder.reorder`).

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

Схема версии 5 (задача 0.4.6, миграция 4→5). Пользовательские записи удаляются физически, встроенные отключаются. При обновлении приложения встроенные записи добавляются по `builtin_id`, существующие строки и их `enabled` не трогаются.

Разделители не настраиваются: фиксированы `;` и `,`.

### Заготовки на следующие версии
- `smart_playlist(id, name, rules_json)` (версия 1.4).
- `playback_queue_item(position, track_id)` и таблица состояния позиции (версия 0.2, либо DataStore).

## Основные запросы (ориентиры для DAO)

- Все треки исполнителя: `track_artist` JOIN `track` по `artist_id`.
- Топ исполнителей за период: `listen_event` (counted = 1) JOIN `track_artist` по `track_id`, группировка по `artist_id`. Для событий без `track_id` используется `artists_snapshot`.
- Карта активности по часам и дням недели: группировка `started_at` с учётом часового пояса пользователя.
- Дубликаты: группировка по (`title_key`, набор `artist_id`, округлённый `duration_ms`).
- Последние альбомы для «Continue listening» (0.6.5, с 1.1.5 со временем): `listen_event` JOIN `track` по `track_id`, `album_id IS NOT NULL`, `playlist_id IS NULL` (прослушивания из плейлиста альбом не поднимают), группировка по `album_id`, `MAX(started_at)`, сортировка по нему по убыванию, `LIMIT 5`.
- Плейлисты со счётчиком и первым треком (1.1.5): подзапросы к `playlist_track` JOIN `track` с `is_missing = 0`, порядок по `position`, затем `id`.
- Разделы карточки исполнителя (0.4.11): альбомы исполнителя из `album_artist` и `track_artist`, `LEFT JOIN track` по `album_id` (только `is_missing = 0`, все треки альбома, не только его), `MAX(track_total)`, `MAX(disc_total)`, `MAX(disc_number)`, группировка по `album.id`.
- Фото исполнителей альбома (0.4.8): `album_artist` JOIN `artist` по `artist_id`, порядок по `position`, поле `artist.photo_uri`.

## Правила миграций

1. Каждая новая версия схемы это новый номер и файл миграции.
2. Для каждой миграции есть тест на `MigrationTestHelper`.
3. Таблицы, которые невозможно восстановить сканированием (`artist_alias`, `artist_compound`, `listen_event`, `playlist*`), не пересоздаются при деструктивных миграциях.
4. Кэш-таблицы (`track`, `album`, `artist` без алиасов) допустимо пересоздавать, но только с последующим повторным сканированием и сохранением пользовательских данных.
