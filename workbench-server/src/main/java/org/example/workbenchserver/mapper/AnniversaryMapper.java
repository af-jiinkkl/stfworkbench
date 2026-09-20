package org.example.workbenchserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.workbenchserver.entity.Anniversary;

/**
 * 生日与纪念日的数据库操作。
 *
 * <p>只继承 {@code BaseMapper}，没有自定义方法 —— 本模块的查询条件都很简单，
 * 唯一有算法的是"下一次是哪天"，那部分刻意放在 Java 里做而不是写进 SQL
 * （docs/数据模型.md §4.3 说明过原因）。
 *
 * <p>所有方法的 SQL 都会被租户插件补上 {@code user_id} 条件，这里不写。
 */
public interface AnniversaryMapper extends BaseMapper<Anniversary> {

}
