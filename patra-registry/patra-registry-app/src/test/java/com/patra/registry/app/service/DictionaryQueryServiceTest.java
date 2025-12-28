package com.patra.registry.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.registry.domain.exception.dictionary.DictionaryStandardDisabledException;
import com.patra.registry.domain.exception.dictionary.DictionaryStandardNotFoundException;
import com.patra.registry.domain.exception.dictionary.DictionaryTypeNotFoundException;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveStatus;
import com.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import com.patra.registry.domain.model.vo.dictionary.DictionaryType;
import com.patra.registry.domain.model.vo.reference.ReferenceStandard;
import com.patra.registry.domain.port.DictionaryRepository;
import com.patra.registry.domain.port.ReferenceStandardRepository;
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
/// - ✅ 正常场景 - 通过字典项代码解析成功
/// - ✅ 正常场景 - 通过外部别名解析成功
/// - ✅ 边界场景 - 空白值返回 UNKNOWN
/// - ✅ 异常场景 - 字典类型不存在抛出异常
/// - ✅ 状态场景 - 字典项被禁用返回 DISABLED
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
    @DisplayName("应该优先通过字典项代码解析")
    void shouldResolveByItemCode() {
      String typeCode = "COUNTRY";
      String sourceStandard = "ISO_3166_1_ALPHA2";
      List<String> rawValues = List.of("US");

      DictionaryType type = new DictionaryType(1L, "country");
      DictionaryItem item = new DictionaryItem(10L, 1L, "US", "United States", true);
      ReferenceStandard standard =
          new ReferenceStandard(20L, "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByCode("ISO_3166_1_ALPHA2")).thenReturn(Optional.of(standard));
      when(repository.findItemsByTypeAndCodes(1L, Set.of("US"))).thenReturn(Map.of("US", item));
      when(repository.findItemsByAliases(1L, "iso_3166_1_alpha2", Set.of("US")))
          .thenReturn(Map.of());
      when(repository.findItemsByAliases(1L, "iso-3166-1-alpha2", Set.of("US")))
          .thenReturn(Map.of());
      when(repository.findItemsByAliases(1L, "global", Set.of("US"))).thenReturn(Map.of());

      DictionaryResolveQuery result =
          queryService.resolveBatch(typeCode, sourceStandard, rawValues);

      assertThat(result.typeCode()).isEqualTo("country");
      assertThat(result.sourceStandard()).isEqualTo("ISO_3166_1_ALPHA2");
      assertThat(result.items()).hasSize(1);
      assertThat(result.items().getFirst().resolvedCode()).isEqualTo("US");
      assertThat(result.items().getFirst().status()).isEqualTo(DictionaryResolveStatus.RESOLVED);

      verify(repository).findTypeByCode("country");
      verify(standardRepository).findByCode("ISO_3166_1_ALPHA2");
      verify(repository).findItemsByTypeAndCodes(eq(1L), eq(Set.of("US")));
      verify(repository).findItemsByAliases(eq(1L), eq("iso_3166_1_alpha2"), eq(Set.of("US")));
      verify(repository).findItemsByAliases(eq(1L), eq("iso-3166-1-alpha2"), eq(Set.of("US")));
      verify(repository).findItemsByAliases(eq(1L), eq("global"), eq(Set.of("US")));
    }

    @Test
    @DisplayName("应该通过外部别名解析")
    void shouldResolveByAlias() {
      String typeCode = "country";
      String sourceStandard = "name_en";
      List<String> rawValues = List.of("United States");

      DictionaryType type = new DictionaryType(1L, "country");
      DictionaryItem item = new DictionaryItem(10L, 1L, "US", "United States", true);
      ReferenceStandard standard = new ReferenceStandard(21L, "NAME_EN", "English Name", true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByCode("NAME_EN")).thenReturn(Optional.of(standard));
      when(repository.findItemsByTypeAndCodes(1L, Set.of("UNITED STATES"))).thenReturn(Map.of());
      when(repository.findItemsByAliases(1L, "name_en", Set.of("United States")))
          .thenReturn(Map.of("United States", item));
      when(repository.findItemsByAliases(1L, "name-en", Set.of("United States")))
          .thenReturn(Map.of());
      when(repository.findItemsByAliases(1L, "global", Set.of("United States")))
          .thenReturn(Map.of());

      DictionaryResolveQuery result =
          queryService.resolveBatch(typeCode, sourceStandard, rawValues);

      assertThat(result.items()).hasSize(1);
      assertThat(result.items().getFirst().resolvedCode()).isEqualTo("US");
      assertThat(result.items().getFirst().status()).isEqualTo(DictionaryResolveStatus.RESOLVED);
      assertThat(result.sourceStandard()).isEqualTo("NAME_EN");

      verify(standardRepository).findByCode("NAME_EN");
      verify(repository).findItemsByAliases(eq(1L), eq("name_en"), eq(Set.of("United States")));
      verify(repository).findItemsByAliases(eq(1L), eq("name-en"), eq(Set.of("United States")));
      verify(repository).findItemsByAliases(eq(1L), eq("global"), eq(Set.of("United States")));
    }

    @Test
    @DisplayName("空白值应该返回 UNKNOWN")
    void shouldReturnUnknownForBlankValue() {
      String typeCode = "country";
      String sourceStandard = null;
      List<String> rawValues = List.of("  ");

      DictionaryType type = new DictionaryType(1L, "country");
      ReferenceStandard standard = new ReferenceStandard(22L, "GLOBAL", "Global", true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByCode("GLOBAL")).thenReturn(Optional.of(standard));
      when(repository.findItemsByTypeAndCodes(1L, Set.of())).thenReturn(Map.of());
      when(repository.findItemsByAliases(1L, "global", Set.of())).thenReturn(Map.of());

      DictionaryResolveQuery result =
          queryService.resolveBatch(typeCode, sourceStandard, rawValues);

      assertThat(result.items()).hasSize(1);
      assertThat(result.sourceStandard()).isEqualTo("GLOBAL");
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
          new ReferenceStandard(20L, "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByCode("ISO_3166_1_ALPHA2")).thenReturn(Optional.of(standard));
      when(repository.findItemsByTypeAndCodes(1L, Set.of("US"))).thenReturn(Map.of("US", item));
      when(repository.findItemsByAliases(1L, "iso_3166_1_alpha2", Set.of("US")))
          .thenReturn(Map.of());
      when(repository.findItemsByAliases(1L, "iso-3166-1-alpha2", Set.of("US")))
          .thenReturn(Map.of());
      when(repository.findItemsByAliases(1L, "global", Set.of("US"))).thenReturn(Map.of());

      DictionaryResolveQuery result =
          queryService.resolveBatch(typeCode, sourceStandard, rawValues);

      assertThat(result.items()).hasSize(1);
      assertThat(result.items().getFirst().status()).isEqualTo(DictionaryResolveStatus.DISABLED);
    }

    @Test
    @DisplayName("来源标准别名使用短横线时也应解析成功")
    void shouldResolveByAliasWithDashStandard() {
      String typeCode = "country";
      String sourceStandard = "ISO-3166-1-alpha2";
      List<String> rawValues = List.of("US");

      DictionaryType type = new DictionaryType(1L, "country");
      DictionaryItem item = new DictionaryItem(10L, 1L, "US", "United States", true);
      ReferenceStandard standard =
          new ReferenceStandard(20L, "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", true);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByCode("ISO_3166_1_ALPHA2")).thenReturn(Optional.of(standard));
      when(repository.findItemsByTypeAndCodes(1L, Set.of("US"))).thenReturn(Map.of());
      when(repository.findItemsByAliases(1L, "iso_3166_1_alpha2", Set.of("US")))
          .thenReturn(Map.of());
      when(repository.findItemsByAliases(1L, "iso-3166-1-alpha2", Set.of("US")))
          .thenReturn(Map.of("US", item));
      when(repository.findItemsByAliases(1L, "global", Set.of("US"))).thenReturn(Map.of());

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
      when(standardRepository.findByCode("ISO_3166_1_ALPHA2")).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> queryService.resolveBatch("country", "iso_3166_1_alpha2", List.of("US")))
          .isInstanceOf(DictionaryStandardNotFoundException.class);
    }

    @Test
    @DisplayName("来源标准被禁用时应抛出异常")
    void shouldThrowWhenStandardDisabled() {
      DictionaryType type = new DictionaryType(1L, "country");
      ReferenceStandard standard =
          new ReferenceStandard(20L, "ISO_3166_1_ALPHA2", "ISO 3166-1 alpha-2", false);

      when(repository.findTypeByCode("country")).thenReturn(Optional.of(type));
      when(standardRepository.findByCode("ISO_3166_1_ALPHA2")).thenReturn(Optional.of(standard));

      assertThatThrownBy(
              () -> queryService.resolveBatch("country", "iso_3166_1_alpha2", List.of("US")))
          .isInstanceOf(DictionaryStandardDisabledException.class);
    }
  }
}
