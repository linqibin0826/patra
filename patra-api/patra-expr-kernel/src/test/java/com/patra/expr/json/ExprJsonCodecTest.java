package com.patra.expr.json;

import com.patra.expr.*;
import com.patra.expr.Atom.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExprJsonCodecTest {

    @Test
    @DisplayName("Round-trip: complex nested expression")
    void roundTrip_complex() {
        Expr expr = Exprs.and(List.of(
                Exprs.term("title", "heart failure", TextMatch.PHRASE),
                Exprs.or(List.of(
                        Exprs.rangeDate("published", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31")),
                        Exprs.not(Exprs.exists("retracted", true))
                )),
                Exprs.rangeNumber("score", new BigDecimal("0.8"), new BigDecimal("0.95"), true, false),
                Exprs.in("journal", List.of("NEJM", "Lancet"), true),
                Exprs.token("mesh", "MESH", "D012345"),
                Exprs.constTrue()
        ));

        String json = ExprJsonCodec.toJson(expr);
        Expr parsed = ExprJsonCodec.fromJson(json);

        assertNotNull(parsed);
        assertInstanceOf(And.class, parsed);
        // 再序列化一次进行幂等性校验（结构序列化稳定性）
        String json2 = ExprJsonCodec.toJson(parsed);
        assertEquals(json, json2, "Serialized JSON should be stable");
    }

    @Test
    @DisplayName("Serialize CONST true/false")
    void constSerialize() {
        assertTrue(ExprJsonCodec.toJson(Const.TRUE).contains("\"value\":true"));
        assertTrue(ExprJsonCodec.toJson(Const.FALSE).contains("\"value\":false"));
    }

    @Test
    @DisplayName("Atom TERM with case + match")
    void atomTerm() {
        Expr term = Exprs.term("title", "Heart", TextMatch.ANY, true);
        String json = ExprJsonCodec.toJson(term);
        assertTrue(json.contains("\"TERM\""));
        Expr back = ExprJsonCodec.fromJson(json);
        assertInstanceOf(Atom.class, back);
        Atom a = (Atom) back;
        assertEquals(Atom.Operator.TERM, a.operator());
        assertInstanceOf(TermValue.class, a.value());
        TermValue tv = (TermValue) a.value();
        assertEquals(CaseSensitivity.SENSITIVE, tv.caseSensitivity());
    }

    @Test
    @DisplayName("Range DATE/DATETIME/NUMBER variants")
    void rangeVariants() {
        Expr d = Exprs.rangeDate("d", LocalDate.parse("2024-01-01"), LocalDate.parse("2024-02-01"));
        Expr dt = Exprs.rangeDateTime("dt", Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"));
        Expr num = Exprs.rangeNumber("n", new BigDecimal("1.0"), new BigDecimal("2.5"));

        for (Expr e : List.of(d, dt, num)) {
            String json = ExprJsonCodec.toJson(e);
            Expr back = ExprJsonCodec.fromJson(json);
            assertEquals(json, ExprJsonCodec.toJson(back));
        }
    }

    @Test
    @DisplayName("EXISTS flag round trip")
    void existsFlag() {
        Expr e = Exprs.exists("retracted", false);
        String json = ExprJsonCodec.toJson(e);
        Expr b = ExprJsonCodec.fromJson(json);
        assertEquals(json, ExprJsonCodec.toJson(b));
    }
}
