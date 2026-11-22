# Course Report

Back to [[Home]] and [[Course Mapping]]

This report shows where each Software Construction and Development topic is used in AgroShift, so it can be checked directly in the code.

## How to run the tests

```bash
./gradlew :core-domain:test :core-data:testDebugUnitTest :app:testDebugUnitTest
```

Version 0.8.0 has 100 automated tests. App screen tests run on the computer with Robolectric and do not replace testing on a real phone.

## Topic to code

| Topic | Where it is used | Why it is there | Test |
|---|---|---|---|
| Thread start, sleep, stop | `core-domain/.../concurrent/StoppableWorker.java`, used by `AnalysisRepository` | Reports NASA download progress and stops cleanly | `ConcurrencyTest` |
| Inter-thread communication | `concurrent/BoundedBuffer.java`, `concurrent/ChunkPipeline.java` | Producer and consumer threads pass NASA year blocks | `ConcurrencyTest`, `ChunkPipelineTest` |
| Synchronization | `BoundedBuffer`, `data/Cache.java` | Protects shared state across threads | `ConcurrencyTest`, `CourseTypesTest.cacheIsSafeAcrossThreads` |
| Locks | `concurrent/OrderedLocks.java`, used by `AnalysisRepository` | Coordinates analysis of the same climate cell and field | `ConcurrencyTest` |
| Deadlock prevention | `OrderedLocks` | Takes locks in one global order with a timeout | `ConcurrencyTest` |
| Generic classes | `Result<T>`, `Repository<T, ID>`, `Cache<K, V>`, `SyncOutbox<E>`, `BoundedBuffer<T>` | Reusable, type-safe building blocks | `DomainTest`, `CourseTypesTest` |
| Generic methods | `Result.map`, `Result.success`, `Result.failure` | Transforms results without casting | `DomainTest` |
| Immutability | Records such as `FarmField`, `RotationScores`, `DayForecast`, `CanalTurn`, `ChatSession` | Values can be shared safely between threads | `EngineTest`, `WaterTest` |
| Mutability | Room entities, updated only through repositories | Local data changes in one controlled place | `DatabaseUpgradeTest` |
| Abstract data types | `RotationPlan`, `CropCalendar`, `AlertQueue`, `SyncOutbox`, `Cache` | Each documents its rep invariant and abstraction | `EngineTest`, `CourseTypesTest` |
| Exception handling | `DataAccessException`, `DataErrors`, `NasaPowerClient`, `AnalysisRepository.fallback` | Typed errors, retries and last-good-data fallback | `CourseTypesTest`, `ChunkPipelineTest` |
| JUnit suites | `AllDomainTests` | Runs every domain test class together | All domain tests |
| Test-driven development | Commits `8f814ac` (tests fail) and `0795e0d` (code passes) | Shows the red and green steps | `CourseTypesTest` |

## Named libraries

| Library | Where it is used |
|---|---|
| Retrofit and Gson | `NasaPowerClient`, `OnlineServices` |
| Room | `AgroShiftDatabase`, `FarmDao`, `ChatDao` |
| osmdroid | `ZameenMap`, Field Setup, Home |
| MPAndroidChart | `RainDemandChart` in Advisor |
| WorkManager | `ClimateRefreshWorker`, `DangerAlertWorker`, `OutboxSyncWorker` |
| AndroidX Navigation | `MainActivity`, `nav_graph.xml` |
| RecyclerView | `FieldListBinder`, `RecordListBinder` |
| JUnit | All test modules |

## Honest limits

- Earlier work was committed in large steps, so test-first commit evidence starts in Sprint 2.
- Backend sync is designed and tested locally, but no live sync server exists yet.
- Irrigation and danger alerts are estimates and are not official warnings.
- Urdu text is a machine draft awaiting native-speaker review.
