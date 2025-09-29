package com.patra.ingest.app.planning.slice.model;

import com.patra.ingest.app.planning.expression.PlanExpressionDescriptor;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import java.util.Objects;

/**
 * 切片策略执行上下文（Application 级输入模型）。
 * <p>
 * 由计划装配阶段集中构建，确保在进入 {@code SlicePlanner} 时：
 * <ul>
 *   <li>触发规范（模式 / 步长 / 操作）已被归一化</li>
 *   <li>计划窗口已最终确定（UTC，半开区间语义 [from, to)）</li>
 *   <li>业务表达式已编译（含 JSON 快照与哈希，便于幂等与调试）</li>
 *   <li>来源配置快照（若存在）与表达式逻辑一致</li>
 * </ul>
 * </p>
 * <h4>语义说明</h4>
 * <ul>
 *   <li><b>norm</b>：保证非 null；封装触发模式（如 HARVEST / BACKFILL / UPDATE）、切片步长、优先级等。</li>
 *   <li><b>window</b>：非 null；半开区间；若 window.to() 为空表示“开放式上界”。</li>
 *   <li><b>planExpression</b>：非 null；其哈希在后续切片签名/任务幂等中可用。</li>
 *   <li><b>configSnapshot</b>：可为 null；表示某些测试或临时场景未携带来源配置（策略需对 null 做降级处理）。</li>
 * </ul>
 * <h4>不变式</h4>
 * <ul>
 *   <li>{@code norm != null}</li>
 *   <li>{@code window != null}</li>
 *   <li>{@code planExpression != null}</li>
 * </ul>
 * <h4>线程安全</h4>
 * <p>record 不可变；内部不暴露可变集合，安全跨线程。</p>
 * <h4>扩展点提示</h4>
 * <p>未来若要添加租约 / 限流信息，可通过新增字段并保持 record 向后兼容（或迁移为类）。</p>
 *
 * @param norm            触发规范，包含计划模式、步长等信息（必填）
 * @param window          计划窗口，统一采用 UTC 半开区间（必填）
 * @param planExpression  计划表达式描述，含 Expr、快照 JSON 与哈希（必填）
 * @param configSnapshot  来源配置快照，可选（为空时策略应回退至内置默认）
 *
 * @author linqibin
 * @since 0.1.0
 */
public record SlicePlanningContext(PlanTriggerNorm norm,
                                   PlannerWindow window,
                                   PlanExpressionDescriptor planExpression,
                                   ProvenanceConfigSnapshot configSnapshot) {
    public SlicePlanningContext {
        Objects.requireNonNull(norm, "norm不能为空");
        Objects.requireNonNull(window, "window不能为空");
        Objects.requireNonNull(planExpression, "planExpression不能为空");
        // configSnapshot 允许为空（某些测试或调用场景下可能未提供）
    }
}
