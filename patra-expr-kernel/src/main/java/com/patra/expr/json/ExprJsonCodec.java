package com.patra.expr.json;

import com.patra.expr.*;
import com.patra.expr.Atom.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

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
  public static tools.jackson.databind.JacksonModule module() {
    SimpleModule m = new SimpleModule("expr-json-module");
    m.addSerializer(Expr.class, new ExprSerializer());
    m.addDeserializer(Expr.class, new ExprDeserializer());
    return m;
  }

  /// 构建预配置表达式模块的 {@link ObjectMapper}。
  ///
  /// @return 配置好的 mapper
  public static ObjectMapper mapper() {
    return JsonMapper.builder()
        .addModule(ExprJsonCodec.module())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }

  // ================= 序列化器 =================
  static class ExprSerializer extends ValueSerializer<Expr> implements ExprVisitor<Void> {
    private JsonGenerator gen;

    /// 序列化表达式为 JSON。
    ///
    /// @param value 待序列化的表达式
    /// @param gen JSON 生成器
    /// @param serializers 序列化上下文
    @Override
    public void serialize(Expr value, JsonGenerator gen, SerializationContext serializers) {
      this.gen = gen;
      value.accept(this);
    }

    /// {@inheritDoc}
    @Override
    public Void visitAnd(And andExpr) {
      return wrap(() -> writeAndExpression(andExpr));
    }

    /// {@inheritDoc}
    @Override
    public Void visitOr(Or orExpr) {
      return wrap(() -> writeOrExpression(orExpr));
    }

    /// {@inheritDoc}
    @Override
    public Void visitNot(Not notExpr) {
      return wrap(() -> writeNotExpression(notExpr));
    }

    /// {@inheritDoc}
    @Override
    public Void visitConst(Const constantExpr) {
      return wrap(() -> writeConstExpression(constantExpr));
    }

    /// {@inheritDoc}
    @Override
    public Void visitAtom(Atom atomExpr) {
      return wrap(() -> writeAtomExpression(atomExpr));
    }

    /// 写入 AND 表达式的 JSON 表示。
    ///
    /// @param andExpr AND 表达式
    private void writeAndExpression(And andExpr) {
      gen.writeStartObject();
      gen.writeStringProperty("type", "AND");
      gen.writeArrayPropertyStart("children");
      for (Expr c : andExpr.children()) {
        c.accept(this);
      }
      gen.writeEndArray();
      gen.writeEndObject();
    }

    /// 写入 OR 表达式的 JSON 表示。
    ///
    /// @param orExpr OR 表达式
    private void writeOrExpression(Or orExpr) {
      gen.writeStartObject();
      gen.writeStringProperty("type", "OR");
      gen.writeArrayPropertyStart("children");
      for (Expr c : orExpr.children()) {
        c.accept(this);
      }
      gen.writeEndArray();
      gen.writeEndObject();
    }

    /// 写入 NOT 表达式的 JSON 表示。
    ///
    /// @param notExpr NOT 表达式
    private void writeNotExpression(Not notExpr) {
      gen.writeStartObject();
      gen.writeStringProperty("type", "NOT");
      gen.writeName("child");
      notExpr.child().accept(this);
      gen.writeEndObject();
    }

    /// 写入常量表达式的 JSON 表示。
    ///
    /// @param constantExpr 常量表达式
    private void writeConstExpression(Const constantExpr) {
      gen.writeStartObject();
      gen.writeStringProperty("type", "CONST");
      gen.writeBooleanProperty("value", constantExpr == Const.TRUE);
      gen.writeEndObject();
    }

    /// 写入原子表达式的 JSON 表示。
    ///
    /// @param atomExpr 原子表达式
    private void writeAtomExpression(Atom atomExpr) {
      gen.writeStartObject();
      gen.writeStringProperty("type", "ATOM");
      gen.writeStringProperty("field", atomExpr.fieldKey());
      gen.writeStringProperty("op", atomExpr.operator().name());
      gen.writeName("value");
      writeAtomValue(atomExpr.value());
      gen.writeEndObject();
    }

    /// 包装操作为返回 Void 的函数。
    ///
    /// @param action 要执行的操作
    /// @return null
    private Void wrap(Runnable action) {
      action.run();
      return null;
    }

    /// 写入原子值的 JSON 表示。
    ///
    /// @param v 原子值
    /// @throws IllegalArgumentException 如果值类型不支持
    private void writeAtomValue(Value v) {
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
    private void writeTermValue(TermValue tv) {
      gen.writeStartObject();
      gen.writeStringProperty("kind", "TERM");
      gen.writeStringProperty("text", tv.text());
      gen.writeStringProperty("match", tv.match().name());
      gen.writeStringProperty("case", tv.caseSensitivity().name());
      gen.writeEndObject();
    }

    /// 写入 InValues 的 JSON 表示。
    ///
    /// @param iv InValues
    private void writeInValues(InValues iv) {
      gen.writeStartObject();
      gen.writeStringProperty("kind", "IN");
      gen.writeArrayPropertyStart("values");
      for (String s : iv.values()) {
        gen.writeString(s);
      }
      gen.writeEndArray();
      gen.writeStringProperty("case", iv.caseSensitivity().name());
      gen.writeEndObject();
    }

    /// 写入 RangeValue 的 JSON 表示。
    ///
    /// @param rv RangeValue
    private void writeRangeValue(RangeValue rv) {
      gen.writeStartObject();
      gen.writeStringProperty("kind", "RANGE");
      writeRangeTypeAndBounds(rv);
      gen.writeStringProperty("fromBoundary", rv.fromBoundary().name());
      gen.writeStringProperty("toBoundary", rv.toBoundary().name());
      gen.writeEndObject();
    }

    /// 写入范围类型和边界值。
    ///
    /// @param rv RangeValue
    private void writeRangeTypeAndBounds(RangeValue rv) {
      if (rv instanceof DateRange dr) {
        gen.writeStringProperty("rangeType", "DATE");
        if (dr.from() != null) {
          gen.writeStringProperty("from", dr.from().toString());
        }
        if (dr.to() != null) {
          gen.writeStringProperty("to", dr.to().toString());
        }
      } else if (rv instanceof DateTimeRange dtr) {
        gen.writeStringProperty("rangeType", "DATETIME");
        if (dtr.from() != null) {
          gen.writeStringProperty("from", dtr.from().toString());
        }
        if (dtr.to() != null) {
          gen.writeStringProperty("to", dtr.to().toString());
        }
      } else if (rv instanceof NumberRange nr) {
        gen.writeStringProperty("rangeType", "NUMBER");
        if (nr.from() != null) {
          gen.writeStringProperty("from", nr.from().toPlainString());
        }
        if (nr.to() != null) {
          gen.writeStringProperty("to", nr.to().toPlainString());
        }
      }
    }

    /// 写入 ExistsFlag 的 JSON 表示。
    ///
    /// @param ef ExistsFlag
    private void writeExistsFlag(ExistsFlag ef) {
      gen.writeStartObject();
      gen.writeStringProperty("kind", "EXISTS");
      gen.writeBooleanProperty("shouldExist", ef.shouldExist());
      gen.writeEndObject();
    }

    /// 写入 TokenValue 的 JSON 表示。
    ///
    /// @param tv TokenValue
    private void writeTokenValue(TokenValue tv) {
      gen.writeStartObject();
      gen.writeStringProperty("kind", "TOKEN");
      gen.writeStringProperty("tokenType", tv.tokenType());
      gen.writeStringProperty("tokenValue", tv.tokenValue());
      gen.writeEndObject();
    }
  }

  // ================= 反序列化器 =================
  static class ExprDeserializer extends ValueDeserializer<Expr> {
    /// 反序列化 JSON 为表达式。
    ///
    /// @param p JSON 解析器
    /// @param ctxt 反序列化上下文
    /// @return 表达式
    @Override
    public Expr deserialize(JsonParser p, DeserializationContext ctxt) {
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
    private void validateParserState(JsonParser p, DeserializationContext ctxt) {
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
    /// @throws IllegalArgumentException 如果类型未知
    private Expr parseExprByType(
        JsonNode root, String type, ObjectMapper mapper, DeserializationContext ctxt) {
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
    private Expr parseAndExpression(JsonNode root) {
      JsonNode arr = root.get("children");
      List<Expr> children = parseChildren(arr);
      return new And(children);
    }

    /// 解析 OR 表达式。
    ///
    /// @param root JSON 根节点
    /// @return OR 表达式
    private Expr parseOrExpression(JsonNode root) {
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
    private Expr parseNotExpression(
        JsonNode root, ObjectMapper mapper, DeserializationContext ctxt) {
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
    private Expr parseAtomExpression(JsonNode root, DeserializationContext ctxt) {
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
    private List<Expr> parseChildren(JsonNode arr) {
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
    /// @throws IllegalArgumentException 如果值类型未知
    private Value parseAtomValue(JsonNode node, DeserializationContext ctxt) {
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
  /// @throws JacksonException 如果序列化失败
  public static String toJson(Expr expr) {
    return mapper().writeValueAsString(expr);
  }

  /// 将 JSON 反序列化为表达式树。
  ///
  /// @param json 待解析的 JSON 字符串
  /// @return 表达式树
  /// @throws JacksonException 如果反序列化失败
  public static Expr fromJson(String json) {
    Objects.requireNonNull(json, "json");
    return mapper().readValue(json, Expr.class);
  }
}
