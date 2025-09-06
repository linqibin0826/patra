package com.patra.registry.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.SourceApiParamMappingDO;

/**
 * 数据源API参数映射 Mapper
 * docref: 红线规范 - Mapper仅extends BaseMapper<DO>，严禁自定义方法
 */
public interface SourceApiParamMappingMapper extends BaseMapper<SourceApiParamMappingDO> {
    
    // 严禁添加自定义方法，所有操作通过 MyBatis-Plus 提供的标准方法实现
}
