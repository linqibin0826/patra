package com.patra.ingest.app.usecase.plan;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 计划持久化协调器。
///
/// 职责：
///
/// - 安全地持久化计划聚合根、切片、任务和调度实例
///   - 提供适当的异常处理和日志记录
///
/// 注意：该协调器不使用 `@Transactional`，依赖主编排器的外部事务边界来确保与事件发布的原子性。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPersistenceCoordinator {

  private final PlanRepository planRepository;
  private final PlanSliceRepository planSliceRepository;
  private final TaskRepository taskRepository;
  private final ScheduleInstanceRepository scheduleInstanceRepository;

  /// 保存或更新调度实例（幂等）。
  ///
  /// @param request 调度请求
  /// @return 已持久化的调度实例
  /// @throws PlanPersistenceException 存储失败时
  public ScheduleInstanceAggregate persistScheduleInstance(PlanIngestionCommand request) {
    ProvenanceCode provenanceCode = request.provenanceCode();
    ScheduleInstanceAggregate schedule =
        ScheduleInstanceAggregate.start(
            request.scheduler(),
            request.schedulerJobId(),
            request.schedulerLogId(),
            request.triggerType(),
            request.triggeredAt(),
            request.triggerParams(),
            provenanceCode);
    try {
      return scheduleInstanceRepository.saveOrUpdateInstance(schedule);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.SCHEDULE_INSTANCE, "持久化调度实例失败", ex);
    }
  }

  /// 持久化计划聚合根并包装底层异常。
  ///
  /// @param draftPlan 草稿计划聚合根
  /// @return 已持久化的计划聚合根
  /// @throws PlanPersistenceException 持久化失败时
  public PlanAggregate savePlan(PlanAggregate draftPlan) {
    try {
      return planRepository.save(draftPlan);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(PlanPersistenceException.Stage.PLAN, "持久化计划聚合根失败", ex);
    }
  }

  /// 批量持久化计划切片聚合根。
  ///
  /// @param plan 计划聚合根（已持久化）
  /// @param slices 切片集合
  /// @return 已持久化的切片集合
  /// @throws PlanPersistenceException 持久化失败时
  public List<PlanSliceAggregate> persistSlices(
      PlanAggregate plan, List<PlanSliceAggregate> slices) {
    if (CollUtil.isEmpty(slices)) {
      return List.of();
    }
    slices.forEach(slice -> slice.bindPlan(plan.getId()));
    try {
      return planSliceRepository.saveAll(slices);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.PLAN_SLICE, "持久化计划切片失败", ex);
    }
  }

  /// 批量持久化任务聚合根并绑定计划和切片 ID。
  ///
  /// 任务与切片的匹配策略：从任务的 paramsJson 中解析 sliceNo，然后根据 sliceNo 找到对应的切片。
  /// 这是因为任务创建时 sliceId 为 null（真正的 sliceId 在切片持久化后才知道），但 paramsJson 中
  /// 已经存储了 sliceNo 用于匹配。
  ///
  /// @param plan 计划聚合根
  /// @param persistedSlices 已持久化的切片
  /// @param tasks 任务集合
  /// @return 已持久化的任务集合
  /// @throws PlanPersistenceException 持久化失败时
  public List<TaskAggregate> persistTasks(
      PlanAggregate plan, List<PlanSliceAggregate> persistedSlices, List<TaskAggregate> tasks) {
    if (CollUtil.isEmpty(tasks)) {
      return List.of();
    }
    Map<Integer, PlanSliceAggregate> sliceBySeq = MapUtil.newHashMap(persistedSlices.size());
    for (PlanSliceAggregate slice : persistedSlices) {
      sliceBySeq.putIfAbsent(slice.getSliceNo(), slice);
    }
    for (TaskAggregate task : tasks) {
      Integer sliceNo = extractSliceNoFromParams(task.getParamsJson());
      PlanSliceAggregate slice = sliceNo != null ? sliceBySeq.get(sliceNo) : null;
      task.bindPlanAndSlice(plan.getId(), slice != null ? slice.getId() : null);
    }
    try {
      return taskRepository.saveAll(tasks);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(PlanPersistenceException.Stage.TASK, "持久化任务失败", ex);
    }
  }

  /// 从任务参数 JSON 中提取 sliceNo。
  ///
  /// paramsJson 格式为规范化的 JSON，如 `{"sliceNo":1}`。
  ///
  /// @param paramsJson 任务参数 JSON 字符串
  /// @return sliceNo 值，如果解析失败或不存在则返回 null
  private Integer extractSliceNoFromParams(String paramsJson) {
    if (paramsJson == null || paramsJson.isBlank()) {
      return null;
    }
    // 使用正则从规范化 JSON 中提取 sliceNo 值
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("\"sliceNo\":(\\d+)").matcher(paramsJson);
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
  }

  /// 持久化任务重试状态。
  ///
  /// @param task 任务聚合根
  /// @throws PlanPersistenceException 持久化失败时
  public void saveTask(TaskAggregate task) {
    try {
      taskRepository.save(task);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.TASK_RETRY, "持久化任务重试状态失败", ex);
    }
  }
}
