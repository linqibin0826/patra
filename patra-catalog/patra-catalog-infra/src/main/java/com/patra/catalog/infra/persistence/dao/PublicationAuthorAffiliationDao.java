package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.domain.model.enums.DisambiguationStatus;
import com.patra.catalog.infra.persistence.entity.PublicationAuthorAffiliationEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/// 作者-机构归属 JPA Repository。
///
/// **职责**：
///
/// - 管理 `PublicationAuthorAffiliationEntity` 的 CRUD 操作
/// - 支持按文献-作者关联、出版物、作者等维度查询
/// - 支持消歧状态和标识符查询（用于批量消歧处理）
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationAuthorAffiliationDao
    extends JpaRepository<PublicationAuthorAffiliationEntity, Long> {

  // ========== 按文献-作者关联查询 ==========

  /// 按文献-作者关联 ID 查询所有机构归属。
  ///
  /// @param pubAuthorId 文献-作者关联 ID
  /// @return 机构归属实体列表
  List<PublicationAuthorAffiliationEntity> findAllByPubAuthorId(Long pubAuthorId);

  /// 按文献-作者关联 ID 列表批量查询机构归属。
  ///
  /// @param pubAuthorIds 文献-作者关联 ID 列表
  /// @return 机构归属实体列表
  List<PublicationAuthorAffiliationEntity> findAllByPubAuthorIdIn(Collection<Long> pubAuthorIds);

  /// 按文献-作者关联 ID 删除所有机构归属。
  ///
  /// @param pubAuthorId 文献-作者关联 ID
  void deleteAllByPubAuthorId(Long pubAuthorId);

  /// 按文献-作者关联 ID 列表批量删除机构归属。
  ///
  /// @param pubAuthorIds 文献-作者关联 ID 列表
  void deleteAllByPubAuthorIdIn(Collection<Long> pubAuthorIds);

  // ========== 按出版物查询（冗余字段加速） ==========

  /// 按出版物 ID 查询所有机构归属。
  ///
  /// @param publicationId 出版物 ID
  /// @return 机构归属实体列表
  List<PublicationAuthorAffiliationEntity> findAllByPublicationId(Long publicationId);

  /// 按出版物 ID 列表批量查询机构归属。
  ///
  /// @param publicationIds 出版物 ID 列表
  /// @return 机构归属实体列表
  List<PublicationAuthorAffiliationEntity> findAllByPublicationIdIn(
      Collection<Long> publicationIds);

  /// 按出版物 ID 删除所有机构归属。
  ///
  /// @param publicationId 出版物 ID
  void deleteAllByPublicationId(Long publicationId);

  // ========== 按作者查询（冗余字段加速） ==========

  /// 按作者 ID 查询所有机构归属。
  ///
  /// @param authorId 作者 ID
  /// @return 机构归属实体列表
  List<PublicationAuthorAffiliationEntity> findAllByAuthorId(Long authorId);

  /// 按作者 ID 列表批量查询机构归属。
  ///
  /// @param authorIds 作者 ID 列表
  /// @return 机构归属实体列表
  List<PublicationAuthorAffiliationEntity> findAllByAuthorIdIn(Collection<Long> authorIds);

  // ========== 消歧相关查询 ==========

  /// 按消歧状态查询机构归属。
  ///
  /// @param disambiguationStatus 消歧状态
  /// @return 机构归属实体列表
  List<PublicationAuthorAffiliationEntity> findAllByDisambiguationStatus(
      DisambiguationStatus disambiguationStatus);

  /// 按 ROR ID 查询机构归属。
  ///
  /// @param rorId ROR ID
  /// @return 机构归属实体列表
  List<PublicationAuthorAffiliationEntity> findAllByRorId(String rorId);

  /// 按 Ringgold ID 查询机构归属。
  ///
  /// @param ringgoldId Ringgold ID
  /// @return 机构归属实体列表
  List<PublicationAuthorAffiliationEntity> findAllByRinggoldId(String ringgoldId);

  /// 统计指定消歧状态的记录数量。
  ///
  /// @param disambiguationStatus 消歧状态
  /// @return 记录数
  long countByDisambiguationStatus(DisambiguationStatus disambiguationStatus);

  // ========== 分页查询（用于批量处理） ==========

  /// 按消歧状态分页查询机构归属。
  ///
  /// 用于批量消歧处理时分页加载待处理记录。
  ///
  /// @param disambiguationStatus 消歧状态
  /// @param pageable 分页参数
  /// @return 机构归属分页结果
  Page<PublicationAuthorAffiliationEntity> findByDisambiguationStatus(
      DisambiguationStatus disambiguationStatus, Pageable pageable);

  /// 按出版物 ID 分页查询机构归属。
  ///
  /// @param publicationId 出版物 ID
  /// @param pageable 分页参数
  /// @return 机构归属分页结果
  Page<PublicationAuthorAffiliationEntity> findByPublicationId(
      Long publicationId, Pageable pageable);

  /// 按作者 ID 分页查询机构归属。
  ///
  /// @param authorId 作者 ID
  /// @param pageable 分页参数
  /// @return 机构归属分页结果
  Page<PublicationAuthorAffiliationEntity> findByAuthorId(Long authorId, Pageable pageable);
}
