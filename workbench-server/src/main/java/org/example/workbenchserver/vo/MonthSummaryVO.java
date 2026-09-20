package org.example.workbenchserver.vo;

import java.math.BigDecimal;

/**
 * 按月汇总的一项，对应 docs/接口清单.md §9 的
 * {@code GET /api/expense/summary/month}，给前端画趋势图。
 *
 * <p><b>恒为 12 项，没记录的月份补 {@code 0.00}，且顺序固定为一月到十二月。</b>
 * 这是本接口唯一超出"数据库 GROUP BY 出来什么就返回什么"的地方，值得说清：
 * {@code GROUP BY} 只会返回**有记录**的月份，于是"只有 1 月和 5 月有消费"会返回
 * 两项。前端拿它画折线图，横轴就变成"1 月、5 月"两个点紧紧挨着 ——
 * 图上看着是"连续两个月都有花销"，实际中间空了三个月。
 * 补零把"时间轴"这件事重新交还给数据本身，前端就不必自己造轴。
 *
 * <p>补零还有个副作用是对的：某个月消费全被删了，它会**回到 0.00 而不是消失**，
 * 趋势图上表现为掉到谷底。这正是用户期望看到的。
 *
 * @param month  月份，格式 {@code yyyy-MM}
 * @param amount 该月合计金额。无记录时为 {@code 0.00}
 */
public record MonthSummaryVO(String month, BigDecimal amount) {
}
