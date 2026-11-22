# Course Mapping

Back to [[Home]]. Full evidence is in [[Course Report]].

Status reflects version 0.8.0. "Tested" means automated tests pass. App behavior still needs confirmation on a physical phone.

| Requirement | Implementation | Status |
|---|---|---|
| Java Android app | Multi-module Java app | Built |
| Map-based field selection | `FieldSetupFragment`, `ZameenMap`, osmdroid | Built |
| Three rotation comparisons | `RotationAdvisor`, Advisor page | Built and tested |
| 20 years of NASA POWER data | `NasaPowerClient`, `AnalysisRepository` | Built |
| Heat, drought and frost alerts | `AlertEngine`, `ClimateAlert` | Built and tested |
| Thread start, sleep, stop | `StoppableWorker` | Built and tested |
| Inter-thread communication | `BoundedBuffer`, `ChunkPipeline` | Built and tested |
| Synchronization | `BoundedBuffer`, `Cache` | Built and tested |
| Locks and deadlock prevention | `OrderedLocks` | Built and tested |
| Generic classes and methods | `Result<T>`, `Validator<T>`, `Repository<T, ID>`, `Cache<K, V>`, `SyncOutbox<E>`, `BoundedBuffer<T>`, `OrderedLocks<K>` | Built and tested |
| Immutability | Java records such as `FarmField`, `DayForecast`, `CanalTurn`, `ChatSession` | Built |
| Mutability | Room entities and controlled repository updates | Built |
| Abstract data types | `RotationPlan`, `CropCalendar`, `AlertQueue`, `SyncOutbox`, `Cache` | Built |
| Exception handling | `DataAccessException`, `DataErrors`, `Result`, retries, saved-data fallback | Built and tested |
| JUnit, TDD and suites | `AllDomainTests`, app screen tests, red and green commits | Built |
| Room | `AgroShiftDatabase`, migrations 1 to 5 | Built and tested |
| Retrofit and Gson | NASA, weather, place and assistant clients | Built |
| osmdroid | Field and Home maps | Built |
| MPAndroidChart | Advisor rain and water-loss chart | Built |
| WorkManager | Climate refresh, danger alerts, outbox sync | Built, backend sync pending |
| AndroidX Navigation and RecyclerView | App navigation, farm and record lists | Built and tested |
