package com.patra.starter.mybatis.type;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.ibatis.type.*;

/// 用于在 `Map<String, Object>` 与数据库列中存储的 JSON 字符串之间进行转换的类型处理器。
/// 
/// **功能说明:**
/// 
/// - **写入:** 将 `Map<String, Object>` 序列化为 JSON 字符串,使用 `setString` 存储到数据库。
///   - **读取:** 使用 `getString` 从数据库反序列化 JSON 字符串为 `Map<String, Object>`。
///   - **验证:** 执行轻量级检查,确保 JSON 字符串表示一个对象(即以 '{' 开头)。
/// 
@MappedTypes(Map.class)
@MappedJdbcTypes(
    value = {
      JdbcType.VARCHAR,
      JdbcType.LONGVARCHAR,
      JdbcType.LONGNVARCHAR,
      JdbcType.CLOB,
      JdbcType.OTHER
    },
    includeNullJdbcType = true)
public class JsonToMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private final ObjectReader reader;
  private final ObjectWriter writer;

  /// 构造函数,使用给定的 ObjectMapper 初始化读写器。
/// 
/// @param objectMapper Spring 管理的 ObjectMapper 实例
  public JsonToMapTypeHandler(ObjectMapper objectMapper) {
    this.reader = objectMapper.readerFor(MAP_TYPE);
    this.writer = objectMapper.writerFor(MAP_TYPE);
  }

  /// 将非空的 Map 参数设置到 PreparedStatement 中。
/// 
/// @param ps JDBC PreparedStatement
/// @param i 参数索引
/// @param parameter 要序列化的 Map 对象
/// @param jdbcType JDBC 类型
/// @throws SQLException 序列化失败时抛出
  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType)
      throws SQLException {
    final String json;
    try {
      json = writer.writeValueAsString(parameter);
    } catch (Exception e) {
      throw new SQLException("在参数索引 " + i + " 处将 Map 序列化为 JSON 失败", e);
    }
    if (jdbcType != null) {
      ps.setObject(i, json, jdbcType.TYPE_CODE);
    } else {
      ps.setString(i, json);
    }
  }

  /// 通过列名从 ResultSet 中获取可空的 Map 结果。
/// 
/// @param rs 结果集
/// @param columnName 列名
/// @return 解析后的 Map 对象,可能为 null
/// @throws SQLException 解析失败时抛出
  @Override
  public Map<String, Object> getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    return parse(rs.getString(columnName));
  }

  /// 通过列索引从 ResultSet 中获取可空的 Map 结果。
/// 
/// @param rs 结果集
/// @param columnIndex 列索引
/// @return 解析后的 Map 对象,可能为 null
/// @throws SQLException 解析失败时抛出
  @Override
  public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  /// 从 CallableStatement 中获取可空的 Map 结果。
/// 
/// @param cs CallableStatement
/// @param columnIndex 列索引
/// @return 解析后的 Map 对象,可能为 null
/// @throws SQLException 解析失败时抛出
  @Override
  public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  /// 解析 JSON 字符串为 Map 对象。
/// 
/// @param json JSON 字符串
/// @return 解析后的 Map 对象,可能为 null
/// @throws SQLException 解析失败或 JSON 不是对象类型时抛出
  private Map<String, Object> parse(String json) throws SQLException {
    if (json == null) return null;
    String trimmed = json.trim();
    if (trimmed.isEmpty()) return null;

    if (trimmed.charAt(0) != '{') {
      throw new SQLException("获取的 JSON 值不是对象类型: " + preview(trimmed));
    }

    try {
      return reader.readValue(trimmed);
    } catch (Exception e) {
      throw new SQLException("将 JSON 字符串解析为 Map 失败: " + preview(trimmed), e);
    }
  }

  /// 生成字符串的预览文本,用于错误消息。
/// 
/// @param s 要预览的字符串
/// @return 截断后的预览字符串
  private String preview(String s) {
    s = s.replace('\n', ' ').replace('\r', ' ');
    final int maxLength = 160;
    return s.length() <= maxLength
        ? s
        : s.substring(0, maxLength) + "...(" + s.length() + " chars)";
  }
}
