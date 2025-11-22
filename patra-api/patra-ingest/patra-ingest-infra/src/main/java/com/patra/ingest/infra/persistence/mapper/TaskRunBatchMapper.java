package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import org.apache.ibatis.annotations.Mapper;

/// 任务执行批次 Mapper 接口 — 对任务执行批次表的数据访问操作。
/// 
/// 目的: 将一组TaskRun记录分组为逻辑批次,用于统一重试、对账和监控统计。仅提供基本CRUD;批量更新或聚合应在仓储实现或专用查询组件中完成。
/// 
/// @author linqibin
/// @since 0.1.0
@Mapper
public interface TaskRunBatchMapper extends BaseMapper<TaskRunBatchDO> {}
