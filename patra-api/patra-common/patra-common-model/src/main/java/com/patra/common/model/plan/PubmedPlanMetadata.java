package com.patra.common.model.plan;

/**
 * PubMed 特定的计划元数据
 *
 * <p>包含 PubMed ESearch API 返回的特定信息:
 * <ul>
 *   <li>webEnv - History Server 会话令牌
 *   <li>queryKey - 查询键,与 webEnv 配对使用
 * </ul>
 *
 * <p>业务约束:
 * <ul>
 *   <li>webEnv 和 queryKey 必须同时存在或同时为空
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
public class PubmedPlanMetadata extends PlanMetadata {

    private final String webEnv;
    private final String queryKey;

    public PubmedPlanMetadata(int totalCount, String webEnv, String queryKey) {
        super("pubmed", totalCount);

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
        return String.format("PubmedPlanMetadata[totalCount=%d, hasWebEnv=%b]",
                totalCount(), hasSessionToken());
    }
}
