package com.patra.catalog.domain.port.repository;

import com.patra.catalog.domain.model.aggregate.VenueRatingAggregate;
import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.domain.model.vo.venue.VenueRatingId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// 载体评级聚合根仓储接口（领域层定义，基础设施层实现）。
///
/// **聚合边界**：
///
/// - VenueRatingAggregate：独立聚合根
/// - 业务唯一键：`(venueId, year, ratingSystem)`
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - 以聚合根为操作单位，保持一致性边界
///
/// **主要使用场景**：
///
/// - JCR/CAS/SCOPUS 评级数据导入
/// - 期刊评级查询（按载体、年份、评价体系）
///
/// @author Patra Lin
/// @since 0.6.0
public interface VenueRatingRepository {

  // ========== 基本查询 ==========

  /// 根据 ID 查找评级聚合根。
  ///
  /// @param id 聚合根 ID
  /// @return 聚合根（不存在时返回 empty）
  Optional<VenueRatingAggregate> findById(VenueRatingId id);

  /// 根据业务唯一键查找评级聚合根。
  ///
  /// 业务唯一键：`(venueId, year, ratingSystem)`
  ///
  /// @param venueId 载体 ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  /// @return 聚合根（不存在时返回 empty）
  Optional<VenueRatingAggregate> findByVenueIdAndYearAndRatingSystem(
      Long venueId, int year, RatingSystem ratingSystem);

  /// 根据载体 ID 查找所有评级。
  ///
  /// @param venueId 载体 ID
  /// @return 该载体的所有评级列表（永不为 null）
  List<VenueRatingAggregate> findByVenueId(Long venueId);

  /// 根据载体 ID 和评价体系查找评级。
  ///
  /// @param venueId 载体 ID
  /// @param ratingSystem 评价体系
  /// @return 该载体在指定评价体系下的所有年度评级（永不为 null）
  List<VenueRatingAggregate> findByVenueIdAndRatingSystem(Long venueId, RatingSystem ratingSystem);

  /// 根据载体 ID 和年份查找评级。
  ///
  /// @param venueId 载体 ID
  /// @param year 评级年份
  /// @return 该载体在指定年份的所有评价体系评级（永不为 null）
  List<VenueRatingAggregate> findByVenueIdAndYear(Long venueId, int year);

  // ========== 批量查询 ==========

  /// 批量根据载体 ID 查找评级。
  ///
  /// @param venueIds 载体 ID 集合（不能为 null，可以为空）
  /// @return Map，key 为 venueId，value 为该载体的评级列表（永不为 null）
  Map<Long, List<VenueRatingAggregate>> findByVenueIds(Collection<Long> venueIds);

  // ========== 保存操作 ==========

  /// 保存评级聚合根（INSERT 或 UPDATE）。
  ///
  /// - 如果聚合根是瞬态的（`isTransient() == true`），执行 INSERT
  /// - 如果聚合根已持久化且被修改（`isDirty() == true`），执行 UPDATE
  /// - 保存成功后，清除脏标记并分配 ID（如果是新建）
  ///
  /// @param aggregate 聚合根
  /// @return 持久化后的聚合根（包含 ID 和更新后的 version）
  VenueRatingAggregate save(VenueRatingAggregate aggregate);

  /// 批量保存评级聚合根。
  ///
  /// 内部区分新增和更新：
  /// - 新增（`isTransient() == true`）：批量 INSERT
  /// - 更新（`isDirty() == true`）：批量 UPDATE
  ///
  /// @param aggregates 聚合根列表（不能为 null，可以为空）
  void saveAll(List<VenueRatingAggregate> aggregates);

  // ========== 删除操作 ==========

  /// 根据 ID 删除评级（软删除）。
  ///
  /// @param id 聚合根 ID
  void deleteById(VenueRatingId id);

  /// 根据载体 ID 删除所有评级（软删除）。
  ///
  /// @param venueId 载体 ID
  void deleteByVenueId(Long venueId);
}
