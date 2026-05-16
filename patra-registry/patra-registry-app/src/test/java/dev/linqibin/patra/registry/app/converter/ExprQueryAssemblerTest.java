package dev.linqibin.patra.registry.app.converter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.registry.domain.model.read.expr.ApiParamMappingQuery;
import dev.linqibin.patra.registry.domain.model.read.expr.ExprCapabilityQuery;
import dev.linqibin.patra.registry.domain.model.read.expr.ExprFieldQuery;
import dev.linqibin.patra.registry.domain.model.read.expr.ExprRenderRuleQuery;
import dev.linqibin.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
import dev.linqibin.patra.registry.domain.model.vo.expr.ApiParamMapping;
import dev.linqibin.patra.registry.domain.model.vo.expr.ExprCapability;
import dev.linqibin.patra.registry.domain.model.vo.expr.ExprField;
import dev.linqibin.patra.registry.domain.model.vo.expr.ExprRenderRule;
import dev.linqibin.patra.registry.domain.model.vo.expr.ExprSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/// ExprQueryAssembler 单元测试。
///
/// 测试策略: 使用 MapStruct 生成的实现类进行纯 Java 单元测试, 无需 Spring 容器。
///
/// 测试覆盖:
///
/// - 字段映射验证 - 所有字段正确映射
///   - 集合转换 - List 转换保持顺序
///   - 复杂对象转换 - ExprSnapshot 聚合转换
///   - Null 值处理 - 输入/可选字段为 null 的场景
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ExprQueryAssembler 单元测试")
class ExprQueryAssemblerTest {

  private ExprQueryAssembler assembler;

  @BeforeEach
  void setUp() {
    // 使用 MapStruct 生成的实现类
    assembler = Mappers.getMapper(ExprQueryAssembler.class);
  }

