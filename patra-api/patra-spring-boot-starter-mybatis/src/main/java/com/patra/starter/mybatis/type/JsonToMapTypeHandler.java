package com.patra.starter.mybatis.type;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.ibatis.type.*;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * A TypeHandler for converting between a {@code Map<String, Object>} and a JSON string stored in a database column (e.g., JSON, TEXT, or VARCHAR).
 *
 * <p><b>Functionality:</b></p>
 * <ul>
 *   <li><b>Writing:</b> Serializes a {@code Map<String, Object>} into a JSON string using {@code setString}.</li>
 *   <li><b>Reading:</b> Deserializes a JSON string from the database into a {@code Map<String, Object>} using {@code getString}.</li>
 *   <li><b>Validation:</b> Performs a lightweight check to ensure the JSON string represents an object (i.e., starts with '{').</li>
 * </ul>
 */
@MappedTypes(Map.class)
@MappedJdbcTypes(
        value = {JdbcType.VARCHAR, JdbcType.LONGVARCHAR, JdbcType.LONGNVARCHAR, JdbcType.CLOB, JdbcType.OTHER},
        includeNullJdbcType = true
)
public class JsonToMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final ObjectReader reader;
    private final ObjectWriter writer;

    public JsonToMapTypeHandler(ObjectMapper objectMapper) {
        this.reader = objectMapper.readerFor(MAP_TYPE);
        this.writer = objectMapper.writerFor(MAP_TYPE);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter,JdbcType jdbcType) throws SQLException {
        final String json;
        try {
            json = writer.writeValueAsString(parameter);
        } catch (Exception e) {
            throw new SQLException("Failed to serialize Map to JSON", e);
        }
        if (jdbcType != null) {
            ps.setObject(i, json, jdbcType.TYPE_CODE);
        } else {
            ps.setString(i, json);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Map<String, Object> parse(String json) throws SQLException {
        if (json == null) return null;
        String trimmed = json.trim();
        if (trimmed.isEmpty()) return null;

        if (trimmed.charAt(0) != '{') {
            throw new SQLException("The retrieved JSON value is not an object: " + preview(trimmed));
        }

        try {
            return reader.readValue(trimmed);
        } catch (Exception e) {
            throw new SQLException("Failed to parse JSON string into a Map: " + preview(trimmed), e);
        }
    }

    private String preview(String s) {
        s = s.replace('\n', ' ').replace('\r', ' ');
        final int maxLength = 160;
        return s.length() <= maxLength ? s : s.substring(0, maxLength) + "...(" + s.length() + " chars)";
    }
}