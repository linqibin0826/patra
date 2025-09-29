package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务运行批次（TaskRunBatch）表 Mapper。
 * <p>
 * 设计用途：将一组 TaskRun 聚合到逻辑批次，用于统一重试 / 对账与监控统计。
 * 仅提供基础 CRUD；批量更新 / 聚合查询放在仓储实现或专用 Query 组件中完成。
 * </p>
 */
@Mapper
public interface TaskRunBatchMapper extends BaseMapper<TaskRunBatchDO> {
}
