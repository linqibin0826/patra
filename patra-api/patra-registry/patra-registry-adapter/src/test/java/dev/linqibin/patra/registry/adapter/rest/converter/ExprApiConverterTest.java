package dev.linqibin.patra.registry.adapter.rest.converter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.registry.api.dto.expr.ApiParamMappingResp;
import dev.linqibin.patra.registry.api.dto.expr.ExprCapabilityResp;
import dev.linqibin.patra.registry.api.dto.expr.ExprFieldResp;
import dev.linqibin.patra.registry.api.dto.expr.ExprRenderRuleResp;
import dev.linqibin.patra.registry.api.dto.expr.ExprSnapshotResp;
import dev.linqibin.patra.registry.domain.model.read.expr.ApiParamMappingQuery;
import dev.linqibin.patra.registry.domain.model.read.expr.ExprCapabilityQuery;
import dev.linqibin.patra.registry.domain.model.read.expr.ExprFieldQuery;
import dev.linqibin.patra.registry.domain.model.read.expr.ExprRenderRuleQuery;
import dev.linqibin.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/// ExprApiConverter 单元测试。
///
/// 测试策略: 使用 MapStruct 生成的实现类进行纯 Java 单元测试, 无需 Spring 容器。
///
/// 测试覆盖:
///
/// - 字段映射验证 - Query → Resp 所有字段正确映射
///   - List 转换 - 集合转换保持迭代顺序
///   - Null 值处理 - 输入为 null 或可选字段为 null 的场景
///   - 布尔字段转换 - boolean 类型正确映射
///   - 时间类型转换 - Instant/LocalDate 正确转换
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ExprApiConverter 单元测试")
class ExprApiConverterTest {

  private ExprApiConverter converter;

  @BeforeEach
  void setUp() {
    // 使用 MapStruct 生成的实现类
    converter = Mappers.getMapper(ExprApiConverter.class);
  }

