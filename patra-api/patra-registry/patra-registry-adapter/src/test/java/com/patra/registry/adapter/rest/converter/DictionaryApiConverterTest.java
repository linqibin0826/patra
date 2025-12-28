package com.patra.registry.adapter.rest.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.registry.api.dto.dict.DictionaryResolveResp;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveItemQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryResolveStatus;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mapstruct.factory.Mappers;

/// DictionaryApiConverter 单元测试。
///
/// 测试覆盖:
///
/// - ✅ 批量解析结果转换为 API 响应
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DictionaryApiConverter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class DictionaryApiConverterTest {

  private final DictionaryApiConverter converter = Mappers.getMapper(DictionaryApiConverter.class);

  @Test
  @DisplayName("应该正确转换批量解析结果")
  void shouldConvertResolveQuery() {
    DictionaryResolveQuery query =
        new DictionaryResolveQuery(
            "country",
            "ISO_3166_1_ALPHA2",
            List.of(
                new DictionaryResolveItemQuery(
                    "US", "US", "United States", DictionaryResolveStatus.RESOLVED)));

    DictionaryResolveResp resp = converter.toResp(query);

    assertThat(resp.typeCode()).isEqualTo("country");
    assertThat(resp.sourceStandard()).isEqualTo("ISO_3166_1_ALPHA2");
    assertThat(resp.items()).hasSize(1);
    assertThat(resp.items().getFirst().resolvedCode()).isEqualTo("US");
    assertThat(resp.items().getFirst().status()).isEqualTo("RESOLVED");
  }
}
