package com.patra.catalog.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.port.MeshImportPort;
import com.patra.catalog.infra.persistence.converter.MeshImportConverter;
import com.patra.catalog.infra.persistence.entity.MeshImportTaskDO;
import com.patra.catalog.infra.persistence.entity.MeshTableProgressDO;
import com.patra.catalog.infra.persistence.mapper.MeshImportTaskMapper;
import com.patra.catalog.infra.persistence.mapper.MeshTableProgressMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// MeSH 导入任务仓储实现。
///
/// **职责**：
///
/// - 管理 MeSH 导入任务聚合根的持久化
///   - 协调任务主表和表进度的存储
///   - 使用 MapStruct 进行 Domain 对象与 DO 对象转换
///   - 保证 DO 对象不泄露到 Infrastructure 层外
///
/// **设计原则**：
///
/// - 六边形架构：实现 Domain 层定义的 {@link MeshImportPort} 接口
///   - 依赖注入：使用构造器注入 Mapper 和 Converter
///   - 事务管理：由 Application 层管理事务边界
///   - 乐观锁：使用 MyBatis-Plus 的 version 字段防止并发修改
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class MeshImportRepositoryImpl implements MeshImportPort {

  private final MeshImportTaskMapper taskMapper;
  private final MeshTableProgressMapper progressMapper;
  private final MeshImportConverter converter;

  /// 保存导入任务（新增或更新）。
  ///
  /// **设计原则**：
  ///
  /// - Repository 模式：save() 表示持久化聚合根，而非 SQL 操作
  ///   - 调用者无需关心是新增还是更新，由 Repository 自动判断
  ///   - 符合 DDD "告诉，别问" 原则
  ///
  /// **实现逻辑**：
  ///
  /// - 如果 aggregate.getId() 为 null → INSERT（自动分配雪花 ID）
  ///   - 如果 aggregate.getId() 不为 null → UPDATE（使用乐观锁）
  ///   - 关联数据采用"删除+重新插入"策略（简化并发控制）
  ///
  /// **标准参考**：
  ///
  /// - Spring Data JPA: save() → upsert 语义
  ///   - Hibernate: save() / persist() → upsert 语义
  ///   - Evans DDD 书籍：Repository 应隐藏持久化细节
  ///
  /// @param aggregate 导入任务聚合根
  /// @return 保存后的聚合根（包含生成的 ID 和更新后的 version）
  /// @throws IllegalStateException 如果更新时发生乐观锁冲突
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

    // 3. UPSERT 表进度记录（更新或插入）
    List<MeshTableProgressDO> progressDOList = converter.toProgressDOList(aggregate);
    Set<String> currentTableNames = new HashSet<>();

    for (MeshTableProgressDO progressDO : progressDOList) {
      progressDO.setImportId(taskDO.getId()); // 设置外键
      currentTableNames.add(progressDO.getTableName());

      // 查询是否已存在该表的进度记录
      LambdaQueryWrapper<MeshTableProgressDO> queryWrapper = new LambdaQueryWrapper<>();
      queryWrapper
          .eq(MeshTableProgressDO::getImportId, taskDO.getId())
          .eq(MeshTableProgressDO::getTableName, progressDO.getTableName());
      MeshTableProgressDO existing = progressMapper.selectOne(queryWrapper);

      if (existing != null) {
        // 存在 → 更新（保留 ID 和 version 以支持乐观锁）
        progressDO.setId(existing.getId());
        progressDO.setVersion(existing.getVersion());
        int updated = progressMapper.updateById(progressDO);
        if (updated == 0) {
          log.warn("更新表进度失败（乐观锁冲突），表名: {}", progressDO.getTableName());
          throw new IllegalStateException("表进度更新失败，可能已被其他进程修改");
        }
        log.debug("更新表进度记录: {} (ID: {})", progressDO.getTableName(), progressDO.getId());
      } else {
        // 不存在 → 插入
        progressMapper.insert(progressDO);
        log.debug("新增表进度记录: {} (ID: {})", progressDO.getTableName(), progressDO.getId());
      }
    }

    // 4. 删除数据库中存在但聚合中不存在的表进度记录（逻辑删除）
    // 这种情况发生在：表列表从 [A, B, C] 变成 [A, B] 时，需要删除 C
    if (taskDO.getId() != null && !currentTableNames.isEmpty()) {
      LambdaQueryWrapper<MeshTableProgressDO> deleteWrapper = new LambdaQueryWrapper<>();
      deleteWrapper
          .eq(MeshTableProgressDO::getImportId, taskDO.getId())
          .notIn(MeshTableProgressDO::getTableName, currentTableNames);
      int deleted = progressMapper.delete(deleteWrapper);
      if (deleted > 0) {
        log.debug("逻辑删除多余的表进度记录，数量: {}", deleted);
      }
    }

    log.debug("UPSERT 表进度记录成功，处理数: {}", progressDOList.size());

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

    // 2. 查询表进度记录（使用 LambdaQueryWrapper）
    LambdaQueryWrapper<MeshTableProgressDO> progressWrapper = new LambdaQueryWrapper<>();
    progressWrapper
        .eq(MeshTableProgressDO::getImportId, importId.value())
        .orderByAsc(MeshTableProgressDO::getTableName);
    List<MeshTableProgressDO> progressDOList = progressMapper.selectList(progressWrapper);
    log.debug("查询到表进度记录数: {}", progressDOList.size());

    // 3. 转换为聚合根
    MeshImportAggregate aggregate = converter.toDomain(taskDO, progressDOList);

    return Optional.of(aggregate);
  }

  @Override
  public Optional<MeshImportAggregate> findRunningTask() {
    log.debug("查询当前正在运行的任务");

    // 1. 查询任务主表（使用 LambdaQueryWrapper）
    LambdaQueryWrapper<MeshImportTaskDO> taskWrapper = new LambdaQueryWrapper<>();
    taskWrapper
        .eq(MeshImportTaskDO::getStatus, "PROCESSING")
        .orderByAsc(MeshImportTaskDO::getStartTime)
        .last("LIMIT 1");
    MeshImportTaskDO taskDO = taskMapper.selectOne(taskWrapper);

    if (taskDO == null) {
      log.debug("不存在正在运行的任务");
      return Optional.empty();
    }

    log.info("查询到正在运行的任务，任务ID: {}", taskDO.getId());

    // 2. 查询表进度记录（使用 LambdaQueryWrapper）
    LambdaQueryWrapper<MeshTableProgressDO> progressWrapper = new LambdaQueryWrapper<>();
    progressWrapper
        .eq(MeshTableProgressDO::getImportId, taskDO.getId())
        .orderByAsc(MeshTableProgressDO::getTableName);
    List<MeshTableProgressDO> progressDOList = progressMapper.selectList(progressWrapper);

    // 3. 转换为聚合根
    MeshImportAggregate aggregate = converter.toDomain(taskDO, progressDOList);

    return Optional.of(aggregate);
  }

  @Override
  public boolean existsRunningTask() {
    log.debug("判断是否存在正在运行的任务");

    // 使用 COUNT 查询优化性能（使用 LambdaQueryWrapper）
    LambdaQueryWrapper<MeshImportTaskDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(MeshImportTaskDO::getStatus, "PROCESSING");
    Long count = taskMapper.selectCount(wrapper);
    boolean exists = count > 0;

    log.debug("正在运行的任务数: {}", count);
    return exists;
  }
}
