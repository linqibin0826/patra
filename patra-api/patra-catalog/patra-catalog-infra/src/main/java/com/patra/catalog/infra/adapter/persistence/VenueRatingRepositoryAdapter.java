package com.patra.catalog.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.patra.catalog.domain.model.aggregate.VenueRatingAggregate;
import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.domain.model.vo.venue.VenueRatingId;
import com.patra.catalog.domain.port.repository.VenueRatingRepository;
import com.patra.catalog.infra.persistence.converter.VenueRatingConverter;
import com.patra.catalog.infra.persistence.entity.VenueRatingDO;
import com.patra.catalog.infra.persistence.mapper.VenueRatingMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 载体评级聚合根仓储实现。
///
/// **职责**：
///
/// 实现 `VenueRatingRepository` 接口，提供载体评级的持久化操作。
///
/// **数据访问**：
///
/// - 使用 MyBatis-Plus 进行数据库操作
/// - 批量插入使用 `Db.saveBatch()` 优化性能
/// - 使用 `VenueRatingConverter` 进行 DO ↔ 聚合根转换
///
/// @author Patra Lin
/// @since 0.6.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class VenueRatingRepositoryAdapter implements VenueRatingRepository {

  private final VenueRatingMapper venueRatingMapper;
  private final VenueRatingConverter venueRatingConverter;

  @Override
  public Optional<VenueRatingAggregate> findById(VenueRatingId id) {
    if (id == null) {
      return Optional.empty();
    }
    VenueRatingDO doEntity = venueRatingMapper.selectById(id.value());
    return Optional.ofNullable(venueRatingConverter.toAggregate(doEntity));
  }

  @Override
  public Optional<VenueRatingAggregate> findByVenueIdAndYearAndRatingSystem(
      Long venueId, int year, RatingSystem ratingSystem) {
    if (venueId == null || ratingSystem == null) {
      return Optional.empty();
    }

    LambdaQueryWrapper<VenueRatingDO> query =
        new LambdaQueryWrapper<VenueRatingDO>()
            .eq(VenueRatingDO::getVenueId, venueId)
            .eq(VenueRatingDO::getYear, (short) year)
            .eq(VenueRatingDO::getRatingSystem, ratingSystem.getCode());

    VenueRatingDO doEntity = venueRatingMapper.selectOne(query);
    return Optional.ofNullable(venueRatingConverter.toAggregate(doEntity));
  }

  @Override
  public List<VenueRatingAggregate> findByVenueId(Long venueId) {
    if (venueId == null) {
      return List.of();
    }

    List<VenueRatingDO> doList =
        venueRatingMapper.selectList(
            new LambdaQueryWrapper<VenueRatingDO>().eq(VenueRatingDO::getVenueId, venueId));

    return doList.stream().map(venueRatingConverter::toAggregate).toList();
  }

  @Override
  public List<VenueRatingAggregate> findByVenueIdAndRatingSystem(
      Long venueId, RatingSystem ratingSystem) {
    if (venueId == null || ratingSystem == null) {
      return List.of();
    }

    List<VenueRatingDO> doList =
        venueRatingMapper.selectList(
            new LambdaQueryWrapper<VenueRatingDO>()
                .eq(VenueRatingDO::getVenueId, venueId)
                .eq(VenueRatingDO::getRatingSystem, ratingSystem.getCode()));

    return doList.stream().map(venueRatingConverter::toAggregate).toList();
  }

  @Override
  public List<VenueRatingAggregate> findByVenueIdAndYear(Long venueId, int year) {
    if (venueId == null) {
      return List.of();
    }

    List<VenueRatingDO> doList =
        venueRatingMapper.selectList(
            new LambdaQueryWrapper<VenueRatingDO>()
                .eq(VenueRatingDO::getVenueId, venueId)
                .eq(VenueRatingDO::getYear, (short) year));

    return doList.stream().map(venueRatingConverter::toAggregate).toList();
  }

  @Override
  public Map<Long, List<VenueRatingAggregate>> findByVenueIds(Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenueRatingDO> doList =
        venueRatingMapper.selectList(
            new LambdaQueryWrapper<VenueRatingDO>().in(VenueRatingDO::getVenueId, venueIds));

    Map<Long, List<VenueRatingAggregate>> result = new HashMap<>();
    for (VenueRatingDO doEntity : doList) {
      VenueRatingAggregate aggregate = venueRatingConverter.toAggregate(doEntity);
      result.computeIfAbsent(doEntity.getVenueId(), k -> new ArrayList<>()).add(aggregate);
    }
    return result;
  }

  @Override
  public VenueRatingAggregate save(VenueRatingAggregate aggregate) {
    if (aggregate == null) {
      throw new IllegalArgumentException("聚合根不能为 null");
    }

    VenueRatingDO doEntity = venueRatingConverter.toDO(aggregate);

    if (aggregate.isTransient()) {
      // 新建
      venueRatingMapper.insert(doEntity);
      aggregate.assignId(VenueRatingId.of(doEntity.getId()));
      log.debug(
          "插入载体评级：venueId={}, year={}, system={}",
          aggregate.getVenueId(),
          aggregate.getYear(),
          aggregate.getRatingSystem());
    } else if (aggregate.isDirty()) {
      // 更新
      venueRatingMapper.updateById(doEntity);
      aggregate.clearDirty();
      log.debug("更新载体评级：id={}", aggregate.getId());
    }

    // 更新版本号（从 DO 回填）
    aggregate.assignVersion(doEntity.getVersion());
    return aggregate;
  }

  @Override
  public void saveAll(List<VenueRatingAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    // 区分新增和更新
    List<VenueRatingAggregate> toInsert = new ArrayList<>();
    List<VenueRatingAggregate> toUpdate = new ArrayList<>();

    for (VenueRatingAggregate aggregate : aggregates) {
      if (aggregate.isTransient()) {
        toInsert.add(aggregate);
      } else if (aggregate.isDirty()) {
        toUpdate.add(aggregate);
      }
    }

    // 批量新增
    if (!toInsert.isEmpty()) {
      List<VenueRatingDO> insertDoList = toInsert.stream().map(venueRatingConverter::toDO).toList();

      Db.saveBatch(insertDoList);

      // ID 回填
      for (int i = 0; i < toInsert.size(); i++) {
        VenueRatingDO doEntity = insertDoList.get(i);
        VenueRatingAggregate aggregate = toInsert.get(i);
        aggregate.assignId(VenueRatingId.of(doEntity.getId()));
        aggregate.assignVersion(doEntity.getVersion());
      }

      log.debug("批量插入载体评级完成：{} 条", toInsert.size());
    }

    // 批量更新
    if (!toUpdate.isEmpty()) {
      List<VenueRatingDO> updateDoList = toUpdate.stream().map(venueRatingConverter::toDO).toList();

      Db.updateBatchById(updateDoList);

      // 清除脏标记并更新版本
      for (int i = 0; i < toUpdate.size(); i++) {
        VenueRatingDO doEntity = updateDoList.get(i);
        VenueRatingAggregate aggregate = toUpdate.get(i);
        aggregate.clearDirty();
        aggregate.assignVersion(doEntity.getVersion());
      }

      log.debug("批量更新载体评级完成：{} 条", toUpdate.size());
    }
  }

  @Override
  public void deleteById(VenueRatingId id) {
    if (id == null) {
      return;
    }
    venueRatingMapper.deleteById(id.value());
    log.debug("删除载体评级：id={}", id);
  }

  @Override
  public void deleteByVenueId(Long venueId) {
    if (venueId == null) {
      return;
    }
    int deleted =
        venueRatingMapper.delete(
            new LambdaQueryWrapper<VenueRatingDO>().eq(VenueRatingDO::getVenueId, venueId));
    log.debug("根据 venueId={} 删除载体评级：{} 条", venueId, deleted);
  }
}