  @Nested
  @DisplayName("toResp(ExprFieldQuery) 转换测试")
  class ExprFieldQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的字段查询对象为 Resp")
    void shouldConvertCompleteFieldQuery() {
      // Given: 准备完整的 Query 对象
      ExprFieldQuery query =
          new ExprFieldQuery(
              "publish_date",
              "Publication Date",
              "The date when the article was published",
              "DATE",
              "SINGLE",
              true,
              true);

      // When: 执行转换
      ExprFieldResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.fieldKey()).isEqualTo("publish_date");
      assertThat(result.displayName()).isEqualTo("Publication Date");
      assertThat(result.description()).isEqualTo("The date when the article was published");
      assertThat(result.dataTypeCode()).isEqualTo("DATE");
      assertThat(result.cardinalityCode()).isEqualTo("SINGLE");
      assertThat(result.exposable()).isTrue();
      assertThat(result.dateField()).isTrue();
    }

    @Test
    @DisplayName("应该正确处理可选字段为空的情况")
    void shouldHandleEmptyOptionalFields() {
      // Given: 可选字段为空字符串
      ExprFieldQuery query =
          new ExprFieldQuery("article_title", "", "", "TEXT", "SINGLE", false, false);

      // When: 执行转换
      ExprFieldResp result = converter.toResp(query);

      // Then: 验证可选字段为空字符串
      assertThat(result).isNotNull();
      assertThat(result.fieldKey()).isEqualTo("article_title");
      assertThat(result.displayName()).isEmpty();
      assertThat(result.description()).isEmpty();
      assertThat(result.dataTypeCode()).isEqualTo("TEXT");
      assertThat(result.cardinalityCode()).isEqualTo("SINGLE");
      assertThat(result.exposable()).isFalse();
      assertThat(result.dateField()).isFalse();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      ExprFieldResp result = converter.toResp((ExprFieldQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确转换布尔字段 exposable 为 true")
    void shouldConvertExposableToTrue() {
      // Given: exposable 为 true
      ExprFieldQuery query =
          new ExprFieldQuery("author", "Author", "Author name", "TEXT", "MULTI", true, false);

      // When: 执行转换
      ExprFieldResp result = converter.toResp(query);

      // Then: 验证 exposable 为 true
      assertThat(result.exposable()).isTrue();
    }

    @Test
    @DisplayName("应该正确转换日期字段标记")
    void shouldConvertDateFieldFlag() {
      // Given: dateField 为 true
      ExprFieldQuery query =
          new ExprFieldQuery("created_date", "Created Date", "", "DATETIME", "SINGLE", true, true);

      // When: 执行转换
      ExprFieldResp result = converter.toResp(query);

      // Then: 验证 dateField 为 true
      assertThat(result.dateField()).isTrue();
    }
  }

  @Nested
  @DisplayName("toResp(List<ExprFieldQuery>) 转换测试")
  class ExprFieldQueryListToRespTests {

    @Test
    @DisplayName("应该正确转换字段查询对象列表")
    void shouldConvertFieldQueryList() {
      // Given: 准备查询对象列表
      ExprFieldQuery query1 =
          new ExprFieldQuery("field1", "Field 1", "desc1", "TEXT", "SINGLE", true, false);
      ExprFieldQuery query2 =
          new ExprFieldQuery("field2", "Field 2", "desc2", "NUMBER", "MULTI", false, true);
      List<ExprFieldQuery> queries = List.of(query1, query2);

      // When: 执行转换
      List<ExprFieldResp> result = converter.toResp(queries);

      // Then: 验证列表转换正确
      assertThat(result).hasSize(2);
      assertThat(result.get(0).fieldKey()).isEqualTo("field1");
      assertThat(result.get(1).fieldKey()).isEqualTo("field2");
    }

    @Test
    @DisplayName("当输入列表为空时应该返回空列表")
    void shouldReturnEmptyListWhenInputIsEmpty() {
      // When: 输入空列表
      List<ExprFieldResp> result = converter.toResp(List.of());

      // Then: 返回空列表
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      List<ExprFieldResp> result = converter.toResp((List<ExprFieldQuery>) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toResp(ApiParamMappingQuery) 转换测试")
  class ApiParamMappingQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的 API 参数映射查询对象")
    void shouldConvertCompleteApiParamMappingQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              1L,
              "HARVEST",
              "esearch",
              "from",
              "mindate",
              "TO_PUBMED_DATE",
              "{\"description\":\"Date range parameter\"}",
              effectiveFrom,
              effectiveTo);

      // When: 执行转换
      ApiParamMappingResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.endpointName()).isEqualTo("esearch");
      assertThat(result.stdKey()).isEqualTo("from");
      assertThat(result.providerParamName()).isEqualTo("mindate");
      assertThat(result.transformCode()).isEqualTo("TO_PUBMED_DATE");
      assertThat(result.notesJson()).isEqualTo("{\"description\":\"Date range parameter\"}");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
    }

    @Test
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 可选字段为 null
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      ApiParamMappingQuery query =
          new ApiParamMappingQuery(
              2L, null, null, "term", "query", null, null, effectiveFrom, null);

      // When: 执行转换
      ApiParamMappingResp result = converter.toResp(query);

      // Then: 验证可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.provenanceId()).isEqualTo(2L);
      assertThat(result.operationType()).isNull();
      assertThat(result.endpointName()).isNull();
      assertThat(result.stdKey()).isEqualTo("term");
      assertThat(result.providerParamName()).isEqualTo("query");
      assertThat(result.transformCode()).isNull();
      assertThat(result.notesJson()).isNull();
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      ApiParamMappingResp result = converter.toResp((ApiParamMappingQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toResp(ExprCapabilityQuery) 转换测试")
  class ExprCapabilityQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的表达式能力查询对象")
    void shouldConvertCompleteExprCapabilityQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");
      LocalDate dateMin = LocalDate.of(1900, 1, 1);
      LocalDate dateMax = LocalDate.of(2099, 12, 31);

      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              1L,
              "HARVEST",
              "publish_date",
              "[\"TERM\",\"IN\",\"RANGE\"]",
              "[\"TERM\",\"IN\"]",
              true,
              "[\"PHRASE\",\"EXACT\"]",
              true,
              false,
              3,
              100,
              "[a-zA-Z0-9]+",
              50,
              false,
              "DATE",
              true,
              true,
              false,
              dateMin,
              dateMax,
              effectiveFrom,
              effectiveTo,
              BigDecimal.ZERO,
              BigDecimal.valueOf(1000),
              true,
              "[\"owner\",\"pmcid\"]",
              "[a-z]+",
              effectiveFrom,
              effectiveTo);

      // When: 执行转换
      ExprCapabilityResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.fieldKey()).isEqualTo("publish_date");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(result.opsJson()).isEqualTo("[\"TERM\",\"IN\",\"RANGE\"]");
      assertThat(result.negatableOpsJson()).isEqualTo("[\"TERM\",\"IN\"]");
      assertThat(result.supportsNot()).isTrue();
      assertThat(result.termMatchesJson()).isEqualTo("[\"PHRASE\",\"EXACT\"]");
      assertThat(result.termCaseSensitiveAllowed()).isTrue();
      assertThat(result.termAllowBlank()).isFalse();
      assertThat(result.termMinLength()).isEqualTo(3);
      assertThat(result.termMaxLength()).isEqualTo(100);
      assertThat(result.termPattern()).isEqualTo("[a-zA-Z0-9]+");
      assertThat(result.inMaxSize()).isEqualTo(50);
      assertThat(result.inCaseSensitiveAllowed()).isFalse();
      assertThat(result.rangeKindCode()).isEqualTo("DATE");
      assertThat(result.rangeAllowOpenStart()).isTrue();
      assertThat(result.rangeAllowOpenEnd()).isTrue();
      assertThat(result.rangeAllowClosedAtInfinity()).isFalse();
      assertThat(result.dateMin()).isEqualTo(dateMin);
      assertThat(result.dateMax()).isEqualTo(dateMax);
      assertThat(result.datetimeMin()).isEqualTo(effectiveFrom);
      assertThat(result.datetimeMax()).isEqualTo(effectiveTo);
      assertThat(result.numberMin()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(result.numberMax()).isEqualByComparingTo(BigDecimal.valueOf(1000));
      assertThat(result.existsSupported()).isTrue();
      assertThat(result.tokenKindsJson()).isEqualTo("[\"owner\",\"pmcid\"]");
      assertThat(result.tokenValuePattern()).isEqualTo("[a-z]+");
    }

    @Test
    @DisplayName("应该正确处理布尔字段为 false 的情况")
    void shouldHandleBooleanFieldsAsFalse() {
      // Given: 布尔字段为 false
      Instant effectiveFrom = Instant.now();

      ExprCapabilityQuery query =
          new ExprCapabilityQuery(
              2L,
              null,
              "title",
              null,
              null,
              false,
              null,
              false,
              false,
              0,
              0,
              null,
              0,
              false,
              "NONE",
              false,
              false,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null,
              effectiveFrom,
              null);

      // When: 执行转换
      ExprCapabilityResp result = converter.toResp(query);

      // Then: 验证布尔字段为 false
      assertThat(result).isNotNull();
      assertThat(result.supportsNot()).isFalse();
      assertThat(result.termCaseSensitiveAllowed()).isFalse();
      assertThat(result.termAllowBlank()).isFalse();
      assertThat(result.inCaseSensitiveAllowed()).isFalse();
      assertThat(result.rangeAllowOpenStart()).isFalse();
      assertThat(result.rangeAllowOpenEnd()).isFalse();
      assertThat(result.rangeAllowClosedAtInfinity()).isFalse();
      assertThat(result.existsSupported()).isFalse();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      ExprCapabilityResp result = converter.toResp((ExprCapabilityQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toResp(ExprRenderRuleQuery) 转换测试")
  class ExprRenderRuleQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的表达式渲染规则查询对象")
    void shouldConvertCompleteExprRenderRuleQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");

      ExprRenderRuleQuery query =
          new ExprRenderRuleQuery(
              1L,
              "HARVEST",
              "publish_date",
              "RANGE",
              "EXACT",
              true,
              "DATE",
              "PARAMS",
              "{{from}} TO {{to}}",
              "{{item}}",
              "OR",
              true,
              "{\"from\":\"mindate\",\"to\":\"maxdate\"}",
              "PUBMED_DATETYPE",
              effectiveFrom,
              effectiveTo);

      // When: 执行转换
      ExprRenderRuleResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.fieldKey()).isEqualTo("publish_date");
      assertThat(result.opCode()).isEqualTo("RANGE");
      assertThat(result.matchTypeCode()).isEqualTo("EXACT");
      assertThat(result.negated()).isTrue();
      assertThat(result.valueTypeCode()).isEqualTo("DATE");
      assertThat(result.emitTypeCode()).isEqualTo("PARAMS");
      assertThat(result.template()).isEqualTo("{{from}} TO {{to}}");
      assertThat(result.itemTemplate()).isEqualTo("{{item}}");
      assertThat(result.joiner()).isEqualTo("OR");
      assertThat(result.wrapGroup()).isTrue();
      assertThat(result.paramsJson()).isEqualTo("{\"from\":\"mindate\",\"to\":\"maxdate\"}");
      assertThat(result.functionCode()).isEqualTo("PUBMED_DATETYPE");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
    }

    @Test
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 可选字段为 null
      Instant effectiveFrom = Instant.now();

      ExprRenderRuleQuery query =
          new ExprRenderRuleQuery(
              2L,
              null,
              "title",
              "TERM",
              null,
              null,
              null,
              "QUERY",
              "{{term}}",
              null,
              null,
              false,
              null,
              null,
              effectiveFrom,
              null);

      // When: 执行转换
      ExprRenderRuleResp result = converter.toResp(query);

      // Then: 验证可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.provenanceId()).isEqualTo(2L);
      assertThat(result.operationType()).isNull();
      assertThat(result.fieldKey()).isEqualTo("title");
      assertThat(result.opCode()).isEqualTo("TERM");
      assertThat(result.matchTypeCode()).isNull();
      assertThat(result.negated()).isNull();
      assertThat(result.valueTypeCode()).isNull();
      assertThat(result.emitTypeCode()).isEqualTo("QUERY");
      assertThat(result.template()).isEqualTo("{{term}}");
      assertThat(result.itemTemplate()).isNull();
      assertThat(result.joiner()).isNull();
      assertThat(result.wrapGroup()).isFalse();
      assertThat(result.paramsJson()).isNull();
      assertThat(result.functionCode()).isNull();
      assertThat(result.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      ExprRenderRuleResp result = converter.toResp((ExprRenderRuleQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确转换 negated 布尔字段为 false")
    void shouldConvertNegatedBooleanToFalse() {
      // Given: negated 为 false
      Instant effectiveFrom = Instant.now();

      ExprRenderRuleQuery query =
          new ExprRenderRuleQuery(
              3L,
              null,
              "abstract",
              "TERM",
              null,
              false,
              null,
              "QUERY",
              "{{term}}",
              null,
              null,
              false,
              null,
              null,
              effectiveFrom,
              null);

      // When: 执行转换
      ExprRenderRuleResp result = converter.toResp(query);

      // Then: 验证 negated 为 false
      assertThat(result.negated()).isFalse();
    }

    @Test
    @DisplayName("应该正确转换 wrapGroup 布尔字段为 true")
    void shouldConvertWrapGroupToTrue() {
      // Given: wrapGroup 为 true
      Instant effectiveFrom = Instant.now();

      ExprRenderRuleQuery query =
          new ExprRenderRuleQuery(
              4L,
              null,
              "keyword",
              "IN",
              null,
              null,
              null,
              "QUERY",
              "{{items}}",
              "{{item}}",
              "OR",
              true,
              null,
              null,
              effectiveFrom,
              null);

      // When: 执行转换
      ExprRenderRuleResp result = converter.toResp(query);

      // Then: 验证 wrapGroup 为 true
      assertThat(result.wrapGroup()).isTrue();
    }
  }

  @Nested
  @DisplayName("toResp(ExprSnapshotQuery) 转换测试")
  class ExprSnapshotQueryToRespTests {

    @Test
    @DisplayName("应该正确转换完整的表达式快照查询对象")
    void shouldConvertCompleteExprSnapshotQuery() {
      // Given: 准备完整的 Query 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      ExprSnapshotQuery query =
          new ExprSnapshotQuery(
              List.of(new ExprFieldQuery("field1", "Field 1", "", "TEXT", "SINGLE", true, false)),
              List.of(
                  new ExprCapabilityQuery(
                      1L,
                      "HARVEST",
                      "field1",
                      null,
                      null,
                      false,
                      null,
                      false,
                      false,
                      0,
                      0,
                      null,
                      0,
                      false,
                      "NONE",
                      false,
                      false,
                      false,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      null,
                      null,
                      effectiveFrom,
                      null)),
              List.of(
                  new ExprRenderRuleQuery(
                      1L,
                      "HARVEST",
                      "field1",
                      "TERM",
                      null,
                      null,
                      null,
                      "QUERY",
                      "{{term}}",
                      null,
                      null,
                      false,
                      null,
                      null,
                      effectiveFrom,
                      null)),
              List.of(
                  new ApiParamMappingQuery(
                      1L, "HARVEST", "esearch", "term", "query", null, null, effectiveFrom, null)));

      // When: 执行转换
      ExprSnapshotResp result = converter.toResp(query);

      // Then: 验证所有字段正确映射
      assertThat(result).isNotNull();
      assertThat(result.fields()).hasSize(1);
      assertThat(result.fields().get(0).fieldKey()).isEqualTo("field1");
      assertThat(result.apiParamMappings()).hasSize(1);
      assertThat(result.apiParamMappings().get(0).provenanceId()).isEqualTo(1L);
      assertThat(result.capabilities()).hasSize(1);
      assertThat(result.capabilities().get(0).provenanceId()).isEqualTo(1L);
      assertThat(result.renderRules()).hasSize(1);
      assertThat(result.renderRules().get(0).provenanceId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该正确处理空列表字段")
    void shouldHandleEmptyListFields() {
      // Given: 列表字段为空
      ExprSnapshotQuery query = new ExprSnapshotQuery(List.of(), List.of(), List.of(), List.of());

      // When: 执行转换
      ExprSnapshotResp result = converter.toResp(query);

      // Then: 验证列表字段为空
      assertThat(result).isNotNull();
      assertThat(result.fields()).isEmpty();
      assertThat(result.apiParamMappings()).isEmpty();
      assertThat(result.capabilities()).isEmpty();
      assertThat(result.renderRules()).isEmpty();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      ExprSnapshotResp result = converter.toResp((ExprSnapshotQuery) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }
}
