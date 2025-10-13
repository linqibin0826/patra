package com.patra.starter.mybatis.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonToMapTypeHandlerTest {

  private JsonToMapTypeHandler handler;

  @BeforeEach
  void setUp() {
    handler = new JsonToMapTypeHandler(new ObjectMapper());
  }

  @Test
  void setNonNullParameter_shouldSerializeMap() throws Exception {
    JsonToJsonNodeTypeHandlerTest.RecordingPreparedStatement recorder =
        JsonToJsonNodeTypeHandlerTest.RecordingPreparedStatement.create();
    Map<String, Object> value = Map.of("k", "v");

    handler.setNonNullParameter(recorder.proxy(), 1, value, JdbcType.VARCHAR);

    assertThat(recorder.index()).isEqualTo(1);
    assertThat(recorder.value()).isEqualTo("{\"k\":\"v\"}");
    assertThat(recorder.jdbcType()).isEqualTo(JdbcType.VARCHAR.TYPE_CODE);
  }

  @Test
  void getNullableResult_shouldParseJsonIntoMap() throws Exception {
    ResultSet rs = singleStringResultSet("{\"k\":123}");
    Map<String, Object> value = handler.getNullableResult(rs, "data");
    assertThat(value).containsEntry("k", 123);
  }

  @Test
  void getNullableResult_shouldReturnNullForBlank() throws Exception {
    ResultSet rs = singleStringResultSet("   ");
    assertThat(handler.getNullableResult(rs, "data")).isNull();
  }

  @Test
  void getNullableResult_shouldRejectNonObjectJson() throws Exception {
    ResultSet rs = singleStringResultSet("[]");
    assertThatThrownBy(() -> handler.getNullableResult(rs, "data"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("JSON value is not an object");
  }

  private ResultSet singleStringResultSet(String value) {
    return (ResultSet)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[] {ResultSet.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getString" -> value;
                  case "close", "clearWarnings" -> null;
                  case "wasNull" -> value == null;
                  case "unwrap" -> null;
                  case "isWrapperFor" -> false;
                  case "toString" -> "ResultSetStringStub";
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }
}
