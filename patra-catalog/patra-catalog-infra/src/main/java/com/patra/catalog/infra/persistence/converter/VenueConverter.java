package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.port.VenueRepository.VenueData;
import com.patra.catalog.domain.port.VenueRepository.VenueIdentifierData;
import com.patra.catalog.domain.port.VenueRepository.VenueMetricsData;
import com.patra.catalog.infra.persistence.entity.VenueDO;
import com.patra.catalog.infra.persistence.entity.VenueIdentifierDO;
import com.patra.catalog.infra.persistence.entity.VenueMetricsDO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 载体聚合根持久化转换器。
///
/// **职责**：
///
/// - VenueDO ↔ VenueData 双向转换
/// - VenueIdentifierDO ↔ VenueIdentifierData 双向转换
/// - VenueMetricsDO ↔ VenueMetricsData 双向转换
///
/// **设计说明**：
///
/// - 使用 `unmappedTargetPolicy = IGNORE` 忽略 DO 中未映射到 Data 的字段
///   （如 alternateTitles、societies 等扩展字段）
/// - VenueMetricsDO.year 为 Short 类型，VenueMetricsData.year 为 int 类型，
///   MapStruct 自动处理类型转换
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VenueConverter {

  // ========================================
  // Venue 转换
  // ========================================

  /// 将数据库实体转换为数据传输对象。
  ///
  /// @param entity 数据库实体
  /// @return 数据传输对象
  VenueData toVenueData(VenueDO entity);

  /// 将数据传输对象转换为数据库实体。
  ///
  /// @param data 数据传输对象
  /// @return 数据库实体
  VenueDO toVenueDO(VenueData data);

  /// 批量转换：DO 列表 → Data 列表。
  ///
  /// @param entities 数据库实体列表
  /// @return 数据传输对象列表
  List<VenueData> toVenueDataList(List<VenueDO> entities);

  /// 批量转换：Data 列表 → DO 列表。
  ///
  /// @param dataList 数据传输对象列表
  /// @return 数据库实体列表
  List<VenueDO> toVenueDOList(List<VenueData> dataList);

  // ========================================
  // VenueIdentifier 转换
  // ========================================

  /// 将标识符实体转换为数据传输对象。
  ///
  /// @param entity 数据库实体
  /// @return 数据传输对象
  VenueIdentifierData toIdentifierData(VenueIdentifierDO entity);

  /// 将标识符数据传输对象转换为数据库实体。
  ///
  /// @param data 数据传输对象
  /// @return 数据库实体
  VenueIdentifierDO toIdentifierDO(VenueIdentifierData data);

  /// 批量转换：标识符 DO 列表 → Data 列表。
  ///
  /// @param entities 数据库实体列表
  /// @return 数据传输对象列表
  List<VenueIdentifierData> toIdentifierDataList(List<VenueIdentifierDO> entities);

  /// 批量转换：标识符 Data 列表 → DO 列表。
  ///
  /// @param dataList 数据传输对象列表
  /// @return 数据库实体列表
  List<VenueIdentifierDO> toIdentifierDOList(List<VenueIdentifierData> dataList);

  // ========================================
  // VenueMetrics 转换
  // ========================================

  /// 将年度指标实体转换为数据传输对象。
  ///
  /// @param entity 数据库实体
  /// @return 数据传输对象
  VenueMetricsData toMetricsData(VenueMetricsDO entity);

  /// 将年度指标数据传输对象转换为数据库实体。
  ///
  /// **类型转换**：`int year` → `Short year`
  ///
  /// @param data 数据传输对象
  /// @return 数据库实体
  @Mapping(target = "year", expression = "java((short) data.year())")
  VenueMetricsDO toMetricsDO(VenueMetricsData data);

  /// 批量转换：年度指标 DO 列表 → Data 列表。
  ///
  /// @param entities 数据库实体列表
  /// @return 数据传输对象列表
  List<VenueMetricsData> toMetricsDataList(List<VenueMetricsDO> entities);

  /// 批量转换：年度指标 Data 列表 → DO 列表。
  ///
  /// @param dataList 数据传输对象列表
  /// @return 数据库实体列表
  List<VenueMetricsDO> toMetricsDOList(List<VenueMetricsData> dataList);
}
