package org.example.workbenchserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.workbenchserver.entity.PlanTask;

/**
 * 每日计划任务的数据访问。
 *
 * <p>整个接口没有一个自定义方法 —— 单表 CRUD 由 {@code BaseMapper} 提供就够了。
 * 也**不该**在这里手写 {@code user_id} 条件：隔离由拦截器统一注入
 * （docs/数据模型.md §2.2），手写反而会掩盖拦截器失效的问题。
 *
 * <p>本接口无需 {@code @Mapper} 注解，{@code MybatisPlusConfig} 上的
 * {@code @MapperScan} 已经扫描了本包。
 */
public interface PlanTaskMapper extends BaseMapper<PlanTask> {

}
