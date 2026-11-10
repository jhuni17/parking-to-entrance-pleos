package kr.juchakkaji.app;

import android.car.FuelType;
import org.junit.Test;

import static kr.juchakkaji.app.ParkingPlan.*;
import static kr.juchakkaji.app.VehicleProfile.DriveType.*;
import static org.junit.Assert.*;

public final class VehicleProfileTest {
    @Test public void hardwareHeightAndWidthOverrideCatalogAndTallVehicleUsesAlternative() {
        VehicleProfile profile = VehicleProfile.fromHardware("Hyundai", "IONIQ 5 N", 2025,
                new Integer[]{2250, 4700, 1990, 2100}, new Integer[]{FuelType.UNLEADED});
        assertEquals(Integer.valueOf(2250), profile.heightMm());
        assertEquals(Integer.valueOf(1990), profile.widthMm());
        assertFalse(profile.catalogHeight());
        assertEquals("대안 A · 시연 주차장",
                compare(SampleTrip.LOTS, profile.heightMm(), profile.drive(), 120, false).get(0).lot().name());
    }

    @Test public void catalogRequiresTheExactManufacturerModelAndYear() {
        VehicleProfile match = VehicleProfile.fromHardware("HYUNDAI", "IONIQ 5 N", 2025, null, null);
        assertEquals(Integer.valueOf(1585), match.heightMm());
        assertTrue(match.catalogHeight());
        assertNull(VehicleProfile.fromHardware("HYUNDAI", "IONIQ 5", 2025, null, null).heightMm());
        assertNull(VehicleProfile.fromHardware("HYUNDAI", "IONIQ 5 N", null, null, null).heightMm());
    }

    @Test public void missingOrInvalidDimensionsCannotCreateARequestableOption() {
        VehicleProfile profile = VehicleProfile.fromHardware(null, null, null,
                new Integer[]{0, 4800}, new Integer[]{FuelType.UNLEADED});
        assertNull(profile.heightMm());
        assertEquals(0, requestableCount(compare(SampleTrip.LOTS, profile.heightMm(), profile.drive(), 120, false)));
    }

    @Test public void electricAndAmbiguousFuelDoNotBypassUnknownEvPolicy() {
        VehicleProfile electric = VehicleProfile.fromHardware(null, null, null,
                new Integer[]{1850}, new Integer[]{FuelType.ELECTRIC});
        VehicleProfile mixed = VehicleProfile.fromHardware(null, null, null,
                new Integer[]{1850}, new Integer[]{FuelType.ELECTRIC, FuelType.UNLEADED});
        assertEquals(ELECTRIC, electric.drive());
        assertEquals(NEEDS_CHECK, mixed.drive());
        for (VehicleProfile profile : new VehicleProfile[]{electric, mixed}) {
            var options = compare(SampleTrip.LOTS, profile.heightMm(), profile.drive(), 120, false);
            assertEquals("대안 A · 시연 주차장", options.get(0).lot().name());
            assertFalse(options.get(1).requestAvailability().available());
        }
    }
}
