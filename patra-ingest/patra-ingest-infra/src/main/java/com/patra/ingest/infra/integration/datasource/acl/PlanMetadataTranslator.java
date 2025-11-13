package com.patra.ingest.infra.integration.datasource.acl;

import com.patra.ingest.domain.model.vo.plan.BatchPlan;
import com.patra.starter.provenance.internal.metadata.DoajPlanMetadata;
import com.patra.starter.provenance.internal.metadata.EpmcPlanMetadata;
import com.patra.starter.provenance.internal.metadata.PlanMetadata;
import com.patra.starter.provenance.internal.metadata.PubmedPlanMetadata;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * PlanMetadata 翻译器（防腐层）
 *
 * <p>将 Provenance Starter 的 {@link PlanMetadata} 翻译为 Ingest 领域的 {@link BatchPlan}。
 *
 * <p><strong>翻译策略</strong>：
 * <ul>
 *   <li>提取批次生成所需的核心信息（总记录数、数据源代码）</li>
 *   <li>将数据源特定的状态令牌转换为通用的 Map 结构</li>
 *   <li>屏蔽 Provenance 的实现细节（如 plannedAt、extensionMetadata）</li>
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.3.0
 */
@Component
public class PlanMetadataTranslator {

    /**
     * 翻译 PlanMetadata 为 BatchPlan
     *
     * @param planMetadata Provenance 的计划元数据
     * @return Ingest 的批次计划
     * @throws IllegalArgumentException 如果遇到未知的 PlanMetadata 类型
     */
    public BatchPlan translate(PlanMetadata planMetadata) {
        // 空计划处理
        if (planMetadata.totalCount() == 0) {
            return BatchPlan.empty(planMetadata.dataSourceType());
        }

        // 根据具体类型翻译（使用模式匹配）
        return switch (planMetadata) {
            case PubmedPlanMetadata pubmed -> translatePubmed(pubmed);
            case DoajPlanMetadata doaj -> translateDoaj(doaj);
            case EpmcPlanMetadata epmc -> translateEpmc(epmc);
            default -> throw new IllegalArgumentException(
                "未知的 PlanMetadata 类型: " + planMetadata.getClass().getName()
            );
        };
    }

    /**
     * 翻译 PubMed 计划元数据
     */
    private BatchPlan translatePubmed(PubmedPlanMetadata pubmed) {
        return new SimpleBatchPlan(
            pubmed.totalCount(),
            pubmed.dataSourceType(),
            extractPubmedStateToken(pubmed)
        );
    }

    /**
     * 提取 PubMed 状态令牌
     */
    private Optional<Map<String, String>> extractPubmedStateToken(PubmedPlanMetadata pubmed) {
        if (!pubmed.hasSessionToken()) {
            return Optional.empty();
        }
        return Optional.of(Map.of(
            "webEnv", pubmed.webEnv(),
            "queryKey", pubmed.queryKey()
        ));
    }

    /**
     * 翻译 DOAJ 计划元数据
     */
    private BatchPlan translateDoaj(DoajPlanMetadata doaj) {
        return new SimpleBatchPlan(
            doaj.totalCount(),
            doaj.dataSourceType(),
            extractDoajStateToken(doaj)
        );
    }

    /**
     * 提取 DOAJ 状态令牌（DOAJ 使用 scrollId）
     */
    private Optional<Map<String, String>> extractDoajStateToken(DoajPlanMetadata doaj) {
        if (!doaj.hasSessionToken()) {
            return Optional.empty();
        }
        return Optional.of(Map.of("cursorMark", doaj.scrollId()));
    }

    /**
     * 翻译 EPMC 计划元数据
     */
    private BatchPlan translateEpmc(EpmcPlanMetadata epmc) {
        return new SimpleBatchPlan(
            epmc.totalCount(),
            epmc.dataSourceType(),
            extractEpmcStateToken(epmc)
        );
    }

    /**
     * 提取 EPMC 状态令牌（EPMC 使用 cursorMark）
     */
    private Optional<Map<String, String>> extractEpmcStateToken(EpmcPlanMetadata epmc) {
        if (!epmc.hasSessionToken()) {
            return Optional.empty();
        }
        return Optional.of(Map.of("cursorMark", epmc.cursorMark()));
    }
}

/**
 * 简单的 BatchPlan 实现（包级私有）
 */
record SimpleBatchPlan(
    int totalRecords,
    String dataSourceCode,
    Optional<Map<String, String>> stateToken
) implements BatchPlan {

    @Override
    public boolean hasStateToken() {
        return stateToken.isPresent();
    }
}
