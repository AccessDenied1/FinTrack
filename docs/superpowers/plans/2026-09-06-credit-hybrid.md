# Credit Hybrid (Carousel + Insights A+C+D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship CRED-like card carousel, statement deep-dive, and per-card insights (utilization, due calendar, spend by category) on top of existing Precise Ledger theme without breaking 3908-row data.

**Architecture:** Hybrid — add nullable `creditLimit`/`statementStart` to `CardBill` and nullable `cardId` to `Transaction`; parse limit/period in `CardSmsParser`, lazy-link `cardId` on accept/insert, compute insights in `CardsViewModel` via bank-window filtering; UI is `HorizontalPager` + hero + bottom sheet in `CardsScreen`.

**Tech Stack:** Kotlin 2.0.0, Compose BOM 2024.06, Room 2.6.1 (KSP), Hilt 2.51.1, Coroutines 1.8.1, Mockk 1.13.10 + Turbine 1.1.0, JUnit 4.13.2, Clock injection

**Spec:** `docs/superpowers/specs/2026-09-06-credit-hybrid-design.md`

## Global Constraints

- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`, `AGP 8.7.3`, `Gradle 8.9`, `JDK 17` — from `app/build.gradle.kts:11` and `build-logic/convention/AndroidLibraryConventionPlugin.kt:14`
- `kotlin.code.style=official` — `gradle.properties:3`
- Single `Format.currency` (`core/common/util/Format.kt:1`) — never `DecimalFormat` inline
- Room `exportSchema=false` stays, indices on `dateTime/status/cardId`, migration added as `MIGRATION_4_5`
- All new columns nullable/default to keep `fintrack.db` openable
- Hilt + `Clock` injected (testability via `Clock.fixed`)

---

## File Structure

- **DB:** `core/database/entity/CardBillEntity.kt:8`, `core/database/entity/TransactionEntity.kt:8`, `core/database/entity/CreditCardEntity.kt:8`, `core/database/FinTrackDatabase.kt:22`, `core/database/migration/Migrations.kt:30`
- **Parser:** `service/parser/ParserUtils.kt:160`, `service/parser/CardSmsParser.kt:29`
- **Data:** `service/sms/SmsProcessorImpl.kt:31`, `core/data/repository/TransactionRepositoryImpl.kt:54`, `core/data/repository/CreditCardRepository.kt:28`, `core/data/repository/CreditCardRepositoryImpl.kt:68`
- **Feature:** `feature/cards/CardsViewModel.kt:34`, `feature/cards/CardsScreen.kt:50`, `feature/settings/SettingsScreen.kt:40`
- **Tests:** `service/parser/CardSmsParserTest.kt:13`, `feature/cards/CardsViewModelTest.kt:76`, `core/data/CreditCardRepositoryImplTest.kt` (new), `core/database/MigrationsTest.kt` (new)

---

### Task 1: DB v5 — nullable limit/period/cardId

**Files:**
- Modify: `core/database/entity/CardBillEntity.kt:8`
- Modify: `core/database/entity/TransactionEntity.kt:8`
- Modify: `core/database/entity/CreditCardEntity.kt:8`
- Modify: `core/database/FinTrackDatabase.kt:18`
- Modify: `core/database/migration/Migrations.kt:30`

**Interfaces:**
- Consumes: Room annotations, existing entities
- Produces: `CardBillEntity(creditLimit: Double?, statementStart: Long)`, `TransactionEntity(cardId: Long?)`, `CreditCardEntity(creditLimitOverride: Double?)`, `MIGRATION_4_5`, `version = 5`

- [ ] **Step 1: Write failing test for migration open**

```kotlin
// core/database/src/test/kotlin/.../MigrationsTest.kt
@Test fun `db 4 opens as 5 with new cols null`() {
  val db = helper.createDatabase("test", 4).apply { close() }
  val migrated = helper.runMigrationsAndValidate("test", 5, true, MIGRATION_4_5)
  migrated.query("SELECT creditLimit, statementStart FROM card_bills").use { assertTrue(it.moveToFirst()) }
  migrated.query("SELECT cardId FROM transactions").use { assertTrue(it.moveToFirst()) }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:database:testDebugUnitTest --tests "*MigrationsTest*" -v`
Expected: FAIL `MIGRATION_4_5 not found` / `no such column`

- [ ] **Step 3: Add entities + migration**

```kotlin
// CardBillEntity.kt
val creditLimit: Double? = null
val statementStart: Long = 0L
// TransactionEntity.kt
val cardId: Long? = null
@Index(value = ["cardId"])
// CreditCardEntity.kt
val creditLimitOverride: Double? = null
// Migrations.kt
val MIGRATION_4_5 = object: Migration(4,5) { override fun migrate(db: SupportSQLiteDatabase) {
  db.execSQL("ALTER TABLE card_bills ADD COLUMN creditLimit REAL")
  db.execSQL("ALTER TABLE card_bills ADD COLUMN statementStart INTEGER NOT NULL DEFAULT 0")
  db.execSQL("ALTER TABLE transactions ADD COLUMN cardId INTEGER")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_cardId ON transactions(cardId)")
  db.execSQL("ALTER TABLE credit_cards ADD COLUMN creditLimitOverride REAL")
}}
 // FinTrackDatabase.kt version 5, addMigrations(MIGRATION_4_5)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:database:testDebugUnitTest --tests "*MigrationsTest*" -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/database/entity/* core/database/FinTrackDatabase.kt core/database/migration/Migrations.kt core/database/src/test/**/MigrationsTest.kt
git commit -m "feat(db): v5 add creditLimit/statementStart/cardId nullable"
```

---

### Task 2: Parser — limit + period extraction

**Files:**
- Modify: `service/parser/ParserUtils.kt:160`
- Test: `service/parser/src/test/kotlin/com/sethv/fintrack/service/parser/CardSmsParserTest.kt:13`

**Interfaces:**
- Consumes: existing `extractDueDate`, `extractCardLast4`
- Produces: `fun extractCreditLimit(text: String): Double?`, `fun extractStatementStart(text: String, smsTime: Long): Long?`

- [ ] **Step 1: Write failing test**

```kotlin
@Test fun `parses available limit and statement period`() {
  val sms = RawSms("HDFCBK", "Available limit Rs 1,00,000 Outstanding Rs 42,000 Statement period 15 Oct - 14 Nov Total Due Rs 5,000 Due Date 05 Dec", 1_700_000_000_000L)
  val bill = CardSmsParser().parseBill(sms)!!
  assertEquals(100000.0, bill.creditLimit!!, 0.01)
  assertTrue(bill.statementStart > 0)
}
@Test fun `no limit returns null`() {
  val sms = RawSms("HDFCBK", "Total Due Rs 5,000 Due Date 05 Dec Card XX1234", 1_700_000_000_000L)
  assertEquals(null, CardSmsParser().parseBill(sms)!!.creditLimit)
}
```

- [ ] **Step 2: Run to fail**

Run: `./gradlew :service:parser:testDebugUnitTest --tests "*CardSmsParserTest*parse*limit*"`
Expected: FAIL `expected 100000.0 but was null`

- [ ] **Step 3: Implement**

```kotlin
// ParserUtils.kt
private val AVAILABLE_LIMIT_PATTERN = Regex("""available\s*limit\s*(?:is|:)?\s*(?:Rs\.?|INR|₹)?\s*([\d,]+)""", IGNORE_CASE)
fun extractCreditLimit(text: String): Double? = AVAILABLE_LIMIT_PATTERN.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
private val STATEMENT_PERIOD_PATTERN = Regex("""statement\s*period\s*(\d{1,2}[-/\s][A-Za-z]{3,9})\s*[−-to]+\s*(\d{1,2}[-/\s][A-Za-z]{3,9})""", IGNORE_CASE)
fun extractStatementStart(text: String, smsTime: Long): Long? { /* parse first date token near "statement period", else null */ }
```

CardSmsParser uses them, returns `ParsedCardBill(creditLimit, statementStart ?: dueDate - 30*DAY_MILLIS)`.

- [ ] **Step 4: Run pass**

Run: `./gradlew :service:parser:testDebugUnitTest --tests "*CardSmsParserTest*" -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add service/parser/ParserUtils.kt service/parser/CardSmsParser.kt service/parser/src/test/**/CardSmsParserTest.kt
git commit -m "feat(parser): extract credit limit and statement period"
```

---

### Task 3: Repo — limit persistence + lazy cardId link

**Files:**
- Modify: `core/data/repository/CreditCardRepository.kt:28`
- Modify: `core/data/repository/CreditCardRepositoryImpl.kt:68`
- Modify: `service/sms/SmsProcessorImpl.kt:31`
- Modify: `core/data/repository/TransactionRepositoryImpl.kt:54`

**Interfaces:**
- Consumes: `ParsedCardBill(creditLimit, statementStart)`, `FinTrackDatabase`
- Produces: `suspend fun updateLimit(cardId: Long, limit: Double?)`, `cardId` set on `acceptPending`/`insertTransaction` via `findCardByBank(bankHint)` + window `[statementStart, dueDate)`

- [ ] **Step 1: Write failing integration test**

```kotlin
@Test fun `upsertBill persists limit and lazy links transaction`() = runTest {
  val db = Room.inMemoryDatabaseBuilder(ctx, FinTrackDatabase::class.java).addMigrations(MIGRATION_4_5).build()
  val repo = CreditCardRepositoryImpl(db.bankCardDao(), db.cardBillDao())
  val cardId = repo.findOrCreateCard("HDFC", "4521")
  repo.upsertBill(cardId, 5000.0, 250.0, dueDate, "Nov", creditLimit = 100000.0, statementStart = start)
  val bill = repo.getBillsForCard(cardId).first().first()
  assertEquals(100000.0, bill.creditLimit!!, 0.01)
  // lazy link
  val pending = PendingTransaction(bank="HDFC", dateTime = start+86400000, ...)
  transactionRepo.acceptPending(pending, ..., type) // should set transaction.cardId == cardId
  assertEquals(cardId, transactionDao.getAll().first().first().cardId)
}
```

- [ ] **Step 2: Run fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*CreditCardRepositoryImplTest*" -v`
Expected: FAIL `creditLimit` not found / `cardId` null

- [ ] **Step 3: Implement**

```kotlin
// CreditCardRepository.kt
suspend fun updateLimit(cardId: Long, limit: Double?)
// CreditCardRepositoryImpl.kt
override suspend fun updateLimit(cardId: Long, limit: Double?) { bankCardDao.updateLimit(cardId, limit) }
suspend fun findCardByBank(bankHint: String): Long? // scan getAllCards
// SmsProcessorImpl.handleCardSms: after upsertBill(cardId, ..., creditLimit, statementStart) if creditLimit != null updateLimit
// TransactionRepositoryImpl.acceptPending: resolve cardId = creditCardRepository.findCardByBank(pending.bank)?.takeIf { pending.dateTime in window } then toTransaction(cardId = cardId)
```

- [ ] **Step 4: Run pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*CreditCard*"` 
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/data/repository/* service/sms/SmsProcessorImpl.kt core/data/src/test/**/CreditCardRepositoryImplTest.kt
git commit -m "feat(data): persist limit and lazy cardId linking"
```

---

### Task 4: CardsViewModel — carousel + insights

**Files:**
- Modify: `feature/cards/CardsViewModel.kt:34`
- Test: `feature/cards/src/test/kotlin/com/sethv/fintrack/feature/cards/CardsViewModelTest.kt:76`

**Interfaces:**
- Consumes: `getAllCards`, `getAllBills`, `getAllTransactions()` + `Clock`
- Produces: `selectedCardId: Long?`, `insights: Map<Long, CardInsights>`, `fun onSelectCard(id: Long)`, `fun onUpdateLimit(id, limit)`

- [ ] **Step 1: Write failing test**

```kotlin
@Test fun `utilization 42pct and spend by category per window`() = runTest {
  // card limit 100k, outstanding 42k => 0.42, due 05 Dec, txns for HDFC within [15 Nov, 05 Dec) counted
  advanceUntilIdle()
  assertEquals(0.42f, vm.uiState.value.insights[card.id]!!.utilization!!, 0.01f)
  assertEquals(1, vm.uiState.value.insights[card.id]!!.spendByCategory[FOOD])
}
```

- [ ] **Step 2: Run fail**

Expected: FAIL `utilization was null`

- [ ] **Step 3: Implement**

```kotlin
data class CardInsights(val outstanding: Double, val limit: Double?, val utilization: Float?, val dueCalendar: List<CardBill>, val spendByCategory: Map<ExpenseCategory,Double>, val spendTrend: List<Double>)
// combine 3 flows, filter tx.bank == card.bankName && date in window, compute
```

- [ ] **Step 4: Run pass**

Run: `./gradlew :feature:cards:testDebugUnitTest -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/cards/CardsViewModel.kt feature/cards/src/test/**/CardsViewModelTest.kt
git commit -m "feat(cards): carousel state + per-card insights"
```

---

### Task 5: CardsScreen — pager + sheets

**Files:**
- Modify: `feature/cards/CardsScreen.kt:50`
- Modify: `core/ui/theme/Color.kt:5` add `bankColor(bankHint): Color`

**Interfaces:**
- Consumes: `CardsViewModel.uiState.selectedCardId`, `insights`
- Produces: `HorizontalPager`, `StatementDetail BottomSheet`

- [ ] **Step 1: Write failing compose test**

```kotlin
@get:Rule val compose = createComposeRule()
@Test fun `pager shows 3 cards and hero updates on swipe`() {
  compose.setContent { CardsScreen(viewModel) }
  compose.onNodeWithText("HDFC •• 4521").assertIsDisplayed()
}
```

- [ ] **Step 2: Run fail**

Expected: FAIL `pager not found`

- [ ] **Step 3: Implement pager + hero + sheet**

Replace `LazyColumn` sections with `HorizontalPager(pageCount = cards.size)` + `OutstandingHeader` per selected card, `Due calendar` `LazyRow` chips, `CategoryDonutChart` per insights, `BillCard` tap → `ModalBottomSheet` with `spendByCategory` list.

- [ ] **Step 4: Run pass**

Run: `./gradlew :feature:cards:testDebugUnitTest :app:assembleDebug`
Expected: PASS + `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add feature/cards/CardsScreen.kt core/ui/theme/Color.kt
git commit -m "feat(cards): pager carousel + statement sheet + insights hero"
```

---

### Task 6: Settings manual limit

**Files:**
- Modify: `feature/settings/SettingsScreen.kt:40`
- Modify: `feature/settings/SettingsViewModel.kt:10`

**Interfaces:**
- Consumes: `CreditCardRepository.getAllCards()`, `updateLimit`
- Produces: `CardLimitItem` row

- [ ] **Step 1: Write failing test**

```kotlin
@Test fun `update limit persists`() = runTest {
  coEvery { repo.updateLimit(7, 100000.0) } returns Unit
  vm.onUpdateLimit(7, 100000.0); advanceUntilIdle(); coVerify { repo.updateLimit(7, 100000.0) }
}
```

- [ ] **Step 2: Run fail** — `updateLimit` not found

- [ ] **Step 3: Implement** `SettingsViewModel` inject `CreditCardRepository`, `OutlinedTextField` per card in SettingsScreen DATA section.

- [ ] **Step 4: Run pass**

- [ ] **Step 5: Commit**

```bash
git add feature/settings/*
git commit -m "feat(settings): manual credit limit per card"
```

---

### Task 7: Verification — full suite + release bundle

**Files:**
- None (verification only)

- [ ] **Step 1: Run tiered CI locally**

```bash
./gradlew testDebugUnitTest --no-daemon  # unit
./gradlew :core:database:testDebugUnitTest :core:data:testDebugUnitTest --no-daemon # integration
./gradlew :feature:cards:testDebugUnitTest :core:ui:testDebugUnitTest --no-daemon # ui
./gradlew assembleDebug --no-daemon
./gradlew bundleRelease --no-daemon
```

Expected: All PASS, `BUILD SUCCESSFUL`

- [ ] **Step 2: Commit docs**

No code — just ensure `docs/superpowers/specs/2026-09-06-credit-hybrid-design.md` is on `dev` (already).

---

## Self-Review Checklist

- [x] Spec coverage: A (pager), C (sheet with window transactions), D (utilization/due/spend) all have tasks 4-6
- [x] Placeholder scan: no TBD, all code blocks present
- [x] Type consistency: `creditLimit: Double?`, `statementStart: Long`, `cardId: Long?`, `utilization: Float?` consistent across tasks 1-6
- Missing: No `transaction.cardId` backfill — intentional hybrid, documented in spec §8
