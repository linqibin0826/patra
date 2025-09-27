package com.patra.expr.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.util.HashUtils;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import com.patra.common.json.JsonNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ExprCanonicalizer 的兼容性校验，确保输出与既有规范化策略保持一致。
 */
class ExprCanonicalizerTest {

    @Test
    void canonicalizeMatchesDefaultNormalizer() {
        Expr expr = Exprs.and(List.of(
                Exprs.term(" title ", " deep   learning ", TextMatch.PHRASE),
                Exprs.in("lang", List.of("en", "en", "zh")),
                Exprs.constTrue()
        ));
        ExprCanonicalSnapshot snapshot = ExprCanonicalizer.canonicalize(expr);

        JsonNormalizer normalizer = JsonNormalizer.withMapper(
                new ObjectMapper(),
                JsonNormalizer.Config.builder().build()
        );
        JsonNormalizer.Result expected = normalizer.normalize(Exprs.toJson(expr));

        assertEquals(expected.getCanonicalJson(), snapshot.canonicalJson());
        assertEquals(HashUtils.sha256Hex(expected.getHashMaterial()), snapshot.hash());
    }
}
