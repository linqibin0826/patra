package com.patra.catalog.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.domain.port.repository.VenueInstanceRepository;
import com.patra.catalog.infra.persistence.converter.VenueInstanceConverter;
import com.patra.catalog.infra.persistence.entity.VenueInstanceDO;
import com.patra.catalog.infra.persistence.mapper.VenueInstanceMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 载体实例聚合根仓储实现。
///
/// **职责**：
///
/// 实现 `VenueInstanceRepository` 接口，提供载体实例的持久化操作。
///
/// **数据访问**：
///
/// - 使用 MyBatis-Plus 进行数据库操作
/// - 批量插入使用 `Db.saveBatch()` 优化性能
/// - 使用 `VenueInstanceConverter` 进行 DO ↔ 聚合根转换
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class VenueInstanceRepositoryAdapter implements VenueInstanceRepository {

  private final VenueInstanceMapper venueInstanceMapper;
  private final VenueInstanceConverter venueInstanceConverter;

  @Override
  public Optional<VenueInstanceAggregate> findById(Long id) {
    VenueInstanceDO doEntity = venueInstanceMapper.selectById(id);
    return Optional.ofNullable(venueInstanceConverter.toAggregate(doEntity));
  }

  @Override
  public Map<Long, List<VenueInstanceAggregate>> findByVenueIds(Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenueInstanceDO> doList =
        venueInstanceMapper.selectList(
            new LambdaQueryWrapper<VenueInstanceDO>().in(VenueInstanceDO::getVenueId, venueIds));

    Map<Long, List<VenueInstanceAggregate>> result = new HashMap<>();
    for (VenueInstanceDO doEntity : doList) {
      VenueInstanceAggregate aggregate = venueInstanceConverter.toAggregate(doEntity);
      result.computeIfAbsent(doEntity.getVenueId(), k -> new ArrayList<>()).add(aggregate);
    }
    return result;
  }

  @Override
  public Optional<VenueInstanceAggregate> findJournalInstance(
      Long venueId, String volume, String issue, Integer publicationYear) {
    LambdaQueryWrapper<VenueInstanceDO> query =
        new LambdaQueryWrapper<VenueInstanceDO>()
            .eq(VenueInstanceDO::getVenueId, venueId)
            .eq(VenueInstanceDO::getPublicationYear, publicationYear);

    // volume 和 issue 可能为 null，需要特殊处理
    if (volume != null) {
      query.eq(VenueInstanceDO::getVolume, volume);
    } else {
      query.isNull(VenueInstanceDO::getVolume);
    }

    if (issue != null) {
      query.eq(VenueInstanceDO::getIssue, issue);
    } else {
      query.isNull(VenueInstanceDO::getIssue);
    }

    VenueInstanceDO doEntity = venueInstanceMapper.selectOne(query);
    return Optional.ofNullable(venueInstanceConverter.toAggregate(doEntity));
  }

  @Override
  public Optional<VenueInstanceAggregate> findBookInstance(
      Long venueId, String edition, Integer publicationYear) {
    LambdaQueryWrapper<VenueInstanceDO> query =
        new LambdaQueryWrapper<VenueInstanceDO>()
            .eq(VenueInstanceDO::getVenueId, venueId)
            .eq(VenueInstanceDO::getPublicationYear, publicationYear);

    if (edition != null) {
      query.eq(VenueInstanceDO::getEdition, edition);
    } else {
      query.isNull(VenueInstanceDO::getEdition);
    }

    VenueInstanceDO doEntity = venueInstanceMapper.selectOne(query);
    return Optional.ofNullable(venueInstanceConverter.toAggregate(doEntity));
  }

  @Override
  public Optional<VenueInstanceAggregate> findConferenceInstance(
      Long venueId, String conferenceName, Integer publicationYear) {
    LambdaQueryWrapper<VenueInstanceDO> query =
        new LambdaQueryWrapper<VenueInstanceDO>()
            .eq(VenueInstanceDO::getVenueId, venueId)
            .eq(VenueInstanceDO::getPublicationYear, publicationYear);

    if (conferenceName != null) {
      query.eq(VenueInstanceDO::getConferenceName, conferenceName);
    } else {
      query.isNull(VenueInstanceDO::getConferenceName);
    }

    VenueInstanceDO doEntity = venueInstanceMapper.selectOne(query);
    return Optional.ofNullable(venueInstanceConverter.toAggregate(doEntity));
  }

  @Override
  public void save(VenueInstanceAggregate instance) {
    VenueInstanceDO doEntity = venueInstanceConverter.toDO(instance);

    if (instance.getId() == null) {
      // 新建
      venueInstanceMapper.insert(doEntity);
      instance.assignId(doEntity.getId());
    } else {
      // 更新
      venueInstanceMapper.updateById(doEntity);
    }
  }

  @Override
  public void insertAll(List<VenueInstanceAggregate> instances) {
    if (instances == null || instances.isEmpty()) {
      return;
    }

    List<VenueInstanceDO> doList = instances.stream().map(venueInstanceConverter::toDO).toList();

    Db.saveBatch(doList);

    // ID 回填
    for (int i = 0; i < instances.size(); i++) {
      instances.get(i).assignId(doList.get(i).getId());
    }

    log.debug("批量插入载体实例完成：{} 条", instances.size());
  }

  @Override
  public void updateBatch(List<VenueInstanceAggregate> instances) {
    if (instances == null || instances.isEmpty()) {
      return;
    }

    List<VenueInstanceDO> doList = instances.stream().map(venueInstanceConverter::toDO).toList();

    Db.updateBatchById(doList);
    log.debug("批量更新载体实例完成：{} 条", instances.size());
  }

  @Override
  public boolean deleteById(Long id) {
    int deleted = venueInstanceMapper.deleteById(id);
    return deleted > 0;
  }

  @Override
  public int deleteByVenueId(Long venueId) {
    return venueInstanceMapper.delete(
        new LambdaQueryWrapper<VenueInstanceDO>().eq(VenueInstanceDO::getVenueId, venueId));
  }
}
