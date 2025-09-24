package com.patra.expr.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.patra.expr.*;
import com.patra.expr.Atom.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JSON 编解码：提供 Expr -> JSON 以及 JSON -> Expr 的 Jackson 支持。
 * 设计目标：
 * 1. 明确、稳定的结构，便于跨语言消费
 * 2. 无需在模型上增加 Jackson 注解，保持内核纯净
 * 3. 前向兼容：保留未知字段时忽略
 * <p>
 * JSON 结构示例：
 * {"type":"AND","children":[ ... ]}
 * {"type":"ATOM","field":"title","op":"TERM","value":{"kind":"TERM","text":"heart", "match":"ANY","case":"INSENSITIVE"}}
 * {"type":"CONST","value":true}
 */
public final class ExprJsonCodec {
    private ExprJsonCodec() {
    }

    /**
     * 注册 serializer / deserializer 的模块
     */
    public static com.fasterxml.jackson.databind.Module module() {
        com.fasterxml.jackson.databind.module.SimpleModule m = new com.fasterxml.jackson.databind.module.SimpleModule("expr-json-module");
        m.addSerializer(Expr.class, new ExprSerializer());
        m.addDeserializer(Expr.class, new ExprDeserializer());
        return m;
    }

    /**
     * 创建绑定了本模块的 ObjectMapper
     */
    public static ObjectMapper mapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(ExprJsonCodec.module());
        om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return om;
    }

    // ================= Serializer =================
    static class ExprSerializer extends JsonSerializer<Expr> implements ExprVisitor<java.lang.Void> {
        private JsonGenerator gen;

        @Override
        public void serialize(Expr value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            this.gen = gen;
            value.accept(this);
        }

        @Override
        public java.lang.Void visitAnd(And andExpr) {
            try {
                gen.writeStartObject();
                gen.writeStringField("type", "AND");
                gen.writeArrayFieldStart("children");
                for (Expr c : andExpr.children()) {
                    c.accept(this);
                }
                gen.writeEndArray();
                gen.writeEndObject();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

        @Override
        public java.lang.Void visitOr(Or orExpr) {
            try {
                gen.writeStartObject();
                gen.writeStringField("type", "OR");
                gen.writeArrayFieldStart("children");
                for (Expr c : orExpr.children()) {
                    c.accept(this);
                }
                gen.writeEndArray();
                gen.writeEndObject();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

        @Override
        public java.lang.Void visitNot(Not notExpr) {
            try {
                gen.writeStartObject();
                gen.writeStringField("type", "NOT");
                gen.writeFieldName("child");
                notExpr.child().accept(this);
                gen.writeEndObject();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

        @Override
        public java.lang.Void visitConst(Const constantExpr) {
            try {
                gen.writeStartObject();
                gen.writeStringField("type", "CONST");
                gen.writeBooleanField("value", constantExpr == Const.TRUE);
                gen.writeEndObject();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

        @Override
        public java.lang.Void visitAtom(Atom atomExpr) {
            try {
                gen.writeStartObject();
                gen.writeStringField("type", "ATOM");
                gen.writeStringField("field", atomExpr.fieldKey());
                gen.writeStringField("op", atomExpr.operator().name());
                gen.writeFieldName("value");
                writeAtomValue(atomExpr.value());
                gen.writeEndObject();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

        private void writeAtomValue(Atom.Value v) throws IOException {
            if (v instanceof TermValue tv) {
                gen.writeStartObject();
                gen.writeStringField("kind", "TERM");
                gen.writeStringField("text", tv.text());
                gen.writeStringField("match", tv.match().name());
                gen.writeStringField("case", tv.caseSensitivity().name());
                gen.writeEndObject();
            } else if (v instanceof InValues iv) {
                gen.writeStartObject();
                gen.writeStringField("kind", "IN");
                gen.writeArrayFieldStart("values");
                for (String s : iv.values()) gen.writeString(s);
                gen.writeEndArray();
                gen.writeStringField("case", iv.caseSensitivity().name());
                gen.writeEndObject();
            } else if (v instanceof RangeValue rv) {
                gen.writeStartObject();
                gen.writeStringField("kind", "RANGE");
                if (rv instanceof DateRange dr) {
                    gen.writeStringField("rangeType", "DATE");
                    if (dr.from() != null) gen.writeStringField("from", dr.from().toString());
                    if (dr.to() != null) gen.writeStringField("to", dr.to().toString());
                } else if (rv instanceof DateTimeRange dtr) {
                    gen.writeStringField("rangeType", "DATETIME");
                    if (dtr.from() != null) gen.writeStringField("from", dtr.from().toString());
                    if (dtr.to() != null) gen.writeStringField("to", dtr.to().toString());
                } else if (rv instanceof NumberRange nr) {
                    gen.writeStringField("rangeType", "NUMBER");
                    if (nr.from() != null) gen.writeStringField("from", nr.from().toPlainString());
                    if (nr.to() != null) gen.writeStringField("to", nr.to().toPlainString());
                }
                gen.writeStringField("fromBoundary", rv.fromBoundary().name());
                gen.writeStringField("toBoundary", rv.toBoundary().name());
                gen.writeEndObject();
            } else if (v instanceof ExistsFlag ef) {
                gen.writeStartObject();
                gen.writeStringField("kind", "EXISTS");
                gen.writeBooleanField("shouldExist", ef.shouldExist());
                gen.writeEndObject();
            } else if (v instanceof TokenValue tv) {
                gen.writeStartObject();
                gen.writeStringField("kind", "TOKEN");
                gen.writeStringField("tokenType", tv.tokenType());
                gen.writeStringField("tokenValue", tv.tokenValue());
                gen.writeEndObject();
            } else {
                throw new IllegalArgumentException("Unsupported atom value type: " + v.getClass());
            }
        }
    }

    // ================= Deserializer =================
    static class ExprDeserializer extends JsonDeserializer<Expr> {
        @Override
        public Expr deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == null) {
                p.nextToken();
            }
            if (p.currentToken() != JsonToken.START_OBJECT) {
                ctxt.reportInputMismatch(Expr.class, "Expected START_OBJECT, got %s", p.currentToken());
            }
            String type = null;
            List<Expr> children = null;
            Expr child = null;
            Boolean constValue = null;
            String field = null;
            String op = null;
            JsonNode valueNode = null;
            ObjectMapper mapper = mapper();
            JsonNode root = mapper.readTree(p);
            type = getText(root, "type");
            switch (type) {
                case "AND" -> {
                    JsonNode arr = root.get("children");
                    children = parseChildren(arr);
                    return new And(children);
                }
                case "OR" -> {
                    JsonNode arr = root.get("children");
                    children = parseChildren(arr);
                    return new Or(children);
                }
                case "NOT" -> {
                    JsonNode c = root.get("child");
                    if (c == null) ctxt.reportInputMismatch(Expr.class, "NOT missing child");
                    child = mapper.treeToValue(c, Expr.class);
                    return new Not(child);
                }
                case "CONST" -> {
                    constValue = root.get("value").asBoolean();
                    return constValue ? Const.TRUE : Const.FALSE;
                }
                case "ATOM" -> {
                    field = getText(root, "field");
                    op = getText(root, "op");
                    valueNode = root.get("value");
                    if (valueNode == null || !valueNode.isObject()) {
                        ctxt.reportInputMismatch(Expr.class, "ATOM missing value");
                    }
                    Atom.Value val = parseAtomValue(valueNode, ctxt);
                    Atom.Operator operator = Atom.Operator.valueOf(op);
                    return new Atom(field, operator, val);
                }
                default -> ctxt.reportInputMismatch(Expr.class, "Unknown type %s", type);
            }
            return null; // unreachable
        }

        private List<Expr> parseChildren(JsonNode arr) throws IOException {
            List<Expr> list = new ArrayList<>();
            if (arr != null && arr.isArray()) {
                for (JsonNode n : arr) {
                    list.add(mapper().treeToValue(n, Expr.class));
                }
            }
            return list;
        }

        private Atom.Value parseAtomValue(JsonNode node, DeserializationContext ctxt) throws IOException {
            String kind = getText(node, "kind");
            return switch (kind) {
                case "TERM" -> new TermValue(getText(node, "text"), TextMatch.valueOf(getText(node, "match")),
                        CaseSensitivity.valueOf(getText(node, "case")));
                case "IN" -> {
                    List<String> vals = new ArrayList<>();
                    JsonNode vs = node.get("values");
                    if (vs != null && vs.isArray()) {
                        vs.forEach(v -> vals.add(v.asText()));
                    }
                    CaseSensitivity cs = CaseSensitivity.valueOf(getText(node, "case"));
                    yield new InValues(vals, cs);
                }
                case "RANGE" -> parseRange(node, ctxt);
                case "EXISTS" -> new ExistsFlag(node.get("shouldExist").asBoolean());
                case "TOKEN" -> new TokenValue(getText(node, "tokenType"), getText(node, "tokenValue"));
                default -> throw new IllegalArgumentException("Unknown atom value kind: " + kind);
            };
        }

        private Atom.Value parseRange(JsonNode node, DeserializationContext ctxt) {
            String rangeType = getText(node, "rangeType");
            Atom.RangeValue.Boundary fb = Atom.RangeValue.Boundary.valueOf(getText(node, "fromBoundary"));
            Atom.RangeValue.Boundary tb = Atom.RangeValue.Boundary.valueOf(getText(node, "toBoundary"));
            JsonNode fromN = node.get("from");
            JsonNode toN = node.get("to");
            return switch (rangeType) {
                case "DATE" -> new Atom.DateRange(fromN == null ? null : LocalDate.parse(fromN.asText()),
                        toN == null ? null : LocalDate.parse(toN.asText()), fb, tb);
                case "DATETIME" -> new Atom.DateTimeRange(fromN == null ? null : Instant.parse(fromN.asText()),
                        toN == null ? null : Instant.parse(toN.asText()), fb, tb);
                case "NUMBER" -> new Atom.NumberRange(fromN == null ? null : new BigDecimal(fromN.asText()),
                        toN == null ? null : new BigDecimal(toN.asText()), fb, tb);
                default -> throw new IllegalArgumentException("Unknown rangeType: " + rangeType);
            };
        }

        private String getText(JsonNode node, String field) {
            JsonNode n = node.get(field);
            if (n == null || n.isNull()) throw new IllegalArgumentException("Missing field: " + field);
            return n.asText();
        }
    }

    // =============== 公共 API ===============
    public static String toJson(Expr expr) {
        try {
            return mapper().writeValueAsString(expr);
        } catch (IOException e) {
            throw new RuntimeException("Serialize expr failed", e);
        }
    }

    public static Expr fromJson(String json) {
        Objects.requireNonNull(json, "json");
        try {
            return mapper().readValue(json, Expr.class);
        } catch (IOException e) {
            throw new RuntimeException("Deserialize expr failed", e);
        }
    }
}
