package dev.patra.catalog.infra.adapter.read;

/// 文献日期子表投影接口，对应 `PublicationDetailDao.findDatesByPublicationId` 查询结果。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationDateRow {

  /// 日期类型。
  ///
  /// @return 日期类型（如 published）
  String getType();

  /// 年。
  ///
  /// @return 年
  Integer getYear();

  /// 月。
  ///
  /// @return 月，可空
  Integer getMonth();

  /// 日。
  ///
  /// @return 日，可空
  Integer getDay();

  /// 精度。
  ///
  /// @return `year`、`month` 或 `day`
  String getPrecision();
}
