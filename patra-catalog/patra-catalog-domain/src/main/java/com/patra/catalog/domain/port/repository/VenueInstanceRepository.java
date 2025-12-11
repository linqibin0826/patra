package com.patra.catalog.domain.port.repository;

import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// 载体实例聚合根仓储接口（领域层定义，基础设施层实现）。
///
/// **聚合边界**：
///
/// VenueInstanceAggregate 是独立聚合根，通过 `venueId` 关联 VenueAggregate。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 以聚合根为操作单位，保持一致性边界
///
/// **主要使用场景**：
///
/// - 文献关联时查找或创建载体实例（期刊卷期、书籍版次、会议届次）
/// - 批量导入时的实例数据管理
///
/// @author linqibin
/// @since 0.1.0
public interface VenueInstanceRepository {

  /// 根据 ID 查找载体实例。
  ///
  /// @param id 实例 ID
  /// @return 载体实例聚合根，如果不存在返回 empty
  Optional<VenueInstanceAggregate> findById(Long id);

  /// 根据 venueId 批量查找载体实例。
  ///
  /// @param venueIds Venue ID 集合
  /// @return Map，key 为 venueId，value 为该 Venue 的实例列表
  Map<Long, List<VenueInstanceAggregate>> findByVenueIds(Collection<Long> venueIds);

  /// 查找期刊的特定卷期实例。
  ///
  /// 用于避免重复创建相同卷期的实例。
  ///
  /// @param venueId 载体 ID
  /// @param volume 卷号（可为 null）
  /// @param issue 期号（可为 null）
  /// @param publicationYear 出版年份
  /// @return 匹配的实例，如果不存在返回 empty
  Optional<VenueInstanceAggregate> findJournalInstance(
      Long venueId, String volume, String issue, Integer publicationYear);

  /// 查找书籍的特定版次实例。
  ///
  /// @param venueId 载体 ID
  /// @param edition 版次
  /// @param publicationYear 出版年份
  /// @return 匹配的实例，如果不存在返回 empty
  Optional<VenueInstanceAggregate> findBookInstance(
      Long venueId, String edition, Integer publicationYear);

  /// 查找会议的特定届次实例。
  ///
  /// @param venueId 载体 ID
  /// @param conferenceName 会议名称
  /// @param publicationYear 出版年份
  /// @return 匹配的实例，如果不存在返回 empty
  Optional<VenueInstanceAggregate> findConferenceInstance(
      Long venueId, String conferenceName, Integer publicationYear);

  /// 保存单个载体实例（插入或更新）。
  ///
  /// @param instance 载体实例聚合根
  void save(VenueInstanceAggregate instance);

  /// 批量插入载体实例。
  ///
  /// **适用场景**：批量导入时的实例数据初始化
  ///
  /// @param instances 实例列表（不能为 null，可以为空）
  void insertAll(List<VenueInstanceAggregate> instances);

  /// 批量更新载体实例。
  ///
  /// @param instances 实例列表（不能为 null，可以为空）
  void updateBatch(List<VenueInstanceAggregate> instances);

  /// 根据 ID 删除载体实例。
  ///
  /// @param id 实例 ID
  /// @return 是否成功删除
  boolean deleteById(Long id);

  /// 根据 venueId 删除所有关联实例。
  ///
  /// @param venueId 载体 ID
  /// @return 删除的实例数量
  int deleteByVenueId(Long venueId);
}
