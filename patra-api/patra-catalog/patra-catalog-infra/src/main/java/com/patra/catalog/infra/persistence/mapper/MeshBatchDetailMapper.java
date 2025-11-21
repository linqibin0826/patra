package com.patra.catalog.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.catalog.infra.persistence.entity.MeshBatchDetailDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MeSH 批次详情 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，获得基础 CRUD 能力。
 *
 * <p><b>功能</b>：
 *
 * <ul>
 *   <li>基础 CRUD：继承自 {@link BaseMapper}
 * </ul>
 *
 * <p><b>查询规范</b>：
 *
 * <ul>
 *   <li>简单查询：直接在 Repository 中使用 LambdaQueryWrapper
 *   <li>复杂查询：在 XML Mapper 文件中定义 SQL（如 JOIN、子查询等）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Mapper
public interface MeshBatchDetailMapper extends BaseMapper<MeshBatchDetailDO> {}
