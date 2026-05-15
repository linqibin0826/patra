package dev.linqibin.patra.registry.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import dev.linqibin.patra.registry.domain.exception.dictionary.DictionaryStandardDisabledException;
import dev.linqibin.patra.registry.domain.exception.dictionary.DictionaryStandardNotFoundException;
import dev.linqibin.patra.registry.domain.exception.dictionary.DictionaryTypeNotFoundException;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryItemListResult;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryItemSummary;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryResolveQuery;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryResolveStatus;
import dev.linqibin.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import dev.linqibin.patra.registry.domain.model.vo.dictionary.DictionaryType;
import dev.linqibin.patra.registry.domain.model.vo.reference.ReferenceStandard;
import dev.linqibin.patra.registry.domain.port.DictionaryRepository;
import dev.linqibin.patra.registry.domain.port.ReferenceStandardRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// DictionaryQueryService 单元测试。
///
/// 测试覆盖:
///
/// **resolveBatch()**:
/// - ✅ 规范标准场景 - 通过 item_code 直接解析
/// - ✅ 非规范标准场景 - 通过外部别名解析
/// - ✅ 边界场景 - 空白值返回 UNKNOWN
/// - ✅ 边界场景 - 空列表直接返回空结果
/// - ✅ 异常场景 - 字典类型不存在抛出异常
/// - ✅ 异常场景 - sourceStandard 为空时抛出异常
/// - ✅ 状态场景 - 字典项被禁用返回 DISABLED
///
/// **listItems()**:
/// - ✅ 有效类型 - 返回所有启用项
/// - ✅ 带 labelStandard - 返回含本地化标签的结果
/// - ✅ 类型不存在 - 抛出异常
/// - ✅ 标准不存在 - 抛出异常
/// - ✅ 标准被禁用 - 抛出异常
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("DictionaryQueryService 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class DictionaryQueryServiceTest {

  @Mock private DictionaryRepository repository;
  @Mock private ReferenceStandardRepository standardRepository;

  private DictionaryQueryService queryService;

  @BeforeEach
  void setUp() {
    queryService = new DictionaryQueryService(repository, standardRepository);
  }

  @Nested
  @DisplayName("resolveBatch() 方法测试")
  class ResolveBatchTests {

    @Test
    @DisplayName("规范标准应直接通过 item_code 解析，跳过别名查找")
    void shouldResolveByItemCodeWhenCanonicalStandard() {
      String typeCode = "COUNTRY";
      String sourceStandard = "ISO_3166_1_ALPHA2";
      List<String> rawValues = List.of("US");

      DictionaryType type = new DictionaryType(1L, "country");
      DictionaryItem item = new DictionaryItem(10L, 1L, "US", "United States", true);
      // canonical = true 表示这是规范标准
      ReferenceStandard standard =
          new ReferenceStandard(
              20L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", true, true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "ISO_3166_1_ALPHA2"))
          .thenReturn(Optional.of(standard));
      when(repository.findItemsByTypeAndCodes(1L, Set.of("US"))).thenReturn(Map.of("US", item));

      DictionaryResolveQuery result =
          queryService.resolveBatch(typeCode, sourceStandard, rawValues);

      assertThat(result.typeCode()).isEqualTo("country");
      assertThat(result.sourceStandard()).isEqualTo("ISO_3166_1_ALPHA2");
      assertThat(result.items()).hasSize(1);
      assertThat(result.items().getFirst().resolvedCode()).isEqualTo("US");
      assertThat(result.items().getFirst().status()).isEqualTo(DictionaryResolveStatus.RESOLVED);

      // 验证只调用了 item_code 查询，没有调用别名查询
      verify(repository).findTypeByCode("country");
      verify(standardRepository).findByDictTypeCodeAndStandardCode("country", "ISO_3166_1_ALPHA2");
      verify(repository).findItemsByTypeAndCodes(eq(1L), eq(Set.of("US")));
      verify(repository, never())
          .findItemsByAliases(eq(1L), eq("iso_3166_1_alpha2"), eq(Set.of("US")));
    }

    @Test
    @DisplayName("非规范标准应通过外部别名解析，跳过 item_code 查找")
    void shouldResolveByAliasWhenNonCanonicalStandard() {
      String typeCode = "country";
      String sourceStandard = "name_en";
      List<String> rawValues = List.of("United States");

      DictionaryType type = new DictionaryType(1L, "country");
      DictionaryItem item = new DictionaryItem(10L, 1L, "US", "United States", true);
      // canonical = false 表示这不是规范标准
      ReferenceStandard standard =
          new ReferenceStandard(21L, "country", "NAME_EN", "English Name", false, true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "NAME_EN"))
          .thenReturn(Optional.of(standard));

      Map<String, DictionaryItem> aliasResults = new HashMap<>();
      aliasResults.put("United States", item);
      when(repository.findItemsByAliases(1L, "name_en", Set.of("United States")))
          .thenReturn(aliasResults);
      when(repository.findItemsByAliases(1L, "name-en", Set.of("United States")))
          .thenReturn(Map.of());

      DictionaryResolveQuery result =
          queryService.resolveBatch(typeCode, sourceStandard, rawValues);

      assertThat(result.items()).hasSize(1);
      assertThat(result.items().getFirst().resolvedCode()).isEqualTo("US");
      assertThat(result.items().getFirst().status()).isEqualTo(DictionaryResolveStatus.RESOLVED);
      assertThat(result.sourceStandard()).isEqualTo("NAME_EN");

      // 验证只调用了别名查询，没有调用 item_code 查询
      verify(standardRepository).findByDictTypeCodeAndStandardCode("country", "NAME_EN");
      verify(repository).findItemsByAliases(eq(1L), eq("name_en"), eq(Set.of("United States")));
      verify(repository, never()).findItemsByTypeAndCodes(eq(1L), eq(Set.of("UNITED STATES")));
    }

    @Test
    @DisplayName("sourceStandard 为空时应抛出异常")
    void shouldThrowWhenSourceStandardIsNull() {
      assertThatThrownBy(() -> queryService.resolveBatch("country", null, List.of("US")))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Source standard");
    }

    @Test
    @DisplayName("sourceStandard 为空白时应抛出异常")
    void shouldThrowWhenSourceStandardIsBlank() {
      assertThatThrownBy(() -> queryService.resolveBatch("country", "  ", List.of("US")))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Source standard");
    }

    @Test
    @DisplayName("空白原始值应该返回 UNKNOWN")
    void shouldReturnUnknownForBlankValue() {
      String typeCode = "country";
      String sourceStandard = "NAME_EN";
      List<String> rawValues = List.of("  ");

      DictionaryType type = new DictionaryType(1L, "country");
      ReferenceStandard standard =
          new ReferenceStandard(21L, "country", "NAME_EN", "English Name", false, true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "NAME_EN"))
          .thenReturn(Optional.of(standard));
      when(repository.findItemsByAliases(1L, "name_en", Set.of())).thenReturn(Map.of());
      when(repository.findItemsByAliases(1L, "name-en", Set.of())).thenReturn(Map.of());

      DictionaryResolveQuery result =
          queryService.resolveBatch(typeCode, sourceStandard, rawValues);

      assertThat(result.items()).hasSize(1);
      assertThat(result.sourceStandard()).isEqualTo("NAME_EN");
      assertThat(result.items().getFirst().status()).isEqualTo(DictionaryResolveStatus.UNKNOWN);
    }

    @Test
    @DisplayName("字典项禁用时应返回 DISABLED")
    void shouldReturnDisabledWhenItemDisabled() {
      String typeCode = "country";
      String sourceStandard = "iso_3166_1_alpha2";
      List<String> rawValues = List.of("US");

      DictionaryType type = new DictionaryType(1L, "country");
      DictionaryItem item = new DictionaryItem(10L, 1L, "US", "United States", false);
      ReferenceStandard standard =
          new ReferenceStandard(
              20L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", true, true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "ISO_3166_1_ALPHA2"))
          .thenReturn(Optional.of(standard));
      when(repository.findItemsByTypeAndCodes(1L, Set.of("US"))).thenReturn(Map.of("US", item));

      DictionaryResolveQuery result =
          queryService.resolveBatch(typeCode, sourceStandard, rawValues);

      assertThat(result.items()).hasSize(1);
      assertThat(result.items().getFirst().status()).isEqualTo(DictionaryResolveStatus.DISABLED);
    }

    @Test
    @DisplayName("来源标准使用短横线格式时也应解析成功")
    void shouldResolveWithDashFormatStandard() {
      String typeCode = "country";
      String sourceStandard = "ISO-3166-1-alpha2";
      List<String> rawValues = List.of("US");

      DictionaryType type = new DictionaryType(1L, "country");
      DictionaryItem item = new DictionaryItem(10L, 1L, "US", "United States", true);
      ReferenceStandard standard =
          new ReferenceStandard(
              20L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", true, true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "ISO_3166_1_ALPHA2"))
          .thenReturn(Optional.of(standard));
      when(repository.findItemsByTypeAndCodes(1L, Set.of("US"))).thenReturn(Map.of("US", item));

      DictionaryResolveQuery result =
          queryService.resolveBatch(typeCode, sourceStandard, rawValues);

      assertThat(result.items()).hasSize(1);
      assertThat(result.items().getFirst().resolvedCode()).isEqualTo("US");
      assertThat(result.items().getFirst().status()).isEqualTo(DictionaryResolveStatus.RESOLVED);
      assertThat(result.sourceStandard()).isEqualTo("ISO_3166_1_ALPHA2");
    }

    @Test
    @DisplayName("字典类型不存在时应抛出异常")
    void shouldThrowWhenTypeMissing() {
      when(repository.findTypeByCode("country")).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> queryService.resolveBatch("country", "iso_3166_1_alpha2", List.of("US")))
          .isInstanceOf(DictionaryTypeNotFoundException.class);
    }

    @Test
    @DisplayName("来源标准不存在时应抛出异常")
    void shouldThrowWhenStandardMissing() {
      DictionaryType type = new DictionaryType(1L, "country");

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "ISO_3166_1_ALPHA2"))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> queryService.resolveBatch("country", "iso_3166_1_alpha2", List.of("US")))
          .isInstanceOf(DictionaryStandardNotFoundException.class);
    }

    @Test
    @DisplayName("来源标准被禁用时应抛出异常")
    void shouldThrowWhenStandardDisabled() {
      DictionaryType type = new DictionaryType(1L, "country");
      ReferenceStandard standard =
          new ReferenceStandard(
              20L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", true, false);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "ISO_3166_1_ALPHA2"))
          .thenReturn(Optional.of(standard));

      assertThatThrownBy(
              () -> queryService.resolveBatch("country", "iso_3166_1_alpha2", List.of("US")))
          .isInstanceOf(DictionaryStandardDisabledException.class);
    }

    @Test
    @DisplayName("空 rawValues 列表应直接返回空结果，跳过数据查询")
    void shouldReturnEmptyResultForEmptyRawValues() {
      DictionaryType type = new DictionaryType(1L, "country");
      ReferenceStandard standard =
          new ReferenceStandard(
              20L, "country", "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", true, true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "ISO_3166_1_ALPHA2"))
          .thenReturn(Optional.of(standard));

      DictionaryResolveQuery result =
          queryService.resolveBatch("country", "iso_3166_1_alpha2", List.of());

      assertThat(result.typeCode()).isEqualTo("country");
      assertThat(result.items()).isEmpty();
      verify(repository, never()).findItemsByTypeAndCodes(any(), any());
      verify(repository, never()).findItemsByAliases(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("listItems() 方法测试")
  class ListItemsTests {

    private static final DictionaryType COUNTRY_TYPE = new DictionaryType(1L, "country");

    @Test
    @DisplayName("有效类型应返回所有启用字典项")
    void shouldReturnAllEnabledItems() {
      var items =
          List.of(
              new DictionaryItemSummary("CN", "China", null, 156),
              new DictionaryItemSummary("US", "United States of America", null, 840));

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(COUNTRY_TYPE));
      when(repository.findAllEnabledItems(1L, null)).thenReturn(items);

      DictionaryItemListResult result = queryService.listItems("country", null);

      assertThat(result.typeCode()).isEqualTo("country");
      assertThat(result.labelStandard()).isNull();
      assertThat(result.items()).hasSize(2);
      assertThat(result.items().getFirst().code()).isEqualTo("CN");
      assertThat(result.items().getFirst().label()).isNull();
    }

    @Test
    @DisplayName("指定 labelStandard 应返回含本地化标签的结果")
    void shouldReturnLabelsWhenLabelStandardSpecified() {
      ReferenceStandard nameZh =
          new ReferenceStandard(4L, "country", "NAME_ZH", "中文名称", false, true);
      var items =
          List.of(
              new DictionaryItemSummary("CN", "China", "中国", 156),
              new DictionaryItemSummary("US", "United States of America", "美国", 840));

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(COUNTRY_TYPE));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "NAME_ZH"))
          .thenReturn(Optional.of(nameZh));
      when(repository.findAllEnabledItems(1L, "name_zh")).thenReturn(items);

      DictionaryItemListResult result = queryService.listItems("COUNTRY", "NAME_ZH");

      assertThat(result.typeCode()).isEqualTo("country");
      assertThat(result.labelStandard()).isEqualTo("NAME_ZH");
      assertThat(result.items()).hasSize(2);
      assertThat(result.items().getFirst().label()).isEqualTo("中国");
      assertThat(result.items().get(1).label()).isEqualTo("美国");
    }

    @Test
    @DisplayName("字典类型不存在应抛出 DictionaryTypeNotFoundException")
    void shouldThrowWhenTypeCodeNotFound() {
      when(repository.findTypeByCode("invalid")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> queryService.listItems("invalid", null))
          .isInstanceOf(DictionaryTypeNotFoundException.class);
    }

    @Test
    @DisplayName("labelStandard 不存在应抛出 DictionaryStandardNotFoundException")
    void shouldThrowWhenStandardNotFound() {
      when(repository.findTypeByCode("country")).thenReturn(Optional.of(COUNTRY_TYPE));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "NAME_XX"))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> queryService.listItems("country", "NAME_XX"))
          .isInstanceOf(DictionaryStandardNotFoundException.class);
    }

    @Test
    @DisplayName("labelStandard 被禁用应抛出 DictionaryStandardDisabledException")
    void shouldThrowWhenStandardDisabled() {
      ReferenceStandard disabled =
          new ReferenceStandard(4L, "country", "NAME_ZH", "中文名称", false, false);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(COUNTRY_TYPE));
      when(standardRepository.findByDictTypeCodeAndStandardCode("country", "NAME_ZH"))
          .thenReturn(Optional.of(disabled));

      assertThatThrownBy(() -> queryService.listItems("country", "NAME_ZH"))
          .isInstanceOf(DictionaryStandardDisabledException.class);
    }
  }
}
