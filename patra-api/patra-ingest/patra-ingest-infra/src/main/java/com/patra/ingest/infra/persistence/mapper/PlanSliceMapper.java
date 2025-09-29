package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计划切片（PlanSlice）数据表 Mapper。
 * <p>
 * 说明：
 * <ul>
 *   <li>继承 {@link BaseMapper}，具备基础 CRUD 能力；</li>
 *   <li>后续如需增加按时间窗口 / 状态过滤等自定义查询，请在此接口添加方法并补充 Javadoc；</li>
 *   <li>保持“只做数据访问”原则，不侵入领域逻辑；复杂聚合操作交由仓储层协调。</li>
 * </ul>
 * </p>
 */
@Mapper
public interface PlanSliceMapper extends BaseMapper<PlanSliceDO> {
	// 占位：扩展自定义 SQL 时请补充方法级注释。
}
