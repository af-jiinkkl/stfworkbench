package org.example.workbenchserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.workbenchserver.entity.User;

/**
 * 用户表 Mapper。
 *
 * <p>{@code wb_user} 已在 {@code MybatisPlusConfig} 中被排除出数据隔离拦截器 ——
 * 它没有 {@code user_id} 列。因此本 Mapper 的查询条件必须自己写全
 * （例如按 username 查，必须同时依赖 {@code deleted = 0} 由逻辑删除自动补上）。
 *
 * <p>没有标注 {@code @Mapper}：统一由启动类上的 {@code @MapperScan} 扫描注册，
 * 避免两个地方各配一份。
 */
public interface UserMapper extends BaseMapper<User> {

}
