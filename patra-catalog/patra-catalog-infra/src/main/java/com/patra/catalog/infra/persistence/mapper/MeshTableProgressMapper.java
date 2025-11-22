package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshTableProgressDO;
import org.apache.ibatis.annotations.Mapper;

/// MeSH 表进度记录 Mapper 接口。
///
/// 继承 MyBatis-Plus 的 {@link BaseMapper}，获得基础 CRUD 能力。
///
/// **功能**：
///
/// - 基础 CRUD：继承自 {@link BaseMapper}
///
/// **查询规范**：
///
/// - 简单查询：直接在 Repository 中使用 LambdaQueryWrapper
///   - 复杂查询：在 XML Mapper 文件中定义 SQL（如 JOIN、子查询等）
///
/// @author linqibin
/// @since 0.1.0
@Mapper
public interface MeshTableProgressMapper extends BaseMapper<MeshTableProgressDO> {}
