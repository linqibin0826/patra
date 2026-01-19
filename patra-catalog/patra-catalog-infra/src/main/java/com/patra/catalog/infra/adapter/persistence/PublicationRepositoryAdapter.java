package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.vo.publication.PublicationAbstract;
import com.patra.catalog.domain.model.vo.publication.PublicationAlternativeAbstract;
import com.patra.catalog.domain.model.vo.publication.PublicationDate;
import com.patra.catalog.domain.model.vo.publication.PublicationId;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifier;
import com.patra.catalog.domain.model.vo.publication.PublicationMetadata;
import com.patra.catalog.domain.model.vo.publication.PublicationOaLocation;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.infra.adapter.persistence.converter.mapper.PublicationJpaMapper;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationAbstractDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationAlternativeAbstractDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationDateDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationIdentifierDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMetadataDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationOaLocationDao;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationAbstractEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationAlternativeAbstractEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationDateEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationIdentifierEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationMetadataEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationOaLocationEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
/// - 使用 `PublicationJpaMapper` 进行 Entity ↔ 聚合根转换
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

  /// 批量操作时每批次的大小，超过此值会 flush 并 clear 以防内存溢出。
  private static final int BATCH_FLUSH_SIZE = 500;

  private final PublicationDao jpaRepository;
  private final PublicationJpaMapper jpaConverter;
  private final EntityManager entityManager;

  // ========== 补充数据 DAO ==========
  private final PublicationIdentifierDao identifierDao;
  private final PublicationAbstractDao abstractDao;
  private final PublicationDateDao dateDao;
  private final PublicationMetadataDao metadataDao;
  private final PublicationAlternativeAbstractDao alternativeAbstractDao;
  private final PublicationOaLocationDao oaLocationDao;

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

  // ========== 标识符管理（聚合边界内，独立表） ==========

  @Override
  public void replaceIdentifiersBatch(
      Map<Long, List<PublicationIdentifier>> identifiersByPublicationId) {
    if (identifiersByPublicationId == null || identifiersByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(identifiersByPublicationId.keySet());

    // 删除旧数据
    identifierDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationIdentifierEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<PublicationIdentifier>> entry :
        identifiersByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      for (PublicationIdentifier identifier : entry.getValue()) {
        PublicationIdentifierEntity entity =
            jpaConverter.toIdentifierEntity(identifier, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, identifierDao);
      log.debug("批量插入标识符 {} 条", entities.size());
    }
  }

  // ========== 摘要管理（聚合边界内，独立表，1:1） ==========

  @Override
  public void replaceAbstractsBatch(Map<Long, PublicationAbstract> abstractsByPublicationId) {
    if (abstractsByPublicationId == null || abstractsByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(abstractsByPublicationId.keySet());

    // 删除旧数据
    abstractDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationAbstractEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, PublicationAbstract> entry : abstractsByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      PublicationAbstract pubAbstract = entry.getValue();
      if (pubAbstract != null && pubAbstract.hasContent()) {
        PublicationAbstractEntity entity =
            jpaConverter.toAbstractEntity(pubAbstract, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, abstractDao);
      log.debug("批量插入摘要 {} 条", entities.size());
    }
  }

  // ========== 补充数据管理（聚合边界外） ==========

  @Override
  public void replaceDatesBatch(Map<Long, List<PublicationDate>> datesByPublicationId) {
    if (datesByPublicationId == null || datesByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(datesByPublicationId.keySet());

    // 删除旧数据
    dateDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationDateEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<PublicationDate>> entry : datesByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      for (PublicationDate date : entry.getValue()) {
        PublicationDateEntity entity = jpaConverter.toDateEntity(date, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, dateDao);
      log.debug("批量插入日期 {} 条", entities.size());
    }
  }

  @Override
  public void replaceMetadataBatch(Map<Long, PublicationMetadata> metadataByPublicationId) {
    if (metadataByPublicationId == null || metadataByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(metadataByPublicationId.keySet());

    // 删除旧数据
    metadataDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationMetadataEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, PublicationMetadata> entry : metadataByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      PublicationMetadata metadata = entry.getValue();
      if (metadata != null) {
        PublicationMetadataEntity entity = jpaConverter.toMetadataEntity(metadata, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, metadataDao);
      log.debug("批量插入元数据 {} 条", entities.size());
    }
  }

  @Override
  public void replaceAlternativeAbstractsBatch(
      Map<Long, List<PublicationAlternativeAbstract>> abstractsByPublicationId) {
    if (abstractsByPublicationId == null || abstractsByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(abstractsByPublicationId.keySet());

    // 删除旧数据
    alternativeAbstractDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationAlternativeAbstractEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<PublicationAlternativeAbstract>> entry :
        abstractsByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      for (PublicationAlternativeAbstract altAbstract : entry.getValue()) {
        PublicationAlternativeAbstractEntity entity =
            jpaConverter.toAlternativeAbstractEntity(altAbstract, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, alternativeAbstractDao);
      log.debug("批量插入翻译摘要 {} 条", entities.size());
    }
  }

  @Override
  public void replaceOaLocationsBatch(
      Map<Long, List<PublicationOaLocation>> locationsByPublicationId) {
    if (locationsByPublicationId == null || locationsByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(locationsByPublicationId.keySet());

    // 删除旧数据
    oaLocationDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationOaLocationEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<PublicationOaLocation>> entry : locationsByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      for (PublicationOaLocation location : entry.getValue()) {
        PublicationOaLocationEntity entity =
            jpaConverter.toOaLocationEntity(location, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, oaLocationDao);
      log.debug("批量插入 OA 位置 {} 条", entities.size());
    }
  }

  // ========== 批量操作辅助方法 ==========

  /// 批量保存实体，定期 flush 以防内存溢出。
  ///
  /// 使用 `saveAll()` 批量保存，配合 `rewriteBatchedStatements=true` 启用 JDBC 批量插入，
  /// 相比逐条 `save()` 性能提升 10 倍以上。
  ///
  /// @param entities 实体列表
  /// @param repository JPA Repository
  /// @param <T> 实体类型
  private <T> void batchSaveWithFlush(
      List<T> entities, org.springframework.data.jpa.repository.JpaRepository<T, Long> repository) {
    if (entities.isEmpty()) {
      return;
    }

    // 分批保存，每批 BATCH_FLUSH_SIZE 条
    for (int i = 0; i < entities.size(); i += BATCH_FLUSH_SIZE) {
      int end = Math.min(i + BATCH_FLUSH_SIZE, entities.size());
      List<T> batch = entities.subList(i, end);
      repository.saveAll(batch); // 批量保存
      entityManager.flush();
      entityManager.clear();
    }
  }
}
