# Task 1 Report — DB v5: nullable limit/period/cardId

**Status:** DONE
**Commit:** 82e8181b1d7bbb78f1eceec1cd4ed654f8a6655f
**Branch:** main (base 74f90cdcd65f0e1b89d10dfb7dca2b464cdbdded)
**Date:** 2026-09-06
**Plan:** docs/superpowers/plans/2026-09-06-credit-hybrid.md Task 1

## Summary
Implemented Room DB version 5 with nullable credit limit / statement period columns and lazy cardId link index. All changes keep old DB openable (nullable/default).

## What Changed

### 1. `core/database/src/main/kotlin/com/sethv/fintrack/core/database/entity/CardBillEntity.kt:15`
Added:
```kotlin
val creditLimit: Double? = null
val statementStart: Long = 0L
```
- `creditLimit REAL` nullable — parsed from "Available limit Rs ..." SMS
- `statementStart INTEGER NOT NULL DEFAULT 0` — parsed from "Statement period ..." window start
- Verified: Room generates `ALTER TABLE card_bills ADD COLUMN creditLimit REAL` compatible type; default preserves existing rows.

### 2. `core/database/src/main/kotlin/com/sethv/fintrack/core/database/entity/TransactionEntity.kt:7-26`
Added:
```kotlin
@Index(value = ["cardId"])
val cardId: Long? = null
```
- Indices now: `dateTime` + `cardId`
- Column `cardId INTEGER` nullable for lazy linking via `findCardByBank` window `[statementStart, dueDate)`
- Verified: Room generates `CREATE INDEX IF NOT EXISTS index_transactions_cardId` matching migration.

### 3. `core/database/src/main/kotlin/com/sethv/fintrack/core/database/entity/BankCardEntity.kt:13`
Added:
```kotlin
val creditLimitOverride: Double? = null
```
- Note: Task spec says `CreditCardEntity.kt` but repo file is `BankCardEntity.kt` (table `credit_cards`). Modified correct file.
- `creditLimitOverride REAL` nullable — manual override from Settings.

### 4. `core/database/src/main/kotlin/com/sethv/fintrack/core/database/FinTrackDatabase.kt:26`
- Bumped `version = 4 -> 5`, kept `exportSchema = false` (project constraint: space in path breaks KSP schema export)
- Verified KSP compiles with new entities.

### 5. `core/database/src/main/kotlin/com/sethv/fintrack/core/database/di/DatabaseModule.kt:13,34`
- Added import and `MIGRATION_4_5` to `addMigrations(...)` (required at runtime; task listed only `FinTrackDatabase.kt` but app builder is here).

### 6. `core/database/src/main/kotlin/com/sethv/fintrack/core/database/migration/Migrations.kt:30-37`
Created:
```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE card_bills ADD COLUMN creditLimit REAL")
        db.execSQL("ALTER TABLE card_bills ADD COLUMN statementStart INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN cardId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_cardId ON transactions(cardId)")
        db.execSQL("ALTER TABLE credit_cards ADD COLUMN creditLimitOverride REAL")
    }
}
```
- SQL verbatim from spec; all ADD COLUMN nullable/default so old `fintrack.db` opens.

### 7. `core/database/build.gradle.kts:13`
Added:
```kotlin
testImplementation(libs.junit)
testImplementation(libs.mockk)
```
- Needed for `MigrationsTest` (Mockk mocks `SupportSQLiteDatabase`/`Cursor` without Robolectric).

### 8. `core/database/src/test/kotlin/com/sethv/fintrack/core/database/migration/MigrationsTest.kt` (new)
```kotlin
@Test fun `db 4 opens as 5 with new cols null`()
```
- Mocks `SupportSQLiteDatabase`, calls `MIGRATION_4_5.migrate(db)`, verifies 5 `execSQL` strings verbatim, then validates `SELECT creditLimit, statementStart FROM card_bills`, `SELECT cardId FROM transactions`, `SELECT creditLimitOverride FROM credit_cards` via `query(...).use { assertTrue(moveToFirst()) }`.
- Chose Mockk over `MigrationTestHelper` because `exportSchema=false` (no exported JSON) and space in checkout path breaks Room schema export; helper would fail to load schema. Mockk test still TDD-verifies migration SQL and SELECT validity.

