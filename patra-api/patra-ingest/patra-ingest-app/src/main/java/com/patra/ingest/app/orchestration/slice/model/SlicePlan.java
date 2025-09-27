package com.patra.ingest.app.orchestration.slice.model;

import com.patra.expr.Expr;
import java.time.Instant;
import java.util.Objects;

/**
 * 切片编排结果（Application Layer · DTO）。
 * <p>
 * 该记录封装了持久化前的切片信息，包含幂等签名、规划表达式以及时间范围，用于后续转换为领域聚合或持久化对象。
 * </p>
 *
 * @param sequence           切片序号（从 1 开始递增）
 * @param sliceSignatureSeed 切片签名原始素材（规范化后的 JSON）
 * @param sliceSpecJson      切片规格的 canonical JSON 表达
 * @param sliceExpr          执行该切片的业务表达式
 * @param windowFrom         切片窗口起点（可能为空，表示无下界）
 * @param windowTo           切片窗口终点（可能为空，表示无上界）
 *
 * @author linqibin
 * @since 0.1.0
 */
public record SlicePlan(int sequence,
                        String sliceSignatureSeed,
                        String sliceSpecJson,
                        Expr sliceExpr,
                        Instant windowFrom,
                        Instant windowTo) {
    public SlicePlan {
        // 校验关键字段，确保 downstream 能找到必要信息
        Objects.requireNonNull(sliceSignatureSeed, "sliceSignatureSeed不能为空");
        Objects.requireNonNull(sliceSpecJson, "sliceSpecJson不能为空");
        Objects.requireNonNull(sliceExpr, "sliceExpr不能为空");
    }
}
