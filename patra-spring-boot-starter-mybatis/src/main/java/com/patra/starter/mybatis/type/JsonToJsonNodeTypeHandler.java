package com.patra.starter.mybatis.type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import org.apache.ibatis.type.*;

/**
 * 用于在 Jackson 的 {@link JsonNode} 和 SQL 列之间进行转换的统一类型处理器。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li><b>依赖注入：</b> 使用 Spring 管理的 {@link ObjectMapper} 实例，以确保与全局 Jackson 配置保持一致，而不是创建自己的实例。
 *   <li><b>写入数据库：</b> 将 {@code JsonNode} 序列化为 JSON 字符串。如果指定了 {@link JdbcType}，则遵循相应的 TYPE_CODE。
 *   <li><b>从数据库读取：</b> 灵活地从各种 JDBC 表示形式反序列化 {@code JsonNode}，包括 String、CLOB、字节数组和 PostgreSQL 的
 *       PGobject（用于 json/jsonb 类型）。
 *   <li><b>空值处理：</b> 将数据库 NULL 值转换为 Java null。空字符串或仅包含空白字符的字符串也会被视为 null，以防止解析错误。
 * </ul>
 *
 * <p><b>数据库兼容性：</b>
 *
 * <ul>
 *   <li><b>MySQL：</b> 兼容 JSON、TEXT 和 LONGTEXT 列类型。
 *   <li><b>PostgreSQL：</b> 兼容 JSON 和 JSONB 列类型；自动处理驱动程序返回的 {@code org.postgresql.util.PGobject}。
 * </ul>
 *
 * <p><b>注册示例：</b>
 *
 * <pre>
 * configuration.getTypeHandlerRegistry()
 *              .register(JsonNode.class, new JsonToJsonNodeTypeHandler(objectMapper));
 * </pre>
 */
@MappedTypes(JsonNode.class)
@MappedJdbcTypes(
    value = {
      JdbcType.VARCHAR,
      JdbcType.LONGVARCHAR,
      JdbcType.CLOB,
      JdbcType.LONGNVARCHAR,
      JdbcType.OTHER
    },
    includeNullJdbcType = true)
public class JsonToJsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

  private final ObjectMapper objectMapper;

  public JsonToJsonNodeTypeHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  // ---------------- 写入 (Java -> JDBC) ----------------

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType) throws SQLException {
    final String json;
    try {
      // 将 JsonNode 序列化为 JSON 字符串。使用注入的 ObjectMapper 确保配置一致性。
      json = objectMapper.writeValueAsString(parameter);
    } catch (Exception e) {
      throw new SQLException("在参数索引 " + i + " 处将 JsonNode 序列化到数据库列失败", e);
    }

    if (jdbcType != null) {
      ps.setObject(i, json, jdbcType.TYPE_CODE);
    } else {
      // 如果未指定类型，让驱动程序推断类型（适用于 MySQL 和 PostgreSQL）。
      ps.setObject(i, json);
    }
  }

  // ---------------- 读取 (JDBC -> Java) ----------------

  @Override
  public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getObject(columnName));
  }

  @Override
  public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getObject(columnIndex));
  }

  @Override
  public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return parse(cs.getObject(columnIndex));
  }

  // ---------------- 内部解析逻辑 ----------------

  private JsonNode parse(Object raw) throws SQLException {
    if (raw == null) return null;

    try {
      if (raw instanceof String s) {
        return parseString(s);
      } else if (raw instanceof byte[] bytes) {
        return parseString(new String(bytes, StandardCharsets.UTF_8));
      } else if (raw instanceof Clob clob) { // 包括 NClob
        return parseString(readClob(clob));
      } else if (raw instanceof Reader r) { // 罕见情况的后备方案
        return parseString(readAll(r));
      } else if ("org.postgresql.util.PGobject".equals(raw.getClass().getName())) {
        // 处理 PostgreSQL 的 PGobject（用于 json/jsonb 类型）。
        return parseString(raw.toString());
      }

      // 作为最后的手段，将对象转换为字符串并尝试解析。
      return parseString(String.valueOf(raw));
    } catch (Exception e) {
      throw new SQLException("解析 JSON 值失败: " + raw, e);
    }
  }

  private JsonNode parseString(String json) throws Exception {
    if (json == null) return null;
    String trimmed = json.trim();
    if (trimmed.isEmpty()) return null;
    return objectMapper.readTree(trimmed);
  }

  private String readClob(Clob clob) throws Exception {
    try (Reader r = clob.getCharacterStream()) {
      return readAll(r);
    }
  }

  private String readAll(Reader r) throws Exception {
    StringBuilder sb = new StringBuilder(1024);
    char[] buf = new char[2048];
    int n;
    while ((n = r.read(buf)) != -1) {
      sb.append(buf, 0, n);
    }
    return sb.toString();
  }
}