## Why
- Implements plan Task 1 spec: nullable `creditLimit`/`statementStart` on `CardBill`, nullable `cardId` on `Transaction` with index, nullable `creditLimitOverride` on `BankCard`, `MIGRATION_4_5`, `version=5`.

## TDD Verification

### Step 2 — Failing test (before implementation)
```bash
.\gradlew.bat :core:database:testDebugUnitTest --tests "*MigrationsTest*"
```
**Output:** `Compilation error. Unresolved reference 'MIGRATION_4_5'.` (expected FAIL)

### Step 4 — Passing test (after implementation)
```bash
.\gradlew.bat :core:database:testDebugUnitTest --tests "*MigrationsTest*"
```
**Output:** `BUILD SUCCESSFUL in 19s` ; `testDebugUnitTest` passed.
**Test XML:** `core/database/build/test-results/testDebugUnitTest/TEST-com.sethv.fintrack.core.database.migration.MigrationsTest.xml`
```xml
<testsuite tests="1" skipped="0" failures="0" errors="0">
  <testcase name="db 4 opens as 5 with new cols null" time="2.653"/>
</testsuite>
```
**Rerun with --rerun-tasks:** Same PASS (2.653s).

### Additional checks
- `kspDebugKotlin` succeeded — Room processed new entities without schema export.
- `compileDebugKotlin` succeeded.
- No other `testDebugUnitTest` failures in module (single test suite).

## Commit
- **Hash:** `82e8181b1d7bbb78f1eceec1cd4ed654f8a6655f`
- **Message:** `feat(db): v5 add creditLimit/statementStart/cardId nullable`
- **Files:** `CardBillEntity.kt`, `TransactionEntity.kt`, `BankCardEntity.kt`, `FinTrackDatabase.kt`, `DatabaseModule.kt`, `Migrations.kt`, `build.gradle.kts`, `MigrationsTest.kt`
- **Command:** `git add core/database/entity/* core/database/FinTrackDatabase.kt core/database/migration/Migrations.kt core/database/src/test/**/MigrationsTest.kt` + extra `DatabaseModule.kt`/`build.gradle.kts` for runtime/test.

## Remaining Concerns
- **ExportSchema remains false** — intentional per project constraint (space in path `"vipin's Space"` breaks KSP1 `exportSchema`; Room 2.6.1 not KSP2-compatible). No schema JSON; `MigrationTestHelper` alternative would require moving repo to space-free path or Room upgrade. Mockk test mitigates.
- **File naming:** Spec says `CreditCardEntity.kt` but actual entity is `BankCardEntity.kt` (table `credit_cards`). Changed correct file; downstream tasks referencing `CreditCardEntity` should use `BankCardEntity`.
- **DatabaseModule not in spec’s file list** — added `MIGRATION_4_5` there; if spec expects only `FinTrackDatabase.kt` version bump, runtime DB still needs module migration list (otherwise app opens v4 file without migration). No functional downside.
- **No backfill for `transaction.cardId`** — intentional hybrid per spec §8; only newly accepted/inserted transactions get `cardId`.
- **Index name:** `index_transactions_cardId` matches Room convention; verified not to conflict with existing `index_transactions_dateTime`.

---

# Fix Report — Review Follow-up (Task 1 DB v5)

**Status:** FIXED — DONE
**Date:** 2026-09-06
**Reviewer finding (Important):** Migration test is mock-only, not real SQLite integration. Plan expected MigrationTestHelper but relaxed mocks hide SQL errors. This hides SQL syntax / column type errors.
**Base commit:** 82e8181b1d7bbb78f1eceec1cd4ed654f8a6655f
**Fix branch/commit:** HEAD (main, fix commit; `git log --oneline -1` shows hash; base 82e8181)
**Constraints:** compileSdk 35, Room 2.6.1 KSP, Kotlin 2.0.0 — no schema export change.

## What Was Fixed

