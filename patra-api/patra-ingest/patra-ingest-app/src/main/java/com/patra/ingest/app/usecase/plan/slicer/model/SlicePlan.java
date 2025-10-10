package com.patra.ingest.app.usecase.plan.slicer.model;

import com.patra.expr.Expr;
import java.util.Objects;

/**
 * 切片编排结果（Application Layer · DTO）。
 * <p>
 * 按切片策略产出的中间表示，尚未入库。包含：
 * <ul>
 *   <li>序号：用于保持生成顺序与后续任务序列关系（从 1 起递增，不允许 0 / 负数）</li>
 *   <li>签名素材：构造切片幂等签名的源 JSON（需 canonical 规范化）</li>
 *   <li>窗口规格 JSON：窗口边界参数（与签名素材可不同，前者偏幂等，后者偏执行）</li>
 *   <li>表达式：执行该切片的数据过滤 / 约束条件表达式（Expr 已编译）</li>
 * </ul>
 * </p>
 * <h4>不变式</h4>
 * <ul>
 *   <li>{@code sliceNo >= 1}</li>
 *   <li>{@code sliceSignatureSeed != null && !sliceSignatureSeed.isBlank()}</li>
 *   <li>{@code windowSpecJson != null && !windowSpecJson.isBlank()}</li>
 *   <li>{@code sliceExpr != null}</li>
 * </ul>
 * <h4>线程安全</h4>
 * <p>record 不可变，可安全跨线程传递。</p>
 * <h4>与下游关系</h4>
 * <p>后续会被转换为领域层 Slice 聚合或直接派生任务参数；签名素材将参与 SHA-256 或类似算法生成幂等键。</p>
 *
 * @param sliceNo            切片序号（从 1 开始递增）
 * @param sliceSignatureSeed 切片签名原始素材（规范化后的 JSON）
 * @param windowSpecJson     窗口边界规格的 canonical JSON 表达
 * @param sliceExpr          执行该切片的业务表达式
 *
 * @author linqibin
 * @since 0.1.0
 */
public record SlicePlan(int sliceNo,
                        String sliceSignatureSeed,
                        String windowSpecJson,
                        Expr sliceExpr) {
    public SlicePlan {
        // 校验关键字段，确保 downstream 能找到必要信息
        Objects.requireNonNull(sliceSignatureSeed, "sliceSignatureSeed不能为空");
        Objects.requireNonNull(windowSpecJson, "windowSpecJson不能为空");
        Objects.requireNonNull(sliceExpr, "sliceExpr不能为空");
    }
}
