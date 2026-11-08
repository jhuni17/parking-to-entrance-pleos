package kr.juchakkaji.app;

import org.junit.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static kr.juchakkaji.app.ParkingPlan.*;
import static org.junit.Assert.*;

public final class DemoProfileTest {
    @Test public void allSixteenInputCombinationsRoundTripWithSamePlan() {
        int count = 0;
        for (boolean roof : new boolean[]{false, true}) {
            for (boolean electric : new boolean[]{false, true}) {
                for (boolean discount : new boolean[]{false, true}) {
                    for (int minutes : new int[]{60, 120}) {
                        DemoProfile before = new DemoProfile(roof, electric, discount, minutes);
                        DemoProfile after = roundTrip(before);
                        assertEquals(roof, after.roofBox());
                        assertEquals(electric, after.electric());
                        assertEquals(discount, after.purchaseDiscount());
                        assertEquals(minutes, after.stayMinutes());
                        assertEquals(roof ? 2250 : 1850, after.vehicleHeightMm());
                        assertEquals(plan(before), plan(after));
                        count++;
                    }
                }
            }
        }
        assertEquals(16, count);
    }

    @Test public void missingStateUsesOriginalFirstStartDefaults() {
        for (DemoProfile profile : new DemoProfile[]{DemoProfile.defaults(),
                DemoProfile.restore(null), DemoProfile.restore(Map.of())}) {
            assertFalse(profile.roofBox());
            assertEquals(1850, profile.vehicleHeightMm());
            assertFalse(profile.electric());
            assertTrue(profile.purchaseDiscount());
            assertEquals(120, profile.stayMinutes());
            assertEquals("코엑스 지하주차장", plan(profile).get(0).lot().name());
            assertEquals(Integer.valueOf(0), plan(profile).get(0).expectedWon());
        }
    }

    @Test public void restoredRoofBoxKeepsCoexRestrictedAndAlternativeFirst() {
        List<Option> options = plan(roundTrip(new DemoProfile(true, false, true, 120)));
        Option coex = find(options, "코엑스 지하주차장");
        assertEquals(Fit.RESTRICTED, coex.fit());
        assertFalse(coex.requestAvailability().available());
        assertFalse(routeRequest(SampleTrip.VISIT, coex).isPresent());
        assertEquals("대안 A · 시연 주차장", options.get(0).lot().name());
        assertEquals(Integer.valueOf(6000), options.get(0).expectedWon());
    }

    @Test public void restoredElectricProfileKeepsUnknownPolicyUnrequestable() {
        Option coex = find(plan(roundTrip(new DemoProfile(false, true, true, 120))),
                "코엑스 지하주차장");
        assertEquals(Fit.NEEDS_CHECK, coex.fit());
        assertFalse(coex.requestAvailability().available());
        assertFalse(routeRequest(SampleTrip.VISIT, coex).isPresent());
    }

    @Test public void restoredNoDiscountAndOneHourKeepFullQuotes() {
        List<Option> options = plan(roundTrip(new DemoProfile(false, false, false, 60)));
        assertEquals(Integer.valueOf(6000), find(options, "코엑스 지하주차장").expectedWon());
        assertEquals(Integer.valueOf(3000), find(options, "대안 A · 시연 주차장").expectedWon());
    }

    private DemoProfile roundTrip(DemoProfile profile) {
        Map<String, Object> saved = new HashMap<>(profile.save());
        assertEquals(4, saved.size());
        return DemoProfile.restore(saved);
    }

    private List<Option> plan(DemoProfile profile) {
        return compare(SampleTrip.LOTS, profile.vehicleHeightMm(), profile.electric(),
                profile.stayMinutes(), profile.purchaseDiscount());
    }

    private Option find(List<Option> options, String name) {
        return options.stream().filter(option -> option.lot().name().equals(name)).findFirst().get();
    }
}
