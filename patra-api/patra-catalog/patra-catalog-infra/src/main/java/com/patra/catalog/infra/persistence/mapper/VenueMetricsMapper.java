package com.patra.catalog.infra.persistence.mapper;

import com.patra.catalog.infra.persistence.entity.VenueMetricsDO;
import com.patra.starter.mybatis.mapper.PatraBaseMapper;

/// 载体年度指标 Mapper 接口 — 对载体指标表的数据访问操作。
///
/// 继承 `PatraBaseMapper` 以获得批量插入能力（`insertBatchSomeColumn`）。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueMetricsMapper extends PatraBaseMapper<VenueMetricsDO> {}
