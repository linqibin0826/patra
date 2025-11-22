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
///
// {"type":"ATOM","field":"title","op":"TERM","value":{"kind":"TERM","text":"heart","match":"ANY","case":"INSENSITIVE"}}
/// {"type":"CONST","value":true}
///
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class ExprJsonCodec {
  /// 私有构造函数,防止实例化工具类。
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

    /// 序列化表达式为 JSON。
    ///
    /// @param value 待序列化的表达式
    /// @param gen JSON 生成器
    /// @param serializers 序列化提供者
    /// @throws IOException 如果序列化失败
    @Override
    public void serialize(Expr value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      this.gen = gen;
      value.accept(this);
    }

    /// {@inheritDoc}
    @Override
    public Void visitAnd(And andExpr) {
      return wrapIOException(() -> writeAndExpression(andExpr));
    }

    /// {@inheritDoc}
    @Override
    public Void visitOr(Or orExpr) {
      return wrapIOException(() -> writeOrExpression(orExpr));
    }

    /// {@inheritDoc}
    @Override
    public Void visitNot(Not notExpr) {
      return wrapIOException(() -> writeNotExpression(notExpr));
    }

    /// {@inheritDoc}
    @Override
    public Void visitConst(Const constantExpr) {
      return wrapIOException(() -> writeConstExpression(constantExpr));
    }

    /// {@inheritDoc}
    @Override
    public Void visitAtom(Atom atomExpr) {
      return wrapIOException(() -> writeAtomExpression(atomExpr));
    }

    /// 写入 AND 表达式的 JSON 表示。
    ///
    /// @param andExpr AND 表达式
    /// @throws IOException 如果写入失败
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

    /// 写入 OR 表达式的 JSON 表示。
    ///
    /// @param orExpr OR 表达式
    /// @throws IOException 如果写入失败
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

    /// 写入 NOT 表达式的 JSON 表示。
    ///
    /// @param notExpr NOT 表达式
    /// @throws IOException 如果写入失败
    private void writeNotExpression(Not notExpr) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("type", "NOT");
      gen.writeFieldName("child");
      notExpr.child().accept(this);
      gen.writeEndObject();
    }

    /// 写入常量表达式的 JSON 表示。
    ///
    /// @param constantExpr 常量表达式
    /// @throws IOException 如果写入失败
    private void writeConstExpression(Const constantExpr) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("type", "CONST");
      gen.writeBooleanField("value", constantExpr == Const.TRUE);
      gen.writeEndObject();
    }

    /// 写入原子表达式的 JSON 表示。
    ///
    /// @param atomExpr 原子表达式
    /// @throws IOException 如果写入失败
    private void writeAtomExpression(Atom atomExpr) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("type", "ATOM");
      gen.writeStringField("field", atomExpr.fieldKey());
      gen.writeStringField("op", atomExpr.operator().name());
      gen.writeFieldName("value");
      writeAtomValue(atomExpr.value());
      gen.writeEndObject();
    }

    /// 包装 IO 异常为运行时异常。
    ///
    /// @param action 可能抛出 IOException 的操作
    /// @return null
    /// @throws RuntimeException 如果操作失败
    private Void wrapIOException(IOAction action) {
      try {
        action.run();
        return null;
      } catch (IOException e) {
        throw new RuntimeException("JSON serialization failed", e);
      }
    }

    /// 可能抛出 IOException 的函数式接口。
    @FunctionalInterface
    private interface IOAction {
      /// 执行可能抛出 IOException 的操作。
      ///
      /// @throws IOException 如果操作失败
      void run() throws IOException;
    }

    /// 写入原子值的 JSON 表示。
    ///
    /// @param v 原子值
    /// @throws IOException 如果写入失败
    /// @throws IllegalArgumentException 如果值类型不支持
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

    /// 写入 TermValue 的 JSON 表示。
    ///
    /// @param tv TermValue
    /// @throws IOException 如果写入失败
    private void writeTermValue(TermValue tv) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("kind", "TERM");
      gen.writeStringField("text", tv.text());
      gen.writeStringField("match", tv.match().name());
      gen.writeStringField("case", tv.caseSensitivity().name());
      gen.writeEndObject();
    }

    /// 写入 InValues 的 JSON 表示。
    ///
    /// @param iv InValues
    /// @throws IOException 如果写入失败
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

    /// 写入 RangeValue 的 JSON 表示。
    ///
    /// @param rv RangeValue
    /// @throws IOException 如果写入失败
    private void writeRangeValue(RangeValue rv) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("kind", "RANGE");
      writeRangeTypeAndBounds(rv);
      gen.writeStringField("fromBoundary", rv.fromBoundary().name());
      gen.writeStringField("toBoundary", rv.toBoundary().name());
      gen.writeEndObject();
    }

    /// 写入范围类型和边界值。
    ///
    /// @param rv RangeValue
    /// @throws IOException 如果写入失败
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

    /// 写入 ExistsFlag 的 JSON 表示。
    ///
    /// @param ef ExistsFlag
    /// @throws IOException 如果写入失败
    private void writeExistsFlag(ExistsFlag ef) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("kind", "EXISTS");
      gen.writeBooleanField("shouldExist", ef.shouldExist());
      gen.writeEndObject();
    }

    /// 写入 TokenValue 的 JSON 表示。
    ///
    /// @param tv TokenValue
    /// @throws IOException 如果写入失败
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
    /// 反序列化 JSON 为表达式。
    ///
    /// @param p JSON 解析器
    /// @param ctxt 反序列化上下文
    /// @return 表达式
    /// @throws IOException 如果反序列化失败
    @Override
    public Expr deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      validateParserState(p, ctxt);
      ObjectMapper mapper = mapper();
      JsonNode root = mapper.readTree(p);
      String type = getText(root, "type");
      return parseExprByType(root, type, mapper, ctxt);
    }

    /// 验证解析器状态是否有效。
    ///
    /// @param p JSON 解析器
    /// @param ctxt 反序列化上下文
    /// @throws IOException 如果状态无效
    private void validateParserState(JsonParser p, DeserializationContext ctxt) throws IOException {
      if (p.currentToken() == null) {
        p.nextToken();
      }
      if (p.currentToken() != JsonToken.START_OBJECT) {
        ctxt.reportInputMismatch(Expr.class, "Expected START_OBJECT, got %s", p.currentToken());
      }
    }

    /// 根据类型解析表达式。
    ///
    /// @param root JSON 根节点
    /// @param type 表达式类型
    /// @param mapper ObjectMapper
    /// @param ctxt 反序列化上下文
    /// @return 表达式
    /// @throws IOException 如果解析失败
    /// @throws IllegalArgumentException 如果类型未知
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

    /// 解析 AND 表达式。
    ///
    /// @param root JSON 根节点
    /// @return AND 表达式
    /// @throws IOException 如果解析失败
    private Expr parseAndExpression(JsonNode root) throws IOException {
      JsonNode arr = root.get("children");
      List<Expr> children = parseChildren(arr);
      return new And(children);
    }

    /// 解析 OR 表达式。
    ///
    /// @param root JSON 根节点
    /// @return OR 表达式
    /// @throws IOException 如果解析失败
    private Expr parseOrExpression(JsonNode root) throws IOException {
      JsonNode arr = root.get("children");
      List<Expr> children = parseChildren(arr);
      return new Or(children);
    }

    /// 解析 NOT 表达式。
    ///
    /// @param root JSON 根节点
    /// @param mapper ObjectMapper
    /// @param ctxt 反序列化上下文
    /// @return NOT 表达式
    /// @throws IOException 如果解析失败
    private Expr parseNotExpression(JsonNode root, ObjectMapper mapper, DeserializationContext ctxt)
        throws IOException {
      JsonNode c = root.get("child");
      if (c == null) {
        ctxt.reportInputMismatch(Expr.class, "NOT missing child");
      }
      Expr child = mapper.treeToValue(c, Expr.class);
      return new Not(child);
    }

    /// 解析常量表达式。
    ///
    /// @param root JSON 根节点
    /// @return 常量表达式
    private Expr parseConstExpression(JsonNode root) {
      Boolean constValue = root.get("value").asBoolean();
      return constValue ? Const.TRUE : Const.FALSE;
    }

    /// 解析原子表达式。
    ///
    /// @param root JSON 根节点
    /// @param ctxt 反序列化上下文
    /// @return 原子表达式
    /// @throws IOException 如果解析失败
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

    /// 解析子表达式列表。
    ///
    /// @param arr JSON 数组节点
    /// @return 子表达式列表
    /// @throws IOException 如果解析失败
    private List<Expr> parseChildren(JsonNode arr) throws IOException {
      List<Expr> list = new ArrayList<>();
      if (arr != null && arr.isArray()) {
        for (JsonNode n : arr) {
          list.add(mapper().treeToValue(n, Expr.class));
        }
      }
      return list;
    }

    /// 解析原子值。
    ///
    /// @param node JSON 节点
    /// @param ctxt 反序列化上下文
    /// @return 原子值
    /// @throws IOException 如果解析失败
    /// @throws IllegalArgumentException 如果值类型未知
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

    /// 解析 TermValue。
    ///
    /// @param node JSON 节点
    /// @return TermValue
    private TermValue parseTermValue(JsonNode node) {
      return new TermValue(
          getText(node, "text"),
          TextMatch.valueOf(getText(node, "match")),
          CaseSensitivity.valueOf(getText(node, "case")));
    }

    /// 解析 InValues。
    ///
    /// @param node JSON 节点
    /// @return InValues
    private InValues parseInValues(JsonNode node) {
      List<String> vals = new ArrayList<>();
      JsonNode vs = node.get("values");
      if (vs != null && vs.isArray()) {
        vs.forEach(v -> vals.add(v.asText()));
      }
      CaseSensitivity cs = CaseSensitivity.valueOf(getText(node, "case"));
      return new InValues(vals, cs);
    }

    /// 解析 ExistsFlag。
    ///
    /// @param node JSON 节点
    /// @return ExistsFlag
    private ExistsFlag parseExistsFlag(JsonNode node) {
      return new ExistsFlag(node.get("shouldExist").asBoolean());
    }

    /// 解析 TokenValue。
    ///
    /// @param node JSON 节点
    /// @return TokenValue
    private TokenValue parseTokenValue(JsonNode node) {
      return new TokenValue(getText(node, "tokenType"), getText(node, "tokenValue"));
    }

    /// 解析范围值。
    ///
    /// @param node JSON 节点
    /// @param ctxt 反序列化上下文
    /// @return 范围值
    /// @throws IllegalArgumentException 如果范围类型未知
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

    /// 解析日期范围。
    ///
    /// @param fromNode 起始日期节点
    /// @param toNode 结束日期节点
    /// @param fromBoundary 起始边界
    /// @param toBoundary 结束边界
    /// @return 日期范围
    private DateRange parseDateRange(
        JsonNode fromNode,
        JsonNode toNode,
        RangeValue.Boundary fromBoundary,
        RangeValue.Boundary toBoundary) {
      LocalDate from = fromNode == null ? null : LocalDate.parse(fromNode.asText());
      LocalDate to = toNode == null ? null : LocalDate.parse(toNode.asText());
      return new DateRange(from, to, fromBoundary, toBoundary);
    }

    /// 解析日期时间范围。
    ///
    /// @param fromNode 起始时刻节点
    /// @param toNode 结束时刻节点
    /// @param fromBoundary 起始边界
    /// @param toBoundary 结束边界
    /// @return 日期时间范围
    private DateTimeRange parseDateTimeRange(
        JsonNode fromNode,
        JsonNode toNode,
        RangeValue.Boundary fromBoundary,
        RangeValue.Boundary toBoundary) {
      Instant from = fromNode == null ? null : Instant.parse(fromNode.asText());
      Instant to = toNode == null ? null : Instant.parse(toNode.asText());
      return new DateTimeRange(from, to, fromBoundary, toBoundary);
    }

    /// 解析数字范围。
    ///
    /// @param fromNode 起始数字节点
    /// @param toNode 结束数字节点
    /// @param fromBoundary 起始边界
    /// @param toBoundary 结束边界
    /// @return 数字范围
    private NumberRange parseNumberRange(
        JsonNode fromNode,
        JsonNode toNode,
        RangeValue.Boundary fromBoundary,
        RangeValue.Boundary toBoundary) {
      BigDecimal from = fromNode == null ? null : new BigDecimal(fromNode.asText());
      BigDecimal to = toNode == null ? null : new BigDecimal(toNode.asText());
      return new NumberRange(from, to, fromBoundary, toBoundary);
    }

    /// 从 JSON 节点获取文本字段值。
    ///
    /// @param node JSON 节点
    /// @param field 字段名
    /// @return 字段文本值
    /// @throws IllegalArgumentException 如果字段缺失或为 null
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
