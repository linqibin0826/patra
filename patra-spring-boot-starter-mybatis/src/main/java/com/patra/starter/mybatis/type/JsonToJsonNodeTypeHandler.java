package com.patra.starter.mybatis.type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import org.apache.ibatis.type.*;

/// 用于在 Jackson 的 {@link JsonNode} 和 SQL 列之间进行转换的统一类型处理器。
///
/// **设计原则：**
///
/// - **依赖注入：** 使用 Spring 管理的 {@link ObjectMapper} 实例，以确保与全局 Jackson 配置保持一致，而不是创建自己的实例。
///   - **写入数据库：** 将 `JsonNode` 序列化为 JSON 字符串。如果指定了 {@link JdbcType}，则遵循相应的 TYPE_CODE。
///   - **从数据库读取：** 灵活地从各种 JDBC 表示形式反序列化 `JsonNode`，包括 String、CLOB、字节数组和 PostgreSQL 的
///       PGobject（用于 json/jsonb 类型）。
///   - **空值处理：** 将数据库 NULL 值转换为 Java null。空字符串或仅包含空白字符的字符串也会被视为 null，以防止解析错误。
///
/// **数据库兼容性：**
///
/// - **MySQL：** 兼容 JSON、TEXT 和 LONGTEXT 列类型。
///   - **PostgreSQL：** 兼容 JSON 和 JSONB 列类型；自动处理驱动程序返回的 `org.postgresql.util.PGobject`。
///
/// **注册示例：**
///
/// ```
///
/// configuration.getTypeHandlerRegistry()
///              .register(JsonNode.class, new JsonToJsonNodeTypeHandler(objectMapper));
///
/// ```
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

  /// 构造 JsonNode 类型处理器。
  ///
  /// @param objectMapper Spring 管理的 ObjectMapper 实例
  public JsonToJsonNodeTypeHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  // ---------------- 写入 (Java -> JDBC) ----------------

  /// 将非空的 JsonNode 参数设置到 PreparedStatement 中。
  ///
  /// @param ps JDBC PreparedStatement
  /// @param i 参数索引
  /// @param parameter 要序列化的 JsonNode 对象
  /// @param jdbcType JDBC 类型
  /// @throws SQLException 序列化失败时抛出
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

  /// 通过列名从 ResultSet 中获取可空的 JsonNode 结果。
  ///
  /// @param rs 结果集
  /// @param columnName 列名
  /// @return 解析后的 JsonNode 对象,可能为 null
  /// @throws SQLException 解析失败时抛出
  @Override
  public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getObject(columnName));
  }

  /// 通过列索引从 ResultSet 中获取可空的 JsonNode 结果。
  ///
  /// @param rs 结果集
  /// @param columnIndex 列索引
  /// @return 解析后的 JsonNode 对象,可能为 null
  /// @throws SQLException 解析失败时抛出
  @Override
  public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getObject(columnIndex));
  }

  /// 从 CallableStatement 中获取可空的 JsonNode 结果。
  ///
  /// @param cs CallableStatement
  /// @param columnIndex 列索引
  /// @return 解析后的 JsonNode 对象,可能为 null
  /// @throws SQLException 解析失败时抛出
  @Override
  public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return parse(cs.getObject(columnIndex));
  }

  // ---------------- 内部解析逻辑 ----------------

  /// 解析 JDBC 对象为 JsonNode。
  ///
  /// @param raw 从 JDBC 获取的原始对象
  /// @return 解析后的 JsonNode 对象,可能为 null
  /// @throws SQLException 解析失败时抛出
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

  /// 解析 JSON 字符串为 JsonNode。
  ///
  /// @param json JSON 字符串
  /// @return 解析后的 JsonNode 对象,可能为 null
  /// @throws Exception 解析失败时抛出
  private JsonNode parseString(String json) throws Exception {
    if (json == null) return null;
    String trimmed = json.trim();
    if (trimmed.isEmpty()) return null;
    return objectMapper.readTree(trimmed);
  }

  /// 从 Clob 读取字符串内容。
  ///
  /// @param clob CLOB 对象
  /// @return 读取的字符串内容
  /// @throws Exception 读取失败时抛出
  private String readClob(Clob clob) throws Exception {
    try (Reader r = clob.getCharacterStream()) {
      return readAll(r);
    }
  }

  /// 从 Reader 读取所有内容。
  ///
  /// @param r Reader 对象
  /// @return 读取的字符串内容
  /// @throws Exception 读取失败时抛出
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
