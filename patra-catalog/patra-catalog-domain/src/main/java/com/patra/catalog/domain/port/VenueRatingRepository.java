package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.entity.VenueRating;
import com.patra.catalog.domain.model.enums.RatingSystem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/// 载体评级仓储接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - VenueRating 是独立实体，按需加载，不属于 VenueAggregate 聚合
///
/// **主要使用场景**：
///
/// - JCR/中科院分区/Scopus 评级数据的批量导入
/// - 按 Venue 查询历年评级数据
/// - 按年份和评价体系查询评级数据
///
/// @author linqibin
/// @since 0.1.0
public interface VenueRatingRepository {

  /// 批量插入评级记录。
  ///
  /// **适用场景**：JCR/中科院分区等评级数据的批量导入
  ///
  /// @param ratings 评级记录列表（不能为 null，可以为空）
  void insertAll(List<VenueRating> ratings);

  /// 根据 Venue ID 查询所有评级记录。
  ///
  /// @param venueId Venue ID
  /// @return 该 Venue 的所有评级记录（按年份降序）
  List<VenueRating> findByVenueId(Long venueId);

  /// 根据 Venue ID 和年份查询评级记录。
  ///
  /// @param venueId Venue ID
  /// @param year 评级年份
  /// @return 该 Venue 指定年份的所有评级记录（不同评价体系）
  List<VenueRating> findByVenueIdAndYear(Long venueId, int year);

  /// 根据 Venue ID、年份和评价体系查询单条评级记录。
  ///
  /// @param venueId Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  /// @return 评级记录（如果存在）
  Optional<VenueRating> findByVenueIdAndYearAndSystem(
      Long venueId, int year, RatingSystem ratingSystem);

  /// 查询 Venue 的最新评级记录。
  ///
  /// @param venueId Venue ID
  /// @param ratingSystem 评价体系
  /// @return 该 Venue 最新的评级记录（如果存在）
  Optional<VenueRating> findLatestByVenueIdAndSystem(Long venueId, RatingSystem ratingSystem);

  /// 物理删除指定 Venue 的所有评级记录。
  ///
  /// **注意**：用于级联删除场景，绕过逻辑删除。
  ///
  /// @param venueIds Venue ID 集合
  /// @return 删除的记录数
  int deleteByVenueIds(Collection<Long> venueIds);

  /// 清空表（TRUNCATE TABLE）。
  ///
  /// **警告**：DDL 操作，会隐式提交事务，无法回滚。仅用于开发/测试环境。
  void truncateTable();
}
