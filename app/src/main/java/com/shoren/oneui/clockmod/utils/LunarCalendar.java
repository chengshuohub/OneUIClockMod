package com.shoren.oneui.clockmod.utils;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * High-precision standalone Chinese Lunar Calendar (农历) & 24 Solar Terms conversion engine.
 * Covers years 1900 through 2100 without any external dependencies.
 */
public class LunarCalendar {

    // Bit-packed Lunar data table (1900 - 2100)
    // Bits 0-3: Leap month (0 if no leap month)
    // Bit 4: Leap month size (0 = 29 days, 1 = 30 days)
    // Bits 5-16: Normal months 1-12 days (0 = 29 days, 1 = 30 days)
    private static final long[] LUNAR_INFO = new long[]{
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06aa0, 0x1a6c4, 0x0aae0,
        0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
        0x0d520
    };

    private static final String[] HEAVENLY_STEMS = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] EARTHLY_BRANCHES = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] ANIMALS = {"鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"};

    private static final String[] LUNAR_MONTHS = {
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    };

    private static final String[] LUNAR_DAYS = {
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };

    private static final String[] SOLAR_TERMS = {
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
        "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
        "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    };

    // Solar term info coefficients for solar longitude calculation
    private static final int[] SOLAR_TERM_INFO = {
        0, 21208, 42467, 63836, 85337, 107014,
        128867, 150921, 173149, 195551, 218072, 240693,
        263343, 285989, 308563, 331033, 353350, 375494,
        397447, 419210, 440795, 462224, 483532, 504758
    };

    // Result structure
    public static class LunarResult {
        public int lunarYear;
        public int lunarMonth;
        public int lunarDay;
        public boolean isLeap;
        public String ganZhiYear;
        public String animal;
        public String monthStr;
        public String dayStr;
        public String solarTerm;
        public String festival;

        public String getFullDisplay(boolean showYear, boolean showSolarTerm) {
            StringBuilder sb = new StringBuilder();
            if (showYear) {
                sb.append(ganZhiYear).append("年(").append(animal).append(")");
            }
            if (showSolarTerm && solarTerm != null && !solarTerm.isEmpty()) {
                sb.append(solarTerm).append(" ");
            } else if (festival != null && !festival.isEmpty()) {
                sb.append(festival).append(" ");
            }
            if (isLeap) {
                sb.append("闰");
            }
            sb.append(monthStr).append(dayStr);
            return sb.toString().trim();
        }

        public String getSimpleDisplay() {
            StringBuilder sb = new StringBuilder();
            if (solarTerm != null && !solarTerm.isEmpty()) {
                sb.append(solarTerm).append("·");
            }
            if (isLeap) {
                sb.append("闰");
            }
            sb.append(monthStr).append(dayStr);
            return sb.toString();
        }
    }

    /**
     * Converts a Gregorian Date to Lunar Calendar details.
     */
    public static LunarResult fromDate(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return fromCalendar(cal);
    }

    public static LunarResult fromCalendar(Calendar cal) {
        int gYear = cal.get(Calendar.YEAR);
        int gMonth = cal.get(Calendar.MONTH) + 1;
        int gDay = cal.get(Calendar.DAY_OF_MONTH);

        Calendar baseCal = Calendar.getInstance();
        baseCal.set(1900, 0, 31, 0, 0, 0); // 1900-01-31 is Lunar 1900-01-01
        baseCal.set(Calendar.MILLISECOND, 0);

        Calendar targetCal = (Calendar) cal.clone();
        targetCal.set(Calendar.HOUR_OF_DAY, 0);
        targetCal.set(Calendar.MINUTE, 0);
        targetCal.set(Calendar.SECOND, 0);
        targetCal.set(Calendar.MILLISECOND, 0);

        long offset = (targetCal.getTimeInMillis() - baseCal.getTimeInMillis()) / 86400000L;

        int lYear = 1900;
        int daysInYear = 0;
        for (int i = 1900; i <= 2100 && offset > 0; i++) {
            daysInYear = getLunarYearDays(i);
            offset -= daysInYear;
            lYear = i;
        }

        if (offset < 0) {
            offset += daysInYear;
        } else {
            lYear++;
        }

        int leapMonth = getLeapMonth(lYear);
        boolean isLeap = false;
        int lMonth = 1;
        int daysInMonth = 0;

        for (int m = 1; m <= 12 && offset >= 0; m++) {
            // Check leap month
            if (leapMonth > 0 && m == (leapMonth + 1) && !isLeap) {
                --m;
                isLeap = true;
                daysInMonth = getLeapMonthDays(lYear);
            } else {
                daysInMonth = getLunarMonthDays(lYear, m);
            }

            if (isLeap && m == (leapMonth + 1)) {
                isLeap = false;
            }

            offset -= daysInMonth;
            if (offset <= 0) {
                lMonth = m;
                break;
            }
        }

        offset += daysInMonth;
        int lDay = (int) offset + 1;

        LunarResult result = new LunarResult();
        result.lunarYear = lYear;
        result.lunarMonth = lMonth;
        result.lunarDay = lDay;
        result.isLeap = isLeap;

        // GanZhi and ShengXiao
        int ganZhiIdx = (lYear - 4) % 60;
        if (ganZhiIdx < 0) ganZhiIdx += 60;
        result.ganZhiYear = HEAVENLY_STEMS[ganZhiIdx % 10] + EARTHLY_BRANCHES[ganZhiIdx % 12];
        result.animal = ANIMALS[(lYear - 4) % 12];

        // Month and Day Strings
        result.monthStr = (lMonth >= 1 && lMonth <= 12) ? LUNAR_MONTHS[lMonth - 1] : (lMonth + "月");
        result.dayStr = (lDay >= 1 && lDay <= 30) ? LUNAR_DAYS[lDay - 1] : (lDay + "日");

        // Solar Terms
        result.solarTerm = getSolarTerm(gYear, gMonth, gDay);

        // Festival Calculation
        result.festival = getFestival(lMonth, lDay, isLeap, gMonth, gDay);

        return result;
    }

    private static int getLunarYearDays(int y) {
        if (y < 1900 || y > 2100) return 365;
        int sum = 348;
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            sum += ((LUNAR_INFO[y - 1900] & i) != 0) ? 1 : 0;
        }
        return sum + getLeapMonthDays(y);
    }

    private static int getLeapMonth(int y) {
        if (y < 1900 || y > 2100) return 0;
        return (int) (LUNAR_INFO[y - 1900] & 0xf);
    }

    private static int getLeapMonthDays(int y) {
        if (getLeapMonth(y) != 0) {
            return ((LUNAR_INFO[y - 1900] & 0x10000) != 0) ? 30 : 29;
        }
        return 0;
    }

    private static int getLunarMonthDays(int y, int m) {
        if (y < 1900 || y > 2100 || m < 1 || m > 12) return 30;
        return ((LUNAR_INFO[y - 1900] & (0x10000 >> m)) != 0) ? 30 : 29;
    }

    /**
     * Calculates 24 Solar Terms for the given Gregorian date.
     */
    public static String getSolarTerm(int year, int month, int day) {
        int termIdx1 = (month - 1) * 2;
        int termIdx2 = termIdx1 + 1;

        int day1 = getSolarTermDay(year, termIdx1);
        int day2 = getSolarTermDay(year, termIdx2);

        if (day == day1) {
            return SOLAR_TERMS[termIdx1];
        } else if (day == day2) {
            return SOLAR_TERMS[termIdx2];
        }
        return null;
    }

    private static int getSolarTermDay(int year, int n) {
        double offDate = (365.242 * (year - 1900) + SOLAR_TERM_INFO[n] / 1000.0) / 1440.0;
        Calendar base = Calendar.getInstance();
        base.set(1900, 0, 6, 2, 5, 0); // 1900-01-06 02:05:00 Xiaohan
        long time = base.getTimeInMillis() + (long) (offDate * 86400000L);
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(time);
        return target.get(Calendar.DAY_OF_MONTH);
    }

    private static String getFestival(int lMonth, int lDay, boolean isLeap, int gMonth, int gDay) {
        if (!isLeap) {
            if (lMonth == 1 && lDay == 1) return "春节";
            if (lMonth == 1 && lDay == 15) return "元宵";
            if (lMonth == 5 && lDay == 5) return "端午";
            if (lMonth == 7 && lDay == 7) return "七夕";
            if (lMonth == 7 && lDay == 15) return "中元";
            if (lMonth == 8 && lDay == 15) return "中秋";
            if (lMonth == 9 && lDay == 9) return "重阳";
            if (lMonth == 12 && lDay == 8) return "腊八";
            if (lMonth == 12 && (lDay == 29 || lDay == 30)) return "除夕";
        }
        // Gregorian statutory festivals
        if (gMonth == 1 && gDay == 1) return "元旦";
        if (gMonth == 5 && gDay == 1) return "五一";
        if (gMonth == 10 && gDay == 1) return "国庆";
        return null;
    }
}
