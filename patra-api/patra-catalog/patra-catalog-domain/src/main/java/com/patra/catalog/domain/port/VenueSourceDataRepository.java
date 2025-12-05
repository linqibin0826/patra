package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.entity.VenueSourceData;
import com.patra.catalog.domain.model.enums.DataSourceCode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/// 载体数据源仓储接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 接口在 Domain 层定义，确保领域层独立
/// - 实现在 Infrastructure 层，遵循依赖倒置原则（DIP）
/// - VenueSourceData 是独立实体，按需加载，不属于 VenueAggregate 聚合
///
/// **主要使用场景**：
///
/// - 多数据源（OpenAlex/PubMed/DOAJ/Crossref/JCR）数据的存储与溯源
/// - 数据审计和版本追踪
/// - 按数据源查询原始数据
///
/// @author linqibin
/// @since 0.1.0
public interface VenueSourceDataRepository {

  /// 批量插入数据源记录。
  ///
  /// **适用场景**：多数据源数据的批量导入
  ///
  /// @param sourceDataList 数据源记录列表（不能为 null，可以为空）
  void insertAll(List<VenueSourceData> sourceDataList);

  /// 根据 Venue ID 查询所有数据源记录。
  ///
  /// @param venueId Venue ID
  /// @return 该 Venue 的所有数据源记录
  List<VenueSourceData> findByVenueId(Long venueId);

  /// 根据 Venue ID 和数据源代码查询单条记录。
  ///
  /// 每个 Venue + 数据源代码 组合唯一（uk_venue_source）。
  ///
  /// @param venueId Venue ID
  /// @param sourceCode 数据源代码
  /// @return 数据源记录（如果存在）
  Optional<VenueSourceData> findByVenueIdAndSourceCode(Long venueId, DataSourceCode sourceCode);

  /// 根据数据源代码批量查询。
  ///
  /// @param sourceCode 数据源代码
  /// @return 该数据源的所有记录
  List<VenueSourceData> findBySourceCode(DataSourceCode sourceCode);

  /// 保存或更新数据源记录（Upsert 语义）。
  ///
  /// 基于 venue_id + source_code 唯一约束进行 Upsert。
  ///
  /// @param sourceData 数据源记录
  void saveOrUpdate(VenueSourceData sourceData);

  /// 物理删除指定 Venue 的所有数据源记录。
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
