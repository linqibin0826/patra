package com.patra.starter.mybatis.type;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.ibatis.type.*;

import java.sql.*;
import java.util.Map;

/**
 * MySQL 专用：JSON(Map) ↔ JSON/TEXT/VARCHAR
 *
 * - 写入：Map -> JSON 字符串，用 setString
 * - 读取：getString -> Map
 * - 轻量校验：必须以 '{' 开头，保证顶层是对象
 */
@MappedTypes(Map.class)
@MappedJdbcTypes(
        value = {JdbcType.VARCHAR, JdbcType.LONGVARCHAR, JdbcType.LONGNVARCHAR, JdbcType.CLOB, JdbcType.OTHER},
        includeNullJdbcType = true
)
public class JsonToMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {};
    private final ObjectReader reader;
    private final ObjectWriter writer;

    public JsonToMapTypeHandler(ObjectMapper objectMapper) {
        this.reader = objectMapper.readerFor(MAP_TYPE);
        this.writer = objectMapper.writerFor(MAP_TYPE);
    }

    // 写入：Map -> JSON 字符串
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    Map<String, Object> parameter,
                                    JdbcType jdbcType) throws SQLException {
        final String json;
        try {
            json = writer.writeValueAsString(parameter);
        } catch (Exception e) {
            throw new SQLException("Serialize Map to JSON failed", e);
        }
        if (jdbcType != null) {
            ps.setObject(i, json, jdbcType.TYPE_CODE);
        } else {
            ps.setString(i, json);
        }
    }

    // 读取：JDBC -> Map
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
        String s = json.trim();
        if (s.isEmpty()) return null;
        if (s.charAt(0) != '{') {
            throw new SQLException("JSON value is not an object: " + preview(s));
        }
        try {
            return reader.readValue(s);
        } catch (Exception e) {
            throw new SQLException("Parse JSON to Map failed: " + preview(s), e);
        }
    }

    private String preview(String s) {
        s = s.replace('\n', ' ').replace('\r', ' ');
        final int N = 160;
        return s.length() <= N ? s : s.substring(0, N) + "...(" + s.length() + " chars)";
    }
}
