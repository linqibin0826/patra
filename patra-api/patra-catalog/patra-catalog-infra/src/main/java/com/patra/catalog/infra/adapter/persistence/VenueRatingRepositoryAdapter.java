package com.patra.catalog.infra.adapter.persistence;

import com.patra.catalog.domain.model.aggregate.VenueRatingAggregate;
import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.domain.model.vo.venue.VenueRatingId;
import com.patra.catalog.domain.port.repository.VenueRatingRepository;
import com.patra.catalog.infra.adapter.persistence.converter.VenueRatingJpaConverter;
import com.patra.catalog.infra.adapter.persistence.dao.VenueRatingDao;
import com.patra.catalog.infra.adapter.persistence.entity.VenueRatingEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
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

/// 载体评级聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// 实现 `VenueRatingRepository` 接口，提供载体评级的持久化操作。
///
/// **数据访问**：
///
/// - 使用 Spring Data JPA 进行数据库操作
/// - 批量插入使用 `saveAll()` 优化性能
/// - 使用 `VenueRatingJpaConverter` 进行 Entity ↔ 聚合根转换
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
public class VenueRatingRepositoryAdapter implements VenueRatingRepository {

  private final VenueRatingDao jpaRepository;
  private final VenueRatingJpaConverter jpaConverter;
  private final EntityManager entityManager;

  @Override
  public Optional<VenueRatingAggregate> findById(VenueRatingId id) {
    if (id == null) {
      return Optional.empty();
    }
    return jpaRepository.findById(id.value()).map(jpaConverter::toAggregate);
  }

  @Override
  public Optional<VenueRatingAggregate> findByVenueIdAndYearAndRatingSystem(
      Long venueId, int year, RatingSystem ratingSystem) {
    if (venueId == null || ratingSystem == null) {
      return Optional.empty();
    }
    return jpaRepository
        .findByVenueIdAndYearAndRatingSystem(venueId, (short) year, ratingSystem)
        .map(jpaConverter::toAggregate);
  }

  @Override
  public List<VenueRatingAggregate> findByVenueId(Long venueId) {
    if (venueId == null) {
      return List.of();
    }
    return jpaRepository.findByVenueId(venueId).stream().map(jpaConverter::toAggregate).toList();
  }

  @Override
  public List<VenueRatingAggregate> findByVenueIdAndRatingSystem(
      Long venueId, RatingSystem ratingSystem) {
    if (venueId == null || ratingSystem == null) {
      return List.of();
    }
    return jpaRepository.findByVenueIdAndRatingSystem(venueId, ratingSystem).stream()
        .map(jpaConverter::toAggregate)
        .toList();
  }

  @Override
  public List<VenueRatingAggregate> findByVenueIdAndYear(Long venueId, int year) {
    if (venueId == null) {
      return List.of();
    }
    return jpaRepository.findByVenueIdAndYear(venueId, (short) year).stream()
        .map(jpaConverter::toAggregate)
        .toList();
  }

  @Override
  public Map<Long, List<VenueRatingAggregate>> findByVenueIds(Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenueRatingEntity> entities = jpaRepository.findByVenueIdIn(venueIds);

    Map<Long, List<VenueRatingAggregate>> result = new HashMap<>();
    for (VenueRatingEntity entity : entities) {
      VenueRatingAggregate aggregate = jpaConverter.toAggregate(entity);
      result.computeIfAbsent(entity.getVenueId(), k -> new ArrayList<>()).add(aggregate);
    }
    return result;
  }

  @Override
  public VenueRatingAggregate save(VenueRatingAggregate aggregate) {
    if (aggregate == null) {
      throw new IllegalArgumentException("聚合根不能为 null");
    }

    VenueRatingEntity saved;

    if (aggregate.isTransient()) {
      // 新增：创建实体并持久化
      VenueRatingEntity entity = jpaConverter.toEntity(aggregate);
      assignIdIfMissing(entity);
      saved = jpaRepository.save(entity);
    } else {
      // 更新：查找托管实体并原地更新
      VenueRatingEntity managed =
          entityManager.find(VenueRatingEntity.class, aggregate.getId().value());
      if (managed == null) {
        throw new IllegalStateException("实体不存在：id=" + aggregate.getId());
      }
      jpaConverter.updateEntity(managed, aggregate);
      saved = managed; // 托管实体会在事务提交时自动 flush
    }

    log.debug(
        "保存载体评级：id={}, venueId={}, year={}, system={}",
        saved.getId(),
        aggregate.getVenueId(),
        aggregate.getYear(),
        aggregate.getRatingSystem());

    return jpaConverter.toAggregate(saved);
  }

  @Override
  public void saveAll(List<VenueRatingAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    List<VenueRatingEntity> entities =
        aggregates.stream().map(jpaConverter::toEntity).peek(this::assignIdIfMissing).toList();

    List<VenueRatingEntity> savedEntities = jpaRepository.saveAll(entities);

    // 回填 ID 和版本
    for (int i = 0; i < aggregates.size(); i++) {
      VenueRatingAggregate aggregate = aggregates.get(i);
      VenueRatingEntity saved = savedEntities.get(i);
      aggregate.assignId(VenueRatingId.of(saved.getId()));
      aggregate.assignVersion(saved.getVersion());
      aggregate.clearDirty();
    }

    log.info("批量保存载体评级完成：{} 条", aggregates.size());
  }

  @Override
  public void deleteById(VenueRatingId id) {
    if (id == null) {
      return;
    }
    jpaRepository.deleteById(id.value());
    log.debug("删除载体评级：id={}", id);
  }

  @Override
  public void deleteByVenueId(Long venueId) {
    if (venueId == null) {
      return;
    }
    jpaRepository.deleteByVenueId(venueId);
    log.debug("根据 venueId={} 删除载体评级", venueId);
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(VenueRatingEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }
}
