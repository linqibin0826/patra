package com.patra.starter.provenance.internal.metadata;

import com.patra.common.enums.ProvenanceCode;

/// EPMC 特定的计划元数据
///
/// 包含 EPMC API 返回的特定信息:
///
/// - cursorMark - 游标标记,用于基于游标的分页
///
/// @author Patra Architecture Team
/// @since 0.1.0
public class EpmcPlanMetadata extends PlanMetadata {

  private final String cursorMark;

  public EpmcPlanMetadata(int totalCount, String cursorMark) {
    super(ProvenanceCode.EPMC.lowerCaseCode(), totalCount);
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
    return String.format(
        "EpmcPlanMetadata[totalCount=%d, hasCursorMark=%b]", totalCount(), hasSessionToken());
  }
}
