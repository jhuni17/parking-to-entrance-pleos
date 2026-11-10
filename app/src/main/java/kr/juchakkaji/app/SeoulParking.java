package kr.juchakkaji.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 서울 열린데이터광장 OA-21709, GetParkingInfo 공개 샘플. */
final class SeoulParking {
    private static final String ENDPOINT = "http://openapi.seoul.go.kr:8088/sample/json/GetParkingInfo/1/5/";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter INPUT_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(SEOUL);
    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    enum Area {
        COEX("강남구", null, "1366600", 0),
        JAMSIL("송파구", "1277145", SincheonParking.CODE, 900);

        final String district;
        final String destinationCode;
        final String nearbyCode;
        final int distanceMeters;

        Area(String district, String destinationCode, String nearbyCode, int distanceMeters) {
            this.district = district;
            this.destinationCode = destinationCode;
            this.nearbyCode = nearbyCode;
            this.distanceMeters = distanceMeters;
        }
    }

    record Rate(int baseMinutes, int baseWon, Integer extraMinutes, Integer extraWon) {
        String label() {
            if (extraMinutes == null || extraWon == null || extraMinutes <= 0
                    || (extraMinutes == baseMinutes && extraWon == baseWon)) {
                return baseMinutes + "분 " + money(baseWon);
            }
            return "기본 " + baseMinutes + "분 " + money(baseWon)
                    + " · 추가 " + extraMinutes + "분 " + money(extraWon);
        }

        private static String money(int won) {
            return String.format(Locale.KOREA, "%,d원", won);
        }
    }

    record LotStatus(String code, String name, Integer capacity, Integer occupied, Instant updated,
                     Rate rate, boolean open24Hours) {
        Integer available(Instant now) {
            if (capacity == null || capacity <= 0 || occupied == null || occupied < 0
                    || occupied > capacity || updated == null
                    || !now.isBefore(updated.plus(MAX_AGE)) || updated.isAfter(now.plusSeconds(30))) {
                return null;
            }
            return capacity - occupied;
        }

        Instant vacancyExpiresAt(Instant now) {
            return available(now) == null ? null : updated.plus(MAX_AGE);
        }

        boolean crowded(Instant now) {
            Integer spaces = available(now);
            return spaces != null && spaces * 20L <= capacity;
        }

        String updatedLabel() {
            return updated == null ? "" : DISPLAY_TIME.format(updated);
        }
    }

    record Comparison(LotStatus destination, LotStatus nearby, boolean destinationCrowded,
                      boolean recommendNearby) {}

    static Area areaFor(String name, double latitude, double longitude, boolean coex) {
        if (coex) return Area.COEX;
        String normalized = name.replace(" ", "");
        if ((normalized.equals("잠실역") || normalized.equals("잠실역2호선")
                || normalized.equals("잠실역8호선") || normalized.equals("잠실역공영주차장")
                || normalized.equals("잠실역환승공영주차장"))
                && Math.abs(latitude - 37.5143) < 0.005 && Math.abs(longitude - 127.1034) < 0.005) {
            return Area.JAMSIL;
        }
        return null;
    }

    static Comparison compare(Area area, List<LotStatus> lots, Instant now) {
        LotStatus destination = null;
        LotStatus nearby = null;
        for (LotStatus lot : lots) {
            if (lot.code().equals(area.destinationCode)) destination = lot;
            if (lot.code().equals(area.nearbyCode)) nearby = lot;
        }
        // The Jamsil Station garage operates daily from 05:00 through 01:00 the next day.
        int hour = now.atZone(SEOUL).getHour();
        boolean crowded = destination != null && (hour >= 5 || hour < 1) && destination.crowded(now);
        boolean recommend = crowded
                && nearby != null && nearby.open24Hours()
                && nearby.available(now) != null && !nearby.crowded(now);
        return new Comparison(destination, nearby, crowded, recommend);
    }

    static List<LotStatus> fetch(Area area) throws IOException, JSONException {
        URL url = new URL(ENDPOINT + URLEncoder.encode(area.district, StandardCharsets.UTF_8.name()));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        try (InputStream stream = connection.getInputStream()) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                if (body.size() + length > 64 * 1024) throw new IOException("Unexpected parking response size");
                body.write(buffer, 0, length);
            }
            return parse(area, new JSONObject(body.toString(StandardCharsets.UTF_8.name())));
        } finally {
            connection.disconnect();
        }
    }

    private static List<LotStatus> parse(Area area, JSONObject root) throws JSONException {
        JSONObject feed = root.getJSONObject("GetParkingInfo");
        if (!"INFO-000".equals(feed.getJSONObject("RESULT").optString("CODE"))) {
            throw new JSONException("Parking feed not available");
        }
        JSONArray rows = feed.getJSONArray("row");
        List<LotStatus> lots = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String code = row.optString("PKLT_CD");
            if (!code.equals(area.destinationCode) && !code.equals(area.nearbyCode)) continue;
            String name = row.optString("PKLT_NM").replace("공영주차장(시)", "공영주차장");
            Integer minutes = integer(row, "BSC_PRK_HR");
            Integer won = integer(row, "BSC_PRK_CRG");
            Rate rate = minutes == null || minutes <= 0 || won == null ? null
                    : new Rate(minutes, won, integer(row, "ADD_PRK_HR"), integer(row, "ADD_PRK_CRG"));
            boolean connected = "1".equals(row.optString("PRK_STTS_YN"));
            boolean open24Hours = allDay(row, "WD") && allDay(row, "WE") && allDay(row, "LHLDY");
            if (!name.isBlank()) {
                lots.add(new LotStatus(code, name, integer(row, "TPKCT"),
                        connected ? integer(row, "NOW_PRK_VHCL_CNT") : null,
                        connected ? timestamp(row.optString("NOW_PRK_VHCL_UPDT_TM")) : null,
                        rate, open24Hours));
            }
        }
        return List.copyOf(lots);
    }

    private static boolean allDay(JSONObject row, String day) {
        return "0000".equals(row.optString(day + "_OPER_BGNG_TM"))
                && "2400".equals(row.optString(day + "_OPER_END_TM"));
    }

    private static Integer integer(JSONObject row, String field) {
        double value = row.optDouble(field, Double.NaN);
        return value >= 0 && value <= Integer.MAX_VALUE && value == Math.floor(value) ? (int) value : null;
    }

    private static Instant timestamp(String text) {
        try {
            return LocalDateTime.parse(text, INPUT_TIME).atZone(SEOUL).toInstant();
        } catch (DateTimeException ignored) {
            return null;
        }
    }
}
