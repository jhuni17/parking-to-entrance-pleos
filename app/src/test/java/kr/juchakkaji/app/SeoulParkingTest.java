package kr.juchakkaji.app;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

public class SeoulParkingTest {
    private static final Instant NOW = Instant.parse("2026-11-10T06:21:45Z");

    @Test
    public void nearbyLotIsRecommendedOnlyWithFreshCrowdingAndAvailableSpots() {
        var destination = lot("1277145", 357, 347, NOW, false);
        var nearby = lot("173646", 192, 175, NOW.minusSeconds(20), true);
        var comparison = SeoulParking.compare(SeoulParking.Area.JAMSIL, List.of(destination, nearby), NOW);
        assertEquals(Integer.valueOf(10), comparison.destination().available(NOW));
        assertEquals(Integer.valueOf(17), comparison.nearby().available(NOW));
        assertTrue(comparison.destinationCrowded());
        assertTrue(comparison.recommendNearby());
        assertFalse(SeoulParking.compare(SeoulParking.Area.JAMSIL, List.of(destination, nearby),
                NOW.plusSeconds(301)).recommendNearby());
        assertFalse(SeoulParking.compare(SeoulParking.Area.JAMSIL,
                List.of(destination, lot("173646", 192, 175, NOW, false)), NOW).recommendNearby());
    }

    @Test
    public void unknownOccupancyDoesNotBecomeVacantOrCrowded() {
        var lot = lot("1366600", 180, null, null, false);
        assertNull(lot.available(NOW));
        assertNull(lot("173646", 192, 195, NOW, true).available(NOW));
        assertNull(lot("173646", 192, 175, NOW.plusSeconds(31), true).available(NOW));
        assertEquals(Integer.valueOf(0), lot("173646", 192, 192, NOW, true).available(NOW));
        assertFalse(SeoulParking.compare(SeoulParking.Area.COEX, List.of(lot), NOW).recommendNearby());
    }

    @Test
    public void matchesTheNativeStationNameOnlyNearJamsil() {
        assertEquals(SeoulParking.Area.JAMSIL,
                SeoulParking.areaFor("잠실역 2호선", 37.51321388, 127.10002777, false));
        assertNull(SeoulParking.areaFor("잠실역 2호선", 37.52, 127.104, false));
    }

    @Test
    public void displayKeepsSeparateBasicAndAdditionalRates() {
        assertEquals("5분 440원", new SeoulParking.Rate(5, 440, 5, 440).label());
        assertEquals("기본 30분 2,000원 · 추가 10분 500원",
                new SeoulParking.Rate(30, 2000, 10, 500).label());
    }

    @Test
    public void vacancyExpiresAtFiveMinutesAndTheLastKnownRateRemainsUsable() {
        var rate = new SeoulParking.Rate(5, 440, 5, 440);
        var destination = lot("1277145", 357, 347, NOW, false);
        var nearby = new SeoulParking.LotStatus("173646", "신천유수지", 192, 175, NOW, rate, true);
        var lots = List.of(destination, nearby);
        Instant expiry = NOW.plusSeconds(300);
        assertEquals(expiry, nearby.vacancyExpiresAt(expiry.minusMillis(1)));
        assertTrue(SeoulParking.compare(SeoulParking.Area.JAMSIL, lots, expiry.minusMillis(1)).recommendNearby());
        var afterExpiry = SeoulParking.compare(SeoulParking.Area.JAMSIL, lots, expiry);
        assertNull(afterExpiry.nearby().available(expiry));
        assertNull(afterExpiry.nearby().vacancyExpiresAt(expiry));
        assertFalse(afterExpiry.recommendNearby());
        assertEquals("5분 440원", afterExpiry.nearby().rate().label());
    }

    private static SeoulParking.LotStatus lot(String code, int capacity, Integer occupied,
                                              Instant updated, boolean open24Hours) {
        return new SeoulParking.LotStatus(code, code, capacity, occupied, updated, null, open24Hours);
    }
}
