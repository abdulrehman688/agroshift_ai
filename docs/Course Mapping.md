# Course Mapping

Back to [[Home]]

| Requirement | Implementation | Status |
|---|---|---|
| Thread start, sleep, stop | `StoppableWorker` | Built and tested |
| Inter-thread communication | `BoundedBuffer` | Built and tested |
| Synchronization | `BoundedBuffer`, `StoppableWorker` | Built and tested |
| Locks and deadlock prevention | `OrderedLocks` | Built and tested |
| Generic classes and methods | `Result<T>`, `Validator<T>`, `BoundedBuffer<T>`, `OrderedLocks<K>` | Built |
| Immutability | `FarmField`, `SeasonPosition`, `AppLanguage`, `Result` | Built |
| Mutability | Room entities and outbox state | Built |
| Abstract data types | `BoundedBuffer`, `SeasonPosition`; rotation ADTs planned | Partial |
| Exception handling | `Result`, validation and sync retry | Partial |
| JUnit and test suites | `DomainTest`, `ConcurrencyTest` | Built |
| Room | `AgroShiftDatabase` | Built |
| WorkManager | `OutboxSyncWorker` | Built, backend pending |
| Navigation and RecyclerView | App navigation and field list | Built |
| Retrofit, Gson, osmdroid, MPAndroidChart | Data, map and chart features | Planned |
