package kr.juchakkaji.app;

import java.util.Map;

/** Demo input state only. No Android, selection, dialog or route-result state. */
record DemoProfile(boolean roofBox, boolean electric, boolean purchaseDiscount, int stayMinutes) {
    static final String ROOF_BOX = "demoProfile.roofBox";
    static final String ELECTRIC = "demoProfile.electric";
    static final String PURCHASE_DISCOUNT = "demoProfile.purchaseDiscount";
    static final String STAY_MINUTES = "demoProfile.stayMinutes";

    static DemoProfile defaults() {
        return new DemoProfile(false, false, true, 120);
    }

    int vehicleHeightMm() {
        return roofBox ? 2250 : 1850;
    }

    Map<String, Object> save() {
        return Map.of(ROOF_BOX, roofBox, ELECTRIC, electric,
                PURCHASE_DISCOUNT, purchaseDiscount, STAY_MINUTES, stayMinutes);
    }

    /** Missing saved inputs retain the first-start defaults. */
    static DemoProfile restore(Map<String, Object> saved) {
        DemoProfile defaults = defaults();
        if (saved == null) return defaults;
        return new DemoProfile((Boolean) saved.getOrDefault(ROOF_BOX, defaults.roofBox()),
                (Boolean) saved.getOrDefault(ELECTRIC, defaults.electric()),
                (Boolean) saved.getOrDefault(PURCHASE_DISCOUNT, defaults.purchaseDiscount()),
                (Integer) saved.getOrDefault(STAY_MINUTES, defaults.stayMinutes()));
    }
}
