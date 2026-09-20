package org.example.workbenchserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.result.PageResult;
import org.example.workbenchserver.common.result.ResultCode;
import org.example.workbenchserver.dto.MemoDTO;
import org.example.workbenchserver.entity.Memo;
import org.example.workbenchserver.mapper.MemoMapper;
import org.example.workbenchserver.service.MemoService;
import org.example.workbenchserver.vo.MemoDetailVO;
import org.example.workbenchserver.vo.MemoVO;
import org.springframework.stereotype.Service;

/**
 * 备忘录业务实现。
 *
 * <p><b>注意全类没有任何一处 {@code user_id} 条件</b>，这是刻意的：
 * 隔离由 {@code MybatisPlusConfig} 的租户插件在 SQL 生成阶段注入。
 * 手写反而会掩盖拦截器失效的问题。
 */
@Service
public class MemoServiceImpl implements MemoService {

	/** 不传 pageSize 时的每页条数，与接口清单 §1.3 的默认值一致 */
	private static final int DEFAULT_PAGE_SIZE = 10;

	/** 每页上限。与 {@code PaginationInnerInterceptor} 的 maxLimit 保持一致 */
	private static final int MAX_PAGE_SIZE = 100;

	/** 关键词最大长度。详见 {@link #normalizeKeyword(String)} */
	private static final int MAX_KEYWORD_LENGTH = 50;

	private final MemoMapper memoMapper;

	public MemoServiceImpl(MemoMapper memoMapper) {
		this.memoMapper = memoMapper;
	}

	@Override
	public PageResult<MemoVO> page(int pageNum, int pageSize, String keyword) {
		// 分页参数从 URL 来，取值范围完全由客户端决定，必须先夹到合法范围。
		//
		// 这里刻意**不依赖** MyBatis-Plus 对越界值的容错：它对 size < 0 的处理是
		// "不再改写 SQL"（等于不翻页，整表一次捞出来），而不是"取 0 条" ——
		// 这类"库替你兜底"的行为会随版本变，而且方向恰好是危险的那一边。
		// 夹过之后，出参里回显的 pageNum / pageSize 才是可以信的。
		int safePageNum = Math.max(pageNum, 1);
		int safePageSize = pageSize <= 0
				? DEFAULT_PAGE_SIZE
				: Math.min(pageSize, MAX_PAGE_SIZE);

		LambdaQueryWrapper<Memo> wrapper = new LambdaQueryWrapper<>();

		String kw = normalizeKeyword(keyword);
		if (!kw.isEmpty()) {
			// 关于这里的 and(...)：它**不是**在防一个现成的漏洞。
			//
			// 本来担心的是 SQL 里 AND 优先级高于 OR —— 拦截器补的 user_id 是接在
			// 整个 WHERE 后面的，若上面的 `title LIKE ? OR content LIKE ?` 摊平到顶层，
			// 就拼成 `title LIKE ? OR (content LIKE ? AND user_id = ?)`，
			// 别人的备忘录会被搜出来。**实测这个担心不成立**：MyBatis-Plus 的
			// NormalSegmentList.childrenSqlSegment() 无条件把整段条件包进括号，
			// 3.5.1 起就是这样，生成的 SQL 是
			// `... AND (title LIKE ? OR content LIKE ?) AND user_id = ?`。
			//
			// 保留它是因为这个括号是**实现细节**：没有出现在任何文档或契约里，
			// 而且真正起作用的是"整段被包住"这个巧合 —— 一旦这段条件将来被嵌进
			// 别的 or(...) 里，靠的就是自己这层括号了。写上不改变语义（多一层
			// 括号而已），却让"两个字段是同一个搜索条件"这件事在代码里看得见。
			//
			// 别据此以为数据隔离靠的是它 —— 隔离靠拦截器，见 MemoIsolationTest。
			wrapper.and(w -> w
					.like(Memo::getTitle, kw)
					.or()
					.like(Memo::getContent, kw));
		}

		// 最近改过的排前面。updateTime 只精确到秒，同一秒内新建的几条顺序会不稳，
		// 再按 id 兜一层，翻页时才不会出现同一条一会儿在第二页一会儿在第三页
		wrapper.orderByDesc(Memo::getUpdateTime).orderByDesc(Memo::getId);

		Page<Memo> result = memoMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);

