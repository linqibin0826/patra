package dev.patra.catalog.infra.adapter.read;

import java.math.BigDecimal;

/// `VenueDao#findVenueDetailById` native query 的 Spring Data 接口投影。
///
/// getter 名与 SQL 列别名一一对应（别名在 SQL 中加双引号保持 camelCase）。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueDetailRow {

  Long getId();

  String getTitle();

  String getAbbreviatedTitle();

  String getVenueType();

  String getIssnL();

  String getCountryCode();

  String getPrimaryLanguage();

  Integer getFoundedYear();

  String getCoverObjectKey();

  Boolean getIsOpenAccess();

  BigDecimal getImpactFactor();

  String getJcrQuartile();

  String getJcrSubject();

  String getCasMajorCategory();

  String getCasMajorQuartile();

  Boolean getCasIsTop();

  BigDecimal getCiteScore();

  Integer getHIndex();

  Integer getCitedByCount();

  Integer getWorksCount();

  String getFrequency();

  Boolean getMedlineIndexed();

  String getOaType();

  Integer getApcUsd();

  Boolean getIsInDoaj();
}
