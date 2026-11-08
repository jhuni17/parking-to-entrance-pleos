package kr.juchakkaji.app;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.content.pm.PackageManager;

/** Reads static, global AAOS properties. Call on a background thread. */
final class AndroidVehicleInfo {
    static VehicleReadResult read(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return VehicleReadResult.unavailable();
        }
        Car car = null;
        try {
            car = Car.createCar(context);
            if (car == null || !car.isConnected()) return VehicleReadResult.disconnected();
            CarPropertyManager manager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
            if (manager == null) return VehicleReadResult.disconnected();
            return VehicleReadResult.connected(VehiclePropertyReader.read(property -> {
                CarPropertyValue<?> value = switch (property) {
                    case MAKE -> manager.getProperty(String.class, VehiclePropertyIds.INFO_MAKE, 0);
                    case MODEL -> manager.getProperty(String.class, VehiclePropertyIds.INFO_MODEL, 0);
                    case YEAR -> manager.getProperty(Integer.class, VehiclePropertyIds.INFO_MODEL_YEAR, 0);
                    case DIMENSIONS -> manager.getProperty(Integer[].class, VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS, 0);
                    case FUEL -> manager.getProperty(Integer[].class, VehiclePropertyIds.INFO_FUEL_TYPE, 0);
                };
                return value == null ? null : new VehiclePropertyReader.Response(
                        value.getStatus() == CarPropertyValue.STATUS_AVAILABLE,
                        value.getStatus() == CarPropertyValue.STATUS_AVAILABLE ? value.getValue() : null);
            }));
        } catch (RuntimeException | LinkageError unavailable) {
            return VehicleReadResult.unavailable();
        } finally {
            if (car != null) {
                try { car.disconnect(); } catch (RuntimeException ignored) { /* Cleanup only. */ }
            }
        }
    }

    private AndroidVehicleInfo() {}
}
