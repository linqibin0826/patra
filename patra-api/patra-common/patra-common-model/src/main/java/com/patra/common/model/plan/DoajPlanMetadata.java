package com.patra.common.model.plan;

/**
 * DOAJ 特定的计划元数据
 *
 * <p>包含 DOAJ API 返回的特定信息:
 * <ul>
 *   <li>scrollId - Elasticsearch Scroll ID
 *   <li>pageSize - 每页大小
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
public class DoajPlanMetadata extends PlanMetadata {

    private final String scrollId;
    private final int pageSize;

    public DoajPlanMetadata(int totalCount, String scrollId, int pageSize) {
        super("doaj", totalCount);
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize 必须 > 0");
        }
        this.scrollId = scrollId;
        this.pageSize = pageSize;
    }

    @Override
    public boolean hasSessionToken() {
        return scrollId != null && !scrollId.isBlank();
    }

    public String scrollId() {
        return scrollId;
    }

    public int pageSize() {
        return pageSize;
    }

    @Override
    public String toString() {
        return String.format("DoajPlanMetadata[totalCount=%d, pageSize=%d, hasScrollId=%b]",
                totalCount(), pageSize, hasSessionToken());
    }
}
