package com.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// MeshRecordType 枚举单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshRecordType 枚举测试")
class MeshRecordTypeTest {

  @Test
  @DisplayName("DESCRIPTOR 应该有正确的 code 和 displayName")
  void descriptorShouldHaveCorrectProperties() {
    // when
    MeshRecordType descriptor = MeshRecordType.DESCRIPTOR;

    // then
    assertThat(descriptor.getCode()).isEqualTo("D");
    assertThat(descriptor.getDisplayName()).isEqualTo("主题词");
    assertThat(descriptor.getUiPrefix()).isEqualTo('D');
  }

  @Test
  @DisplayName("SCR 应该有正确的 code 和 displayName")
  void scrShouldHaveCorrectProperties() {
    // when
    MeshRecordType scr = MeshRecordType.SCR;

    // then
    assertThat(scr.getCode()).isEqualTo("C");
    assertThat(scr.getDisplayName()).isEqualTo("补充概念");
    assertThat(scr.getUiPrefix()).isEqualTo('C');
  }

  @Test
  @DisplayName("fromCode 应该正确解析 D")
  void fromCodeShouldParseD() {
    // when
    MeshRecordType result = MeshRecordType.fromCode("D");

    // then
    assertThat(result).isEqualTo(MeshRecordType.DESCRIPTOR);
  }

  @Test
  @DisplayName("fromCode 应该正确解析 C")
  void fromCodeShouldParseC() {
    // when
    MeshRecordType result = MeshRecordType.fromCode("C");

    // then
    assertThat(result).isEqualTo(MeshRecordType.SCR);
  }

  @Test
  @DisplayName("fromCode 应该支持小写")
  void fromCodeShouldSupportLowercase() {
    assertThat(MeshRecordType.fromCode("d")).isEqualTo(MeshRecordType.DESCRIPTOR);
    assertThat(MeshRecordType.fromCode("c")).isEqualTo(MeshRecordType.SCR);
  }

  @Test
  @DisplayName("fromCode 应该拒绝无效 code")
  void fromCodeShouldRejectInvalid() {
    assertThatThrownBy(() -> MeshRecordType.fromCode("X"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未知的 MeSH 记录类型");
  }

  @Test
  @DisplayName("fromUiPrefix 应该正确解析 D")
  void fromUiPrefixShouldParseD() {
    assertThat(MeshRecordType.fromUiPrefix('D')).isEqualTo(MeshRecordType.DESCRIPTOR);
  }

  @Test
  @DisplayName("fromUiPrefix 应该正确解析 C")
  void fromUiPrefixShouldParseC() {
    assertThat(MeshRecordType.fromUiPrefix('C')).isEqualTo(MeshRecordType.SCR);
  }

  @Test
  @DisplayName("fromUiPrefix 应该支持小写")
  void fromUiPrefixShouldSupportLowercase() {
    assertThat(MeshRecordType.fromUiPrefix('d')).isEqualTo(MeshRecordType.DESCRIPTOR);
    assertThat(MeshRecordType.fromUiPrefix('c')).isEqualTo(MeshRecordType.SCR);
  }

  @Test
  @DisplayName("fromUiPrefix 应该拒绝无效前缀")
  void fromUiPrefixShouldRejectInvalid() {
    assertThatThrownBy(() -> MeshRecordType.fromUiPrefix('X'))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未知的 UI 前缀");
  }
}
