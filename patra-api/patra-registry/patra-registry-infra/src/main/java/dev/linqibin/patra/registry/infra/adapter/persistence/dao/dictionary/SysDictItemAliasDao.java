package dev.linqibin.patra.registry.infra.adapter.persistence.dao.dictionary;

import dev.linqibin.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictItemAliasEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 系统字典项别名 JPA Repository。
///
/// **职责**：
///
/// - 提供 SysDictItemAliasEntity 的 CRUD 操作
/// - 通过来源标准和外部代码查询别名
///
/// @author linqibin
/// @since 0.1.0
public interface SysDictItemAliasDao extends JpaRepository<SysDictItemAliasEntity, Long> {

  /// 通过来源标准和外部代码查询别名。
  ///
  /// @param sourceStandard 来源标准标识符
  /// @param externalCode 外部代码
  /// @return 可选的别名实体
  @Query(
      """
      SELECT a FROM SysDictItemAliasEntity a
      WHERE a.sourceStandard = :sourceStandard
        AND a.externalCode = :externalCode
      """)
  Optional<SysDictItemAliasEntity> findBySourceStandardAndExternalCode(
      @Param("sourceStandard") String sourceStandard, @Param("externalCode") String externalCode);

  /// 批量查询外部别名。
  ///
  /// @param sourceStandard 来源标准标识符
  /// @param externalCodes 外部代码集合
  /// @return 别名实体列表
  @Query(
      """
      SELECT a FROM SysDictItemAliasEntity a
      WHERE a.sourceStandard = :sourceStandard
        AND a.externalCode IN :externalCodes
      """)
  List<SysDictItemAliasEntity> findBySourceStandardAndExternalCodeIn(
      @Param("sourceStandard") String sourceStandard,
      @Param("externalCodes") Iterable<String> externalCodes);

  /// 通过字典项 ID 查询所有别名。
  ///
  /// @param itemId 字典项 ID
  /// @return 别名列表
  @Query(
      """
      SELECT a FROM SysDictItemAliasEntity a
      WHERE a.itemId = :itemId
      ORDER BY a.sourceStandard, a.externalCode
      """)
  List<SysDictItemAliasEntity> findByItemId(@Param("itemId") Long itemId);

  /// 按来源标准和字典项 ID 集合批量查询别名（反向查询：已知 item → 获取标签）。
  ///
  /// @param sourceStandard 来源标准标识符
  /// @param itemIds 字典项 ID 集合
  /// @return 别名实体列表
  @Query(
      """
      SELECT a FROM SysDictItemAliasEntity a
      WHERE a.sourceStandard = :sourceStandard AND a.itemId IN :itemIds
      """)
  List<SysDictItemAliasEntity> findBySourceStandardAndItemIdIn(
      @Param("sourceStandard") String sourceStandard, @Param("itemIds") Collection<Long> itemIds);
}
