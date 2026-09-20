package org.example.workbenchserver.vo;

import java.math.BigDecimal;

/**
 * 按分类汇总的一项，对应 docs/接口清单.md §9 的
 * {@code GET /api/expense/summary/category}，给前端画饼图。
 *
 * <p><b>只给"有记录的分类"</b>，不像月度趋势那样把六个分类都补零。
 * 两者看起来不一致，是因为用途不同：饼图的图例列的是扇区，
 * 补上一堆 0 元的扇区只会把图例撑长；而折线图的横轴是按时间排的坐标，
 * 中间缺一个月不能断开，必须补零（见 {@code MonthSummaryVO}）。
 *
 * @param category 分类名
 * @param amount   该分类的合计金额。**由数据库的 SUM 算出**，不是前端累加的
 */
public record CategorySummaryVO(String category, BigDecimal amount) {
}
