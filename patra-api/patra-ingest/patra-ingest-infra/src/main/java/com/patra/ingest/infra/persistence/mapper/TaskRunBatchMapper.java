package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for task run batches (TaskRunBatch).
 *
 * <p>Purpose: group a set of TaskRun records into a logical batch for unified retry,
 * reconciliation, and monitoring statistics. Only provides basic CRUD; batch updates or
 * aggregations should live in the repository implementation or a dedicated query component.
 */
@Mapper
public interface TaskRunBatchMapper extends BaseMapper<TaskRunBatchDO> {}
