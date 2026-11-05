# FocusGuardRepository Breakdown & Dependency Map

## Overview
- **File**: `app/src/main/java/com/focusguard/app/data/repository/FocusGuardRepository.kt`
- **Original Lines**: 1,369
- **Current Lines**: 1,102 (-267 lines)
- **Total Methods**: 94
- **Total Flows**: 33

---

## 1. Group Inventory & Status

| Group | Status | Target File | Lines | Caches Owned |
|---|---|---|---|---|
| **Keywords** | **DONE (Phase 4a)** | `feature/keywordblocker/data/repository/KeywordBlockerRepositoryImpl.kt` | 212 | `cachedBlockedKeywords` |
| **Schedules** | **DONE (Phase 4a)** | `feature/schedules/data/repository/ScheduleRepositoryImpl.kt` | 167 | `cachedActiveSchedules` |
| **TimeLimits** | **DONE (Phase 4b)** | `feature/timelimits/data/repository/TimeLimitRepositoryImpl.kt` | 233 | `cachedTimeLimits`, `cachedExceededTimeLimits`, `cachedExceededDateCode` |
| **Mindful** | **DONE (Phase 4b)** | `feature/mindful/data/repository/MindfulAppsRepositoryImpl.kt` | 116 | (uses `MindfulRepository`: `cachedMindfulPackages`, `activeAllowanceTokens`) |
| **Session** | **DONE (Phase 4b)** | `feature/session/data/repository/SessionRepositoryImpl.kt` | 76 | (uses `SessionBlockManager`, `FocusGuardPreferences`) |
| **AppBlocking** | **DONE (Phase 4c)** | `feature/appblocker/data/repository/AppBlockerRepositoryImpl.kt` | 298 | `cachedBlockedPackages`, `cachedAutoBlockNewApps`, `cachedAutoBlockEnabledTimestamp`, `cachedReviewedPackages` |
| **Protection/Security** | Pending (Phase 4d) | `feature/protection/data/repository/` | - | `cachedUninstallProtection`, `cachedTimedLockExpiry`, `cachedTimedLockDays`, etc. |
| **PendingChanges & Sync/Misc**| Pending (Phase 4d) | `data/repository/` | - | `cachedOnboardingComplete` |

---

## 2. Phase 4a, 4b & 4c Extracted Groups Details

### Group 1: Keywords (DONE)
- **File**: `KeywordBlockerRepositoryImpl.kt` (212 lines)
- **Owned Cache**: `cachedBlockedKeywords: ConcurrentHashMap.KeySetView<String, Boolean>`
- **Flows (3)**: `blockedKeywords`, `blockedKeywordsCount`, `blockedKeywordsSet`
- **Methods (6)**: `getFastBlockedKeywords()`, `getFastBlockedKeywordsEntities()`, `sanitizeKeyword()`, `blockKeyword()`, `unblockKeyword()`, `unblockKeywordById()`
- **Facade Status**: `FocusGuardRepository` delegates all keyword queries and commands directly to `KeywordBlockerRepositoryImpl`.

### Group 2: Schedules (DONE)
- **File**: `ScheduleRepositoryImpl.kt` (167 lines)
- **Owned Cache**: `cachedActiveSchedules: CopyOnWriteArrayList<ScheduleEntity>`
- **Flows (2)**: `allSchedules`, `activeSchedulesCount`
- **Methods (5)**: `getFastActiveSchedules()`, `saveSchedule()`, `deleteSchedule()`, `updateScheduleEnabled()`, `hasActiveSchedulesOrLimits()`
- **Facade Status**: `FocusGuardRepository` delegates schedule storage, fast queries, and active schedule lookups directly to `ScheduleRepositoryImpl`.

### Group 3: TimeLimits (DONE)
- **File**: `TimeLimitRepositoryImpl.kt` (233 lines)
- **Owned Caches**:
  - `cachedTimeLimits: ConcurrentHashMap<String, AppTimeLimitEntity>`
  - `cachedExceededTimeLimits: ConcurrentHashMap.KeySetView<String, Boolean>`
  - `cachedExceededDateCode: AtomicInteger`
- **Flows (2)**: `allTimeLimits`, `activeTimeLimitsCount`
- **Methods (9)**: `getFastTimeLimits()`, `hasFastTimeLimit()`, `isFastTimeLimitExceeded()`, `markTimeLimitExceeded()`, `clearTimeLimitExceeded()`, `refreshExceededTimeLimits()`, `setTimeLimit()`, `removeTimeLimit()`, `cancelPendingChange()`
- **Facade Status**: `FocusGuardRepository` delegates time limit operations and fast 0ms checks directly to `TimeLimitRepositoryImpl`.

### Group 4: Mindful (DONE)
- **File**: `MindfulAppsRepositoryImpl.kt` (116 lines)
- **Owned Caches**: Managed by `MindfulRepository` (`cachedMindfulPackages`, `activeAllowanceTokens`)
- **Facade Status**: `FocusGuardRepository` now delegates mindful allowance and removal pending changes to `MindfulRepository`. `MindfulAppsRepositoryImpl` has zero dependency on `FocusGuardRepository`.

### Group 5: Session (DONE)
- **File**: `SessionRepositoryImpl.kt` (76 lines)
- **Owned Caches**: Managed by `SessionBlockManager` and `FocusGuardPreferences`

### Group 6: AppBlocking (DONE)
- **File**: `AppBlockerRepositoryImpl.kt` (298 lines)
- **Owned Caches**:
  - `cachedBlockedPackages: ConcurrentHashMap.KeySetView<String, Boolean>`
  - `cachedAutoBlockNewApps: AtomicBoolean`
  - `cachedAutoBlockEnabledTimestamp: AtomicLong`
  - `cachedReviewedPackages: ConcurrentHashMap.KeySetView<String, Boolean>`
- **Flows (4)**: `blockedApps`, `blockedAppsCount`, `isAutoBlockNewAppsEnabled`, `todayImpulsesBlockedCount`
- **Methods (13)**: `isFastBlockedApp()`, `getFastBlockedPackages()`, `getFastBlockedAppsCount()`, `isFastAppBlocked()`, `isFastAutoBlockNewAppsEnabled()`, `getFastAutoBlockEnabledTimestamp()`, `isFastAppReviewed()`, `blockApp()`, `unblockApp()`, `unblockAllAutoBlockedNewApps()`, `markAppReviewed()`, `clearAppReviewed()`, `isAppBlocked()`, `setAutoBlockNewAppsEnabled()`, `isAutoBlockNewAppsEnabled()`
- **Facade Status**: `FocusGuardRepository` delegates all app blocking lookups, mutations, and flows directly to `AppBlockerRepositoryImpl`. Caches maintain single ownership with synchronous 0ms memory access. Direct Boot (DE) cache mirroring is preserved.
- **Facade Status**: Session methods in `FocusGuardRepository` (`recordSessionUsage`, `getTodayUsageMillis`) continue forwarding to `timeLimitEvaluator` to preserve 0ms non-blocking accessibility performance.

---

## 3. Remaining for Phase 4c
- **AppBlocking** (`cachedBlockedPackages`, `cachedAutoBlockNewApps`, auto-block review system)
- **Protection / Security** (Timed lock, challenge engine, security events, uninstall protection)
- **PendingChanges & Sync/Misc** (Onboarding state, support verification & emergency bypass)