  @Nested
  @DisplayName("toQuery(ExprField) 转换测试")
  class ExprFieldToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 ExprField 为 ExprFieldQuery")
    void shouldConvertCompleteExprField() {
      // Given: 准备完整的 ExprField 对象
      ExprField field =
          new ExprField(
              1L,
              "publish_date",
              "Publication Date",
              "The date when the article was published",
              "DATE",
              "SINGLE",
              true,
              true);

      // When: 执行转换
      ExprFieldQuery result = assembler.toQuery(field);

      // Then: 验证所有字段正确映射 (注意:Query 不包含 id 字段)
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
    @DisplayName("应该正确处理可选字段为空字符串的情况")
    void shouldHandleEmptyOptionalFields() {
      // Given: 可选字段为空字符串
      ExprField field =
          new ExprField(
              2L,
              "article_title",
              null, // 构造函数会转为 ""
              null, // 构造函数会转为 ""
              "TEXT",
              "SINGLE",
              false,
              false);

      // When: 执行转换
      ExprFieldQuery result = assembler.toQuery(field);

      // Then: 验证空字符串正确传递
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
      ExprFieldQuery result = assembler.toQuery((ExprField) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确转换布尔字段")
    void shouldConvertBooleanFields() {
      // Given: 布尔字段不同组合
      ExprField field1 = new ExprField(3L, "field1", "", "", "TEXT", "SINGLE", true, false);
      ExprField field2 = new ExprField(4L, "field2", "", "", "DATE", "MULTI", false, true);

      // When: 执行转换
      ExprFieldQuery result1 = assembler.toQuery(field1);
      ExprFieldQuery result2 = assembler.toQuery(field2);

      // Then: 验证布尔字段正确转换
      assertThat(result1.exposable()).isTrue();
      assertThat(result1.dateField()).isFalse();
      assertThat(result2.exposable()).isFalse();
      assertThat(result2.dateField()).isTrue();
    }
  }

  @Nested
  @DisplayName("toFieldQueries(List<ExprField>) 转换测试")
  class ExprFieldListToQueriesTests {

    @Test
    @DisplayName("应该正确转换 ExprField 列表为 Query 列表")
    void shouldConvertExprFieldList() {
      // Given: 准备 ExprField 列表
      List<ExprField> fields =
          List.of(
              new ExprField(1L, "field1", "Field 1", "Desc 1", "TEXT", "SINGLE", true, false),
              new ExprField(2L, "field2", "Field 2", "Desc 2", "DATE", "MULTI", false, true),
              new ExprField(3L, "field3", "Field 3", "Desc 3", "NUMBER", "SINGLE", true, false));

      // When: 执行转换
      List<ExprFieldQuery> result = assembler.toFieldQueries(fields);

      // Then: 验证列表正确转换,保持顺序
      assertThat(result).isNotNull().hasSize(3);
      assertThat(result.get(0).fieldKey()).isEqualTo("field1");
      assertThat(result.get(1).fieldKey()).isEqualTo("field2");
      assertThat(result.get(2).fieldKey()).isEqualTo("field3");
    }

    @Test
    @DisplayName("当输入列表为空时应该返回空列表")
    void shouldReturnEmptyListWhenInputIsEmpty() {
      // When: 输入空列表
      List<ExprFieldQuery> result = assembler.toFieldQueries(List.of());

      // Then: 返回空列表
      assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      List<ExprFieldQuery> result = assembler.toFieldQueries(null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toQuery(ApiParamMapping) 转换测试")
  class ApiParamMappingToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 ApiParamMapping 为 Query")
    void shouldConvertCompleteApiParamMapping() {
      // Given: 准备完整的 ApiParamMapping 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");
      String notesJson = "{\"description\":\"Date range parameter\"}";

      ApiParamMapping mapping =
          new ApiParamMapping(
              10L,
              1L,
              "HARVEST",
              "esearch",
              "from",
              "mindate",
              "TO_PUBMED_DATE",
              notesJson,
              effectiveFrom,
              effectiveTo);

      // When: 执行转换
      ApiParamMappingQuery result = assembler.toQuery(mapping);

      // Then: 验证所有字段正确映射 (注意:Query 不包含 id 字段)
      assertThat(result).isNotNull();
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.endpointName()).isEqualTo("esearch");
      assertThat(result.stdKey()).isEqualTo("from");
      assertThat(result.providerParamName()).isEqualTo("mindate");
      assertThat(result.transformCode()).isEqualTo("TO_PUBMED_DATE");
      assertThat(result.notesJson()).isEqualTo(notesJson);
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
    }

    @Test
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 只填充必填字段
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      ApiParamMapping mapping =
          new ApiParamMapping(
              11L,
              2L,
              null, // 可选
              null, // 可选
              "term",
              "query",
              null, // 可选
              null, // 可选
              effectiveFrom,
              null); // 可选

      // When: 执行转换
      ApiParamMappingQuery result = assembler.toQuery(mapping);

      // Then: 验证必填字段存在,可选字段为 null
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
      ApiParamMappingQuery result = assembler.toQuery((ApiParamMapping) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toMappingQueries(List<ApiParamMapping>) 转换测试")
  class ApiParamMappingListToQueriesTests {

    @Test
    @DisplayName("应该正确转换 ApiParamMapping 列表为 Query 列表")
    void shouldConvertApiParamMappingList() {
      // Given: 准备 ApiParamMapping 列表
      Instant now = Instant.now();
      List<ApiParamMapping> mappings =
          List.of(
              new ApiParamMapping(1L, 1L, "HARVEST", "ep1", "k1", "p1", null, null, now, null),
              new ApiParamMapping(2L, 1L, "HARVEST", "ep2", "k2", "p2", null, null, now, null),
              new ApiParamMapping(3L, 2L, "FETCH", "ep3", "k3", "p3", null, null, now, null));

      // When: 执行转换
      List<ApiParamMappingQuery> result = assembler.toMappingQueries(mappings);

      // Then: 验证列表正确转换,保持顺序
      assertThat(result).isNotNull().hasSize(3);
      assertThat(result.get(0).stdKey()).isEqualTo("k1");
      assertThat(result.get(1).stdKey()).isEqualTo("k2");
      assertThat(result.get(2).stdKey()).isEqualTo("k3");
    }

    @Test
    @DisplayName("当输入列表为空时应该返回空列表")
    void shouldReturnEmptyListWhenInputIsEmpty() {
      // When: 输入空列表
      List<ApiParamMappingQuery> result = assembler.toMappingQueries(List.of());

      // Then: 返回空列表
      assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      List<ApiParamMappingQuery> result = assembler.toMappingQueries(null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toQuery(ExprCapability) 转换测试")
  class ExprCapabilityToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 ExprCapability 为 Query")
    void shouldConvertCompleteExprCapability() {
      // Given: 准备完整的 ExprCapability 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");
      LocalDate dateMin = LocalDate.of(1900, 1, 1);
      LocalDate dateMax = LocalDate.of(2099, 12, 31);

      ExprCapability capability =
          new ExprCapability(
              20L,
              1L,
              "HARVEST",
              "publish_date",
              effectiveFrom,
              effectiveTo,
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
              "[a-z]+");

      // When: 执行转换
      ExprCapabilityQuery result = assembler.toQuery(capability);

      // Then: 验证所有字段正确映射 (注意:Query 不包含 id 字段)
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
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 只填充必填字段
      Instant now = Instant.now();

      ExprCapability capability =
          new ExprCapability(
              21L, 2L, null, // 可选
              "title", now, null, // 可选
              null, // 可选
              null, // 可选
              false, null, // 可选
              false, false, 0, 0, null, // 可选
              0, false, "NONE", false, false, false, null, // 可选
              null, // 可选
              null, // 可选
              null, // 可选
              null, // 可选
              null, // 可选
              false, null, // 可选
              null); // 可选

      // When: 执行转换
      ExprCapabilityQuery result = assembler.toQuery(capability);

      // Then: 验证必填字段存在,可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.provenanceId()).isEqualTo(2L);
      assertThat(result.operationType()).isNull();
      assertThat(result.fieldKey()).isEqualTo("title");
      assertThat(result.effectiveFrom()).isEqualTo(now);
      assertThat(result.effectiveTo()).isNull();
      assertThat(result.opsJson()).isNull();
      assertThat(result.negatableOpsJson()).isNull();
      assertThat(result.supportsNot()).isFalse();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      ExprCapabilityQuery result = assembler.toQuery((ExprCapability) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toCapabilityQueries(List<ExprCapability>) 转换测试")
  class ExprCapabilityListToQueriesTests {

    @Test
    @DisplayName("应该正确转换 ExprCapability 列表为 Query 列表")
    void shouldConvertExprCapabilityList() {
      // Given: 准备 ExprCapability 列表
      Instant now = Instant.now();
      List<ExprCapability> capabilities =
          List.of(
              new ExprCapability(
                  1L, 1L, null, "field1", now, null, null, null, false, null, false, false, 0, 0,
                  null, 0, false, "NONE", false, false, false, null, null, null, null, null, null,
                  false, null, null),
              new ExprCapability(
                  2L, 1L, null, "field2", now, null, null, null, false, null, false, false, 0, 0,
                  null, 0, false, "NONE", false, false, false, null, null, null, null, null, null,
                  false, null, null),
              new ExprCapability(
                  3L, 2L, null, "field3", now, null, null, null, false, null, false, false, 0, 0,
                  null, 0, false, "NONE", false, false, false, null, null, null, null, null, null,
                  false, null, null));

      // When: 执行转换
      List<ExprCapabilityQuery> result = assembler.toCapabilityQueries(capabilities);

      // Then: 验证列表正确转换,保持顺序
      assertThat(result).isNotNull().hasSize(3);
      assertThat(result.get(0).fieldKey()).isEqualTo("field1");
      assertThat(result.get(1).fieldKey()).isEqualTo("field2");
      assertThat(result.get(2).fieldKey()).isEqualTo("field3");
    }

    @Test
    @DisplayName("当输入列表为空时应该返回空列表")
    void shouldReturnEmptyListWhenInputIsEmpty() {
      // When: 输入空列表
      List<ExprCapabilityQuery> result = assembler.toCapabilityQueries(List.of());

      // Then: 返回空列表
      assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      List<ExprCapabilityQuery> result = assembler.toCapabilityQueries(null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toQuery(ExprRenderRule) 转换测试")
  class ExprRenderRuleToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 ExprRenderRule 为 Query")
    void shouldConvertCompleteExprRenderRule() {
      // Given: 准备完整的 ExprRenderRule 对象
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-01-01T00:00:00Z");
      String paramsJson = "{\"from\":\"mindate\",\"to\":\"maxdate\"}";

      ExprRenderRule rule =
          new ExprRenderRule(
              30L,
              1L,
              "HARVEST",
              "publish_date",
              "RANGE",
              "EXACT",
              true,
              "DATE",
              "PARAMS",
              "EXACT",
              "T",
              "DATE",
              effectiveFrom,
              effectiveTo,
              "{{from}} TO {{to}}",
              "{{item}}",
              "OR",
              true,
              paramsJson,
              "PUBMED_DATETYPE");

      // When: 执行转换
      ExprRenderRuleQuery result = assembler.toQuery(rule);

      // Then: 验证所有字段正确映射 (注意:Query 不包含 id/matchTypeKey/negatedKey/valueTypeKey 字段)
      assertThat(result).isNotNull();
      assertThat(result.provenanceId()).isEqualTo(1L);
      assertThat(result.operationType()).isEqualTo("HARVEST");
      assertThat(result.fieldKey()).isEqualTo("publish_date");
      assertThat(result.opCode()).isEqualTo("RANGE");
      assertThat(result.matchTypeCode()).isEqualTo("EXACT");
      assertThat(result.negated()).isTrue();
      assertThat(result.valueTypeCode()).isEqualTo("DATE");
      assertThat(result.emitTypeCode()).isEqualTo("PARAMS");
      assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(result.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(result.template()).isEqualTo("{{from}} TO {{to}}");
      assertThat(result.itemTemplate()).isEqualTo("{{item}}");
      assertThat(result.joiner()).isEqualTo("OR");
      assertThat(result.wrapGroup()).isTrue();
      assertThat(result.paramsJson()).isEqualTo(paramsJson);
      assertThat(result.functionCode()).isEqualTo("PUBMED_DATETYPE");
    }

    @Test
    @DisplayName("应该正确处理可选字段为 null 的情况")
    void shouldHandleNullOptionalFields() {
      // Given: 只填充必填字段
      Instant now = Instant.now();

      ExprRenderRule rule =
          new ExprRenderRule(
              31L,
              2L,
              null, // 可选
              "title",
              "TERM",
              null, // 可选
              null, // 可选
              null, // 可选
              "QUERY",
              "ANY",
              "ANY",
              "ANY",
              now,
              null, // 可选
              "{{term}}",
              null, // 可选
              null, // 可选
              false,
              null, // 可选
              null); // 可选

      // When: 执行转换
      ExprRenderRuleQuery result = assembler.toQuery(rule);

      // Then: 验证必填字段存在,可选字段为 null
      assertThat(result).isNotNull();
      assertThat(result.provenanceId()).isEqualTo(2L);
      assertThat(result.operationType()).isNull();
      assertThat(result.fieldKey()).isEqualTo("title");
      assertThat(result.opCode()).isEqualTo("TERM");
      assertThat(result.matchTypeCode()).isNull();
      assertThat(result.negated()).isNull();
      assertThat(result.valueTypeCode()).isNull();
      assertThat(result.emitTypeCode()).isEqualTo("QUERY");
      assertThat(result.effectiveTo()).isNull();
      assertThat(result.template()).isEqualTo("{{term}}");
      assertThat(result.itemTemplate()).isNull();
      assertThat(result.joiner()).isNull();
      assertThat(result.wrapGroup()).isFalse();
      assertThat(result.paramsJson()).isNull();
      assertThat(result.functionCode()).isNull();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      ExprRenderRuleQuery result = assembler.toQuery((ExprRenderRule) null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toRenderRuleQueries(List<ExprRenderRule>) 转换测试")
  class ExprRenderRuleListToQueriesTests {

    @Test
    @DisplayName("应该正确转换 ExprRenderRule 列表为 Query 列表")
    void shouldConvertExprRenderRuleList() {
      // Given: 准备 ExprRenderRule 列表
      Instant now = Instant.now();
      List<ExprRenderRule> rules =
          List.of(
              new ExprRenderRule(
                  1L, 1L, null, "f1", "TERM", null, null, null, "QUERY", "ANY", "ANY", "ANY", now,
                  null, "{{t}}", null, null, false, null, null),
              new ExprRenderRule(
                  2L, 1L, null, "f2", "IN", null, null, null, "QUERY", "ANY", "ANY", "ANY", now,
                  null, "{{t}}", null, null, false, null, null),
              new ExprRenderRule(
                  3L, 2L, null, "f3", "RANGE", null, null, null, "PARAMS", "ANY", "ANY", "ANY", now,
                  null, "{{t}}", null, null, false, null, null));

      // When: 执行转换
      List<ExprRenderRuleQuery> result = assembler.toRenderRuleQueries(rules);

      // Then: 验证列表正确转换,保持顺序
      assertThat(result).isNotNull().hasSize(3);
      assertThat(result.get(0).fieldKey()).isEqualTo("f1");
      assertThat(result.get(1).fieldKey()).isEqualTo("f2");
      assertThat(result.get(2).fieldKey()).isEqualTo("f3");
    }

    @Test
    @DisplayName("当输入列表为空时应该返回空列表")
    void shouldReturnEmptyListWhenInputIsEmpty() {
      // When: 输入空列表
      List<ExprRenderRuleQuery> result = assembler.toRenderRuleQueries(List.of());

      // Then: 返回空列表
      assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("当输入为 null 时应该返回 null")
    void shouldReturnNullWhenInputIsNull() {
      // When: 输入 null
      List<ExprRenderRuleQuery> result = assembler.toRenderRuleQueries(null);

      // Then: 返回 null
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("toQuery(ExprSnapshot) 转换测试")
  class ExprSnapshotToQueryTests {

    @Test
    @DisplayName("应该正确转换完整的 ExprSnapshot 为 ExprSnapshotQuery")
    void shouldConvertCompleteExprSnapshot() {
      // Given: 准备完整的 ExprSnapshot 聚合根
      List<ExprField> fields =
          List.of(
              new ExprField(1L, "field1", "Field 1", "Desc 1", "TEXT", "SINGLE", true, false),
              new ExprField(2L, "field2", "Field 2", "Desc 2", "DATE", "SINGLE", true, true));

      Instant now = Instant.now();
      List<ExprCapability> capabilities =
          List.of(
              new ExprCapability(
                  10L, 1L, null, "field1", now, null, null, null, false, null, false, false, 0, 0,
                  null, 0, false, "NONE", false, false, false, null, null, null, null, null, null,
                  false, null, null));

      List<ExprRenderRule> renderRules =
          List.of(
              new ExprRenderRule(
                  20L, 1L, null, "field1", "TERM", null, null, null, "QUERY", "ANY", "ANY", "ANY",
                  now, null, "{{t}}", null, null, false, null, null));

      List<ApiParamMapping> apiParamMappings =
          List.of(new ApiParamMapping(30L, 1L, "HARVEST", "ep", "k", "p", null, null, now, null));

      ExprSnapshot snapshot = new ExprSnapshot(fields, capabilities, renderRules, apiParamMappings);

      // When: 执行转换
      ExprSnapshotQuery result = assembler.toQuery(snapshot);

      // Then: 验证 ExprSnapshot 正确转换,包含所有子对象
      assertThat(result).isNotNull();
      assertThat(result.fields()).isNotNull().hasSize(2);
      assertThat(result.fields().get(0).fieldKey()).isEqualTo("field1");
      assertThat(result.fields().get(1).fieldKey()).isEqualTo("field2");

      assertThat(result.capabilities()).isNotNull().hasSize(1);
      assertThat(result.capabilities().get(0).fieldKey()).isEqualTo("field1");

      assertThat(result.renderRules()).isNotNull().hasSize(1);
      assertThat(result.renderRules().get(0).fieldKey()).isEqualTo("field1");

      assertThat(result.apiParamMappings()).isNotNull().hasSize(1);
      assertThat(result.apiParamMappings().get(0).stdKey()).isEqualTo("k");
    }

    @Test
    @DisplayName("应该正确处理空列表的 ExprSnapshot")
    void shouldHandleExprSnapshotWithEmptyLists() {
      // Given: ExprSnapshot 包含空列表
      ExprSnapshot snapshot = new ExprSnapshot(List.of(), List.of(), List.of(), List.of());

      // When: 执行转换
      ExprSnapshotQuery result = assembler.toQuery(snapshot);

      // Then: 验证返回的 Query 包含空列表
      assertThat(result).isNotNull();
      assertThat(result.fields()).isNotNull().isEmpty();
      assertThat(result.capabilities()).isNotNull().isEmpty();
      assertThat(result.renderRules()).isNotNull().isEmpty();
      assertThat(result.apiParamMappings()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("当输入为 null 时应该抛出 NullPointerException")
    void shouldThrowNullPointerExceptionWhenInputIsNull() {
      // When & Then: 输入 null 应该抛出 NullPointerException (default 方法不进行 null 检查)
      org.junit.jupiter.api.Assertions.assertThrows(
          NullPointerException.class, () -> assembler.toQuery((ExprSnapshot) null));
    }
  }
}
