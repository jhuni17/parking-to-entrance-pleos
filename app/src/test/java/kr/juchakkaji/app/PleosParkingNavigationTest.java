package kr.juchakkaji.app;

import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.RouteInfo;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class PleosParkingNavigationTest {
    @Test public void missingSubIdRequiresTheRequestedPoiAndDrivingPoint() {
        RouteInfo target = new RouteInfo(37.51, 127.06, null, null,
                "입구", "parking", "", "0", RouteOption.RECOMMENDED, "", false);
        assertTrue(PleosParkingNavigation.sameEntrance(destination(37.51, "parking", null), target));
        assertFalse(PleosParkingNavigation.sameEntrance(destination(37.52, "parking", null), target));
        assertFalse(PleosParkingNavigation.sameEntrance(destination(37.51, "visit", null), target));
        assertFalse(PleosParkingNavigation.sameEntrance(destination(37.51, "parking", "1"), target));
        assertFalse(PleosParkingNavigation.sameEntrance(destination(37.52, "parking", "0"), target));
    }

    @Test public void sincheonChecksRecordedHeightAndRoutesToTheNativeDrivingPoint() {
        var lot = SincheonParking.LOT;
        var route = SincheonParking.route(lot.entrance());
        assertNotNull(route);
        assertTrue(ParkingPlan.compare(List.of(lot), 1776, VehicleProfile.DriveType.ELECTRIC, 120, false)
                .get(0).requestAvailability().available());
        assertFalse(ParkingPlan.compare(List.of(lot), 3001, VehicleProfile.DriveType.ELECTRIC, 120, false)
                .get(0).requestAvailability().available());
        var verifiedPoi = new DestinationInfo(37.52190833, 127.10321944, null, null, "신천유수지공영주차장", "",
                0, 0, 0, "36khp46sed75cm5nqhe3", null, "");
        var facilityCenter = new DestinationInfo(37.52231457, 127.10290327, null, null, "신천유수지공영주차장", "",
                0, 0, 0, "36khp46sed75cm5nqhe3", null, "");
        assertTrue(PleosParkingNavigation.sameEntrance(verifiedPoi, route));
        assertFalse(PleosParkingNavigation.sameEntrance(facilityCenter, route));
    }

    @Test public void coexHeightDemoRanksTheAlternativeWithoutARequestableEntrance() {
        var lots = CoexParking.lotsFor("코엑스", 37.51027777, 127.06156388, true);
        var regular = ParkingPlan.compare(lots, 1776, VehicleProfile.DriveType.ELECTRIC, 120, false);
        var loaded = ParkingPlan.compare(lots, 2350, VehicleProfile.DriveType.ELECTRIC, 120, false);
        assertEquals("코엑스 지하주차장", regular.get(0).lot().name());
        assertTrue(regular.get(0).requestAvailability().available());
        assertEquals(CoexParking.EMULATOR_ALTERNATIVE, loaded.get(0).lot());
        assertEquals(ParkingPlan.Fit.MATCHES_RECORDED_RULES, loaded.get(0).fit());
        assertFalse(loaded.get(0).requestAvailability().available());
        assertNull(CoexParking.route(loaded.get(0).lot().entrance()));
        assertEquals(1, CoexParking.lotsFor("코엑스", 37.51027777, 127.06156388, false).size());
    }

    private static DestinationInfo destination(double latitude, String id, String subId) {
        return new DestinationInfo(latitude, 127.06, null, null, "입구", "",
                0, 0, 0, id, subId, "");
    }
}
