package com.patra.catalog.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.patra.catalog.domain.model.entity.VenueIndexingHistory;
import com.patra.catalog.domain.model.entity.VenueMesh;
import com.patra.catalog.domain.model.entity.VenuePublicationStats;
import com.patra.catalog.domain.model.entity.VenueRelation;
import com.patra.catalog.domain.model.enums.CitationSubset;
import com.patra.catalog.domain.model.enums.IndexingTreatment;
import com.patra.catalog.domain.model.enums.VenueRelationType;
import com.patra.catalog.domain.port.repository.VenueSupplementRepository;
import com.patra.catalog.infra.persistence.entity.VenueIndexingHistoryDO;
import com.patra.catalog.infra.persistence.entity.VenueMeshDO;
import com.patra.catalog.infra.persistence.entity.VenuePublicationStatsDO;
import com.patra.catalog.infra.persistence.entity.VenueRelationDO;
import com.patra.catalog.infra.persistence.mapper.VenueIndexingHistoryMapper;
import com.patra.catalog.infra.persistence.mapper.VenueMeshMapper;
import com.patra.catalog.infra.persistence.mapper.VenuePublicationStatsMapper;
import com.patra.catalog.infra.persistence.mapper.VenueRelationMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// Venue 补充数据仓储实现。
///
/// **职责**：
///
/// - 管理从 VenueAggregate 分离出的独立实体集合
/// - 支持 OpenAlex 和 Serfile 批量数据导入
/// - 提供按 venueId 批量查询和替换能力
///
/// **性能优化**：
///
/// - 批量操作使用 `insertBatchSomeColumn` 提升写入效率
/// - 批量查询一次性加载，避免 N+1 问题
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class VenueSupplementRepositoryAdapter implements VenueSupplementRepository {

  private final VenuePublicationStatsMapper publicationStatsMapper;
  private final VenueMeshMapper meshMapper;
  private final VenueRelationMapper relationMapper;
  private final VenueIndexingHistoryMapper indexingHistoryMapper;

  // ========== 年度指标（OpenAlex 数据） ==========

  @Override
  public Map<Long, List<VenuePublicationStats>> findYearlyMetricsByVenueIds(
      Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenuePublicationStatsDO> doList =
        publicationStatsMapper.selectList(
            new LambdaQueryWrapper<VenuePublicationStatsDO>()
                .in(VenuePublicationStatsDO::getVenueId, venueIds));

    return doList.stream()
        .collect(
            Collectors.groupingBy(
                VenuePublicationStatsDO::getVenueId,
                Collectors.mapping(this::toPublicationStats, Collectors.toList())));
  }

  @Override
  public void replaceYearlyMetricsBatch(Map<Long, List<VenuePublicationStats>> metricsByVenueId) {
    if (metricsByVenueId == null || metricsByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(metricsByVenueId.keySet());

    // 删除旧数据
    publicationStatsMapper.delete(
        new LambdaQueryWrapper<VenuePublicationStatsDO>()
            .in(VenuePublicationStatsDO::getVenueId, venueIds));

    // 收集新数据
    List<VenuePublicationStatsDO> doList = new ArrayList<>();
    for (Map.Entry<Long, List<VenuePublicationStats>> entry : metricsByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenuePublicationStats stats : entry.getValue()) {
        doList.add(toPublicationStatsDO(stats, venueId));
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      publicationStatsMapper.insertBatchSomeColumn(doList);
      log.debug("批量插入年度指标 {} 条", doList.size());
    }
  }

  // ========== MeSH 主题词（Serfile 数据） ==========

  @Override
  public Map<Long, List<VenueMesh>> findMeshTermsByVenueIds(Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenueMeshDO> doList =
        meshMapper.selectList(
            new LambdaQueryWrapper<VenueMeshDO>().in(VenueMeshDO::getVenueId, venueIds));

    return doList.stream()
        .collect(
            Collectors.groupingBy(
                VenueMeshDO::getVenueId, Collectors.mapping(this::toMesh, Collectors.toList())));
  }

  @Override
  public void replaceMeshTermsBatch(Map<Long, List<VenueMesh>> meshTermsByVenueId) {
    if (meshTermsByVenueId == null || meshTermsByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(meshTermsByVenueId.keySet());

    // 删除旧数据
    meshMapper.delete(new LambdaQueryWrapper<VenueMeshDO>().in(VenueMeshDO::getVenueId, venueIds));

    // 收集新数据
    List<VenueMeshDO> doList = new ArrayList<>();
    for (Map.Entry<Long, List<VenueMesh>> entry : meshTermsByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenueMesh mesh : entry.getValue()) {
        doList.add(toMeshDO(mesh, venueId));
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      meshMapper.insertBatchSomeColumn(doList);
      log.debug("批量插入 MeSH 主题词 {} 条", doList.size());
    }
  }

  // ========== 期刊关联关系（Serfile 数据） ==========

  @Override
  public Map<Long, List<VenueRelation>> findRelationsByVenueIds(Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenueRelationDO> doList =
        relationMapper.selectList(
            new LambdaQueryWrapper<VenueRelationDO>().in(VenueRelationDO::getVenueId, venueIds));

    return doList.stream()
        .collect(
            Collectors.groupingBy(
                VenueRelationDO::getVenueId,
                Collectors.mapping(this::toRelation, Collectors.toList())));
  }

  @Override
  public void replaceRelationsBatch(Map<Long, List<VenueRelation>> relationsByVenueId) {
    if (relationsByVenueId == null || relationsByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(relationsByVenueId.keySet());

    // 删除旧数据
    relationMapper.delete(
        new LambdaQueryWrapper<VenueRelationDO>().in(VenueRelationDO::getVenueId, venueIds));

    // 收集新数据
    List<VenueRelationDO> doList = new ArrayList<>();
    for (Map.Entry<Long, List<VenueRelation>> entry : relationsByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenueRelation relation : entry.getValue()) {
        doList.add(toRelationDO(relation, venueId));
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      relationMapper.insertBatchSomeColumn(doList);
      log.debug("批量插入关联关系 {} 条", doList.size());
    }
  }

  // ========== 索引历史（Serfile 数据） ==========

  @Override
  public Map<Long, List<VenueIndexingHistory>> findIndexingHistoriesByVenueIds(
      Collection<Long> venueIds) {
    if (venueIds == null || venueIds.isEmpty()) {
      return Map.of();
    }

    List<VenueIndexingHistoryDO> doList =
        indexingHistoryMapper.selectList(
            new LambdaQueryWrapper<VenueIndexingHistoryDO>()
                .in(VenueIndexingHistoryDO::getVenueId, venueIds));

    return doList.stream()
        .collect(
            Collectors.groupingBy(
                VenueIndexingHistoryDO::getVenueId,
                Collectors.mapping(this::toIndexingHistory, Collectors.toList())));
  }

  @Override
  public void replaceIndexingHistoriesBatch(
      Map<Long, List<VenueIndexingHistory>> historiesByVenueId) {
    if (historiesByVenueId == null || historiesByVenueId.isEmpty()) {
      return;
    }

    List<Long> venueIds = new ArrayList<>(historiesByVenueId.keySet());

    // 删除旧数据
    indexingHistoryMapper.delete(
        new LambdaQueryWrapper<VenueIndexingHistoryDO>()
            .in(VenueIndexingHistoryDO::getVenueId, venueIds));

    // 收集新数据
    List<VenueIndexingHistoryDO> doList = new ArrayList<>();
    for (Map.Entry<Long, List<VenueIndexingHistory>> entry : historiesByVenueId.entrySet()) {
      Long venueId = entry.getKey();
      for (VenueIndexingHistory history : entry.getValue()) {
        doList.add(toIndexingHistoryDO(history, venueId));
      }
    }

    // 批量插入
    if (!doList.isEmpty()) {
      indexingHistoryMapper.insertBatchSomeColumn(doList);
      log.debug("批量插入索引历史 {} 条", doList.size());
    }
  }

  // ========== 便捷方法 ==========

  @Override
  public void replaceSerfileDataBatch(
      Map<Long, List<VenueMesh>> meshTermsByVenueId,
      Map<Long, List<VenueRelation>> relationsByVenueId,
      Map<Long, List<VenueIndexingHistory>> historiesByVenueId) {
    replaceMeshTermsBatch(meshTermsByVenueId);
    replaceRelationsBatch(relationsByVenueId);
    replaceIndexingHistoriesBatch(historiesByVenueId);
  }

  // ========== DO → Entity 转换方法 ==========

  private VenuePublicationStats toPublicationStats(VenuePublicationStatsDO doEntity) {
    return VenuePublicationStats.create(
        doEntity.getYear().intValue(),
        doEntity.getWorksCount() != null ? doEntity.getWorksCount() : 0,
        doEntity.getCitedByCount() != null ? doEntity.getCitedByCount() : 0,
        doEntity.getOaWorksCount());
  }

  private VenueMesh toMesh(VenueMeshDO doEntity) {
    return new VenueMesh(
        doEntity.getDescriptorName(),
        doEntity.getDescriptorUi(),
        Boolean.TRUE.equals(doEntity.getIsMajorTopic()),
        doEntity.getQualifierName(),
        doEntity.getQualifierUi());
  }

  private VenueRelation toRelation(VenueRelationDO doEntity) {
    VenueRelationType relationType = VenueRelationType.fromCodeOrNull(doEntity.getRelationType());
    if (relationType == null) {
      relationType = VenueRelationType.PRECEDING;
    }

    return new VenueRelation(
        doEntity.getRelatedVenueId(),
        doEntity.getRelatedNlmId(),
        doEntity.getRelatedTitle(),
        relationType,
        doEntity.getEffectiveDate(),
        doEntity.getNotes());
  }

  private VenueIndexingHistory toIndexingHistory(VenueIndexingHistoryDO doEntity) {
    IndexingTreatment treatment =
        doEntity.getIndexingTreatment() != null
            ? IndexingTreatment.fromCodeOrNull(doEntity.getIndexingTreatment())
            : null;
    CitationSubset subset =
        doEntity.getCitationSubset() != null
            ? CitationSubset.fromCodeOrNull(doEntity.getCitationSubset())
            : null;

    return new VenueIndexingHistory(
        doEntity.getIndexingSource(),
        Boolean.TRUE.equals(doEntity.getCurrentlyIndexed()),
        treatment,
        subset,
        doEntity.getStartYear(),
        doEntity.getStartVolume(),
        doEntity.getStartIssue(),
        doEntity.getEndYear(),
        doEntity.getEndVolume(),
        doEntity.getEndIssue());
  }

  // ========== Entity → DO 转换方法 ==========

  private VenuePublicationStatsDO toPublicationStatsDO(VenuePublicationStats entity, Long venueId) {
    VenuePublicationStatsDO doEntity = new VenuePublicationStatsDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setYear((short) entity.year());
    doEntity.setWorksCount(entity.worksCount());
    doEntity.setCitedByCount(entity.citedByCount());
    doEntity.setOaWorksCount(entity.oaWorksCount());
    return doEntity;
  }

  private VenueMeshDO toMeshDO(VenueMesh entity, Long venueId) {
    VenueMeshDO doEntity = new VenueMeshDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setDescriptorName(entity.descriptorName());
    doEntity.setDescriptorUi(entity.descriptorUi());
    doEntity.setIsMajorTopic(entity.isMajorTopic());
    doEntity.setQualifierName(entity.qualifierName());
    doEntity.setQualifierUi(entity.qualifierUi());
    return doEntity;
  }

  private VenueRelationDO toRelationDO(VenueRelation entity, Long venueId) {
    VenueRelationDO doEntity = new VenueRelationDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setRelatedVenueId(entity.relatedVenueId());
    doEntity.setRelatedNlmId(entity.relatedNlmId());
    doEntity.setRelatedTitle(entity.relatedTitle());
    doEntity.setRelationType(entity.relationType().getCode());
    doEntity.setEffectiveDate(entity.effectiveDate());
    doEntity.setNotes(entity.notes());
    return doEntity;
  }

  private VenueIndexingHistoryDO toIndexingHistoryDO(VenueIndexingHistory entity, Long venueId) {
    VenueIndexingHistoryDO doEntity = new VenueIndexingHistoryDO();
    doEntity.setId(IdWorker.getId());
    doEntity.setVenueId(venueId);
    doEntity.setIndexingSource(entity.indexingSource());
    doEntity.setCurrentlyIndexed(entity.currentlyIndexed());
    doEntity.setIndexingTreatment(
        entity.indexingTreatment() != null ? entity.indexingTreatment().getCode() : null);
    doEntity.setCitationSubset(
        entity.citationSubset() != null ? entity.citationSubset().getCode() : null);
    doEntity.setStartYear(entity.startYear());
    doEntity.setStartVolume(entity.startVolume());
    doEntity.setStartIssue(entity.startIssue());
    doEntity.setEndYear(entity.endYear());
    doEntity.setEndVolume(entity.endVolume());
    doEntity.setEndIssue(entity.endIssue());
    return doEntity;
  }
}
