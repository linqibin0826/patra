package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.infra.persistence.converter.TaskConverter;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import com.patra.ingest.infra.persistence.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 MyBatis-Plus 的任务仓储实现。
 *
 * <p>职责：
 * <ul>
 *   <li>任务聚合（TaskAggregate）的持久化与回读。</li>
 *   <li>按计划 ID 查询任务集合（用于切片回放 / 统计）。</li>
 *   <li>统计排队状态的任务数量（队列背压判断输入来源）。</li>
 * </ul>
 * 日志策略：
 * <ul>
 *   <li>DEBUG：插入 / 更新操作（含 id / planId）。</li>
 *   <li>INFO：不打印高频查询日志，减少 I/O。</li>
 * </ul>
 * </p>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRepositoryMpImpl implements TaskRepository {

    /** 任务 Mapper */
    private final TaskMapper mapper;
    /** 任务转换器 */
    private final TaskConverter converter;

    /**
     * 保存任务（insert 或 update）。
     * <p>回写自增 ID 与版本字段；DEBUG 打印操作类型。</p>
     */
    @Override
    public TaskAggregate save(TaskAggregate task) {
        TaskDO entity = converter.toEntity(task);
        if (entity.getId() == null) {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] task insert planId={} idemKey={}", entity.getPlanId(), entity.getIdempotentKey());
            }
            mapper.insert(entity);
            task.assignId(entity.getId());
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] task update id={} planId={} status={}", entity.getId(), entity.getPlanId(), entity.getStatusCode());
            }
            mapper.updateById(entity);
        }
        Long version = entity.getVersion();
        task.assignVersion(version == null ? task.getVersion() : version);
        return task;
    }

    /**
     * 批量保存任务（顺序调用 {@link #save(TaskAggregate)}，确保版本/ID 写回一致性）。
     * @param tasks 任务列表
     * @return 持久化后任务集合
     */
    @Override
    public List<TaskAggregate> saveAll(List<TaskAggregate> tasks) {
        List<TaskAggregate> persisted = new ArrayList<>(tasks.size());
        for (TaskAggregate task : tasks) {
            persisted.add(save(task));
        }
        return persisted;
    }

    /**
     * 根据计划 ID 查询任务集合。
     * @param planId 计划 ID
     * @return 任务聚合列表（无则空列表）
     */
    @Override
    public List<TaskAggregate> findByPlanId(Long planId) {
        if (planId == null) {
            return List.of();
        }
        List<TaskDO> entities = mapper.selectList(new QueryWrapper<TaskDO>().eq("plan_id", planId));
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<TaskAggregate> aggregates = new ArrayList<>(entities.size());
        for (TaskDO entity : entities) {
            aggregates.add(converter.toAggregate(entity));
        }
        return aggregates;
    }

    /**
     * 统计处于 QUEUED 状态的任务数量（可选按来源 / 操作过滤）。
     * @param provenanceCode 来源代码，可空
     * @param operationCode 操作代码，可空
     * @return 数量
     */
    @Override
    public long countQueuedTasks(String provenanceCode, String operationCode) {
        QueryWrapper<TaskDO> wrapper = new QueryWrapper<>();
        wrapper.eq("status_code", "QUEUED");
        if (provenanceCode != null) {
            wrapper.eq("provenance_code", provenanceCode);
        }
        if (operationCode != null) {
            wrapper.eq("operation_code", operationCode);
        }
        return mapper.selectCount(wrapper);
    }
}
