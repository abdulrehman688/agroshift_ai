package com.agroshift.core.data;

import com.agroshift.core.domain.data.DataAccessException;
import com.agroshift.core.domain.data.DataAccessException.Kind;
import com.agroshift.core.domain.engine.DailyClimate;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Reads daily agroclimate records from the public NASA POWER API. */
final class NasaPowerClient {
    private static final String BASE_URL = "https://power.larc.nasa.gov/";
    private static final String PARAMETERS = "T2M,T2M_MAX,T2M_MIN,PRECTOTCORR,GWETROOT";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    record YearRange(int start, int end) {
    }

    private final NasaPowerApi api;

    NasaPowerClient() {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", "AgroShift-Android/0.2").build()))
                .build();
        api = new Retrofit.Builder().baseUrl(BASE_URL).client(http)
                .addConverterFactory(GsonConverterFactory.create()).build()
                .create(NasaPowerApi.class);
    }

    List<DailyClimate> fetch(double latitude, double longitude, YearRange range)
            throws DataAccessException, InterruptedException {
        Call<NasaPowerApi.PowerResponse> call = api.daily(PARAMETERS, "AG", latitude, longitude,
                range.start() + "0101", range.end() + "1231", "JSON");
        try {
            Response<NasaPowerApi.PowerResponse> response = call.execute();
            if (response.code() == 429) {
                throw new DataAccessException(Kind.RATE_LIMITED, "NASA POWER rate limit", null);
            }
            if (response.code() >= 500) {
                throw new DataAccessException(Kind.SERVER, "NASA POWER server error", null);
            }
            if (!response.isSuccessful() || response.body() == null
                    || response.body().properties == null
                    || response.body().properties.parameter == null) {
                throw new DataAccessException(Kind.BAD_DATA, "Unexpected NASA POWER response", null);
            }
            return parse(response.body().properties.parameter);
        } catch (InterruptedIOException interrupted) {
            if (Thread.currentThread().isInterrupted()) {
                call.cancel();
                throw new InterruptedException("NASA POWER request cancelled");
            }
            throw new DataAccessException(Kind.NETWORK, "NASA POWER timed out", interrupted);
        } catch (IOException error) {
            throw new DataAccessException(Kind.NETWORK, "Could not reach NASA POWER", error);
        } catch (RuntimeException error) {
            throw new DataAccessException(Kind.BAD_DATA, "Could not read NASA POWER data", error);
        }
    }

    private static List<DailyClimate> parse(Map<String, Map<String, Double>> parameter)
            throws DataAccessException {
        Map<String, Double> mean = parameter.get("T2M");
        Map<String, Double> max = parameter.get("T2M_MAX");
        Map<String, Double> min = parameter.get("T2M_MIN");
        Map<String, Double> rain = parameter.get("PRECTOTCORR");
        Map<String, Double> wetness = parameter.get("GWETROOT");
        if (mean == null || max == null || min == null || rain == null || mean.isEmpty()) {
            throw new DataAccessException(Kind.BAD_DATA, "NASA POWER omitted a parameter", null);
        }
        List<DailyClimate> days = new ArrayList<>(mean.size());
        try {
            for (Map.Entry<String, Double> entry : mean.entrySet()) {
                String key = entry.getKey();
                days.add(new DailyClimate(LocalDate.parse(key, DAY),
                        DailyClimate.clean(entry.getValue()), DailyClimate.clean(max.get(key)),
                        DailyClimate.clean(min.get(key)), DailyClimate.clean(rain.get(key)),
                        wetness == null ? Double.NaN : DailyClimate.clean(wetness.get(key))));
            }
        } catch (DateTimeParseException error) {
            throw new DataAccessException(Kind.BAD_DATA, "NASA POWER date was invalid", error);
        }
        return days;
    }
}
