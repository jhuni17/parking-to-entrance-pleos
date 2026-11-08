package kr.juchakkaji.app;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.content.pm.PackageManager;

/** Reads static, global AAOS properties. Call on a background thread. */
final class AndroidVehicleInfo {
    static VehicleProfile read(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return VehicleProfile.UNKNOWN;
        }
        Car car = null;
        try {
            car = Car.createCar(context);
            if (car == null || !car.isConnected()) return VehicleProfile.UNKNOWN;
            CarPropertyManager manager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
            if (manager == null) return VehicleProfile.UNKNOWN;
            return VehicleProfile.fromHardware(
                    get(manager, String.class, VehiclePropertyIds.INFO_MAKE),
                    get(manager, String.class, VehiclePropertyIds.INFO_MODEL),
                    get(manager, Integer.class, VehiclePropertyIds.INFO_MODEL_YEAR),
                    get(manager, Integer[].class, VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS),
                    get(manager, Integer[].class, VehiclePropertyIds.INFO_FUEL_TYPE));
        } catch (RuntimeException | LinkageError unavailable) {
            return VehicleProfile.UNKNOWN;
        } finally {
            if (car != null) car.disconnect();
        }
    }

    private static <T> T get(CarPropertyManager manager, Class<T> type, int propertyId) {
        try {
            CarPropertyValue<T> value = manager.getProperty(type, propertyId, 0);
            return value != null && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE ? value.getValue() : null;
        } catch (RuntimeException unavailable) {
            // One unsupported or inaccessible property must not discard the rest of the profile.
            return null;
        }
    }

    private AndroidVehicleInfo() {}
}
