package com.patra.ingest.app.service.handler;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Exprs;
import com.patra.expr.TextMatch;
import com.patra.starter.expr.compiler.ExprCompiler;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class HarvestHandlerTest {

    @Resource
    private ExprCompiler exprCompiler;

    @Test
    void handle() {
        var expr = Exprs.and(List.of(
                Exprs.term("tiab", "heart failure", TextMatch.PHRASE))
        );

        var res = exprCompiler.compile(
                expr,
                ProvenanceCode.PUBMED,
                "search",
                new ExprCompiler.CompileOptions(true, 0, "UTC", true)
        );

        System.out.println(res);
    }
}
