package com.agroshift.core.data;

/** Implemented by the Application so feature modules share repositories. */
public interface AppServices {
    FieldRepository fieldRepository();

    AnalysisRepository analysisRepository();

    RecordRepository recordRepository();

    OnlineServices onlineServices();

    WaterRepository waterRepository();
}
