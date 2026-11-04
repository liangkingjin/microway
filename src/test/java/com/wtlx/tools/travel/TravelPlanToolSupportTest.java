package com.wtlx.tools.travel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 工具公共支撑的纯函数测试。
 * <p>
 * 这几处逻辑的输入全部来自模型自由生成的文本（时间写法、金额精度、日期格式），
 * 一旦退化成"格式不对就整批失败"，上层工具会静默少写数据，所以单独锁住。
 *
 * @author 金小梁
 */
class TravelPlanToolSupportTest {

    private final TravelPlanToolSupport support = new TravelPlanToolSupport();

    @Test
    void parseTimeAcceptsCommonModelFormats() {
        assertEquals(LocalTime.of(9, 0), support.parseTime("9:00", "时间"));
        assertEquals(LocalTime.of(9, 0), support.parseTime("09:00", "时间"));
        assertEquals(LocalTime.of(9, 5), support.parseTime("9:5", "时间"));
        assertEquals(LocalTime.of(9, 0), support.parseTime("  09:00  ", "时间"));
        assertEquals(LocalTime.of(23, 45), support.parseTime("23:45", "时间"));
    }

    @Test
    void parseTimeRejectsGarbageWithReadableMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> support.parseTime("9点", "行程时间"));
        assertEquals(true, e.getMessage().contains("HH:mm"));
    }

    @Test
    void parseDateRejectsSlashSeparator() {
        assertEquals(LocalDate.of(2026, 5, 1), support.parseDate("2026-05-01", "出发日期"));
        assertThrows(IllegalArgumentException.class, () -> support.parseDate("2026/05/01", "出发日期"));
    }

    @Test
    void durationCountsBothEndsAsDays() {
        // 首尾都算天数，晚数 = 天数 - 1，与前端创建计划的口径一致
        assertEquals("5天4晚", support.durationOf(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5)));
        assertEquals("1天0晚", support.durationOf(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1)));
        assertEquals("2天1晚", support.durationOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 1)));
    }

    @Test
    void moneyKeepsTwoDecimalsAndRoundsHalfUp() {
        assertEquals("0.00", support.money(null));
        assertEquals("1500.00", support.money(new BigDecimal("1500")));
        assertEquals("2.50", support.money(new BigDecimal("2.5")));
        assertEquals("1.01", support.money(new BigDecimal("1.005")));
    }
}
