package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计划切片 Mapper 接口 — 对计划切片表的数据访问操作。
 *
 * <p>说明:
 *
 * <ul>
 *   <li>继承 {@link BaseMapper} 并提供基本 CRUD
 *   <li>根据需要添加自定义查询(如按窗口/状态),并提供完整 Javadoc
 *   <li>仅数据访问;勿嵌入领域逻辑。让仓储层协调复杂操作
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface PlanSliceMapper extends BaseMapper<PlanSliceDO> {
  // 占位符: 需要自定义 SQL 时添加方法及方法级文档
}
