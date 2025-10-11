package com.patra.starter.mybatis.type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonToJsonNodeTypeHandlerTest {

    private ObjectMapper objectMapper;
    private JsonToJsonNodeTypeHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new JsonToJsonNodeTypeHandler(objectMapper);
    }

    @Test
    void setNonNullParameter_shouldUseJdbcTypeWhenProvided() throws Exception {
        RecordingPreparedStatement recorder = RecordingPreparedStatement.create();
        JsonNode node = objectMapper.readTree("{\"a\":1}");

        handler.setNonNullParameter(recorder.proxy(), 1, node, JdbcType.CLOB);

        assertThat(recorder.index).isEqualTo(1);
        assertThat(recorder.value).isEqualTo("{\"a\":1}");
        assertThat(recorder.jdbcType).isEqualTo(JdbcType.CLOB.TYPE_CODE);
    }

    @Test
    void setNonNullParameter_shouldFallbackWhenJdbcTypeMissing() throws Exception {
        RecordingPreparedStatement recorder = RecordingPreparedStatement.create();
        JsonNode node = objectMapper.readTree("{\"b\":[2]}");

        handler.setNonNullParameter(recorder.proxy(), 2, node, null);

        assertThat(recorder.index).isEqualTo(2);
        assertThat(recorder.value).isEqualTo("{\"b\":[2]}");
        assertThat(recorder.jdbcType).isNull();
    }

    @Test
    void getNullableResult_shouldParseStringAndBytes() throws Exception {
        ResultSet rsString = singleValueResultSet("{\"v\":1}");
        ResultSet rsBytes = singleValueResultSet("{\"v\":2}".getBytes(StandardCharsets.UTF_8));

        JsonNode node1 = handler.getNullableResult(rsString, "col");
        JsonNode node2 = handler.getNullableResult(rsBytes, "col");

        assertThat(node1.get("v").asInt()).isEqualTo(1);
        assertThat(node2.get("v").asInt()).isEqualTo(2);
    }

    @Test
    void getNullableResult_shouldReturnNullForBlankString() throws Exception {
        ResultSet rs = singleValueResultSet("   ");
        assertThat(handler.getNullableResult(rs, "col")).isNull();
    }

    @Test
    void getNullableResult_shouldThrowWhenJsonInvalid() throws Exception {
        ResultSet rs = singleValueResultSet("not-json");
        assertThatThrownBy(() -> handler.getNullableResult(rs, "col"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Failed to parse JSON value");
    }

    private ResultSet singleValueResultSet(Object value) {
        return (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{ResultSet.class},
                new ResultSetHandler(value)
        );
    }

    private static final class ResultSetHandler implements InvocationHandler {
        private final Object value;

        private ResultSetHandler(Object value) {
            this.value = value;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getObject" -> value;
                case "getString" -> (value instanceof String s) ? s : String.valueOf(value);
                case "close", "clearWarnings" -> null;
                case "wasNull" -> value == null;
                case "unwrap" -> null;
                case "isWrapperFor" -> false;
                case "toString" -> "ResultSetStub";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    static final class RecordingPreparedStatement implements InvocationHandler {
        private int index;
        private Object value;
        private Integer jdbcType;
        private PreparedStatement proxy;

        static RecordingPreparedStatement create() {
            RecordingPreparedStatement handler = new RecordingPreparedStatement();
            handler.proxy = (PreparedStatement) Proxy.newProxyInstance(
                    RecordingPreparedStatement.class.getClassLoader(),
                    new Class[]{PreparedStatement.class},
                    handler
            );
            return handler;
        }

        PreparedStatement proxy() {
            return proxy;
        }

        int index() {
            return index;
        }

        Object value() {
            return value;
        }

        Integer jdbcType() {
            return jdbcType;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "setObject" -> {
                    index = (Integer) args[0];
                    value = args[1];
                    if (args.length > 2 && args[2] instanceof Integer type) {
                        jdbcType = type;
                    }
                    yield null;
                }
                case "clearParameters", "clearWarnings", "close" -> null;
                case "unwrap" -> null;
                case "isWrapperFor" -> false;
                case "toString" -> "PreparedStatementRecorder";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
