package com.patra.catalog.infra.adapter.integration.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.enums.DictionaryType;
import com.patra.catalog.domain.model.vo.common.SourceStandard;
import com.patra.registry.api.dto.dict.DictionaryResolveItemResp;
import com.patra.registry.api.dto.dict.DictionaryResolveReq;
import com.patra.registry.api.dto.dict.DictionaryResolveResp;
import com.patra.registry.api.endpoint.DictionaryEndpoint;
import dev.linqibin.commons.error.remote.RemoteCallException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// DictionaryResolverAdapter 单元测试。
///
/// **测试策略说明**：
///
/// 本测试采用单元测试（Mock HTTP Interface），原因如下：
///
/// 1. Adapter 职责单一：仅做 HTTP Interface 调用 + DTO 转换，无复杂 HTTP 交互逻辑
/// 2. HTTP 层可靠性由 HTTP Interface + Starter 保障，无需重复验证
/// 3. 核心测试目标是转换逻辑和异常处理，单元测试足以覆盖
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("DictionaryResolverAdapter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class DictionaryResolverAdapterTest {

  @Mock private DictionaryEndpoint dictionaryEndpoint;
  @InjectMocks private DictionaryResolverAdapter adapter;
  @Captor private ArgumentCaptor<DictionaryResolveReq> requestCaptor;

  @Nested
  @DisplayName("resolve() 方法")
  class ResolveTests {

    @Test
    @DisplayName("正常场景 - 批量解析国家编码成功")
    void shouldResolveCountryCodesSuccessfully() {
      // Given
      Set<String> rawCodes = Set.of("China", "United States", "Japan");
      DictionaryResolveResp response =
          new DictionaryResolveResp(
              "country",
              "NAME_EN",
              List.of(
                  new DictionaryResolveItemResp("China", "CN", "中国", "RESOLVED"),
                  new DictionaryResolveItemResp("United States", "US", "美国", "RESOLVED"),
                  new DictionaryResolveItemResp("Japan", "JP", "日本", "RESOLVED")));
      when(dictionaryEndpoint.resolve(any())).thenReturn(response);

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, rawCodes);

      // Then
      assertThat(result).hasSize(3);
      assertThat(result.get("China")).isEqualTo("CN");
      assertThat(result.get("United States")).isEqualTo("US");
      assertThat(result.get("Japan")).isEqualTo("JP");

      verify(dictionaryEndpoint).resolve(requestCaptor.capture());
      DictionaryResolveReq request = requestCaptor.getValue();
      assertThat(request.typeCode()).isEqualTo("country");
      assertThat(request.sourceStandard()).isEqualTo("NAME_EN");
      assertThat(request.rawValues()).containsExactlyInAnyOrderElementsOf(rawCodes);
    }

    @Test
    @DisplayName("正常场景 - 批量解析语言编码成功")
    void shouldResolveLanguageCodesSuccessfully() {
      // Given
      Set<String> rawCodes = Set.of("eng", "chi", "jpn");
      DictionaryResolveResp response =
          new DictionaryResolveResp(
              "language",
              "ISO_639_3",
              List.of(
                  new DictionaryResolveItemResp("eng", "en", "English", "RESOLVED"),
                  new DictionaryResolveItemResp("chi", "zh", "Chinese", "RESOLVED"),
                  new DictionaryResolveItemResp("jpn", "ja", "Japanese", "RESOLVED")));
      when(dictionaryEndpoint.resolve(any())).thenReturn(response);

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.LANGUAGE, SourceStandard.ISO_639_3, rawCodes);

      // Then
      assertThat(result).hasSize(3);
      assertThat(result.get("eng")).isEqualTo("en");
      assertThat(result.get("chi")).isEqualTo("zh");
      assertThat(result.get("jpn")).isEqualTo("ja");

      verify(dictionaryEndpoint).resolve(requestCaptor.capture());
      DictionaryResolveReq request = requestCaptor.getValue();
      assertThat(request.typeCode()).isEqualTo("language");
      assertThat(request.sourceStandard()).isEqualTo("ISO_639_3");
    }

    @Test
    @DisplayName("部分失败场景 - 仅返回成功解析的值")
    void shouldReturnOnlyResolvedValues() {
      // Given
      Set<String> rawCodes = Set.of("China", "InvalidCountry", "Atlantis");
      DictionaryResolveResp response =
          new DictionaryResolveResp(
              "country",
              "NAME_EN",
              List.of(
                  new DictionaryResolveItemResp("China", "CN", "中国", "RESOLVED"),
                  new DictionaryResolveItemResp("InvalidCountry", null, null, "UNKNOWN"),
                  new DictionaryResolveItemResp("Atlantis", null, null, "UNKNOWN")));
      when(dictionaryEndpoint.resolve(any())).thenReturn(response);

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, rawCodes);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get("China")).isEqualTo("CN");
      assertThat(result).doesNotContainKey("InvalidCountry");
      assertThat(result).doesNotContainKey("Atlantis");
    }

    @Test
    @DisplayName("DISABLED 状态的字典项不应返回")
    void shouldNotReturnDisabledItems() {
      // Given
      Set<String> rawCodes = Set.of("China", "XX");
      DictionaryResolveResp response =
          new DictionaryResolveResp(
              "country",
              "NAME_EN",
              List.of(
                  new DictionaryResolveItemResp("China", "CN", "中国", "RESOLVED"),
                  new DictionaryResolveItemResp("XX", "XX", "已废弃国家", "DISABLED")));
      when(dictionaryEndpoint.resolve(any())).thenReturn(response);

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, rawCodes);

      // Then
      assertThat(result).hasSize(1);
      assertThat(result.get("China")).isEqualTo("CN");
      assertThat(result).doesNotContainKey("XX");
    }
  }

  @Nested
  @DisplayName("异常处理")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("远程调用失败场景 - 返回空 Map")
    void shouldReturnEmptyMapOnRemoteFailure() {
      // Given
      Set<String> rawCodes = Set.of("China");
      when(dictionaryEndpoint.resolve(any()))
          .thenThrow(
              new RemoteCallException(
                  "SERVER_ERROR",
                  500,
                  "Service unavailable",
                  "DictionaryEndpoint#resolve",
                  null,
                  null));

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, rawCodes);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("意外异常场景 - 返回空 Map")
    void shouldReturnEmptyMapOnUnexpectedException() {
      // Given
      Set<String> rawCodes = Set.of("China");
      when(dictionaryEndpoint.resolve(any())).thenThrow(new RuntimeException("Unexpected error"));

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, rawCodes);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("边界条件")
  class EdgeCaseTests {

    @Test
    @DisplayName("空输入场景 - 返回空 Map 且不调用远程服务")
    void shouldReturnEmptyMapForEmptyInput() {
      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, Set.of());

      // Then
      assertThat(result).isEmpty();
      verifyNoInteractions(dictionaryEndpoint);
    }

    @Test
    @DisplayName("null 输入场景 - 返回空 Map 且不调用远程服务")
    void shouldReturnEmptyMapForNullInput() {
      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, null);

      // Then
      assertThat(result).isEmpty();
      verifyNoInteractions(dictionaryEndpoint);
    }

    @Test
    @DisplayName("响应为 null 时返回空 Map")
    void shouldReturnEmptyMapWhenResponseIsNull() {
      // Given
      Set<String> rawCodes = Set.of("China");
      when(dictionaryEndpoint.resolve(any())).thenReturn(null);

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, rawCodes);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("响应 items 为 null 时返回空 Map")
    void shouldReturnEmptyMapWhenResponseItemsIsNull() {
      // Given
      Set<String> rawCodes = Set.of("China");
      DictionaryResolveResp response = new DictionaryResolveResp("country", "NAME_EN", null);
      when(dictionaryEndpoint.resolve(any())).thenReturn(response);

      // When
      Map<String, String> result =
          adapter.resolve(DictionaryType.COUNTRY, SourceStandard.NAME_EN, rawCodes);

      // Then
      assertThat(result).isEmpty();
    }
  }
}
