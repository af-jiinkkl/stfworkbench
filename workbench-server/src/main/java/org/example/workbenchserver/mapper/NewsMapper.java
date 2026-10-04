package org.example.workbenchserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.workbenchserver.entity.News;

/**
 * 新闻缓存 Mapper。
 *
 * <p>没有自定义方法：取当天、按发布时间倒序、限几条，用
 * {@code LambdaQueryWrapper} 都拼得出来（CLAUDE.md 的分层约定：
 * 真出现复杂 SQL 时再建 XML）。
 *
 * <p><b>注意本 Mapper 不需要任何隔离条件，也不该有。</b>
 * 它是全项目唯一一张全局共享的表 —— 八个 Mapper 里只有这一个，
 * 查出来的东西所有用户看到的是同一份，这是需求明确要的
 * （见 docs/需求说明.md §3.5），不是漏了条件。
 * 也正因为如此，{@code DataIsolationTest} 那套探针用例套不到它头上。
 */
@Mapper
public interface NewsMapper extends BaseMapper<News> {

}
