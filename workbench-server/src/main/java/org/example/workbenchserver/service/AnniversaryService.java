package org.example.workbenchserver.service;

import org.example.workbenchserver.dto.AnniversaryDTO;
import org.example.workbenchserver.vo.AnniversaryVO;
import org.example.workbenchserver.vo.UpcomingAnniversaryVO;

import java.util.List;

/**
 * 生日与纪念日业务接口，对应 docs/接口清单.md §5。
 */
public interface AnniversaryService {

	/** 当前用户的全部记录，按日历顺序（先月后日）排 */
	List<AnniversaryVO> list();

	/**
	 * 即将到来的记录，供首页提醒用。
	 *
	 * <p>只返回距下一次不超过该条自身 {@code remindDays} 的记录，按剩余天数升序。
	 */
	List<UpcomingAnniversaryVO> upcoming();

	AnniversaryVO create(AnniversaryDTO dto);

	AnniversaryVO update(Long id, AnniversaryDTO dto);

	void delete(Long id);

}
