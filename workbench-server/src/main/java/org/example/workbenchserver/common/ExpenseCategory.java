package org.example.workbenchserver.common;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消费分类的**预置**取值，见 docs/接口清单.md §9「预置分类（已确认）」。
 *
 * <p>第一版不做自定义分类，所以分类是一份写死的清单，而不是一张表。
 * 数据库里仍是 {@code VARCHAR(20)} 存名字（见 docs/数据模型.md §4.6），
 * 将来要放开自定义时，把这份清单换成一张分类表即可，表结构不用动。
 *
 * <p><b>值得单独成一个类型，是因为它有且只有一个权威来源。</b>
 * 若把六个字面量散在 DTO 的 {@code @Pattern}、Service 的校验、错误提示里，
 * 迟早会出现"校验用的是五个、提示里写着六个"这种不一致 ——
 * 而它的表现是某个合法分类被拒，或者某个非法分类被存进库，
 * 两种都要等到用户碰上才发现。
 *
 * <p><b>前端另有一份同样的清单</b>（{@code src/types/expense.ts} 的
 * {@code EXPENSE_CATEGORIES}）。这是刻意的重复：接口清单 §9 没有"取分类列表"
 * 这个端点，为一个几乎不变、只有六项、且要拿来做下拉选项的清单新增一个接口，
 * 不划算。代价是两处要一起改 —— 所以这里刻意**没有**加"取清单"的接口，
 * 也没有把清单写进任何响应里做隐式同步：不一致时至少能在提交时撞上一个 400，
 * 而不是两边各自安好地跑下去。
 */
public enum ExpenseCategory {

	/** 餐饮 */
	FOOD("餐饮"),

	/** 交通 */
	TRANSPORT("交通"),

	/** 购物 */
	SHOPPING("购物"),

	/** 娱乐 */
	ENTERTAINMENT("娱乐"),

	/** 医疗 */
	MEDICAL("医疗"),

	/** 其他 */
	OTHER("其他");

	/** 对外名字（存库、走 JSON 的都是这个，不是枚举常量名） */
	private final String label;

	ExpenseCategory(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/** 全部合法取值的集合。用 Set 是为了校验时 O(1)，清单只有六项，这里图的是语义清晰 */
	private static final Set<String> LABELS = Arrays.stream(values())
			.map(ExpenseCategory::getLabel)
			.collect(Collectors.toUnmodifiableSet());

	/**
	 * 是不是一个合法分类。
	 *
	 * <p>大小写与空格都**不做**宽容：分类名是中文，不存在大小写问题；
	 * 而首尾空格由调用方先 trim 掉再传进来（见 {@code ExpenseServiceImpl#normalizeCategory}），
	 * 容忍空格等于允许库里出现 "餐饮" 和 "餐饮 " 两条其实是同一个分类的记录，
	 * 之后按分类汇总就会裂成两行。
	 */
	public static boolean isValid(String label) {
		return label != null && LABELS.contains(label);
	}

	/**
	 * 合法取值的逗号连接，给错误提示用。
	 *
	 * <p>从枚举现推而不是再写一遍字面量 —— 提示里列出的必须是**当前**的合法值，
	 * 否则用户照着提示改还是被拒。
	 */
	public static String allowedValues() {
		List<String> labels = Arrays.stream(values()).map(ExpenseCategory::getLabel).toList();
		return String.join(" / ", labels);
	}

}
