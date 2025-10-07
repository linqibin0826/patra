package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.port.TaskRunRepository;
import com.patra.ingest.infra.persistence.converter.TaskRunConverter;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import com.patra.ingest.infra.persistence.mapper.TaskRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 任务运行尝试（TaskRun Attempt）仓储实现。
 * <p>职责：
 * <ul>
 *   <li>插入 / 更新 TaskRun（含统计 / 检查点字段 JSON）。</li>
 *   <li>查询最新一次尝试（按 attempt_no 降序取 1）。</li>
 *   <li>查询所有尝试（审计 / 排障）。</li>
 *   <li>获取最新 attemptNo（用于生成下一次尝试编号）。</li>
 * </ul>
 * </p>
 * <p>设计：
 * <ul>
 *   <li>ID 为空：insert；否则 update。</li>
 *   <li>保存后再次 selectById，确保获取数据库回填字段（如乐观锁 version 若后续添加）。</li>
 * </ul>
 * </p>
 * <p>日志策略：DEBUG 输出 insert / update 关键字段；不打印查询日志。</p>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRunRepositoryMpImpl implements TaskRunRepository {

    private final TaskRunMapper mapper;
    private final TaskRunConverter converter;

    /**
     * 保存运行记录。
     * @param run 运行聚合
     * @return 持久化后的最新聚合
     */
    @Override
    public TaskRun save(TaskRun run) {
        TaskRunDO dto = converter.toDO(run);
        if (dto.getId() == null) {
            mapper.insert(dto);
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] task run insert taskId={} attemptNo={}", dto.getTaskId(), dto.getAttemptNo());
            }
        } else {
            mapper.updateById(dto);
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] task run update id={} attemptNo={} status={}", dto.getId(), dto.getAttemptNo(), dto.getStatusCode());
            }
        }
        // 返回数据库最新状态重新映射（包含生成的 ID）
        TaskRunDO persisted = mapper.selectById(dto.getId());
        return converter.toDomain(persisted);
    }

    /**
     * 查询最新一次任务运行尝试。
     * @param taskId 任务 ID
     * @return Optional（为空表示尚未运行）
     */
    @Override
    public Optional<TaskRun> findLatest(Long taskId) {
        TaskRunDO one = mapper.selectOne(new QueryWrapper<TaskRunDO>()
            .eq("task_id", taskId)
            .orderByDesc("attempt_no")
            .last("limit 1"));
        return Optional.ofNullable(one).map(converter::toDomain);
    }

    /**
     * 查询任务全部运行尝试（按 attemptNo 升序）。
     * @param taskId 任务 ID
     * @return 列表（可能为空）
     */
    @Override
    public List<TaskRun> findAll(Long taskId) {
        return mapper.selectList(new QueryWrapper<TaskRunDO>()
            .eq("task_id", taskId)
            .orderByAsc("attempt_no"))
                .stream().map(converter::toDomain).collect(Collectors.toList());
    }

    /**
     * 获取任务的最新 attemptNo（用于生成下一次尝试编号）。
     * @param taskId 任务 ID
     * @return 最大的 attemptNo，若无运行记录则返回 0
     */
    @Override
    public int getLatestAttemptNo(Long taskId) {
        return mapper.selectLatestAttemptNo(taskId);
    }

    @Override
    public Optional<TaskRun> findById(Long runId) {
        if (runId == null) {
            return Optional.empty();
        }
        TaskRunDO entity = mapper.selectById(runId);
        return Optional.ofNullable(entity).map(converter::toDomain);
    }

    @Override
    public boolean updateCheckpointAndHeartbeat(Long runId, String checkpointJson, Instant now) {
        int updated = mapper.updateCheckpointAndHeartbeat(runId, checkpointJson, now);
        if (updated == 0) {
            log.warn("[INGEST][INFRA] task run checkpoint update missed runId={}", runId);
        }
        return updated > 0;
    }

    @Override
    public boolean touchHeartbeat(Long runId, Instant now) {
        int updated = mapper.touchHeartbeat(runId, now);
        if (updated == 0) {
            log.warn("[INGEST][INFRA] task run heartbeat touch missed runId={}", runId);
        }
        return updated > 0;
    }

    @Override
    public boolean markFailed(Long runId, String errorMessage, Instant now) {
        int updated = mapper.markFailed(runId, errorMessage, now);
        if (updated == 0) {
            log.warn("[INGEST][INFRA] task run markFailed missed runId={}", runId);
        }
        return updated > 0;
    }

    @Override
    public boolean hasSucceededRun(Long taskId) {
        if (taskId == null) {
            return false;
        }
        Long count = mapper.selectCount(new QueryWrapper<TaskRunDO>()
            .eq("task_id", taskId)
            .eq("status_code", "SUCCEEDED"));
        return count != null && count > 0;
    }
}
