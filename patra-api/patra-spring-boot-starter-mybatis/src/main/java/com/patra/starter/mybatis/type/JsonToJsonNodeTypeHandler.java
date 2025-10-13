package com.patra.starter.mybatis.type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import org.apache.ibatis.type.*;

/**
 * A unified TypeHandler for converting between Jackson's {@link JsonNode} and SQL columns.
 *
 * <p><b>Design Principles:</b>
 *
 * <ul>
 *   <li><b>Dependency Injection:</b> It uses a Spring-managed {@link ObjectMapper} instance to
 *       ensure consistency with global Jackson configurations, rather than creating its own.
 *   <li><b>Writing to Database:</b> Serializes a {@code JsonNode} to a JSON string. If a {@link
 *       JdbcType} is specified, it respects the corresponding TYPE_CODE.
 *   <li><b>Reading from Database:</b> Flexibly deserializes a {@code JsonNode} from various JDBC
 *       representations, including String, CLOB, byte array, and PostgreSQL's PGobject (for
 *       json/jsonb types).
 *   <li><b>Null Handling:</b> Converts database NULL values to Java null. Empty or whitespace-only
 *       strings are also treated as null to prevent parsing errors.
 * </ul>
 *
 * <p><b>Database Compatibility:</b>
 *
 * <ul>
 *   <li><b>MySQL:</b> Compatible with JSON, TEXT, and LONGTEXT column types.
 *   <li><b>PostgreSQL:</b> Compatible with JSON and JSONB column types; automatically handles the
 *       {@code org.postgresql.util.PGobject} returned by the driver.
 * </ul>
 *
 * <p><b>Registration Example:</b>
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

  // ---------------- Writing (Java -> JDBC) ----------------

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType) throws SQLException {
    final String json;
    try {
      // Serialize the JsonNode to a JSON string. Using the injected ObjectMapper ensures
      // configuration consistency.
      json = objectMapper.writeValueAsString(parameter);
    } catch (Exception e) {
      throw new SQLException("Failed to serialize JsonNode", e);
    }

    if (jdbcType != null) {
      ps.setObject(i, json, jdbcType.TYPE_CODE);
    } else {
      // Let the driver infer the type if not specified (works for both MySQL and PostgreSQL).
      ps.setObject(i, json);
    }
  }

  // ---------------- Reading (JDBC -> Java) ----------------

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

  // ---------------- Internal Parsing Logic ----------------

  private JsonNode parse(Object raw) throws SQLException {
    if (raw == null) return null;

    try {
      if (raw instanceof String s) {
        return parseString(s);
      } else if (raw instanceof byte[] bytes) {
        return parseString(new String(bytes, StandardCharsets.UTF_8));
      } else if (raw instanceof Clob clob) { // Includes NClob
        return parseString(readClob(clob));
      } else if (raw instanceof Reader r) { // Fallback for rare cases
        return parseString(readAll(r));
      } else if ("org.postgresql.util.PGobject".equals(raw.getClass().getName())) {
        // Handle PostgreSQL's PGobject for json/jsonb types.
        return parseString(raw.toString());
      }

      // As a last resort, convert the object to a string and attempt to parse.
      return parseString(String.valueOf(raw));
    } catch (Exception e) {
      throw new SQLException("Failed to parse JSON value: " + raw, e);
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
