package com.agroshift.core.domain.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Abstract data type holding one strongest alert per type, month and stage.
 *
 * <p>Rep invariant: keys uniquely identify entries, and each entry names every merged crop.
 * Abstraction function: alerts in order of severity, then month, then type.
 */
public final class AlertQueue {
    private static final Comparator<ClimateAlert> ORDER = Comparator
            .comparing(ClimateAlert::severity, Comparator.reverseOrder())
            .thenComparingInt(ClimateAlert::month)
            .thenComparing(ClimateAlert::type);

    private final Map<String, ClimateAlert> alerts = new LinkedHashMap<>();

    public void offer(ClimateAlert alert) {
        String key = alert.type() + ":" + alert.month() + ":" + alert.stageKey();
        ClimateAlert existing = alerts.get(key);
        if (existing == null) {
            alerts.put(key, alert);
            return;
        }
        List<String> crops = new ArrayList<>(existing.cropSlugs());
        for (String crop : alert.cropSlugs()) {
            if (!crops.contains(crop)) {
                crops.add(crop);
            }
        }
        ClimateAlert stronger = alert.severity().compareTo(existing.severity()) > 0 ? alert : existing;
        alerts.put(key, new ClimateAlert(stronger.type(), stronger.severity(), stronger.month(), crops,
                stronger.stageKey(), stronger.value(), stronger.threshold()));
    }

    public List<ClimateAlert> ordered() {
        PriorityQueue<ClimateAlert> queue = new PriorityQueue<>(Math.max(1, alerts.size()), ORDER);
        queue.addAll(alerts.values());
        List<ClimateAlert> result = new ArrayList<>(queue.size());
        while (!queue.isEmpty()) {
            result.add(queue.poll());
        }
        return List.copyOf(result);
    }
}
