package com.patra.expr.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.patra.expr.*;
import com.patra.expr.Atom.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// 表达式模型的 Jackson 编解码器。
/// 
/// 提供双向 JSON 转换,而不会污染模型类的 Jackson 注解。设计目标:
/// 
/// - 暴露清晰、稳定的结构,适合跨语言使用者
///   - 保持内核不依赖框架注解
///   - 通过忽略未知属性保持向前兼容
/// 
/// 示例 JSON 格式:
/// 
/// ```
/// 
/// {"type":"AND","children":[ ... ]}
/// {"type":"ATOM","field":"title","op":"TERM","value":{"kind":"TERM","text":"heart","match":"ANY","case":"INSENSITIVE"}}
/// {"type":"CONST","value":true}
/// 
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
public final class ExprJsonCodec {
  private ExprJsonCodec() {}

  /// 创建一个模块,注册 {@link Expr} 的序列化器和反序列化器。
/// 
/// @return 模块实例
  public static com.fasterxml.jackson.databind.Module module() {
    SimpleModule m = new SimpleModule("expr-json-module");
    m.addSerializer(Expr.class, new ExprSerializer());
    m.addDeserializer(Expr.class, new ExprDeserializer());
    return m;
  }

  /// 构建预配置表达式模块的 {@link ObjectMapper}。
/// 
/// @return 配置好的 mapper
  public static ObjectMapper mapper() {
    ObjectMapper om = new ObjectMapper();
    om.registerModule(ExprJsonCodec.module());
    om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    return om;
  }

  // ================= 序列化器 =================
  static class ExprSerializer extends JsonSerializer<Expr> implements ExprVisitor<Void> {
    private JsonGenerator gen;

    @Override
    public void serialize(Expr value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      this.gen = gen;
      value.accept(this);
    }

    @Override
    public Void visitAnd(And andExpr) {
      return wrapIOException(() -> writeAndExpression(andExpr));
    }

    @Override
    public Void visitOr(Or orExpr) {
      return wrapIOException(() -> writeOrExpression(orExpr));
    }

    @Override
    public Void visitNot(Not notExpr) {
      return wrapIOException(() -> writeNotExpression(notExpr));
    }

    @Override
    public Void visitConst(Const constantExpr) {
      return wrapIOException(() -> writeConstExpression(constantExpr));
    }

    @Override
    public Void visitAtom(Atom atomExpr) {
      return wrapIOException(() -> writeAtomExpression(atomExpr));
    }

    private void writeAndExpression(And andExpr) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("type", "AND");
      gen.writeArrayFieldStart("children");
      for (Expr c : andExpr.children()) {
        c.accept(this);
      }
      gen.writeEndArray();
      gen.writeEndObject();
    }

