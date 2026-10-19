# Persistence and item database

## App persistence

SQLite schema version 3 stores script metadata and profile JSON, bot session profiles, application/overlay settings, scheduler rules, item metadata, and diagnostic events. Script files and item data live in app-private files. The database upgrade path adds profile columns before creating the later tables.

## Item database

The item catalog is app-global under `files/database/items.tsv` and is shared by every account/session. Account creation or login does not request a download. The current game version can be read from package metadata, but no verified game-version-to-catalog compatibility map or remote update source is bundled.

The app does not hardcode a complete Growtopia catalog. Install/update an UTF-8 TSV dataset through `ItemDatabaseUpdater`; each nonempty data row has six tab-separated columns:

```text
id<TAB>name<TAB>type<TAB>rarity<TAB>collisionType<TAB>growTime
```

The active file starts with `# ZenithItemDb<TAB>version`. Loader validation rejects malformed rows, duplicate IDs/names, missing version, and empty tables. Updates cap source size at 10 MiB, validate before activation, write a backup, stage/replace atomically, and support rollback. SHA-256 and version are available in `ItemDatabaseVersion`. No network updater/source is configured; data provenance remains the operator's responsibility.
