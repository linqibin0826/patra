package com.patra.ingest.infra.integration.datasource.acl;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.fetch.FetchMetadata;
import com.patra.starter.provenance.internal.metadata.DoajPlanMetadata;
import com.patra.starter.provenance.internal.metadata.EpmcPlanMetadata;
import com.patra.starter.provenance.internal.metadata.PlanMetadata;
import com.patra.starter.provenance.internal.metadata.PubmedPlanMetadata;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 抓取元数据翻译器（防腐层）
 *
 * <p>将 Provenance Starter 的 {@link PlanMetadata} 翻译为 Ingest 领域的 {@link FetchMetadata}。
 *
 * <p><strong>翻译策略</strong>：
 *
 * <ul>
 *   <li>提取批次生成所需的核心信息（总记录数、数据源代码）
 *   <li>将数据源特定的状态令牌转换为通用的 Map 结构
 *   <li>屏蔽 Provenance 的实现细节（如 plannedAt、extensionMetadata）
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.3.0
 */
@Component
public class FetchMetadataTranslator {

  /**
   * 翻译 PlanMetadata 为 FetchMetadata
   *
   * @param planMetadata Provenance 的计划元数据
   * @return Ingest 的抓取元数据
   * @throws IllegalArgumentException 如果遇到未知的 PlanMetadata 类型
   */
  public FetchMetadata translate(PlanMetadata planMetadata) {
    // 空计划处理
    if (planMetadata.totalCount() == 0) {
      return FetchMetadata.empty(ProvenanceCode.parse(planMetadata.dataSourceType()));
    }

    // 根据具体类型翻译（使用模式匹配）
    return switch (planMetadata) {
      case PubmedPlanMetadata pubmed -> translatePubmed(pubmed);
      case DoajPlanMetadata doaj -> translateDoaj(doaj);
      case EpmcPlanMetadata epmc -> translateEpmc(epmc);
      default ->
          throw new IllegalArgumentException(
              "未知的 PlanMetadata 类型: " + planMetadata.getClass().getName());
    };
  }

  /** 翻译 PubMed 计划元数据 */
  private FetchMetadata translatePubmed(PubmedPlanMetadata pubmed) {
    return new SimpleFetchMetadata(
        pubmed.totalCount(),
        ProvenanceCode.parse(pubmed.dataSourceType()),
        extractPubmedStateToken(pubmed));
  }

  /** 提取 PubMed 状态令牌 */
  private Optional<Map<String, String>> extractPubmedStateToken(PubmedPlanMetadata pubmed) {
    if (!pubmed.hasSessionToken()) {
      return Optional.empty();
    }
    return Optional.of(
        Map.of(
            "webEnv", pubmed.webEnv(),
            "queryKey", pubmed.queryKey()));
  }

  /** 翻译 DOAJ 计划元数据 */
  private FetchMetadata translateDoaj(DoajPlanMetadata doaj) {
    return new SimpleFetchMetadata(
        doaj.totalCount(),
        ProvenanceCode.parse(doaj.dataSourceType()),
        extractDoajStateToken(doaj));
  }

  /** 提取 DOAJ 状态令牌（DOAJ 使用 scrollId） */
  private Optional<Map<String, String>> extractDoajStateToken(DoajPlanMetadata doaj) {
    if (!doaj.hasSessionToken()) {
      return Optional.empty();
    }
    return Optional.of(Map.of("cursorMark", doaj.scrollId()));
  }

  /** 翻译 EPMC 计划元数据 */
  private FetchMetadata translateEpmc(EpmcPlanMetadata epmc) {
    return new SimpleFetchMetadata(
        epmc.totalCount(),
        ProvenanceCode.parse(epmc.dataSourceType()),
        extractEpmcStateToken(epmc));
  }

  /** 提取 EPMC 状态令牌（EPMC 使用 cursorMark） */
  private Optional<Map<String, String>> extractEpmcStateToken(EpmcPlanMetadata epmc) {
    if (!epmc.hasSessionToken()) {
      return Optional.empty();
    }
    return Optional.of(Map.of("cursorMark", epmc.cursorMark()));
  }
}

/** 简单的 FetchMetadata 实现（包级私有） */
record SimpleFetchMetadata(
    int totalRecords, ProvenanceCode provenanceCode, Optional<Map<String, String>> stateToken)
    implements FetchMetadata {

  @Override
  public boolean hasStateToken() {
    return stateToken.isPresent();
  }
}
