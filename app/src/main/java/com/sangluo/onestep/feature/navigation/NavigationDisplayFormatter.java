package com.sangluo.onestep.feature.navigation;

import com.sangluo.onestep.R;

import java.util.Locale;

/** Maps navigation protocol values to user-facing resources and text. */
public final class NavigationDisplayFormatter {
    private NavigationDisplayFormatter() {
    }

    public static int getNavigationManeuverIconRes(int maneuverId) {
        switch (maneuverId) {
            case 2: return R.drawable.nav_maneuver_type_2;
            case 3: return R.drawable.nav_maneuver_type_3;
            case 4: return R.drawable.nav_maneuver_type_4;
            case 5: return R.drawable.nav_maneuver_type_5;
            case 6: return R.drawable.nav_maneuver_type_6;
            case 7: return R.drawable.nav_maneuver_type_7;
            case 8: return R.drawable.nav_maneuver_type_8;
            case 9: return R.drawable.nav_maneuver_type_9;
            case 10: return R.drawable.nav_maneuver_type_10;
            case 11: return R.drawable.nav_maneuver_type_11;
            case 12: return R.drawable.nav_maneuver_type_12;
            case 13: return R.drawable.nav_maneuver_type_13;
            case 14: return R.drawable.nav_maneuver_type_14;
            case 15: return R.drawable.nav_maneuver_type_15;
            case 16: return R.drawable.nav_maneuver_type_16;
            case 17: return R.drawable.nav_maneuver_type_17;
            case 18: return R.drawable.nav_maneuver_type_18;
            case 19: return R.drawable.nav_maneuver_type_19;
            case 20: return R.drawable.nav_maneuver_type_20;
            case 21: return R.drawable.nav_maneuver_type_21;
            case 22: return R.drawable.nav_maneuver_type_22;
            case 23: return R.drawable.nav_maneuver_type_23;
            case 24: return R.drawable.nav_maneuver_type_24;
            case 25: return R.drawable.nav_maneuver_type_25;
            case 26: return R.drawable.nav_maneuver_type_26;
            case 27: return R.drawable.nav_maneuver_type_27;
            case 28: return R.drawable.nav_maneuver_type_28;
            case 29: return R.drawable.nav_maneuver_type_29;
            case 30: return R.drawable.nav_maneuver_type_30;
            case 31: return R.drawable.nav_maneuver_type_31;
            case 32: return R.drawable.nav_maneuver_type_32;
            case 33: return R.drawable.nav_maneuver_type_33;
            case 34: return R.drawable.nav_maneuver_type_34;
            case 35: return R.drawable.nav_maneuver_type_35;
            case 36: return R.drawable.nav_maneuver_type_36;
            case 37: return R.drawable.nav_maneuver_type_37;
            case 38: return R.drawable.nav_maneuver_type_38;
            case 39: return R.drawable.nav_maneuver_type_39;
            case 40: return R.drawable.nav_maneuver_type_40;
            case 41: return R.drawable.nav_maneuver_type_41;
            case 42: return R.drawable.nav_maneuver_type_42;
            case 43: return R.drawable.nav_maneuver_type_43;
            case 44: return R.drawable.nav_maneuver_type_44;
            case 45: return R.drawable.nav_maneuver_type_45;
            case 46: return R.drawable.nav_maneuver_type_46;
            case 47: return R.drawable.nav_maneuver_type_47;
            case 48: return R.drawable.nav_maneuver_type_48;
            case 49: return R.drawable.nav_maneuver_type_49;
            case 50: return R.drawable.nav_maneuver_type_50;
            case 51: return R.drawable.nav_maneuver_type_51;
            case 52: return R.drawable.nav_maneuver_type_52;
            case 53: return R.drawable.nav_maneuver_type_53;
            case 54: return R.drawable.nav_maneuver_type_54;
            case 65: return R.drawable.nav_maneuver_type_65;
            case 66: return R.drawable.nav_maneuver_type_66;
            default: return R.drawable.nav_maneuver_type_9;
        }
    }

    public static String getNavigationManeuverDescription(int maneuverId) {
        switch (maneuverId) {
            case 2: return "左转";
            case 3: return "右转";
            case 4: return "向左前方行驶";
            case 5: return "向右前方行驶";
            case 6: return "向左后方行驶";
            case 7: return "向右后方行驶";
            case 8: return "左转掉头";
            case 10: return "到达途经点";
            case 11: return "进入环岛";
            case 12: return "驶出环岛";
            case 13: return "到达服务区";
            case 14: return "到达收费站";
            case 15: return "到达目的地";
            case 16: return "到达隧道";
            case 17: return "进入左侧环岛";
            case 18: return "离开左侧环岛";
            case 19: return "右转掉头";
            case 20: return "继续行驶";
            case 21: return "环岛左转";
            case 22: return "环岛右转";
            case 23: return "环岛直行";
            case 24: return "环岛掉头";
            case 25: return "左侧环岛左转";
            case 26: return "左侧环岛右转";
            case 27: return "左侧环岛直行";
            case 28: return "左侧环岛掉头";
            case 29: return "人行横道";
            case 30: return "天桥";
            case 31: return "地下通道";
            case 32: return "广场";
            case 33: return "公园";
            case 34: return "楼梯";
            case 35: return "电梯";
            case 36: return "缆车";
            case 37: return "空中通道";
            case 38: return "通道";
            case 39: return "步行道路";
            case 40: return "游轮路线";
            case 41: return "观光巴士路线";
            case 42: return "滑道";
            case 43: return "梯子";
            case 44: return "斜坡";
            case 45: return "桥";
            case 46: return "渡轮";
            case 47: return "地铁";
            case 48: return "进入建筑";
            case 49: return "离开建筑";
            case 50: return "乘电梯";
            case 51: return "走楼梯";
            case 52: return "乘扶梯";
            case 53: return "经过红绿灯";
            case 54: return "通过路口";
            case 65: return "向左并线";
            case 66: return "向右并线";
            case 9: return "直行";
            default: return "继续行驶";
        }
    }

    public static String formatNavigationDistance(int meters) {
        int distance = Math.max(0, meters);
        if (distance < 1000) {
            return distance + " 米";
        }
        if (distance < 10_000) {
            return String.format(Locale.CHINA, "%.1f 公里", distance / 1000f);
        }
        return Math.round(distance / 1000f) + " 公里";
    }

    public static String formatNavigationRoads(String currentRoad, String nextRoad) {
        String current = normalized(currentRoad);
        String next = normalized(nextRoad);
        if (current.isEmpty()) {
            return next.isEmpty() ? "未知道路" : next;
        }
        if (next.isEmpty() || current.equals(next)) {
            return current;
        }
        return current + " → " + next;
    }

    public static String formatNavigationRemainTime(int seconds) {
        int totalMinutes = Math.max(0, (seconds + 59) / 60);
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return hours + "时" + minutes + "分";
        }
        if (hours > 0) {
            return hours + "小时";
        }
        return Math.max(1, minutes) + "分钟";
    }

    public static String formatNavigationRemainingSummary(int seconds, int meters) {
        return formatNavigationRemainTime(seconds) + " · "
                + formatNavigationCompactDistance(meters);
    }

    public static String formatNavigationCompactDistance(int meters) {
        int distance = Math.max(0, meters);
        if (distance < 1000) {
            return distance + "m";
        }
        if (distance < 100_000) {
            return String.format(Locale.CHINA, "%.1fkm", distance / 1000f);
        }
        return Math.round(distance / 1000f) + "km";
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
