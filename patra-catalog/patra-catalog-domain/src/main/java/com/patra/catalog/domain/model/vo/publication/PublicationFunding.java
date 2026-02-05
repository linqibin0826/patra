package com.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;
import lombok.Builder;

/// 文献资助信息值对象。
///
/// 封装文献的资助/基金信息，支持多数据源融合。
///
/// **业务含义**：
///
/// 资助信息记录了支持研究的资金来源，包括：
/// - 资助机构（Funder）：如 NIH、NSFC、ERC 等
/// - 项目编号（Grant ID）：资助项目的唯一标识
/// - 数据来源（Provenance）：PubMed、OpenAlex、Crossref 等
///
/// **机构匹配策略**：
///
/// - `organizationId`：通过 FunderLookupPort 匹配后填充
/// - 原始字段（`funderNameRaw` 等）：保留原始数据，用于匹配失败时的人工审核
///
/// **多数据源融合**：
///
/// 不同数据源对同一资助信息的描述可能不同：
/// - PubMed：使用 NLM 标准化名称
/// - Crossref：使用 FundRef ID
/// - OpenAlex：使用 ROR ID
///
/// @param organizationId 资助机构 ID（匹配后填充，可能为 null）
/// @param grantId 项目编号/授权号
/// @param funderNameRaw 资助机构原始名称
/// @param funderAcronymRaw 资助机构缩写原始值
/// @param funderIdentifierRaw 资助机构标识符原始值
/// @param countryRaw 国家/地区原始值
/// @param fundingOrder 顺序
/// @param provenanceCode 数据来源代码
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationFunding(
    Long organizationId,
    String grantId,
    String funderNameRaw,
    String funderAcronymRaw,
    String funderIdentifierRaw,
    String countryRaw,
    Integer fundingOrder,
    String provenanceCode)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 判断是否已匹配到机构。
  ///
  /// @return true 如果已匹配到机构 ID
  public boolean hasMatchedOrganization() {
    return organizationId != null;
  }

  /// 判断是否有项目编号。
  ///
  /// @return true 如果有 grantId
  public boolean hasGrantId() {
    return StrUtil.isNotBlank(grantId);
  }

  /// 判断是否有原始机构名称。
  ///
  /// @return true 如果有 funderNameRaw
  public boolean hasFunderName() {
    return StrUtil.isNotBlank(funderNameRaw);
  }

  /// 判断是否有原始标识符。
  ///
  /// @return true 如果有 funderIdentifierRaw
  public boolean hasFunderIdentifier() {
    return StrUtil.isNotBlank(funderIdentifierRaw);
  }

  /// 判断是否有国家信息。
  ///
  /// @return true 如果有 countryRaw
  public boolean hasCountry() {
    return StrUtil.isNotBlank(countryRaw);
  }
}
