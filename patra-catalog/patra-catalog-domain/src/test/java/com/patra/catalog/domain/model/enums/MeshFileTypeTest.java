package com.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// MeSH 文件类型枚举测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshFileType 枚举测试")
@Timeout(2)
class MeshFileTypeTest {

  @Test
  @DisplayName("DESCRIPTOR 枚举值应包含正确的属性")
  void descriptor_shouldHaveCorrectProperties() {
    MeshFileType type = MeshFileType.DESCRIPTOR;

    assertThat(type.getDescription()).isEqualTo("主题词");
    assertThat(type.getUrlPrefix()).isEqualTo("desc");
  }

  @Test
  @DisplayName("QUALIFIER 枚举值应包含正确的属性")
  void qualifier_shouldHaveCorrectProperties() {
    MeshFileType type = MeshFileType.QUALIFIER;

    assertThat(type.getDescription()).isEqualTo("限定词");
    assertThat(type.getUrlPrefix()).isEqualTo("qual");
  }

  @Test
  @DisplayName("枚举应只有两个值")
  void enumShouldHaveOnlyTwoValues() {
    assertThat(MeshFileType.values()).hasSize(2);
  }

  @Test
  @DisplayName("枚举值名称应正确")
  void enumValueNamesShouldBeCorrect() {
    assertThat(MeshFileType.DESCRIPTOR.name()).isEqualTo("DESCRIPTOR");
    assertThat(MeshFileType.QUALIFIER.name()).isEqualTo("QUALIFIER");
  }
}
