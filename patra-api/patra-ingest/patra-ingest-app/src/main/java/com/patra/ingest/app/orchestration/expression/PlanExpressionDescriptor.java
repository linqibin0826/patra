package com.patra.ingest.app.orchestration.expression;

import com.patra.expr.Expr;
import java.util.Objects;

/**
 * 计划表达式描述符。
 * <p>
 * 用于封装应用层在编排阶段生成的业务表达式，保持 Expr 对象、本地 JSON 快照与哈希签名三者的一致性，
 * 便于在切片、持久化等后续环节复用。
 * </p>
 *
 * @param expr         编译后的业务表达式
 * @param jsonSnapshot 表达式对应的 JSON 快照，默认"{}"
 * @param hash         表达式快照的哈希签名，用于幂等校验
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
