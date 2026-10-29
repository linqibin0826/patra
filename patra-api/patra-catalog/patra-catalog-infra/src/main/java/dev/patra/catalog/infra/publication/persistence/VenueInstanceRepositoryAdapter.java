package dev.patra.catalog.infra.publication.persistence;

import dev.patra.catalog.domain.publication.model.aggregate.VenueInstanceAggregate;
import dev.patra.catalog.domain.publication.model.vo.VenueInstanceId;
import dev.patra.catalog.domain.publication.port.repository.VenueInstanceRepository;
import dev.patra.catalog.infra.publication.persistence.dao.VenueInstanceDao;
import dev.patra.catalog.infra.publication.persistence.entity.VenueInstanceEntity;
import dev.patra.catalog.infra.publication.persistence.mapper.VenueInstanceJpaMapper;
import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 载体实例聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// 实现 `VenueInstanceRepository` 接口，提供载体实例的持久化操作。
///
/// **数据访问**：
///
/// - 使用 Spring Data JPA 进行数据库操作
/// - 批量插入使用 `saveAll()` 优化性能
/// - 使用 `VenueInstanceJpaMapper` 进行 Entity ↔ 聚合根转换
///
/// **JPA 批量写入说明**：
///
/// - 使用 Spring Data JPA 的 `saveAll()` 进行批量保存
/// - ID 由 `SnowflakeIdGenerator` 雪花算法生成
/// - 审计字段由 JPA Auditing 自动填充
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class VenueInstanceRepositoryAdapter implements VenueInstanceRepository {

  private final VenueInstanceDao jpaRepository;
  private final VenueInstanceJpaMapper jpaConverter;
  private final EntityManager entityManager;

  @Override
  public Optional<VenueInstanceAggregate> findById(Long id) {
    if (id == null) {
      return Optional.empty();
    }
    return jpaRepository.findById(id).map(jpaConverter::toAggregate);
  }

  @Override
  public Map<Long, List<VenueInstanceAggregate>> findByVenueIds(Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenueInstanceEntity> entities = jpaRepository.findByVenueIdIn(venueIds);

    Map<Long, List<VenueInstanceAggregate>> result = new HashMap<>();
    for (VenueInstanceEntity entity : entities) {
      VenueInstanceAggregate aggregate = jpaConverter.toAggregate(entity);
      result.computeIfAbsent(entity.getVenueId(), k -> new ArrayList<>()).add(aggregate);
    }
    return result;
  }

  @Override
  public Optional<VenueInstanceAggregate> findJournalInstance(
      Long venueId, String volume, String issue, Integer publicationYear) {
    if (venueId == null || publicationYear == null) {
      return Optional.empty();
    }
    return jpaRepository
        .findJournalInstance(venueId, volume, issue, publicationYear)
        .map(jpaConverter::toAggregate);
  }

  @Override
  public Optional<VenueInstanceAggregate> findBookInstance(
      Long venueId, String edition, Integer publicationYear) {
    if (venueId == null || publicationYear == null) {
      return Optional.empty();
    }
    return jpaRepository
        .findBookInstance(venueId, edition, publicationYear)
        .map(jpaConverter::toAggregate);
  }

  @Override
  public Optional<VenueInstanceAggregate> findConferenceInstance(
      Long venueId, String conferenceName, Integer publicationYear) {
    if (venueId == null || publicationYear == null) {
      return Optional.empty();
    }
    return jpaRepository
        .findConferenceInstance(venueId, conferenceName, publicationYear)
        .map(jpaConverter::toAggregate);
  }

  /// 用原生 `INSERT … ON CONFLICT DO NOTHING` 插入期刊实例，冲突时不抛异常，所在事务继续可用。
  ///
  /// @param instance 期刊实例（尚未持久化）
  /// @return 是否插入了新行
  @Override
  public boolean insertJournalInstanceIfAbsent(VenueInstanceAggregate instance) {
    long id = SnowflakeIdGenerator.getId();
    int inserted =
        entityManager
            .createNativeQuery(
                """
                INSERT INTO cat_venue_instance
                    (id, venue_id, volume, issue, publication_year, publication_month, publication_day)
                VALUES (:id, :venueId, :volume, :issue, :year, :month, :day)
                ON CONFLICT (venue_id, volume, issue, edition, conference_name, publication_year)
                DO NOTHING
                """)
            .setParameter("id", id)
            .setParameter("venueId", instance.getVenueId().value())
            .setParameter("volume", instance.getVolume())
            .setParameter("issue", instance.getIssue())
            .setParameter("year", instance.getPublicationYear())
            .setParameter("month", instance.getPublicationMonth())
            .setParameter("day", instance.getPublicationDay())
            .executeUpdate();
    if (inserted == 0) {
      return false;
    }
    instance.assignId(VenueInstanceId.of(id));
    return true;
  }

  @Override
  public void save(VenueInstanceAggregate instance) {
    if (instance == null) {
      throw new IllegalArgumentException("实例不能为 null");
    }

    VenueInstanceEntity saved;

    if (instance.isTransient()) {
      // 新增：创建实体并持久化
      VenueInstanceEntity entity = jpaConverter.toEntity(instance);
      assignIdIfMissing(entity);
      saved = jpaRepository.save(entity);
      // 回填 ID
      instance.assignId(VenueInstanceId.of(saved.getId()));
    } else {
      // 更新：查找托管实体并原地更新
      VenueInstanceEntity managed =
          entityManager.find(VenueInstanceEntity.class, instance.getId().value());
      if (managed == null) {
        throw new IllegalStateException("实体不存在：id=" + instance.getId());
      }
      jpaConverter.updateEntity(managed, instance);
      saved = managed; // 托管实体会在事务提交时自动 flush
    }

    log.debug(
        "保存载体实例：id={}, venueId={}, year={}",
        saved.getId(),
        instance.getVenueId(),
        instance.getPublicationYear());
  }

  @Override
  public void insertAll(List<VenueInstanceAggregate> instances) {
    if (instances == null || instances.isEmpty()) {
      return;
    }

    List<VenueInstanceEntity> entities =
        instances.stream().map(jpaConverter::toEntity).peek(this::assignIdIfMissing).toList();

    List<VenueInstanceEntity> savedEntities = jpaRepository.saveAll(entities);

    // 回填 ID 和版本
    for (int i = 0; i < instances.size(); i++) {
      VenueInstanceAggregate instance = instances.get(i);
      VenueInstanceEntity saved = savedEntities.get(i);
      instance.assignId(VenueInstanceId.of(saved.getId()));
      instance.assignVersion(saved.getVersion());
    }

    log.info("批量插入载体实例完成：{} 条", instances.size());
  }

  @Override
  public void updateBatch(List<VenueInstanceAggregate> instances) {
    if (instances == null || instances.isEmpty()) {
      return;
    }

    for (VenueInstanceAggregate instance : instances) {
      if (instance.getId() == null) {
        throw new IllegalArgumentException("批量更新时实例 ID 不能为 null");
      }
      VenueInstanceEntity managed =
          entityManager.find(VenueInstanceEntity.class, instance.getId().value());
      if (managed != null) {
        jpaConverter.updateEntity(managed, instance);
      }
    }

    log.info("批量更新载体实例完成：{} 条", instances.size());
  }

  @Override
  public boolean deleteById(Long id) {
    if (id == null) {
      return false;
    }
    if (jpaRepository.existsById(id)) {
      jpaRepository.deleteById(id);
      log.debug("删除载体实例：id={}", id);
      return true;
    }
    return false;
  }

  @Override
  public int deleteByVenueId(Long venueId) {
    if (venueId == null) {
      return 0;
    }
    int deleted = jpaRepository.deleteByVenueId(venueId);
    log.debug("根据 venueId={} 删除载体实例：{} 条", venueId, deleted);
    return deleted;
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(VenueInstanceEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }
}
