package org.example.workbenchserver.service;

import org.example.workbenchserver.vo.DashboardVO;

/**
 * 首页聚合，对应 docs/接口清单.md §7。
 *
 * <p>只有这一个方法：它不持有自己的数据，存在的意义是把另外三个模块的结果
 * 拼成一次响应。所以实现里**只调其他 Service，不直接碰 Mapper** ——
 * 直接查表意味着"今天算哪天"、"哪些算即将到来"这些口径出现了第二份实现，
 * 两份迟早会分叉。
 */
public interface DashboardService {

	/** 当前用户的首页数据。一眼都不含别人的。 */
	DashboardVO overview();

}
