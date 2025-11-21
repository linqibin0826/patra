package com.patra.catalog.infra.persistence.repository;

import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.port.MeshImportPort;
import com.patra.catalog.infra.persistence.converter.MeshImportConverter;
import com.patra.catalog.infra.persistence.entity.MeshImportTaskDO;
import com.patra.catalog.infra.persistence.entity.MeshTableProgressDO;
import com.patra.catalog.infra.persistence.mapper.MeshImportTaskMapper;
import com.patra.catalog.infra.persistence.mapper.MeshTableProgressMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * MeSH 导入任务仓储实现。
 *
 * <p><b>职责</b>：
 *
 * <ul>
 *   <li>管理 MeSH 导入任务聚合根的持久化
 *   <li>协调任务主表和表进度的存储
 *   <li>使用 MapStruct 进行 Domain 对象与 DO 对象转换
 *   <li>保证 DO 对象不泄露到 Infrastructure 层外
 * </ul>
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>六边形架构：实现 Domain 层定义的 {@link MeshImportPort} 接口
 *   <li>依赖注入：使用构造器注入 Mapper 和 Converter
 *   <li>事务管理：由 Application 层管理事务边界
 *   <li>乐观锁：使用 MyBatis-Plus 的 version 字段防止并发修改
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MeshImportRepositoryImpl implements MeshImportPort {

  private final MeshImportTaskMapper taskMapper;
  private final MeshTableProgressMapper progressMapper;
  private final MeshImportConverter converter;

  @Override
  public MeshImportAggregate save(MeshImportAggregate aggregate) {
    log.debug("保存 MeSH 导入任务: {}", aggregate);

    // 1. 转换聚合根为 TaskDO
    MeshImportTaskDO taskDO = converter.toTaskDO(aggregate);

    // 2. 保存或更新任务主表
    if (taskDO.getId() == null) {
      // 新增任务：MyBatis-Plus 会自动分配雪花 ID
      taskMapper.insert(taskDO);
      log.info("新增 MeSH 导入任务成功，任务ID: {}", taskDO.getId());
    } else {
      // 更新任务：使用乐观锁
      int updated = taskMapper.updateById(taskDO);
      if (updated == 0) {
        log.warn("更新 MeSH 导入任务失败（乐观锁冲突），任务ID: {}", taskDO.getId());
        throw new IllegalStateException("任务更新失败，可能已被其他进程修改");
      }
      log.info("更新 MeSH 导入任务成功，任务ID: {}", taskDO.getId());
    }

    // 3. 删除旧的表进度记录（如果存在）
    if (taskDO.getId() != null) {
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MeshTableProgressDO> wrapper =
          new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
      wrapper.eq(MeshTableProgressDO::getImportId, taskDO.getId());
      progressMapper.delete(wrapper);
      log.debug("删除旧的表进度记录，任务ID: {}", taskDO.getId());
    }

    // 4. 保存新的表进度记录
    List<MeshTableProgressDO> progressDOList = converter.toProgressDOList(aggregate);
    for (MeshTableProgressDO progressDO : progressDOList) {
      progressDO.setImportId(taskDO.getId()); // 设置外键
      progressMapper.insert(progressDO);
    }
    log.debug("保存表进度记录成功，记录数: {}", progressDOList.size());

    // 5. 重新查询并返回（获取最新的 version 和审计字段）
    return findById(MeshImportId.of(taskDO.getId()))
        .orElseThrow(() -> new IllegalStateException("保存后查询任务失败"));
  }

  @Override
  public Optional<MeshImportAggregate> findById(MeshImportId importId) {
    log.debug("根据ID查询 MeSH 导入任务: {}", importId);

    // 1. 查询任务主表
    MeshImportTaskDO taskDO = taskMapper.selectById(importId.value());
    if (taskDO == null) {
      log.debug("任务不存在，任务ID: {}", importId);
      return Optional.empty();
    }

    // 2. 查询表进度记录
    List<MeshTableProgressDO> progressDOList = progressMapper.findByImportId(importId.value());
    log.debug("查询到表进度记录数: {}", progressDOList.size());

    // 3. 转换为聚合根
    MeshImportAggregate aggregate = converter.toDomain(taskDO, progressDOList);

    return Optional.of(aggregate);
  }

  @Override
  public Optional<MeshImportAggregate> findRunningTask() {
    log.debug("查询当前正在运行的任务");

    // 1. 查询任务主表
    Optional<MeshImportTaskDO> taskDOOpt = taskMapper.findRunningTask();
    if (taskDOOpt.isEmpty()) {
      log.debug("不存在正在运行的任务");
      return Optional.empty();
    }

    MeshImportTaskDO taskDO = taskDOOpt.get();
    log.info("查询到正在运行的任务，任务ID: {}", taskDO.getId());

    // 2. 查询表进度记录
    List<MeshTableProgressDO> progressDOList = progressMapper.findByImportId(taskDO.getId());

    // 3. 转换为聚合根
    MeshImportAggregate aggregate = converter.toDomain(taskDO, progressDOList);

    return Optional.of(aggregate);
  }

  @Override
  public boolean existsRunningTask() {
    log.debug("判断是否存在正在运行的任务");

    // 使用 COUNT 查询优化性能
    int count = taskMapper.countRunningTasks();
    boolean exists = count > 0;

    log.debug("正在运行的任务数: {}", count);
    return exists;
  }
}
