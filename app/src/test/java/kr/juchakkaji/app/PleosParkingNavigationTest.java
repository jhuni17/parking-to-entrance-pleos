package kr.juchakkaji.app;

import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.RouteInfo;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PleosParkingNavigationTest {
    @Test public void missingSubIdRequiresTheRequestedPoiAndDrivingPoint() {
        RouteInfo target = new RouteInfo(37.51, 127.06, null, null,
                "입구", "parking", "", "0", RouteOption.RECOMMENDED, "", false);
        assertTrue(PleosParkingNavigation.sameEntrance(destination(37.51, "parking", null), target));
        assertFalse(PleosParkingNavigation.sameEntrance(destination(37.52, "parking", null), target));
        assertFalse(PleosParkingNavigation.sameEntrance(destination(37.51, "visit", null), target));
        assertFalse(PleosParkingNavigation.sameEntrance(destination(37.51, "parking", "1"), target));
    }

    private static DestinationInfo destination(double latitude, String id, String subId) {
        return new DestinationInfo(latitude, 127.06, null, null, "입구", "",
                0, 0, 0, id, subId, "");
    }
}
