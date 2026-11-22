package com.agroshift.core.data;

import com.agroshift.core.domain.engine.ClimateProfile;
import com.agroshift.core.domain.engine.ClimateProfile.Metric;
import java.util.EnumMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Converts monthly climate summaries to and from JSON. Missing values are stored as null. */
final class ClimateProfileCodec {
    private ClimateProfileCodec() {
    }

    static String encode(ClimateProfile profile) {
        try {
            JSONObject root = new JSONObject()
                    .put("startYear", profile.startYear())
                    .put("endYear", profile.endYear())
                    .put("yearCount", profile.yearCount())
                    .put("missingFraction", profile.missingFraction());
            JSONObject metrics = new JSONObject();
            for (Metric metric : Metric.values()) {
                JSONArray values = new JSONArray();
                for (double value : profile.series(metric)) {
                    values.put(Double.isNaN(value) ? JSONObject.NULL : Double.valueOf(value));
                }
                metrics.put(metric.name(), values);
            }
            return root.put("metrics", metrics).toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Could not encode climate profile", error);
        }
    }

    static ClimateProfile decode(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject metrics = root.getJSONObject("metrics");
        Map<Metric, double[]> values = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            JSONArray array = metrics.getJSONArray(metric.name());
            double[] monthly = new double[array.length()];
            for (int i = 0; i < monthly.length; i++) {
                monthly[i] = array.isNull(i) ? Double.NaN : array.getDouble(i);
            }
            values.put(metric, monthly);
        }
        return new ClimateProfile(root.getInt("startYear"), root.getInt("endYear"),
                root.getInt("yearCount"), root.getDouble("missingFraction"), values);
    }
}
