package com.patra.catalog.infra.adapter.integration.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.patra.registry.api.client.DictionaryClient;
import com.patra.registry.api.dto.dict.DictionaryResolveItemResp;
import com.patra.registry.api.dto.dict.DictionaryResolveReq;
import com.patra.registry.api.dto.dict.DictionaryResolveResp;
import com.patra.starter.feign.error.exception.RemoteCallException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// CountryCodeResolverAdapter 单元测试。
///
/// 测试覆盖:
///
/// - ✅ 批量解析成功
/// - ✅ 部分失败仅返回成功项
/// - ✅ 远程调用失败返回空 Map
/// - ✅ 空/null 输入返回空 Map
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("CountryCodeResolverAdapter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class CountryCodeResolverAdapterTest {

  @Mock private DictionaryClient dictionaryClient;
  @InjectMocks private CountryCodeResolverAdapter adapter;
  @Captor private ArgumentCaptor<DictionaryResolveReq> requestCaptor;

  @Test
  @DisplayName("正常场景 - 批量解析成功")
  void shouldResolveBatchSuccessfully() {
    // Given
    Set<String> rawCodes = Set.of("USA", "China", "United Kingdom");
    DictionaryResolveResp response =
        new DictionaryResolveResp(
            "country",
            "NAME_EN",
            List.of(
                new DictionaryResolveItemResp("USA", "US", "United States", "RESOLVED"),
                new DictionaryResolveItemResp("China", "CN", "China", "RESOLVED"),
                new DictionaryResolveItemResp(
                    "United Kingdom", "GB", "United Kingdom", "RESOLVED")));
    when(dictionaryClient.resolve(any())).thenReturn(response);

    // When
    Map<String, String> result = adapter.resolveCountryCodes(rawCodes);

    // Then
    assertThat(result).hasSize(3);
    assertThat(result.get("USA")).isEqualTo("US");
    assertThat(result.get("China")).isEqualTo("CN");
    assertThat(result.get("United Kingdom")).isEqualTo("GB");

    verify(dictionaryClient).resolve(requestCaptor.capture());
    DictionaryResolveReq request = requestCaptor.getValue();
    assertThat(request.typeCode()).isEqualTo("country");
    assertThat(request.sourceStandard()).isEqualTo("NAME_EN");
    assertThat(request.rawValues()).containsExactlyInAnyOrderElementsOf(rawCodes);
  }

  @Test
  @DisplayName("部分失败场景 - 仅返回成功解析的值")
  void shouldReturnOnlyResolvedValues() {
    // Given
    Set<String> rawCodes = Set.of("USA", "InvalidCountry", "Atlantis");
    DictionaryResolveResp response =
        new DictionaryResolveResp(
            "country",
            "NAME_EN",
            List.of(
                new DictionaryResolveItemResp("USA", "US", "United States", "RESOLVED"),
                new DictionaryResolveItemResp("InvalidCountry", null, null, "UNKNOWN"),
                new DictionaryResolveItemResp("Atlantis", null, null, "UNKNOWN")));
    when(dictionaryClient.resolve(any())).thenReturn(response);

    // When
    Map<String, String> result = adapter.resolveCountryCodes(rawCodes);

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get("USA")).isEqualTo("US");
    assertThat(result).doesNotContainKey("InvalidCountry");
    assertThat(result).doesNotContainKey("Atlantis");
  }

  @Test
  @DisplayName("DISABLED 状态的字典项不应返回")
  void shouldNotReturnDisabledItems() {
    // Given
    Set<String> rawCodes = Set.of("USA", "XX");
    DictionaryResolveResp response =
        new DictionaryResolveResp(
            "country",
            "NAME_EN",
            List.of(
                new DictionaryResolveItemResp("USA", "US", "United States", "RESOLVED"),
                new DictionaryResolveItemResp("XX", "XX", "Deprecated Country", "DISABLED")));
    when(dictionaryClient.resolve(any())).thenReturn(response);

    // When
    Map<String, String> result = adapter.resolveCountryCodes(rawCodes);

    // Then
    assertThat(result).hasSize(1);
    assertThat(result.get("USA")).isEqualTo("US");
    assertThat(result).doesNotContainKey("XX");
  }

  @Test
  @DisplayName("远程调用失败场景 - 返回空 Map")
  void shouldReturnEmptyMapOnRemoteFailure() {
    // Given
    Set<String> rawCodes = Set.of("USA");
    when(dictionaryClient.resolve(any()))
        .thenThrow(
            new RemoteCallException(
                "SERVER_ERROR",
                500,
                "Service unavailable",
                "DictionaryClient#resolve",
                null,
                null));

    // When
    Map<String, String> result = adapter.resolveCountryCodes(rawCodes);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("空输入场景 - 返回空 Map 且不调用远程服务")
  void shouldReturnEmptyMapForEmptyInput() {
    // When
    Map<String, String> result = adapter.resolveCountryCodes(Set.of());

    // Then
    assertThat(result).isEmpty();
    verifyNoInteractions(dictionaryClient);
  }

  @Test
  @DisplayName("null 输入场景 - 返回空 Map 且不调用远程服务")
  void shouldReturnEmptyMapForNullInput() {
    // When
    Map<String, String> result = adapter.resolveCountryCodes(null);

    // Then
    assertThat(result).isEmpty();
    verifyNoInteractions(dictionaryClient);
  }

  @Test
  @DisplayName("响应为 null 时返回空 Map")
  void shouldReturnEmptyMapWhenResponseIsNull() {
    // Given
    Set<String> rawCodes = Set.of("USA");
    when(dictionaryClient.resolve(any())).thenReturn(null);

    // When
    Map<String, String> result = adapter.resolveCountryCodes(rawCodes);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("响应 items 为 null 时返回空 Map")
  void shouldReturnEmptyMapWhenResponseItemsIsNull() {
    // Given
    Set<String> rawCodes = Set.of("USA");
    DictionaryResolveResp response = new DictionaryResolveResp("country", "pubmed", null);
    when(dictionaryClient.resolve(any())).thenReturn(response);

    // When
    Map<String, String> result = adapter.resolveCountryCodes(rawCodes);

    // Then
    assertThat(result).isEmpty();
  }
}
