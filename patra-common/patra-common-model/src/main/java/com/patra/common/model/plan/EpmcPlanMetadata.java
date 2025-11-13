package com.patra.common.model.plan;

/**
 * EPMC 特定的计划元数据
 *
 * <p>包含 EPMC API 返回的特定信息:
 * <ul>
 *   <li>cursorMark - 游标标记,用于基于游标的分页
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.2.0
 */
public class EpmcPlanMetadata extends PlanMetadata {

    private final String cursorMark;

    public EpmcPlanMetadata(int totalCount, String cursorMark) {
        super("epmc", totalCount);
        this.cursorMark = cursorMark;
    }

    @Override
    public boolean hasSessionToken() {
        return cursorMark != null && !cursorMark.isBlank();
    }

    public String cursorMark() {
        return cursorMark;
    }

    @Override
    public String toString() {
        return String.format("EpmcPlanMetadata[totalCount=%d, hasCursorMark=%b]",
                totalCount(), hasSessionToken());
    }
}
