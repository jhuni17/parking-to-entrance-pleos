package kr.juchakkaji.app;

import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.RouteInfo;

import static kr.juchakkaji.app.ParkingPlan.*;

final class SincheonParking {
    static final String CODE = "173646";
    static final String FACILITY = "신천유수지 공영주차장";
    static final String ADDRESS = "서울특별시 송파구 오금로 1";
    private static final String POI_NAME = "신천유수지공영주차장";
    private static final String POI_ID = "36khp46sed75cm5nqhe3";
    private static final Entrance ENTRANCE = new Entrance("오금로 1 · 차량 진입 안내점",
            37.52190833, 127.10321944);
    static final Lot LOT = new Lot(FACILITY, "공영 · 노외", false, 3000, EvEntry.ALLOWED,
            new Tariff(5, 440, 0, ""), ENTRANCE,
            "서울시설공단 시설 안내 (2026-11-10 확인)\n"
                    + "https://www.sisul.or.kr/open_content/parking/bbs/bbsMsgDetail.do?bcd=parking&msg_seq=43\n"
                    + "높이 제한 3.0m · 전기차 입차 가능\n"
                    + "전기차 충전 이력 증빙 시 1시간 무료, 이후 50% 감면\n"
                    + "승용차 5부제 해제 (2026-07-01) · 서울시설공단 공지 8791\n"
                    + "오금로 1 차량 안내점은 Pleos 순정 내비의 주차장 POI에서 확인 (2026-11-10)");

    static RouteInfo route(Entrance entrance) {
        if (!ENTRANCE.equals(entrance)) return null;
        return new RouteInfo(ENTRANCE.latitude(), ENTRANCE.longitude(), null, null,
                POI_NAME, POI_ID, ADDRESS, "0", RouteOption.RECOMMENDED, "", false);
    }
}
