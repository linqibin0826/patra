package com.patra.catalog.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.patra.catalog.domain.model.aggregate.AffiliationAggregate;
import com.patra.catalog.domain.port.repository.AffiliationRepository;
import com.patra.catalog.infra.persistence.jpa.AffiliationJpaRepository;
import com.patra.catalog.infra.persistence.jpa.converter.AffiliationJpaConverter;
import com.patra.catalog.infra.persistence.jpa.entity.AffiliationEntity;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 机构聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// - 管理机构聚合根的持久化
/// - 处理 ROR ID/GRID ID 唯一性检查
/// - 支持去重查询和相似度匹配
/// - 提供按地理位置和机构类型查询
///
/// **JPA 批量写入说明**：
///
/// - 使用 Spring Data JPA 的 `saveAll()` 进行批量保存
/// - ID 由 `IdWorker` 雪花算法生成（与 MyBatis-Plus 保持一致）
/// - 审计字段由 JPA Auditing 自动填充
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class AffiliationRepositoryAdapter implements AffiliationRepository {

  private final AffiliationJpaRepository jpaRepository;
  private final AffiliationJpaConverter jpaConverter;

  /// 保存单个机构聚合根。
  ///
  /// @param affiliation 机构聚合根
  /// @return 保存后的聚合根（含 ID）
  public AffiliationAggregate save(AffiliationAggregate affiliation) {
    if (affiliation == null) {
      log.warn("机构为空，跳过保存");
      return null;
    }

    log.debug("保存机构：{}", affiliation.getName());

    AffiliationEntity entity = jpaConverter.toEntity(affiliation);
    assignIdIfMissing(entity);

    AffiliationEntity saved = jpaRepository.save(entity);

    log.debug("机构保存完成，ID：{}", saved.getId());
    return jpaConverter.toAggregate(saved);
  }

  /// 批量保存机构聚合根。
  ///
  /// @param affiliations 机构聚合根列表
  public void saveBatch(List<AffiliationAggregate> affiliations) {
    if (affiliations == null || affiliations.isEmpty()) {
      log.warn("机构列表为空，跳过保存");
      return;
    }

    log.info("批量保存机构，数量：{}", affiliations.size());

    List<AffiliationEntity> entities =
        affiliations.stream().map(jpaConverter::toEntity).peek(this::assignIdIfMissing).toList();

    jpaRepository.saveAll(entities);

    log.info("机构批量保存完成，共 {} 条", entities.size());
  }

  /// 根据 ID 查询机构。
  ///
  /// @param id 机构 ID
  /// @return 机构聚合根（可选）
  public Optional<AffiliationAggregate> findById(Long id) {
    return jpaRepository.findById(id).map(jpaConverter::toAggregate);
  }

  /// 根据 ROR ID 查询机构。
  ///
  /// @param rorId ROR 标识符
  /// @return 机构聚合根（可选）
  public Optional<AffiliationAggregate> findByRorId(String rorId) {
    return jpaRepository.findByRorId(rorId).map(jpaConverter::toAggregate);
  }

  /// 根据 GRID ID 查询机构。
  ///
  /// @param gridId GRID 标识符
  /// @return 机构聚合根（可选）
  public Optional<AffiliationAggregate> findByGridId(String gridId) {
    return jpaRepository.findByGridId(gridId).map(jpaConverter::toAggregate);
  }

  /// 根据去重键查询机构。
  ///
  /// @param dedupKey 去重键
  /// @return 机构聚合根列表
  public List<AffiliationAggregate> findByDedupKey(String dedupKey) {
    return jpaRepository.findByDedupKey(dedupKey).stream().map(jpaConverter::toAggregate).toList();
  }

  /// 根据国家查询机构。
  ///
  /// @param country 国家代码
  /// @return 机构聚合根列表
  public List<AffiliationAggregate> findByCountry(String country) {
    return jpaRepository.findByCountry(country).stream().map(jpaConverter::toAggregate).toList();
  }

  /// 检查 ROR ID 是否已存在。
  ///
  /// @param rorId ROR 标识符
  /// @return true 如果已存在
  public boolean existsByRorId(String rorId) {
    return jpaRepository.existsByRorId(rorId);
  }

  /// 检查 GRID ID 是否已存在。
  ///
  /// @param gridId GRID 标识符
  /// @return true 如果已存在
  public boolean existsByGridId(String gridId) {
    return jpaRepository.existsByGridId(gridId);
  }

  /// 检查表中是否有数据。
  ///
  /// @return true 如果有数据
  public boolean hasAnyData() {
    return jpaRepository.hasAnyData();
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(AffiliationEntity entity) {
    if (entity.getId() == null) {
      entity.setId(IdWorker.getId());
    }
  }
}
