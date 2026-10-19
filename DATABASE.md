# Persistence and item database

## App persistence

SQLite schema version 5 stores account profile labels, script metadata and profile JSON, bot session profiles with optional account linkage, application/overlay settings, scheduler rules, item metadata, diagnostic events, and safety incidents. Script files and item data live in app-private files. Migrations preserve existing rows while adding account linkage and the incident table. Repositories run through controllers/workers rather than UI code.

## Item database

The item catalog is app-global under `files/database/items.tsv` and is shared by every account/session. Account creation or login does not request a download. The current game version can be read from package metadata, but no verified game-version-to-catalog compatibility map or remote update source is bundled.

The app does not hardcode a complete Growtopia catalog. Install/update an UTF-8 TSV dataset through `ItemDatabaseUpdater`; each nonempty data row has six tab-separated columns:

```text
id<TAB>name<TAB>type<TAB>rarity<TAB>collisionType<TAB>growTime
```

The active file starts with `# ZenithItemDb<TAB>version`. Loader validation rejects malformed rows, duplicate IDs/names, missing version, and empty tables. Updates cap source size at 10 MiB, validate before activation, write a backup, stage/replace atomically, and support rollback. SHA-256 and version are available in `ItemDatabaseVersion`. No network updater/source is configured; data provenance remains the operator's responsibility. The catalog is global to the Android app and loaded on demand. Windows currently has no shared catalog implementation.
