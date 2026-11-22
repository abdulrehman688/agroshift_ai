package com.agroshift.core.ui;

import androidx.annotation.DrawableRes;
import com.agroshift.core.domain.scene.CropStage;
import java.util.Map;

/** Real, openly licensed photos for each crop, ordered from sowing to harvest. */
public final class CropPhotos {
    public record Stage(@DrawableRes int photo, @DrawableRes int mask) {
    }

    private static final Map<String, Stage[]> STAGES = Map.ofEntries(
            Map.entry("wheat", new Stage[] {new Stage(R.drawable.crop_wheat_0, R.drawable.crop_wheat_0_mask), new Stage(R.drawable.crop_wheat_1, R.drawable.crop_wheat_1_mask), new Stage(R.drawable.crop_wheat_2, R.drawable.crop_wheat_2_mask), new Stage(R.drawable.crop_wheat_3, R.drawable.crop_wheat_3_mask), new Stage(R.drawable.crop_wheat_4, R.drawable.crop_wheat_4_mask), new Stage(R.drawable.crop_wheat_5, R.drawable.crop_wheat_5_mask)}),
            Map.entry("cotton", new Stage[] {new Stage(R.drawable.crop_cotton_0, R.drawable.crop_cotton_0_mask), new Stage(R.drawable.crop_cotton_1, R.drawable.crop_cotton_1_mask), new Stage(R.drawable.crop_cotton_2, R.drawable.crop_cotton_2_mask), new Stage(R.drawable.crop_cotton_3, R.drawable.crop_cotton_3_mask), new Stage(R.drawable.crop_cotton_4, R.drawable.crop_cotton_4_mask)}),
            Map.entry("rice", new Stage[] {new Stage(R.drawable.crop_rice_0, R.drawable.crop_rice_0_mask), new Stage(R.drawable.crop_rice_1, R.drawable.crop_rice_1_mask), new Stage(R.drawable.crop_rice_2, R.drawable.crop_rice_2_mask), new Stage(R.drawable.crop_rice_3, R.drawable.crop_rice_3_mask), new Stage(R.drawable.crop_rice_4, R.drawable.crop_rice_4_mask), new Stage(R.drawable.crop_rice_5, R.drawable.crop_rice_5_mask)}),
            Map.entry("maize", new Stage[] {new Stage(R.drawable.crop_maize_0, R.drawable.crop_maize_0_mask), new Stage(R.drawable.crop_maize_1, R.drawable.crop_maize_1_mask), new Stage(R.drawable.crop_maize_2, R.drawable.crop_maize_2_mask), new Stage(R.drawable.crop_maize_3, R.drawable.crop_maize_3_mask), new Stage(R.drawable.crop_maize_4, R.drawable.crop_maize_4_mask), new Stage(R.drawable.crop_maize_5, R.drawable.crop_maize_5_mask)}),
            Map.entry("sorghum", new Stage[] {new Stage(R.drawable.crop_sorghum_0, R.drawable.crop_sorghum_0_mask), new Stage(R.drawable.crop_sorghum_1, R.drawable.crop_sorghum_1_mask), new Stage(R.drawable.crop_sorghum_2, R.drawable.crop_sorghum_2_mask), new Stage(R.drawable.crop_sorghum_3, R.drawable.crop_sorghum_3_mask), new Stage(R.drawable.crop_sorghum_4, R.drawable.crop_sorghum_4_mask), new Stage(R.drawable.crop_sorghum_5, R.drawable.crop_sorghum_5_mask)}),
            Map.entry("mungbean", new Stage[] {new Stage(R.drawable.crop_mungbean_0, R.drawable.crop_mungbean_0_mask), new Stage(R.drawable.crop_mungbean_1, R.drawable.crop_mungbean_1_mask), new Stage(R.drawable.crop_mungbean_2, R.drawable.crop_mungbean_2_mask), new Stage(R.drawable.crop_mungbean_3, R.drawable.crop_mungbean_3_mask), new Stage(R.drawable.crop_mungbean_4, R.drawable.crop_mungbean_4_mask)}),
            Map.entry("chickpea", new Stage[] {new Stage(R.drawable.crop_chickpea_0, R.drawable.crop_chickpea_0_mask), new Stage(R.drawable.crop_chickpea_1, R.drawable.crop_chickpea_1_mask), new Stage(R.drawable.crop_chickpea_2, R.drawable.crop_chickpea_2_mask), new Stage(R.drawable.crop_chickpea_3, R.drawable.crop_chickpea_3_mask), new Stage(R.drawable.crop_chickpea_4, R.drawable.crop_chickpea_4_mask), new Stage(R.drawable.crop_chickpea_5, R.drawable.crop_chickpea_5_mask)}),
            Map.entry("berseem", new Stage[] {new Stage(R.drawable.crop_berseem_0, R.drawable.crop_berseem_0_mask), new Stage(R.drawable.crop_berseem_1, R.drawable.crop_berseem_1_mask), new Stage(R.drawable.crop_berseem_2, R.drawable.crop_berseem_2_mask), new Stage(R.drawable.crop_berseem_3, R.drawable.crop_berseem_3_mask), new Stage(R.drawable.crop_berseem_4, R.drawable.crop_berseem_4_mask)}),
            Map.entry("mustard", new Stage[] {new Stage(R.drawable.crop_mustard_0, R.drawable.crop_mustard_0_mask), new Stage(R.drawable.crop_mustard_1, R.drawable.crop_mustard_1_mask), new Stage(R.drawable.crop_mustard_2, R.drawable.crop_mustard_2_mask), new Stage(R.drawable.crop_mustard_3, R.drawable.crop_mustard_3_mask), new Stage(R.drawable.crop_mustard_4, R.drawable.crop_mustard_4_mask), new Stage(R.drawable.crop_mustard_5, R.drawable.crop_mustard_5_mask)}),
            Map.entry("oats", new Stage[] {new Stage(R.drawable.crop_oats_0, R.drawable.crop_oats_0_mask), new Stage(R.drawable.crop_oats_1, R.drawable.crop_oats_1_mask), new Stage(R.drawable.crop_oats_2, R.drawable.crop_oats_2_mask), new Stage(R.drawable.crop_oats_3, R.drawable.crop_oats_3_mask), new Stage(R.drawable.crop_oats_4, R.drawable.crop_oats_4_mask)}),
            Map.entry("alfalfa", new Stage[] {new Stage(R.drawable.crop_alfalfa_0, R.drawable.crop_alfalfa_0_mask), new Stage(R.drawable.crop_alfalfa_1, R.drawable.crop_alfalfa_1_mask), new Stage(R.drawable.crop_alfalfa_2, R.drawable.crop_alfalfa_2_mask), new Stage(R.drawable.crop_alfalfa_3, R.drawable.crop_alfalfa_3_mask), new Stage(R.drawable.crop_alfalfa_4, R.drawable.crop_alfalfa_4_mask)}));

    private CropPhotos() {
    }

    private static Stage[] stages(String crop) {
        return STAGES.getOrDefault(crop, STAGES.get("wheat"));
    }

    /** A representative mid-season photo for crop cards. */
    @DrawableRes
    public static int photo(String crop) {
        Stage[] stages = stages(crop);
        return stages[stages.length / 2].photo();
    }

    /** The real photo for this point in the season, for lists where a still image scrolls fast. */
    public static Stage nearest(String crop, float growth) {
        Stage[] stages = stages(crop);
        return stages[CropStage.index(growth, stages.length)];
    }

    /** Moves step by step to the real photo for this point in the crop's season. */
    public static void show(LivingPhotoView view, String crop, float growth) {
        Stage[] stages = stages(crop);
        int[] photos = new int[stages.length];
        int[] masks = new int[stages.length];
        for (int i = 0; i < stages.length; i++) {
            photos[i] = stages[i].photo();
            masks[i] = stages[i].mask();
        }
        view.setTrack(photos, masks, CropStage.index(growth, stages.length));
    }
}
