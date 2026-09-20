package org.example.workbenchserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.workbenchserver.entity.Memo;

/**
 * 备忘录 Mapper。
 *
 * <p>没有自定义方法：搜索用的 {@code LIKE} 由 {@code LambdaQueryWrapper}
 * 拼得出来，用不着写 XML。真出现复杂 SQL 时再建 XML（CLAUDE.md 的分层约定）。
 */
@Mapper
public interface MemoMapper extends BaseMapper<Memo> {

}
