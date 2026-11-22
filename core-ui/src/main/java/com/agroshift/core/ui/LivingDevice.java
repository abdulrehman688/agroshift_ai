package com.agroshift.core.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.os.PowerManager;
import com.agroshift.core.domain.scene.RenderBudget;

/** Reads the phone's capabilities so living photos suit every device. */
public final class LivingDevice {
    private LivingDevice() {
    }

    public static RenderBudget budget(Context context) {
        ActivityManager activity = context.getSystemService(ActivityManager.class);
        PowerManager power = context.getSystemService(PowerManager.class);
        int memory = activity == null ? 128 : activity.getMemoryClass();
        boolean lowRam = activity != null && activity.isLowRamDevice();
        boolean saver = power != null && power.isPowerSaveMode();
        return RenderBudget.choose(new RenderBudget.Device(memory, lowRam,
                Runtime.getRuntime().availableProcessors(), saver, ZameenUi.motionEnabled(context)));
    }
}
