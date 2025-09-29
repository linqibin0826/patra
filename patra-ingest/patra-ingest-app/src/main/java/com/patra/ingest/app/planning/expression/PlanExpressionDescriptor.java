package com.patra.ingest.app.planning.expression;

import com.patra.expr.Expr;
import java.util.Objects;

/**
 * 计划表达式描述符（表达式三元组：结构 + 快照 + 哈希）。
 * <p>
 * 由表达式构建器在计划装配阶段生成，确保：
 * <ul>
 *   <li>{@code expr}：运行期可直接求值的编译表达式树</li>
 *   <li>{@code jsonSnapshot}：来源原始 DSL/参数 canonical 归一化后的 JSON 字符串（空或解析失败时回退"{}"）</li>
 *   <li>{@code hash}：对 {@code jsonSnapshot} 进行稳定算法（如 SHA-256）计算的摘要，用于后续幂等 / 版本判定 / 变更检测</li>
 * </ul>
 * </p>
 * <h4>不变式</h4>
 * <ul>
 *   <li>{@code expr != null}</li>
 *   <li>{@code jsonSnapshot != null}（构造时回退为空对象表示）</li>
 *   <li>{@code hash != null && !hash.isBlank()}</li>
 * </ul>
 * <h4>语义提示</h4>
 * <ul>
 *   <li>hash 与 expr 之间不做直接一致性校验（expr 可能包含运行期优化结构）；hash 仅绑定 jsonSnapshot。</li>
 *   <li>当上游 DSL 变更只涉及字段顺序 / 空白字符，canonical 过程会保证 hash 稳定，从而避免重复计划。</li>
 *   <li>若需调试，建议打印 hash 与部分 jsonSnapshot（受控脱敏）。</li>
 * </ul>
 * <h4>线程安全</h4>
 * <p>record 不可变，可安全复用。</p>
 *
 * @param expr         编译后的业务表达式（非 null）
 * @param jsonSnapshot 表达式对应的 JSON 快照，默认"{}"（为空时回退）
 * @param hash         快照哈希签名（非空）
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PlanExpressionDescriptor(Expr expr, String jsonSnapshot, String hash) {
    public PlanExpressionDescriptor {
        Objects.requireNonNull(expr, "expr must not be null");
        jsonSnapshot = jsonSnapshot == null ? "{}" : jsonSnapshot;
        Objects.requireNonNull(hash, "hash must not be null");
    }
}
