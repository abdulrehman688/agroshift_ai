package com.agroshift.core.domain.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable crop traits. Values come from the AgroShift crop table, citing FAO sources. */
public record Crop(String slug, String family, String role, int waterNeedMm, int droughtTolerance,
                   int heatTolerance, int frostTolerance, boolean nitrogenFixing, String rootDepth,
                   int groundCover, int residue, int erosionProtection, int incomeLevel,
                   boolean specialEquipment, CropCalendar calendar) {

    private static final Map<String, Crop> ALL = index(List.of(
            new Crop("wheat", "grass", "cash", 500, 3, 2, 4, false, "deep", 4, 3, 4, 4, false,
                    new CropCalendar(11, 4, List.of(
                            GrowthStage.of("establishment", 0, 1),
                            GrowthStage.of("tillering", 1, 3, Hazard.FROST),
                            GrowthStage.of("heading", 3, 4, Hazard.HEAT),
                            GrowthStage.of("grain_fill", 4, 5, Hazard.HEAT, Hazard.DROUGHT)))),
            new Crop("cotton", "malvaceae", "cash", 900, 3, 5, 1, false, "deep", 2, 2, 2, 5, false,
                    new CropCalendar(5, 11, List.of(
                            GrowthStage.of("establishment", 0, 1),
                            GrowthStage.of("vegetative", 1, 2),
                            GrowthStage.of("flowering_boll_set", 2, 4, Hazard.HEAT, Hazard.DROUGHT,
                                    Hazard.WATERLOGGING),
                            GrowthStage.of("boll_opening", 4, 6)))),
            new Crop("rice", "grass", "cash", 1200, 1, 4, 1, false, "shallow", 3, 3, 2, 4, true,
                    new CropCalendar(7, 11, List.of(
                            GrowthStage.of("transplant_establishment", 0, 1),
                            GrowthStage.of("tillering", 1, 2, Hazard.DROUGHT),
                            GrowthStage.of("flowering", 2, 3, Hazard.HEAT, Hazard.DROUGHT),
                            GrowthStage.of("grain_fill", 3, 4)))),
            new Crop("sorghum", "grass", "cash", 500, 5, 5, 1, false, "deep", 4, 4, 4, 3, false,
                    new CropCalendar(7, 10, List.of(
                            GrowthStage.of("establishment", 0, 1),
                            GrowthStage.of("vegetative", 1, 2),
                            GrowthStage.of("flowering", 2, 3, Hazard.DROUGHT)))),
            new Crop("chickpea", "legume", "legume_rotation", 350, 5, 3, 3, true, "deep", 2, 2, 2, 3,
                    false, new CropCalendar(11, 4, List.of(
                            GrowthStage.of("establishment", 0, 1),
                            GrowthStage.of("vegetative", 1, 3),
                            GrowthStage.of("flowering", 3, 4, Hazard.FROST, Hazard.HEAT),
                            GrowthStage.of("pod_fill", 4, 5, Hazard.HEAT, Hazard.DROUGHT)))),
            new Crop("mungbean", "legume", "legume_rotation", 350, 4, 4, 1, true, "medium", 3, 2, 3, 3,
                    false, new CropCalendar(4, 6, List.of(
                            GrowthStage.of("establishment", 0, 1),
                            GrowthStage.of("flowering_pod_fill", 1, 2, Hazard.HEAT, Hazard.DROUGHT)))),
            new Crop("berseem", "legume", "forage", 700, 2, 2, 3, true, "medium", 5, 3, 5, 3, false,
                    new CropCalendar(10, 4, List.of(
                            GrowthStage.of("establishment", 0, 2),
                            GrowthStage.of("cutting_season", 2, 6, Hazard.FROST)))),
            new Crop("maize", "grass", "cash", 650, 2, 3, 1, false, "deep", 3, 4, 3, 4, false,
                    new CropCalendar(7, 11, List.of(
                            GrowthStage.of("establishment", 0, 1, Hazard.WATERLOGGING),
                            GrowthStage.of("vegetative", 1, 2),
                            GrowthStage.of("flowering", 2, 3, Hazard.HEAT, Hazard.DROUGHT),
                            GrowthStage.of("grain_fill", 3, 4, Hazard.DROUGHT)))),
            new Crop("mustard", "brassica", "cash", 400, 4, 3, 4, false, "deep", 3, 2, 3, 3, false,
                    new CropCalendar(10, 3, List.of(
                            GrowthStage.of("establishment", 0, 1),
                            GrowthStage.of("vegetative", 1, 3),
                            GrowthStage.of("flowering", 3, 4, Hazard.FROST),
                            GrowthStage.of("pod_fill", 4, 5, Hazard.HEAT)))),
            new Crop("oats", "grass", "cash", 450, 3, 2, 4, false, "medium", 4, 3, 4, 2, false,
                    new CropCalendar(11, 3, List.of(
                            GrowthStage.of("establishment", 0, 1),
                            GrowthStage.of("growth_cuts", 1, 4)))),
            new Crop("alfalfa", "legume", "forage", 900, 4, 3, 4, true, "deep", 5, 4, 5, 3, true,
                    new CropCalendar(10, 9, List.of(
                            GrowthStage.of("establishment", 0, 2),
                            GrowthStage.of("cutting_season", 2, 11, Hazard.WATERLOGGING))))));

    public static final List<String> KHARIF = List.of("cotton", "rice", "maize", "sorghum", "mungbean");
    public static final List<String> RABI = List.of("wheat", "chickpea", "berseem", "mustard", "oats");
    public static final String YEAR_ROUND = "alfalfa";

    private static Map<String, Crop> index(List<Crop> crops) {
        Map<String, Crop> map = new LinkedHashMap<>();
        for (Crop crop : crops) {
            map.put(crop.slug(), crop);
        }
        return Map.copyOf(map);
    }

    public static Crop bySlug(String slug) {
        Crop crop = ALL.get(slug);
        if (crop == null) {
            throw new IllegalArgumentException("Unknown crop: " + slug);
        }
        return crop;
    }
}
