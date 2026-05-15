package dev.linqibin.patra.registry.infra.adapter.persistence.dao.dictionary;

import dev.linqibin.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictTypeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// 系统字典类型 JPA Repository。
///
/// **职责**：
///
/// - 提供 SysDictTypeEntity 的 CRUD 操作
/// - 通过类型代码查询字典类型
///
/// @author linqibin
/// @since 0.1.0
public interface SysDictTypeDao extends JpaRepository<SysDictTypeEntity, Long> {

  /// 通过类型代码查询字典类型。
  ///
  /// 由于实体继承自 SoftDeletableJpaEntity（使用 Hibernate @SoftDelete），
  /// 框架会自动添加 `deleted_at IS NULL` 过滤条件，无需手动指定。
  ///
  /// @param typeCode 类型代码
  /// @return 可选的字典类型实体
  Optional<SysDictTypeEntity> findByTypeCode(String typeCode);

  /// 查询所有激活的字典类型，按类型代码排序。
  ///
  /// 由于实体使用 Hibernate @SoftDelete，已删除记录会自动被过滤。
  ///
  /// @return 字典类型列表
  List<SysDictTypeEntity> findAllByOrderByTypeCodeAsc();
}
