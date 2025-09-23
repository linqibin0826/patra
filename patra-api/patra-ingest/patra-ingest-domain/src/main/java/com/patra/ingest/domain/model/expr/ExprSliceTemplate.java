package com.patra.ingest.domain.model.expr;

import java.util.Map;

/**
 * 单个切片的表达式渲染模板。
 */
public record ExprSliceTemplate(String exprHash,
                                String exprSnapshotJson,
                                Map<String, Object> sliceParams) {
}
