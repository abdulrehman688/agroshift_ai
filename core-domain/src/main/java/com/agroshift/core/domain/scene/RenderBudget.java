package com.agroshift.core.domain.scene;

/** Chooses how much GPU work a phone should do so visuals stay smooth and battery friendly. */
public record RenderBudget(Tier tier, int maxWidth, int frameMillis, boolean reducedEffects) {
    public enum Tier { STILL, LITE, BALANCED, HIGH }

    public record Device(int memoryClassMb, boolean lowRam, int cores, boolean powerSave, boolean motionEnabled) {
    }

    private static final int LOW_MEMORY_MB = 128;
    private static final int HIGH_MEMORY_MB = 256;
    private static final int LOW_CORES = 4;
    private static final int HIGH_CORES = 6;

    public static RenderBudget choose(Device device) {
        if (device.powerSave() || !device.motionEnabled()) {
            return new RenderBudget(Tier.STILL, 0, 0, true);
        }
        if (device.lowRam() || device.memoryClassMb() < LOW_MEMORY_MB || device.cores() < LOW_CORES) {
            return new RenderBudget(Tier.LITE, 480, 50, true);
        }
        if (device.memoryClassMb() >= HIGH_MEMORY_MB && device.cores() >= HIGH_CORES) {
            return new RenderBudget(Tier.HIGH, 960, 33, false);
        }
        return new RenderBudget(Tier.BALANCED, 720, 33, false);
    }

    public boolean animated() {
        return tier != Tier.STILL;
    }
}
