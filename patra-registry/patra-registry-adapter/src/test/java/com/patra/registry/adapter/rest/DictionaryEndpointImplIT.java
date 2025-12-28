package com.patra.registry.adapter.rest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.registry.adapter.rest.converter.DictionaryApiConverter;
import com.patra.registry.api.dto.dict.DictionaryResolveItemResp;
import com.patra.registry.api.dto.dict.DictionaryResolveResp;
import com.patra.registry.app.service.DictionaryQueryService;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveItemQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/// DictionaryEndpointImpl Web 层切片测试。
///
/// 验证 JSON 绑定与调用链路，包含 sourceSystem → sourceStandard 兼容性。
///
/// @author linqibin
/// @since 0.1.0
@WebMvcTest(DictionaryEndpointImpl.class)
@DisplayName("DictionaryEndpointImpl 切片测试")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class DictionaryEndpointImplIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private DictionaryQueryService queryService;
  @MockitoBean private DictionaryApiConverter converter;

  @Test
  @DisplayName("应该成功解析字典值并兼容 sourceSystem 字段")
  void shouldResolveDictionaryWithSourceSystemAlias() throws Exception {
    Map<String, Object> request =
        Map.of(
            "typeCode", "country", "sourceSystem", "iso_3166_1_alpha2", "rawValues", List.of("US"));

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

    mockMvc
        .perform(
            post("/_internal/dictionaries/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.typeCode").value("country"))
        .andExpect(jsonPath("$.sourceStandard").value("ISO_3166_1_ALPHA2"))
        .andExpect(jsonPath("$.items[0].resolvedCode").value("US"))
        .andExpect(jsonPath("$.items[0].status").value("RESOLVED"));

    verify(queryService).resolveBatch("country", "iso_3166_1_alpha2", List.of("US"));
    verify(converter).toResp(query);
  }
}
