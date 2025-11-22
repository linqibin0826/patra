package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import java.util.Optional;

/// MeSH 导入任务仓储接口（Port）。
///
/// 定义聚合根的持久化操作，由 Infrastructure 层实现。
///
/// **设计原则**：
///
/// - 纯接口定义：不包含实现逻辑
///   - 面向领域对象：参数和返回值都是领域对象
///   - 六边形架构：领域层定义接口，基础设施层实现
///   - 命名规范：使用 Port 后缀表示端口
///
/// **实现说明**：
///
/// - Infrastructure 层实现此接口（MeshImportRepositoryImpl）
///   - 使用 MapStruct 进行 Domain 对象与 DO 对象转换
///   - 使用 MyBatis-Plus 进行数据库操作
///   - 支持乐观锁（version 字段）
///
/// @author linqibin
/// @since 0.1.0
public interface MeshImportPort {

  /// 保存导入任务（新增或更新）。
  ///
  /// 实现说明：
  ///
  /// - 如果 aggregate.getId() 为 null，则新增任务（分配雪花 ID）
  ///   - 如果 aggregate.getId() 不为 null，则更新任务（使用乐观锁）
  ///   - 同时保存或更新关联的表进度记录（cat_mesh_table_progress）
  ///
  /// @param aggregate 导入任务聚合根
  /// @return 保存后的聚合根（包含生成的 ID 和更新后的 version）
  MeshImportAggregate save(MeshImportAggregate aggregate);

  /// 根据 ID 查询导入任务。
  ///
  /// 实现说明：
  ///
  /// - 查询任务主表（cat_mesh_import_task）
  ///   - 关联查询表进度记录（cat_mesh_table_progress）
  ///   - 使用 MapStruct 转换为领域对象
  ///
  /// @param importId 任务 ID
  /// @return 导入任务聚合根，如果不存在则返回 Optional.empty()
  Optional<MeshImportAggregate> findById(MeshImportId importId);

  /// 查询当前正在运行的任务。
  ///
  /// 实现说明：
  ///
  /// - 查询条件：status = 'PROCESSING'
  ///   - 关联查询表进度记录
  ///   - 如果有多个正在运行的任务，返回最早开始的任务
  ///
  /// @return 正在运行的任务，如果不存在则返回 Optional.empty()
  Optional<MeshImportAggregate> findRunningTask();

  /// 判断是否存在正在运行的任务。
  ///
  /// 实现说明：
  ///
  /// - 查询条件：status = 'PROCESSING'
  ///   - 使用 COUNT 查询优化性能
  ///
  /// @return true 如果存在正在运行的任务
  boolean existsRunningTask();
}
