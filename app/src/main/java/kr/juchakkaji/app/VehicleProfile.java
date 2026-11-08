package kr.juchakkaji.app;

import android.car.FuelType;

import java.util.Locale;

record VehicleProfile(String model, Integer heightMm, Integer widthMm, DriveType drive,
                      boolean catalogHeight) {
    enum DriveType { ELECTRIC, NON_ELECTRIC, NEEDS_CHECK }

    static final VehicleProfile UNKNOWN = new VehicleProfile("", null, null, DriveType.NEEDS_CHECK, false);
    static final String CATALOG_SOURCE = "https://dmassets.hyundai.com/is/content/hyundaiautoever/IONIQ_5N_MY25pdf";

    static VehicleProfile fromHardware(String make, String model, Integer year,
                                      Integer[] dimensions, Integer[] fuels) {
        String modelName = model == null ? "" : model.trim();
        Integer height = dimension(dimensions, 0);
        Integer width = dimension(dimensions, 2);
        boolean catalogHeight = false;
        // The brochure covers one exact MY25 model. Generic model or missing year is ambiguous.
        if ("HYUNDAI".equals(normalized(make)) && "IONIQ5N".equals(normalized(modelName))
                && Integer.valueOf(2025).equals(year)) {
            if (height == null) {
                height = 1585;
                catalogHeight = true;
            }
            if (width == null) width = 1940;
        }
        return new VehicleProfile(modelName, height, width, driveType(fuels), catalogHeight);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }

    private static Integer dimension(Integer[] values, int index) {
        return values == null || values.length <= index || values[index] == null || values[index] <= 0
                ? null : values[index];
    }

    private static DriveType driveType(Integer[] fuels) {
        if (fuels == null || fuels.length == 0) return DriveType.NEEDS_CHECK;
        boolean electric = false;
        boolean conventional = false;
        for (Integer fuel : fuels) {
            if (fuel == null) return DriveType.NEEDS_CHECK;
            switch (fuel) {
                case FuelType.ELECTRIC -> electric = true;
                case FuelType.UNLEADED, FuelType.LEADED, FuelType.DIESEL_1, FuelType.DIESEL_2,
                     FuelType.BIODIESEL, FuelType.E85, FuelType.LPG, FuelType.CNG, FuelType.LNG,
                     FuelType.HYDROGEN -> conventional = true;
                default -> { return DriveType.NEEDS_CHECK; }
            }
        }
        if (electric && conventional) return DriveType.NEEDS_CHECK;
        return electric ? DriveType.ELECTRIC : DriveType.NON_ELECTRIC;
    }
}
