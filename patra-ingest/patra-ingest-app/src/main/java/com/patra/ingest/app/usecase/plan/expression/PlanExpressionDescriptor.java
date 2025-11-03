package com.patra.ingest.app.usecase.plan.expression;

import com.patra.expr.Expr;
import java.util.Objects;

/**
 * Plan 表达式描述符(表达式三元组:结构 + 规范化快照 + 哈希)
 *
 * <p>由表达式构建器在 Plan 组装期间生成,以确保:
 *
 * <ul>
 *   <li>{@code expr}: 已编译的表达式树,可在运行时评估
 *   <li>{@code jsonSnapshot}: 从源 DSL/参数派生的规范化 JSON(null/解析错误时回退到 "{}")
 *   <li>{@code hash}: 从 {@code jsonSnapshot} 计算的稳定摘要(例如 SHA-256),用于幂等/版本/变更检测
 * </ul>
 *
 * <h4>不变式</h4>
 *
 * <ul>
 *   <li>{@code expr != null}
 *   <li>{@code jsonSnapshot != null}(构造函数回退到空对象)
 *   <li>{@code hash != null && !hash.isBlank()}
 * </ul>
 *
 * <h4>注意事项</h4>
 *
 * <ul>
 *   <li>我们不强制 hash 与 expr 直接相等(expr 可能包含运行时优化的结构);hash 仅绑定到 jsonSnapshot。
 *   <li>规范化保证 hash 对表面的 DSL 变化(字段顺序/空白)稳定,避免冗余 Plan。
 *   <li>用于调试时,考虑记录 hash 以及 jsonSnapshot 的受控脱敏子集。
 * </ul>
 *
 * <h4>线程安全</h4>
 *
 * <p>Record 不可变,可安全重用。
 *
 * @param expr 已编译的业务表达式(非 null)
 * @param jsonSnapshot 表达式的规范化 JSON 快照(null 时默认为 "{}")
 * @param hash 快照的哈希签名(非空)
 * @author linqibin
 * @since 0.1.0
 */
public record PlanExpressionDescriptor(Expr expr, String jsonSnapshot, String hash) {
  public PlanExpressionDescriptor {
    Objects.requireNonNull(expr, "expr 不能为 null");
    jsonSnapshot = jsonSnapshot == null ? "{}" : jsonSnapshot;
    Objects.requireNonNull(hash, "hash 不能为 null");
  }
}
