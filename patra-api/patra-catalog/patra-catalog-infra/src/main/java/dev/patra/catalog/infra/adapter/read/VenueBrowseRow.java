package dev.patra.catalog.infra.adapter.read;

import java.math.BigDecimal;

/// `VenueDao#findPortalVenueBrowsePage` native query 的 Spring Data 接口投影。
///
/// getter 名与 SQL 列别名一一对应（别名在 SQL 中加双引号保持 camelCase）。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueBrowseRow {

  Long getId();

  String getName();

  String getAbbr();

  String getCoverObjectKey();

  BigDecimal getImpactFactor();

  String getJcrQuartile();

  String getJcrSubject();

  String getCasMajorCategory();

  String getCasMajorQuartile();

  Boolean getCasIsTop();

  String getCountryCode();

  Integer getCitedByCount();

  Integer getFoundedYear();

  Boolean getIsOpenAccess();

  Boolean getIsInDoaj();

  String getIssnL();

  /// 主 NLM（身份锚，字符串保留前导零）。
  ///
  /// @return 主 NLM，可空
  String getNlmId();
}