    private void writeOrExpression(Or orExpr) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("type", "OR");
      gen.writeArrayFieldStart("children");
      for (Expr c : orExpr.children()) {
        c.accept(this);
      }
      gen.writeEndArray();
      gen.writeEndObject();
    }

    private void writeNotExpression(Not notExpr) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("type", "NOT");
      gen.writeFieldName("child");
      notExpr.child().accept(this);
      gen.writeEndObject();
    }

    private void writeConstExpression(Const constantExpr) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("type", "CONST");
      gen.writeBooleanField("value", constantExpr == Const.TRUE);
      gen.writeEndObject();
    }

    private void writeAtomExpression(Atom atomExpr) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("type", "ATOM");
      gen.writeStringField("field", atomExpr.fieldKey());
      gen.writeStringField("op", atomExpr.operator().name());
      gen.writeFieldName("value");
      writeAtomValue(atomExpr.value());
      gen.writeEndObject();
    }

    private Void wrapIOException(IOAction action) {
      try {
        action.run();
        return null;
      } catch (IOException e) {
        throw new RuntimeException("JSON serialization failed", e);
      }
    }

    @FunctionalInterface
    private interface IOAction {
      void run() throws IOException;
    }

    private void writeAtomValue(Value v) throws IOException {
      if (v instanceof TermValue tv) {
        writeTermValue(tv);
      } else if (v instanceof InValues iv) {
        writeInValues(iv);
      } else if (v instanceof RangeValue rv) {
        writeRangeValue(rv);
      } else if (v instanceof ExistsFlag ef) {
        writeExistsFlag(ef);
      } else if (v instanceof TokenValue tv) {
        writeTokenValue(tv);
      } else {
        throw new IllegalArgumentException("Unsupported atom value type: " + v.getClass());
      }
    }

    private void writeTermValue(TermValue tv) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("kind", "TERM");
      gen.writeStringField("text", tv.text());
      gen.writeStringField("match", tv.match().name());
      gen.writeStringField("case", tv.caseSensitivity().name());
      gen.writeEndObject();
    }

    private void writeInValues(InValues iv) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("kind", "IN");
      gen.writeArrayFieldStart("values");
      for (String s : iv.values()) {
        gen.writeString(s);
      }
      gen.writeEndArray();
      gen.writeStringField("case", iv.caseSensitivity().name());
      gen.writeEndObject();
    }

    private void writeRangeValue(RangeValue rv) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("kind", "RANGE");
      writeRangeTypeAndBounds(rv);
      gen.writeStringField("fromBoundary", rv.fromBoundary().name());
      gen.writeStringField("toBoundary", rv.toBoundary().name());
      gen.writeEndObject();
    }

    private void writeRangeTypeAndBounds(RangeValue rv) throws IOException {
      if (rv instanceof DateRange dr) {
        gen.writeStringField("rangeType", "DATE");
        if (dr.from() != null) {
          gen.writeStringField("from", dr.from().toString());
        }
        if (dr.to() != null) {
          gen.writeStringField("to", dr.to().toString());
        }
      } else if (rv instanceof DateTimeRange dtr) {
        gen.writeStringField("rangeType", "DATETIME");
        if (dtr.from() != null) {
          gen.writeStringField("from", dtr.from().toString());
        }
        if (dtr.to() != null) {
          gen.writeStringField("to", dtr.to().toString());
        }
      } else if (rv instanceof NumberRange nr) {
        gen.writeStringField("rangeType", "NUMBER");
        if (nr.from() != null) {
          gen.writeStringField("from", nr.from().toPlainString());
        }
        if (nr.to() != null) {
          gen.writeStringField("to", nr.to().toPlainString());
        }
      }
    }

    private void writeExistsFlag(ExistsFlag ef) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("kind", "EXISTS");
      gen.writeBooleanField("shouldExist", ef.shouldExist());
      gen.writeEndObject();
    }

    private void writeTokenValue(TokenValue tv) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("kind", "TOKEN");
      gen.writeStringField("tokenType", tv.tokenType());
      gen.writeStringField("tokenValue", tv.tokenValue());
      gen.writeEndObject();
    }
  }

  // ================= 反序列化器 =================
  static class ExprDeserializer extends JsonDeserializer<Expr> {
    @Override
    public Expr deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      validateParserState(p, ctxt);
      ObjectMapper mapper = mapper();
      JsonNode root = mapper.readTree(p);
      String type = getText(root, "type");
      return parseExprByType(root, type, mapper, ctxt);
    }

    private void validateParserState(JsonParser p, DeserializationContext ctxt) throws IOException {
      if (p.currentToken() == null) {
        p.nextToken();
      }
      if (p.currentToken() != JsonToken.START_OBJECT) {
        ctxt.reportInputMismatch(Expr.class, "Expected START_OBJECT, got %s", p.currentToken());
      }
    }

    private Expr parseExprByType(
        JsonNode root, String type, ObjectMapper mapper, DeserializationContext ctxt)
        throws IOException {
      return switch (type) {
        case "AND" -> parseAndExpression(root);
        case "OR" -> parseOrExpression(root);
        case "NOT" -> parseNotExpression(root, mapper, ctxt);
        case "CONST" -> parseConstExpression(root);
        case "ATOM" -> parseAtomExpression(root, ctxt);
        default -> throw new IllegalArgumentException("Unknown type: " + type);
      };
    }

    private Expr parseAndExpression(JsonNode root) throws IOException {
      JsonNode arr = root.get("children");
      List<Expr> children = parseChildren(arr);
      return new And(children);
    }

    private Expr parseOrExpression(JsonNode root) throws IOException {
      JsonNode arr = root.get("children");
      List<Expr> children = parseChildren(arr);
      return new Or(children);
    }

    private Expr parseNotExpression(JsonNode root, ObjectMapper mapper, DeserializationContext ctxt)
        throws IOException {
      JsonNode c = root.get("child");
      if (c == null) {
        ctxt.reportInputMismatch(Expr.class, "NOT missing child");
      }
      Expr child = mapper.treeToValue(c, Expr.class);
      return new Not(child);
    }

    private Expr parseConstExpression(JsonNode root) {
      Boolean constValue = root.get("value").asBoolean();
      return constValue ? Const.TRUE : Const.FALSE;
    }

    private Expr parseAtomExpression(JsonNode root, DeserializationContext ctxt)
        throws IOException {
      String field = getText(root, "field");
      String op = getText(root, "op");
      JsonNode valueNode = root.get("value");
      if (valueNode == null || !valueNode.isObject()) {
        ctxt.reportInputMismatch(Expr.class, "ATOM missing value");
      }
      Value val = parseAtomValue(valueNode, ctxt);
      Operator operator = Operator.valueOf(op);
      return new Atom(field, operator, val);
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

    private Value parseAtomValue(JsonNode node, DeserializationContext ctxt) throws IOException {
      String kind = getText(node, "kind");
      return switch (kind) {
        case "TERM" -> parseTermValue(node);
        case "IN" -> parseInValues(node);
        case "RANGE" -> parseRange(node, ctxt);
        case "EXISTS" -> parseExistsFlag(node);
        case "TOKEN" -> parseTokenValue(node);
        default -> throw new IllegalArgumentException("Unknown atom value kind: " + kind);
      };
    }

    private TermValue parseTermValue(JsonNode node) {
      return new TermValue(
          getText(node, "text"),
          TextMatch.valueOf(getText(node, "match")),
          CaseSensitivity.valueOf(getText(node, "case")));
    }

    private InValues parseInValues(JsonNode node) {
      List<String> vals = new ArrayList<>();
      JsonNode vs = node.get("values");
      if (vs != null && vs.isArray()) {
        vs.forEach(v -> vals.add(v.asText()));
      }
      CaseSensitivity cs = CaseSensitivity.valueOf(getText(node, "case"));
      return new InValues(vals, cs);
    }

    private ExistsFlag parseExistsFlag(JsonNode node) {
      return new ExistsFlag(node.get("shouldExist").asBoolean());
    }

    private TokenValue parseTokenValue(JsonNode node) {
      return new TokenValue(getText(node, "tokenType"), getText(node, "tokenValue"));
    }

    private Value parseRange(JsonNode node, DeserializationContext ctxt) {
      String rangeType = getText(node, "rangeType");
      RangeValue.Boundary fromBoundary = RangeValue.Boundary.valueOf(getText(node, "fromBoundary"));
      RangeValue.Boundary toBoundary = RangeValue.Boundary.valueOf(getText(node, "toBoundary"));
      JsonNode fromNode = node.get("from");
      JsonNode toNode = node.get("to");

      return switch (rangeType) {
        case "DATE" -> parseDateRange(fromNode, toNode, fromBoundary, toBoundary);
        case "DATETIME" -> parseDateTimeRange(fromNode, toNode, fromBoundary, toBoundary);
        case "NUMBER" -> parseNumberRange(fromNode, toNode, fromBoundary, toBoundary);
        default -> throw new IllegalArgumentException("Unknown rangeType: " + rangeType);
      };
    }

    private DateRange parseDateRange(
        JsonNode fromNode,
        JsonNode toNode,
        RangeValue.Boundary fromBoundary,
        RangeValue.Boundary toBoundary) {
      LocalDate from = fromNode == null ? null : LocalDate.parse(fromNode.asText());
      LocalDate to = toNode == null ? null : LocalDate.parse(toNode.asText());
      return new DateRange(from, to, fromBoundary, toBoundary);
    }

    private DateTimeRange parseDateTimeRange(
        JsonNode fromNode,
        JsonNode toNode,
        RangeValue.Boundary fromBoundary,
        RangeValue.Boundary toBoundary) {
      Instant from = fromNode == null ? null : Instant.parse(fromNode.asText());
      Instant to = toNode == null ? null : Instant.parse(toNode.asText());
      return new DateTimeRange(from, to, fromBoundary, toBoundary);
    }

    private NumberRange parseNumberRange(
        JsonNode fromNode,
        JsonNode toNode,
        RangeValue.Boundary fromBoundary,
        RangeValue.Boundary toBoundary) {
      BigDecimal from = fromNode == null ? null : new BigDecimal(fromNode.asText());
      BigDecimal to = toNode == null ? null : new BigDecimal(toNode.asText());
      return new NumberRange(from, to, fromBoundary, toBoundary);
    }

    private String getText(JsonNode node, String field) {
      JsonNode n = node.get(field);
      if (n == null || n.isNull()) throw new IllegalArgumentException("Missing field: " + field);
      return n.asText();
    }
  }

  /// 将表达式树序列化为 JSON。
/// 
/// @param expr 待序列化的表达式
/// @return JSON 字符串表示
/// @throws RuntimeException 如果序列化失败
  public static String toJson(Expr expr) {
    try {
      return mapper().writeValueAsString(expr);
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize expression to JSON", e);
    }
  }

  /// 将 JSON 反序列化为表达式树。
/// 
/// @param json 待解析的 JSON 字符串
/// @return 表达式树
/// @throws RuntimeException 如果反序列化失败
  public static Expr fromJson(String json) {
    Objects.requireNonNull(json, "json");
    try {
      return mapper().readValue(json, Expr.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to deserialize JSON to expression", e);
    }
  }
}
