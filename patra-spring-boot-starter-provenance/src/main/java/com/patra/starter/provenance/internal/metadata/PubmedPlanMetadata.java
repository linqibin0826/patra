package com.patra.starter.provenance.internal.metadata;

import dev.linqibin.patra.common.enums.ProvenanceCode;

/// PubMed 特定的计划元数据
///
/// 包含 PubMed ESearch API 返回的特定信息:
///
/// - webEnv - History Server 会话令牌
///   - queryKey - 查询键,与 webEnv 配对使用
///
/// 业务约束:
///
/// - webEnv 和 queryKey 必须同时存在或同时为空
///
/// @author linqibin
/// @since 0.1.0
public class PubmedPlanMetadata extends PlanMetadata {

  private final String webEnv;
  private final String queryKey;

  public PubmedPlanMetadata(int totalCount, String webEnv, String queryKey) {
    super(ProvenanceCode.PUBMED.lowerCaseCode(), totalCount);

    boolean hasWebEnv = webEnv != null && !webEnv.isBlank();
    boolean hasQueryKey = queryKey != null && !queryKey.isBlank();

    if (hasWebEnv != hasQueryKey) {
      throw new IllegalArgumentException("webEnv 和 queryKey 必须同时存在或同时为空");
    }

    this.webEnv = webEnv;
    this.queryKey = queryKey;
  }

  @Override
  public boolean hasSessionToken() {
    return webEnv != null && !webEnv.isBlank();
  }

  public String webEnv() {
    return webEnv;
  }

  public String queryKey() {
    return queryKey;
  }

  @Override
  public String toString() {
    return String.format(
        "PubmedPlanMetadata[totalCount=%d, hasWebEnv=%b]", totalCount(), hasSessionToken());
  }
}
