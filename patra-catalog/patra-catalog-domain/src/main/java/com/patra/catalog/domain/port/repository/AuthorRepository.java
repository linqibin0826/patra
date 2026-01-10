package com.patra.catalog.domain.port.repository;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.domain.model.vo.author.AuthorId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// 作者聚合根仓储接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 以聚合根为单位进行持久化，包括子实体（名字变体、ORCID）
/// - 支持按 normalizedKey 分组查询和按 ORCID 精确查询
///
/// **查询语义**：
///
/// - `findByNormalizedKey`：返回所有匹配的作者（可能多个）
/// - `findByOrcid`：返回唯一匹配的作者（ORCID 全局唯一）
///
/// @author linqibin
/// @since 0.1.0
public interface AuthorRepository {

  // ========== 基本 CRUD ==========

  /// 保存作者聚合根。
  ///
  /// 如果聚合根是新建的（isTransient() == true），则执行 INSERT。
  /// 如果聚合根已存在，则执行 UPDATE，并处理子实体的增删改。
  ///
  /// @param author 作者聚合根
  /// @return 保存后的聚合根（含 ID）
  AuthorAggregate save(AuthorAggregate author);

  /// 批量保存作者聚合根。
  ///
  /// @param authors 作者聚合根列表
  void saveBatch(List<AuthorAggregate> authors);

  /// 根据 ID 查询作者。
  ///
  /// @param id 作者 ID
  /// @return 作者聚合根（可选）
  Optional<AuthorAggregate> findById(AuthorId id);

  /// 根据 ID 删除作者（软删除）。
  ///
  /// @param id 作者 ID
  void deleteById(AuthorId id);

  // ========== 姓名规范化格式查询 ==========

  /// 根据姓名规范化格式查询作者。
  ///
  /// 同一格式下可能有多个不同的已消歧作者。
  ///
  /// @param normalizedKey 姓名规范化格式（如 "SMITH+R"）
  /// @return 匹配的作者列表（可能为空）
  List<AuthorAggregate> findByNormalizedKey(String normalizedKey);

  /// 检查姓名规范化格式下是否有作者。
  ///
  /// @param normalizedKey 姓名规范化格式
  /// @return true 如果有匹配的作者
  boolean existsByNormalizedKey(String normalizedKey);

  /// 批量根据姓名规范化格式查询作者。
  ///
  /// @param normalizedKeys 姓名规范化格式集合
  /// @return 作者聚合根列表
  List<AuthorAggregate> findByNormalizedKeys(List<String> normalizedKeys);

  // ========== ORCID 查询 ==========

  /// 根据 ORCID 查询作者。
  ///
  /// @param orcid ORCID 标识符
  /// @return 作者聚合根（可选）
  Optional<AuthorAggregate> findByOrcid(String orcid);

  /// 检查 ORCID 是否已存在。
  ///
  /// @param orcid ORCID 标识符
  /// @return true 如果已存在
  boolean existsByOrcid(String orcid);

  /// 批量查询已存在的 ORCID。
  ///
  /// 用于批量导入时的去重检查，一次查询替代 N 次单条查询。
  ///
  /// @param orcids 待检查的 ORCID 集合（不能为 null，可以为空）
  /// @return 数据库中已存在的 ORCID 集合（永不为 null）
  Set<String> findExistingOrcids(Collection<String> orcids);

  /// 批量查询通过任一 ORCID 匹配的作者，并返回 ORCID → 作者的映射。
  ///
  /// **使用场景**：
  ///
  /// 批量导入时检测跨批次 ORCID 重复，并获取已存在的作者以便合并名字变体。
  /// 每个匹配的作者可能有多个 ORCID，所有这些 ORCID 都会作为键指向同一个作者。
  ///
  /// **示例**：
  ///
  /// 数据库中有作者 A，ORCID = [X, Y]。
  /// 查询 ORCID 集合 = {X, Z}。
  /// 返回 {X → A, Y → A}（所有属于作者 A 的 ORCID 都作为键）。
  ///
  /// @param orcids 待检查的 ORCID 集合（不能为 null，可以为空）
  /// @return ORCID → 作者聚合根的映射（永不为 null，同一作者的所有 ORCID 都作为键）
  java.util.Map<String, AuthorAggregate> findAuthorsByAnyOrcid(Collection<String> orcids);

  // ========== 统计查询 ==========

  /// 检查表中是否有数据。
  ///
  /// @return true 如果有数据
  boolean hasAnyData();
}