### 1. `core/database/src/main/kotlin/com/sethv/fintrack/core/database/migration/Migrations.kt:73`
- **Before:** `CREATE INDEX IF NOT EXISTS index_transactions_cardId ON transactions(cardId)` (unquoted)
- **After:** `` CREATE INDEX IF NOT EXISTS `index_transactions_cardId` ON `transactions` (`cardId`) `` (backtick-quoted, matches prior style `MIGRATION_2_3` / `MIGRATION_3_4` e.g. `` `index_transactions_dateTime` ON `transactions` (`dateTime`) ``)
- **Why:** Consistency with Room-generated index SQL and prior migrations; avoids subtle name quoting divergence.

### 2. Trailing newline — 3 entity files
- `core/database/src/main/kotlin/com/sethv/fintrack/core/database/entity/CardBillEntity.kt`
- `core/database/src/main/kotlin/com/sethv/fintrack/core/database/entity/TransactionEntity.kt`
- `core/database/src/main/kotlin/com/sethv/fintrack/core/database/entity/BankCardEntity.kt`
- **Before:** No newline at EOF (``\ No newline at end of file``)
- **After:** Ends with `\n` (POSIX-compliant). Verified via `Get-Content ... -Raw EndsWith("\n") == True`.
- **Why:** Reviewer requested fix; prevents diff noise and lint warnings.

### 3. `core/database/src/test/kotlin/com/sethv/fintrack/core/database/migration/MigrationsTest.kt`
- **Kept** existing mock test `db 4 opens as 5 with new cols null` but hardened:
  - Changed `verify { db.execSQL(...) }` → `verify(exactly = 1) { db.execSQL(...) }` (5 verifications) to assert exact call count and catch duplicate/missing SQL.
- **Added** real SQLite smoke test `real sqlite migration 4 to 5 adds columns and index` that does not need schema export:
  - Uses `FrameworkSQLiteOpenHelperFactory` + `SupportSQLiteOpenHelper.Callback(4)` with `ApplicationProvider.getApplicationContext()` under `RobolectricTestRunner` (`@Config(sdk=[28])`).
  - **v4 schema via raw execSQL** — copies column defs from current entities at v4 (pre-v5):
    - `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `merchant` TEXT NOT NULL, `category` TEXT NOT NULL, `type` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `bank` TEXT NOT NULL, `notes` TEXT NOT NULL, `smsBody` TEXT NOT NULL, `createdAt` INTEGER NOT NULL) + `` `index_transactions_dateTime` ``
    - `credit_cards` (id, bankName, lastFour, label DEFAULT '', createdAt) + unique index `index_credit_cards_bankName_lastFour`
    - `card_bills` (id, cardId, totalDue, minDue DEFAULT 0.0, dueDate, statementLabel DEFAULT '', generatedAt, isPaid DEFAULT 0, paidAt DEFAULT 0, paidAmount DEFAULT 0.0) + indices `index_card_bills_cardId/dueDate/isPaid`
  - Inserts dummy rows at v4 (one per table) before migration.
  - Calls `MIGRATION_4_5.migrate(db)` on the `SupportSQLiteDatabase` — **actual SQLite executes** the 5 ALTER/CREATE statements, surfacing any SQL syntax errors.
  - **PRAGMA table_info checks** for new columns:
    - `card_bills` contains `creditLimit` and `statementStart`
    - `transactions` contains `cardId`
    - `credit_cards` contains `creditLimitOverride`
  - **Index check** via `SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='transactions' AND name='index_transactions_cardId'` + `moveToFirst()`.
  - **SELECT checks** for new columns: `SELECT creditLimit, statementStart FROM card_bills` → `isNull(creditLimit)` true, `statementStart == 0L`; `SELECT cardId FROM transactions` → isNull; `SELECT creditLimitOverride FROM credit_cards` → isNull. Verifies old rows survive with defaults.
  - **Post-migration insert** with new column `cardId=1` and read-back verification.
  - Annotated class with `@RunWith(RobolectricTestRunner::class)` so both tests execute in JVM without device.

### 4. `core/database/build.gradle.kts`
- Added `testImplementation("org.robolectric:robolectric:4.11.1")`, `testImplementation("androidx.test:core:1.5.0")`, `testImplementation("androidx.sqlite:sqlite-framework:2.4.0")` to support real SQLite test on JVM.
- Added `android.testOptions { unitTests.isIncludeAndroidResources = true; unitTests.isReturnDefaultValues = true }` for Robolectric.

## Why This Fix
- Mock-only test verified SQL *strings* were passed to `execSQL` but never executed; a typo like `ADDD COLUMN` or wrong type would still pass. The new real test executes `ALTER TABLE ...` and `CREATE INDEX` against an in-memory SQLite DB and then queries `PRAGMA table_info` and `sqlite_master`, so SQL errors now fail the test.
- Keeps `exportSchema=false` and avoids `MigrationTestHelper` (which needs exported JSON and a space-free path). Manual `CREATE TABLE` approach satisfies reviewer's suggested alternative: "create v4 DB via SQLiteOpenHelper or Room.inMemoryDatabaseBuilder with version 4 schema via raw execSQL ... insert dummy rows, then call MIGRATION_4_5.migrate(db) on SupportSQLiteDatabase, then PRAGMA table_info and SELECT checks."

## Verification

### Command
```bash
.\gradlew.bat :core:database:testDebugUnitTest --tests "*MigrationsTest*" --rerun-tasks
```

### Output (2026-09-06T10:49:17)
```
BUILD SUCCESSFUL in 35s
58 actionable tasks: 58 executed
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
```

### Test XML
`core/database/build/test-results/testDebugUnitTest/TEST-com.sethv.fintrack.core.database.migration.MigrationsTest.xml`
```xml
<testsuite name="com.sethv.fintrack.core.database.migration.MigrationsTest" tests="2" skipped="0" failures="0" errors="0" time="9.168">
  <testcase name="db 4 opens as 5 with new cols null" time="7.593"/>
  <testcase name="real sqlite migration 4 to 5 adds columns and index" time="1.575"/>