		return PageResult.of(
				result.getTotal(),
				result.getCurrent(),
				result.getSize(),
				result.getRecords().stream().map(MemoVO::from).toList());
	}

	@Override
	public MemoDetailVO detail(Long id) {
		return MemoDetailVO.from(requireOwned(id));
	}

	@Override
	public MemoDetailVO create(MemoDTO dto) {
		Memo memo = new Memo();
		// 刻意不设 user_id —— 实体上根本没有这个属性，见 Memo 的类注释
		apply(memo, dto);

		memoMapper.insert(memo);

		// 回读一次再返回。create_time / update_time 是**数据库**的
		// DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP 填的，
		// MyBatis-Plus 插完不会把生成的值带回实体 —— 直接返回 memo 的话，
		// 响应里这两个字段是 null，而 VO 明明声明了它们，
		// 客户端有理由把 null 理解成"这条没有时间"。
		//
		// 写路径上多一条主键查询，为这点不值得省：接口返回一份自相矛盾的数据，
		// 排查起来花的时间远超这一次 SELECT。
		return MemoDetailVO.from(requireOwned(memo.getId()));
	}

	@Override
	public MemoDetailVO update(Long id, MemoDTO dto) {
		Memo memo = requireOwned(id);
		apply(memo, dto);

		memoMapper.updateById(memo);
		// 同理：update_time 由 MySQL 的 ON UPDATE 维护，实体里那份还是上一次的。
		// 提交完立刻看到的时间戳若是旧的，用户会以为没保存上
		return MemoDetailVO.from(requireOwned(id));
	}

	@Override
	public long count() {
		// 传 null 表示"没有额外条件"，所以这条 SQL 的 WHERE 完全来自拦截器：
		// user_id = ?（隔离）与 deleted = 0（@TableLogic 的逻辑删除）。
		//
		// 这是本仓库又一条**代码里看不见谓词**的查询，和 AnniversaryServiceImpl#upcoming
		// 属于同一类风险：拦截器一旦失效，这里立刻变成全表 COUNT，
		// 首页上出现的是"别人有多少条备忘"这种离谱数字。
		// 因此 DashboardServiceTest 专门钉了它 —— 不是钉这个数字对不对，
		// 而是钉它确实只数自己的。
		Long total = memoMapper.selectCount(null);
		// COUNT 永远返回一行，理论上不会是 null。但 selectCount 的返回类型是
		// 包装类型 Long，真为 null 时下面自动拆箱会抛 NPE ——
		// 与其让首页崩在一个没人看得懂的 NPE 上，不如当成 0 条
		return total != null ? total : 0L;
	}

	@Override
	public void delete(Long id) {
		// 先确认这条是本人的且存在，为的是能准确返回 404。
		// deleteById 本身也会被拦截器补上 user_id 条件，删不到别人的数据 ——
		// 这一步是为了区分"删了"和"本来就没有"，不是为了安全。
		requireOwned(id);
		memoMapper.deleteById(id);
	}

	/**
	 * 把入参归一化后写进实体。
	 *
	 * <p>两件事：两侧去空格；空值一律落成**空串而不是 null**。
	 *
	 * <p>后半件不能省。MyBatis-Plus 默认的字段更新策略是 {@code NOT_NULL}，
	 * {@code updateById} 会把 null 字段**整条跳过**。PUT 的语义是全量替换，
	 * 若用户清空正文时传的是 null，那条 SET 子句会消失，旧正文原封不动留在库里 ——
	 * 界面上看着已清空，刷新一下又回来了。这与每日计划"取消勾选后完成时间还在"
	 * 是同一类坑（见 {@code AnniversaryServiceImpl.apply}）。
	 */
	private void apply(Memo memo, MemoDTO dto) {
		String title = dto.title() != null ? dto.title().trim() : "";
		String content = dto.content() != null ? dto.content().trim() : "";

		// 交叉字段校验，Bean Validation 的注解表达不了，只能写在这里。
		// 标题和正文都空的一条备忘，在列表里就是一行没有任何信息的空白
		if (title.isEmpty() && content.isEmpty()) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "标题和正文不能都为空");
		}

		memo.setTitle(title);
		memo.setContent(content);
	}

	/**
	 * 关键词归一化：去空格、挡住超长的。
	 *
	 * <p>截断不是为了正确性 —— 关键词再长也只是个查不到结果的 {@code LIKE} ——
	 * 而是因为它是从 URL 进来的、长度完全由客户端决定，不设上限就等于允许
	 * 用一条几十 KB 的查询参数逼数据库做全表扫描。
	 */
	private String normalizeKeyword(String keyword) {
		if (keyword == null) {
			return "";
		}
		String trimmed = keyword.trim();
		return trimmed.length() > MAX_KEYWORD_LENGTH
				? trimmed.substring(0, MAX_KEYWORD_LENGTH)
				: trimmed;
	}

	/**
	 * 取出属于当前用户的记录，不存在则 404。
	 *
	 * <p>{@code selectById} 会被拦截器补上 {@code user_id} 条件，拿别人的 id
	 * 必然返回 null。
	 *
	 * <p><b>返回 404 而不是 403</b>：403 等于确认"这个 id 确实存在，只是不归你"，
	 * 主键连续自增，据此能摸出全库的记录规模。理由与每日计划、生日纪念日相同。
	 */
	private Memo requireOwned(Long id) {
		if (id == null) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "id 不能为空");
		}
		Memo memo = memoMapper.selectById(id);
		if (memo == null) {
			throw new BusinessException(ResultCode.NOT_FOUND, "备忘录不存在");
		}
		return memo;
	}

}
