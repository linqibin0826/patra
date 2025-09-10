package com.patra.starter.mybatis.type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.*;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/**
 * 统一的 Jackson {@link JsonNode} ↔ SQL 列 类型处理器。
 *
 * <p>设计要点：
 * <ul>
 *   <li>不自行 new {@link ObjectMapper}，统一使用 Spring 容器注入的实例（保持与全局 Jackson 配置一致）。</li>
 *   <li>写入：将 {@code JsonNode} 序列化为 JSON 字符串写入数据库；若声明了 {@link JdbcType}，遵循其 TYPE_CODE。</li>
 *   <li>读取：尽可能宽容地从多种 JDBC 表达形式还原（String、CLOB、byte[]、PGobject(json/jsonb)）。</li>
 *   <li>空处理：数据库 NULL → 返回 null；空串/纯空白 → 返回 null（避免解析异常）。</li>
 * </ul>
 *
 * <p>兼容性：
 * <ul>
 *   <li>MySQL：列类型可用 JSON/TEXT/LONGTEXT。</li>
 *   <li>PostgreSQL：列类型 JSON/JSONB；驱动返回 {@code org.postgresql.util.PGobject} 时自动处理。</li>
 * </ul>
 *
 * <p>注册方式（示例）：
 * <pre>
 * configuration.getTypeHandlerRegistry()
 *              .register(JsonNode.class, new JsonNodeTypeHandler(objectMapper));
 * </pre>
 */
@MappedTypes(JsonNode.class)
@MappedJdbcTypes(value = {JdbcType.VARCHAR, JdbcType.LONGVARCHAR, JdbcType.CLOB, JdbcType.LONGNVARCHAR, JdbcType.OTHER}, includeNullJdbcType = true)
public class JsonToJsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

    private final ObjectMapper objectMapper;

    public JsonToJsonNodeTypeHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------------- 写入（Java → JDBC） ----------------

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType) throws SQLException {
        // 直接序列化为 JSON 文本；JsonNode#toString() 亦可，这里显式使用 ObjectMapper 以保持全局配置一致性
        final String json;
        try {
            json = objectMapper.writeValueAsString(parameter);
        } catch (Exception e) {
            throw new SQLException("Serialize JsonNode failed", e);
        }

        if (jdbcType != null) {
            ps.setObject(i, json, jdbcType.TYPE_CODE);
            return;
        }
        // 未指定 JDBC 类型时让驱动推断（对 MySQL/PG 均可行；PG JSONB 也能接受字符串）
        ps.setObject(i, json);
    }

    // ---------------- 读取（JDBC → Java） ----------------

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

    // ---------------- 内部：多形态解析 ----------------

    private JsonNode parse(Object raw) throws SQLException {
        if (raw == null) return null;

        try {
            switch (raw) {
                case String s -> {
                    return parseString(s);
                }
                case byte[] bytes -> {
                    return parseString(new String(bytes, StandardCharsets.UTF_8));
                }

                // 兼容 CLOB
                case NClob nclob -> {
                    return parseString(readClob(nclob));
                }

                // 兼容 Reader（极少见）
                case Clob clob -> {
                    return parseString(readClob(clob));
                }

                // 兼容 NClob
                case Reader r -> {
                    return parseString(readAll(r));
                }
                default -> {
                }
            }
            // 兼容 PostgreSQL PGobject(json/jsonb)
            if ("org.postgresql.util.PGobject".equals(raw.getClass().getName())) {
                // toString() 一般返回其 value；谨慎起见通过反射读 value 字段
                String text = raw.toString();
                try {
                    var f = raw.getClass().getDeclaredField("value");
                    f.setAccessible(true);
                    Object v = f.get(raw);
                    if (v instanceof String sv) text = sv;
                } catch (NoSuchFieldException | IllegalAccessException ignored) {
                }
                return parseString(text);
            }
            // 其他类型：退化为字符串再解析（驱动层可能返回自定义包装）
            return parseString(String.valueOf(raw));
        } catch (Exception e) {
            throw new SQLException("Failed to parse JSON value: " + raw, e);
        }
    }

    private JsonNode parseString(String json) throws Exception {
        if (json == null) return null;
        // 去除空白字符串的噪音
        String s = json.trim();
        if (s.isEmpty()) return null;
        return objectMapper.readTree(s);
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
        while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        return sb.toString();
    }
}
