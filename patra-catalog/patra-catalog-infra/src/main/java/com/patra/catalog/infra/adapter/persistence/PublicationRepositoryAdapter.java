package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.vo.publication.PublicationId;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.infra.adapter.persistence.converter.PublicationJpaConverter;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationDao;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 文献聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// - 实现 `PublicationRepository` 接口，提供文献的持久化操作
/// - 负责 Aggregate ↔ Entity 转换
/// - 协调 JPA Repository 操作
///
/// **数据访问**：
///
/// - 使用 Spring Data JPA 进行数据库操作
/// - 批量插入使用 `saveAll()` 优化性能
/// - 使用 `PublicationJpaConverter` 进行 Entity ↔ 聚合根转换
///
/// **JPA 更新模式**：
///
/// - 新增：创建 Entity，分配雪花 ID，调用 `save()`
/// - 更新：通过 `EntityManager.find()` 获取托管实体，原地更新可变字段
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class PublicationRepositoryAdapter implements PublicationRepository {

  private final PublicationDao jpaRepository;
  private final PublicationJpaConverter jpaConverter;
  private final EntityManager entityManager;

  @Override
  public Optional<PublicationAggregate> findById(Long id) {
    if (id == null) {
      return Optional.empty();
    }
    return jpaRepository.findById(id).map(jpaConverter::toAggregate);
  }

  @Override
  public Optional<PublicationAggregate> findByPmid(String pmid) {
    if (pmid == null || pmid.isBlank()) {
      return Optional.empty();
    }
    return jpaRepository.findByPmid(pmid).map(jpaConverter::toAggregate);
  }

  @Override
  public Optional<PublicationAggregate> findByDoi(String doi) {
    if (doi == null || doi.isBlank()) {
      return Optional.empty();
    }
    return jpaRepository.findByDoi(doi).map(jpaConverter::toAggregate);
  }

  @Override
  public Optional<PublicationAggregate> findByPmidOrDoi(String pmid, String doi) {
    if ((pmid == null || pmid.isBlank()) && (doi == null || doi.isBlank())) {
      return Optional.empty();
    }
    return jpaRepository.findByPmidOrDoi(pmid, doi).map(jpaConverter::toAggregate);
  }

  @Override
  public boolean existsByPmid(String pmid) {
    if (pmid == null || pmid.isBlank()) {
      return false;
    }
    return jpaRepository.existsByPmid(pmid);
  }

  @Override
  public boolean existsByDoi(String doi) {
    if (doi == null || doi.isBlank()) {
      return false;
    }
    return jpaRepository.existsByDoi(doi);
  }

  @Override
  public List<PublicationAggregate> findByVenueInstanceId(Long venueInstanceId) {
    if (venueInstanceId == null) {
      return List.of();
    }
    return jpaRepository.findByVenueInstanceId(venueInstanceId).stream()
        .map(jpaConverter::toAggregate)
        .toList();
  }

  @Override
  public List<PublicationAggregate> findByVenueId(Long venueId) {
    if (venueId == null) {
      return List.of();
    }
    return jpaRepository.findByVenueId(venueId).stream().map(jpaConverter::toAggregate).toList();
  }

  @Override
  public long countByVenueId(Long venueId) {
    if (venueId == null) {
      return 0;
    }
    return jpaRepository.countByVenueId(venueId);
  }

  @Override
  public void save(PublicationAggregate aggregate) {
    if (aggregate == null) {
      throw new IllegalArgumentException("聚合根不能为 null");
    }

    PublicationEntity saved;

    if (aggregate.isTransient()) {
      // 新增：创建实体并持久化
      PublicationEntity entity = jpaConverter.toEntity(aggregate);
      assignIdIfMissing(entity);
      saved = jpaRepository.save(entity);
      // 回填 ID
      aggregate.assignId(PublicationId.of(saved.getId()));
    } else {
      // 更新：查找托管实体并原地更新
      PublicationEntity managed =
          entityManager.find(PublicationEntity.class, aggregate.getId().value());
      if (managed == null) {
        throw new IllegalStateException("实体不存在：id=" + aggregate.getId());
      }
      jpaConverter.updateEntity(managed, aggregate);
      saved = managed; // 托管实体会在事务提交时自动 flush
    }

    log.debug(
        "保存文献：id={}, pmid={}, doi={}, year={}",
        saved.getId(),
        aggregate.getPmid(),
        aggregate.getDoi(),
        aggregate.getPublicationYear());
  }

  @Override
  public void insertAll(List<PublicationAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    List<PublicationEntity> entities =
        aggregates.stream().map(jpaConverter::toEntity).peek(this::assignIdIfMissing).toList();

    List<PublicationEntity> savedEntities = jpaRepository.saveAll(entities);

    // 回填 ID 和版本
    for (int i = 0; i < aggregates.size(); i++) {
      PublicationAggregate aggregate = aggregates.get(i);
      PublicationEntity saved = savedEntities.get(i);
      aggregate.assignId(PublicationId.of(saved.getId()));
      aggregate.assignVersion(saved.getVersion());
    }

    log.info("批量插入文献完成：{} 条", aggregates.size());
  }

  @Override
  public void updateBatch(List<PublicationAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    for (PublicationAggregate aggregate : aggregates) {
      if (aggregate.getId() == null) {
        throw new IllegalArgumentException("批量更新时聚合根 ID 不能为 null");
      }
      PublicationEntity managed =
          entityManager.find(PublicationEntity.class, aggregate.getId().value());
      if (managed != null) {
        jpaConverter.updateEntity(managed, aggregate);
      }
    }

    log.info("批量更新文献完成：{} 条", aggregates.size());
  }

  @Override
  public boolean deleteById(Long id) {
    if (id == null) {
      return false;
    }
    if (jpaRepository.existsById(id)) {
      jpaRepository.deleteById(id);
      log.debug("删除文献：id={}", id);
      return true;
    }
    return false;
  }

  @Override
  public int deleteByVenueInstanceId(Long venueInstanceId) {
    if (venueInstanceId == null) {
      return 0;
    }
    int deleted = jpaRepository.deleteByVenueInstanceId(venueInstanceId);
    log.debug("根据 venueInstanceId={} 删除文献：{} 条", venueInstanceId, deleted);
    return deleted;
  }

  @Override
  public int deleteByVenueId(Long venueId) {
    if (venueId == null) {
      return 0;
    }
    int deleted = jpaRepository.deleteByVenueId(venueId);
    log.debug("根据 venueId={} 删除文献：{} 条", venueId, deleted);
    return deleted;
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(PublicationEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }
}
