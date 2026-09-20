package org.example.workbenchserver.common.util;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 业务时间的统一口径：**所有"今天"都从这里取**。
 *
 * <p>docs/需求说明.md §4 专门提过这个问题：服务器将来可能部署在境外
 * （香港、新加坡等），JVM 默认时区一变，"今天"就跟着变，用户会发现
 * 凌晨时分的记录跑到前一天去了。所以业务时区写死，不跟随 JVM 默认值。
 *
 * <p>抽成公共常量而不是各模块自己写一份，是因为"今天"必须全局一致：
 * 每日计划按东八区算、生日提醒按别的时区算的话，同一天里两个模块会
 * 对"今天"给出不同答案。这种分歧一旦出现极难排查 ——
 * 每个模块单独看都是对的。
 *
 * <p>取值与 application.yml 里 Jackson 的 {@code time-zone}、
 * 以及 JDBC 连接的 {@code connectionTimeZone} 保持一致。
 */
public final class WorkbenchTime {

	/** 业务时区。东八区，无夏令时，不用担心偏移跳变 */
	public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

	private WorkbenchTime() {
	}

	/** 业务意义上的"今天" */
	public static LocalDate today() {
		return LocalDate.now(ZONE);
	}

}
