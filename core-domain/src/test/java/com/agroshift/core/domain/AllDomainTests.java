package com.agroshift.core.domain;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Course test suite for all pure Java domain behavior. */
@RunWith(Suite.class)
@Suite.SuiteClasses({DomainTest.class, EngineTest.class, ConcurrencyTest.class, ChunkPipelineTest.class,
        WaterTest.class, LiveFarmTest.class, RecordStatsTest.class, SearchAndCropTest.class, ChatSessionTest.class, CourseTypesTest.class})
public class AllDomainTests {
}
