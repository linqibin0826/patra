package com.patra.registry.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.registry.adapter.rest.converter.DictionaryApiConverter;
import com.patra.registry.api.dto.dict.DictionaryResolveItemResp;
import com.patra.registry.api.dto.dict.DictionaryResolveReq;
import com.patra.registry.api.dto.dict.DictionaryResolveResp;
import com.patra.registry.app.service.DictionaryQueryService;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveItemQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveStatus;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// DictionaryEndpointImpl 单元测试。
///
/// 测试策略：
///
/// - 使用 Mockito Mock QueryService 和 Converter
///   - 验证 Controller 正确调用 QueryService
///   - 验证 Controller 正确使用 Converter 转换响应
///   - 验证参数正确传递
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("DictionaryEndpointImpl 单元测试")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class DictionaryEndpointImplTest {

  @Mock private DictionaryQueryService queryService;
  @Mock private DictionaryApiConverter converter;

  @InjectMocks private DictionaryEndpointImpl endpoint;

  @Test
  @DisplayName("应该成功解析字典值")
  void shouldResolveDictionary() {
    // given
    DictionaryResolveReq request =
        new DictionaryResolveReq("country", "iso_3166_1_alpha2", List.of("US"));

    DictionaryResolveQuery query =
        new DictionaryResolveQuery(
            "country",
            "ISO_3166_1_ALPHA2",
            List.of(
                new DictionaryResolveItemQuery(
                    "US", "US", "United States", DictionaryResolveStatus.RESOLVED)));

    DictionaryResolveResp expected =
        new DictionaryResolveResp(
            "country",
            "ISO_3166_1_ALPHA2",
            List.of(new DictionaryResolveItemResp("US", "US", "United States", "RESOLVED")));

    when(queryService.resolveBatch(eq("country"), eq("iso_3166_1_alpha2"), eq(List.of("US"))))
        .thenReturn(query);
    when(converter.toResp(query)).thenReturn(expected);

    // when
    DictionaryResolveResp result = endpoint.resolve(request);

    // then
    assertThat(result).isEqualTo(expected);
    assertThat(result.typeCode()).isEqualTo("country");
    assertThat(result.sourceStandard()).isEqualTo("ISO_3166_1_ALPHA2");
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().resolvedCode()).isEqualTo("US");
    assertThat(result.items().getFirst().status()).isEqualTo("RESOLVED");

    verify(queryService).resolveBatch("country", "iso_3166_1_alpha2", List.of("US"));
    verify(converter).toResp(query);
  }
}
