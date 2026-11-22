package com.agroshift.core.data;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

interface NasaPowerApi {
    @GET("api/temporal/daily/point")
    Call<PowerResponse> daily(
            @Query("parameters") String parameters,
            @Query("community") String community,
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("start") String start,
            @Query("end") String end,
            @Query("format") String format);

    final class PowerResponse {
        Properties properties;

        static final class Properties {
            Map<String, Map<String, Double>> parameter;
        }
    }
}
