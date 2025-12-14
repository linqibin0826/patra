package com.patra.catalog.infra.persistence.typehandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.common.json.JsonMapperHolder;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/// `List<Society>` 专用 TypeHandler。
///
/// **设计原因**：
///
/// MyBatis-Plus 的 `JacksonTypeHandler` 对于泛型集合类型存在类型擦除问题。
/// 反序列化 `List<Society>` 时，Java 泛型擦除导致 Jackson 无法推断元素类型，
/// 会将元素反序列化为 `LinkedHashMap` 而非 `Society` 对象。
///
/// **解决方案**：
///
/// 使用 `TypeReference<List<Society>>` 显式指定泛型类型，避免类型擦除。
///
/// **使用方式**：
///
/// ```java
/// @TableField(value = "affiliated_societies", typeHandler = SocietyListTypeHandler.class)
/// private List<Society> affiliatedSocieties;
/// ```
///
/// @author linqibin
/// @since 0.7.0
@MappedTypes(List.class)
@MappedJdbcTypes(
    value = {JdbcType.VARCHAR, JdbcType.OTHER},
    includeNullJdbcType = true)
public class SocietyListTypeHandler extends BaseTypeHandler<List<Society>> {

  private static final TypeReference<List<Society>> TYPE_REF = new TypeReference<>() {};

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, List<Society> parameter, JdbcType jdbcType) throws SQLException {
    try {
      String json = JsonMapperHolder.getObjectMapper().writeValueAsString(parameter);
      ps.setString(i, json);
    } catch (Exception e) {
      throw new SQLException("序列化 List<Society> 为 JSON 失败", e);
    }
  }

  @Override
  public List<Society> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  @Override
  public List<Society> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  @Override
  public List<Society> getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  /// 解析 JSON 字符串为 `List<Society>`。
  ///
  /// @param json JSON 字符串
  /// @return Society 列表，null 或空字符串返回空列表
  /// @throws SQLException 解析失败时抛出
  private List<Society> parse(String json) throws SQLException {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return JsonMapperHolder.getObjectMapper().readValue(json, TYPE_REF);
    } catch (Exception e) {
      throw new SQLException("解析 JSON 为 List<Society> 失败: " + preview(json), e);
    }
  }

  /// 生成字符串预览（用于错误消息）。
  private String preview(String s) {
    final int maxLength = 100;
    return s.length() <= maxLength ? s : s.substring(0, maxLength) + "...";
  }
}
