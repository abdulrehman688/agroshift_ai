package com.agroshift.core.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.agroshift.core.domain.engine.ClimateScenario;
import com.agroshift.core.domain.engine.RotationAdvisor;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.language.ChatLanguage;
import com.agroshift.core.domain.water.DayForecast;
import com.agroshift.core.domain.water.SkyState;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/** Weather, place search and assistant calls. No API keys are stored in the app. */
public final class OnlineServices {
    public record Weather(int currentC, LocalDate rainDate, List<DayForecast> days, long fetchedMillis,
                          @Nullable SkyState sky) {
        public Weather {
            days = List.copyOf(days);
        }
    }

    public record Place(String name, double latitude, double longitude) {
    }

    public record ChatMessage(boolean fromFarmer, String text, List<String> actions, String expert,
                              boolean usedFieldData, String languageCode) {
        public ChatMessage {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    private interface WeatherApi {
        @GET("v1/forecast")
        Call<JsonObject> forecast(@Query("latitude") double latitude, @Query("longitude") double longitude,
                @Query("current") String current, @Query("daily") String daily,
                @Query("forecast_days") int days, @Query("past_days") int pastDays,
                @Query("timezone") String timezone);
    }

    private interface PlaceApi {
        @GET("search")
        Call<JsonArray> search(@Query("q") String query, @Query("countrycodes") String country,
                @Query("format") String format, @Query("limit") int limit);
    }

    private interface AssistantApi {
        @POST("api/chat/")
        Call<JsonObject> chat(@Body JsonObject body);
    }

    private static final int RAIN_LIKELY_PERCENT = 50;
    private static final int FORECAST_DAYS = 7;
    private static final int PAST_DAYS = 31;
    private static final String KEY_FETCHED = "agroshift_fetched";
    private static final String CURRENT_VALUES = "temperature_2m,precipitation,cloud_cover,wind_speed_10m,is_day";
    private static final String DAILY_VALUES = "precipitation_probability_max,precipitation_sum,"
            + "et0_fao_evapotranspiration,temperature_2m_max,temperature_2m_min,wind_speed_10m_max,weather_code";
    private static final int MAX_HISTORY = 12;
    private static final int MAX_ANSWER_ITEMS = 3;
    private static final int MAX_SAVED_MESSAGES = 40;
    private static final String PREFS = "agroshift_weather";
    private static final String CHAT_PREFS = "agroshift_chat";
    private static final String KEY_CHAT_LANGUAGE = "__chat_language";

    private final WeatherApi weatherApi;
    private final PlaceApi placeApi;
    private final AssistantApi assistantApi;
    private final SharedPreferences prefs;
    private final SharedPreferences chatPrefs;
    private final Map<String, MutableLiveData<Weather>> weather = new ConcurrentHashMap<>();
    private final Map<String, MutableLiveData<List<ChatMessage>>> chats = new ConcurrentHashMap<>();

    public OnlineServices(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        chatPrefs = context.getApplicationContext().getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE);
        weatherApi = retrofit("https://api.open-meteo.com/", 20).create(WeatherApi.class);
        placeApi = retrofit("https://nominatim.openstreetmap.org/", 15).create(PlaceApi.class);
        assistantApi = retrofit("https://fieldshift-backend-f2dv.onrender.com/", 90).create(AssistantApi.class);
    }

    private static Retrofit retrofit(String baseUrl, int timeoutSeconds) {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", "AgroShift-Android/0.5").build()))
                .build();
        return new Retrofit.Builder().baseUrl(baseUrl).client(http)
                .addConverterFactory(GsonConverterFactory.create()).build();
    }

    /** Shows saved weather at once, then refreshes it. */
    public LiveData<Weather> observeWeather(FarmField field) {
        String key = weatherKey(field.latitude(), field.longitude());
        MutableLiveData<Weather> live = weather.computeIfAbsent(key, k -> {
            MutableLiveData<Weather> created = new MutableLiveData<>();
            Weather saved = readSaved(k);
            if (saved != null) {
                created.setValue(saved);
            }
            return created;
        });
        forecastCall(field.latitude(), field.longitude()).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Weather parsed = store(key, response.body());
                    if (parsed != null) {
                        live.setValue(parsed);
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable error) {
                // Saved weather stays on screen while offline.
            }
        });
        return live;
    }

    /** Blocking refresh for background work. Returns null when the forecast is unavailable. */
    @Nullable
    public Weather fetchWeather(double latitude, double longitude) {
        try {
            Response<JsonObject> response = forecastCall(latitude, longitude).execute();
            return response.isSuccessful() && response.body() != null
                    ? store(weatherKey(latitude, longitude), response.body()) : null;
        } catch (java.io.IOException offline) {
            return null;
        }
    }

    private Call<JsonObject> forecastCall(double latitude, double longitude) {
        return weatherApi.forecast(latitude, longitude, CURRENT_VALUES, DAILY_VALUES, FORECAST_DAYS,
                PAST_DAYS, "auto");
    }

    private static String weatherKey(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude);
    }

    @Nullable
    private Weather store(String key, JsonObject body) {
        try {
            body.addProperty(KEY_FETCHED, System.currentTimeMillis());
            Weather parsed = parseWeather(body);
            prefs.edit().putString(key, body.toString()).apply();
            return parsed;
        } catch (RuntimeException unusable) {
            return null;
        }
    }

    private Weather readSaved(String key) {
        String value = prefs.getString(key, null);
        if (value == null) {
            return null;
        }
        try {
            return parseWeather(JsonParser.parseString(value).getAsJsonObject());
        } catch (RuntimeException corrupted) {
            return null;
        }
    }

    private static Weather parseWeather(JsonObject body) {
        int current = Math.round(body.getAsJsonObject("current").get("temperature_2m").getAsFloat());
        JsonObject daily = body.getAsJsonObject("daily");
        JsonArray dates = daily.getAsJsonArray("time");
        JsonArray chances = daily.getAsJsonArray("precipitation_probability_max");
        JsonArray rain = daily.getAsJsonArray("precipitation_sum");
        JsonArray et0 = daily.getAsJsonArray("et0_fao_evapotranspiration");
        JsonArray max = daily.getAsJsonArray("temperature_2m_max");
        JsonArray min = daily.getAsJsonArray("temperature_2m_min");
        JsonArray wind = daily.getAsJsonArray("wind_speed_10m_max");
        JsonArray codes = daily.has("weather_code") ? daily.getAsJsonArray("weather_code") : null;
        LocalDate today = LocalDate.now();
        LocalDate nextRain = null;
        List<DayForecast> days = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = LocalDate.parse(dates.get(i).getAsString());
            int chance = chances.get(i).isJsonNull() ? 0 : chances.get(i).getAsInt();
            if (nextRain == null && !date.isBefore(today) && chance >= RAIN_LIKELY_PERCENT) {
                nextRain = date;
            }
            if (!rain.get(i).isJsonNull() && !et0.get(i).isJsonNull() && !max.get(i).isJsonNull()
                    && !min.get(i).isJsonNull() && !wind.get(i).isJsonNull()) {
                days.add(new DayForecast(date, rain.get(i).getAsDouble(), chance, et0.get(i).getAsDouble(),
                        max.get(i).getAsDouble(), min.get(i).getAsDouble(), wind.get(i).getAsDouble(),
                        codes == null || codes.get(i).isJsonNull() ? -1 : codes.get(i).getAsInt()));
            }
        }
        long fetched = body.has(KEY_FETCHED) ? body.get(KEY_FETCHED).getAsLong() : 0;
        return new Weather(current, nextRain, days, fetched, parseSky(body.getAsJsonObject("current")));
    }

    @Nullable
    private static SkyState parseSky(JsonObject current) {
        for (String key : new String[] {"temperature_2m", "precipitation", "cloud_cover", "wind_speed_10m", "is_day"}) {
            if (!current.has(key) || current.get(key).isJsonNull()) {
                return null;
            }
        }
        return new SkyState(current.get("is_day").getAsInt() == 1, current.get("cloud_cover").getAsInt(),
                current.get("precipitation").getAsDouble(), current.get("wind_speed_10m").getAsDouble(),
                current.get("temperature_2m").getAsDouble());
    }

    public void searchPlaces(String query, Consumer<List<Place>> onResult, Runnable onFailure) {
        placeApi.search(query, "pk", "jsonv2", 5).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<JsonArray> call, Response<JsonArray> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    onFailure.run();
                    return;
                }
                try {
                    List<Place> places = new ArrayList<>();
                    for (JsonElement item : response.body()) {
                        JsonObject place = item.getAsJsonObject();
                        places.add(new Place(place.get("display_name").getAsString(),
                                place.get("lat").getAsDouble(), place.get("lon").getAsDouble()));
                    }
                    onResult.accept(places);
                } catch (RuntimeException unreadable) {
                    onFailure.run();
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable error) {
                onFailure.run();
            }
        });
    }

    public LiveData<List<ChatMessage>> chatMessages(String chatKey) {
        return chat(chatKey);
    }

    private MutableLiveData<List<ChatMessage>> chat(String chatKey) {
        return chats.computeIfAbsent(chatKey, key -> new MutableLiveData<>(readChat(key)));
    }

    public void clearChat(String chatKey) {
        chatPrefs.edit().remove(chatKey).apply();
        chat(chatKey).setValue(List.of());
    }

    public ChatLanguage chatLanguage() {
        ChatLanguage saved = ChatLanguage.fromCode(chatPrefs.getString(KEY_CHAT_LANGUAGE, null));
        return saved != null ? saved : ChatLanguage.forLocale(Locale.getDefault());
    }

    public void setChatLanguage(ChatLanguage language) {
        if (language.available()) {
            chatPrefs.edit().putString(KEY_CHAT_LANGUAGE, language.code()).apply();
        }
    }

    /** Sends field facts when available. Exact coordinates and the field name are not included. */
    public void ask(String chatKey, String question, @Nullable FarmField field,
            @Nullable Map<ClimateScenario, RotationAdvisor.Advice> outlooks, String cropContext,
            ChatLanguage language, Consumer<Boolean> onDone) {
        MutableLiveData<List<ChatMessage>> live = chat(chatKey);
        List<ChatMessage> history = new ArrayList<>(live.getValue());
        history.add(new ChatMessage(true, question, List.of(), null, false, language.code()));
        saveChat(chatKey, history);
        live.setValue(List.copyOf(history));

        boolean grounded = field != null && outlooks != null
                && outlooks.containsKey(ClimateScenario.NORMAL);
        JsonArray messages = new JsonArray();
        int start = Math.max(0, history.size() - MAX_HISTORY);
        for (int i = start; i < history.size(); i++) {
            ChatMessage item = history.get(i);
            JsonObject message = new JsonObject();
            message.addProperty("role", item.fromFarmer() ? "user" : "assistant");
            String content = item.text();
            if (i == history.size() - 1) {
                if (!grounded && !cropContext.isEmpty()) {
                    content = "My crops: " + cropContext + ". " + content;
                }
                if (language.instruction() != null) {
                    content += " [Language preference: answer every field in " + language.instruction() + ".]";
                }
            }
            message.addProperty("content", content);
            messages.add(message);
        }
        JsonObject body = new JsonObject();
        body.add("messages", messages);
        body.addProperty("lang", language.serviceCode());
        if (grounded) {
            body.add("analysis", AssistantContext.build(field, outlooks));
        }
        assistantApi.chat(body).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                ChatMessage answer = response.isSuccessful()
                        ? parseAnswer(response.body(), grounded, language) : null;
                finish(chatKey, answer, onDone);
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable error) {
                finish(chatKey, null, onDone);
            }
        });
    }

    private void finish(String chatKey, @Nullable ChatMessage answer, Consumer<Boolean> onDone) {
        if (answer != null) {
            MutableLiveData<List<ChatMessage>> live = chat(chatKey);
            List<ChatMessage> updated = new ArrayList<>(live.getValue());
            updated.add(answer);
            saveChat(chatKey, updated);
            live.setValue(List.copyOf(updated));
        }
        onDone.accept(answer != null);
    }

    private List<ChatMessage> readChat(String key) {
        String saved = chatPrefs.getString(key, null);
        if (saved == null) {
            return List.of();
        }
        try {
            List<ChatMessage> messages = new ArrayList<>();
            for (JsonElement item : JsonParser.parseString(saved).getAsJsonArray()) {
                JsonObject row = item.getAsJsonObject();
                List<String> actions = new ArrayList<>();
                if (row.has("actions")) {
                    row.getAsJsonArray("actions").forEach(value -> actions.add(value.getAsString()));
                }
                messages.add(new ChatMessage(row.get("farmer").getAsBoolean(), row.get("text").getAsString(),
                        actions, row.has("expert") ? row.get("expert").getAsString() : null,
                        row.has("data") && row.get("data").getAsBoolean(),
                        row.has("lang") ? row.get("lang").getAsString() : ChatLanguage.ENGLISH.code()));
            }
            return List.copyOf(messages);
        } catch (RuntimeException corrupted) {
            return List.of();
        }
    }

    private void saveChat(String key, List<ChatMessage> messages) {
        JsonArray saved = new JsonArray();
        for (ChatMessage message : messages.subList(Math.max(0, messages.size() - MAX_SAVED_MESSAGES),
                messages.size())) {
            JsonObject row = new JsonObject();
            row.addProperty("farmer", message.fromFarmer());
            row.addProperty("text", message.text());
            JsonArray actions = new JsonArray();
            message.actions().forEach(actions::add);
            row.add("actions", actions);
            if (message.expert() != null) {
                row.addProperty("expert", message.expert());
            }
            row.addProperty("data", message.usedFieldData());
            row.addProperty("lang", message.languageCode());
            saved.add(row);
        }
        chatPrefs.edit().putString(key, saved.toString()).apply();
    }

    private static boolean citedFieldData(JsonObject body) {
        if (!body.has("ai_used") || !body.get("ai_used").getAsBoolean()
                || !body.has("points") || !body.get("points").isJsonArray()) {
            return false;
        }
        for (JsonElement point : body.getAsJsonArray("points")) {
            if (point.isJsonObject() && point.getAsJsonObject().has("type")
                    && "observed".equals(point.getAsJsonObject().get("type").getAsString())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ChatMessage parseAnswer(@Nullable JsonObject body, boolean grounded, ChatLanguage language) {
        if (body == null) {
            return null;
        }
        String summary = null;
        for (String key : new String[] {"reply", "summary"}) {
            JsonElement value = body.get(key);
            if (summary == null && value != null && value.isJsonPrimitive() && !value.getAsString().isBlank()) {
                summary = value.getAsString().strip();
            }
        }
        if (summary == null) {
            return null;
        }
        List<String> actions = strings(body.get("actions"), MAX_ANSWER_ITEMS);
        List<String> experts = strings(body.get("needs_expert"), 1);
        return new ChatMessage(false, summary, actions, experts.isEmpty() ? null : experts.get(0),
                grounded && citedFieldData(body), language.code());
    }

    private static List<String> strings(@Nullable JsonElement values, int limit) {
        List<String> result = new ArrayList<>();
        if (values != null && values.isJsonArray()) {
            for (JsonElement value : values.getAsJsonArray()) {
                if (result.size() < limit && value.isJsonPrimitive() && !value.getAsString().isBlank()) {
                    result.add(value.getAsString().strip());
                }
            }
        }
        return result;
    }
}
