package org.example.workbenchserver.isolation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 探针 Mapper，由 {@link IsolationProbeConfig} 单独注册。
 */
public interface IsolationProbeMapper extends BaseMapper<IsolationProbe> {

}