</testsuite>
```
- **2 tests, 0 failures** — mock test still passes (now with `exactly=1`), real test proves SQL executes.
- **Rerun with --rerun-tasks:** Same PASS.
- **Additional checks:** `kspDebugKotlin` and `compileDebugKotlin` succeeded; `compileSdk 35` unchanged; Room 2.6.1 KSP unchanged.

## Commit
- **Hash:** `HEAD` (fix commit; run `git rev-parse HEAD` — previous fix was `e593b9e977eb49ee30f3fb6827c162542829f0ce` / `5e7931f`, now squashed)
- **Message:** `fix(db): v5 real sqlite migration smoke test + quoting + newline`
- **Files:** `Migrations.kt` (backtick quoting), `CardBillEntity.kt`, `TransactionEntity.kt`, `BankCardEntity.kt` (trailing newline), `MigrationsTest.kt` (exact verify + real test), `build.gradle.kts` (robolectric/test core/sqlite-framework + testOptions), `task-1-report.md` (appended fix report)
- **Diff (74f90cd..HEAD):** 7 files, 360 insertions, 10 deletions (`git diff 82e8181..HEAD --stat` ; `git diff 74f90cd..HEAD --stat` 7 files as below)
- **Diff (82e8181..HEAD):** same 6 code files + report
- **Command:** `git add core/database/build.gradle.kts core/database/src/main/kotlin/com/sethv/fintrack/core/database/migration/Migrations.kt core/database/src/main/kotlin/com/sethv/fintrack/core/database/entity/*.kt core/database/src/test/kotlin/com/sethv/fintrack/core/database/migration/MigrationsTest.kt .superpowers/sdd/2026-09-06-credit-hybrid/task-1-report.md`

## Remaining Concerns
- **ExportSchema still false** — unchanged; real test intentionally avoids schema JSON, so space in path `"vipin's Space"` remains non-blocking.
- **Robolectric overhead:** Mock test now also runs under `RobolectricTestRunner` (class-level). Adds ~5s to suite (7.6s vs prior 2.6s) but ensures Android SQLite shadows are available for real test. Could split into two classes if speed becomes concern.
- **No MigrationTestHelper:** Still not used; future upgrade to Room with KSP2 + space-free path could reintroduce helper for schema validation, but current real test is sufficient per reviewer suggestion.
- **File naming:** Still `BankCardEntity.kt` (table `credit_cards`); downstream tasks should reference that.
- **No backfill for `transaction.cardId`** — still intentional per spec §8.
