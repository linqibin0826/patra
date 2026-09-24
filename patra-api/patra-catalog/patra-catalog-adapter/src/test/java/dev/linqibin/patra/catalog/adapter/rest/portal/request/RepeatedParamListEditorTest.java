package dev.linqibin.patra.catalog.adapter.rest.portal.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RepeatedParamListEditor：重复参数不拆逗号")
class RepeatedParamListEditorTest {

  @Test
  @DisplayName("setAsText：单个含逗号的值 → 单元素列表")
  void singleValueKeepsComma() {
    RepeatedParamListEditor editor = new RepeatedParamListEditor();
    editor.setAsText("Clinical Trial, Phase III");
    assertThat(editor.getValue()).isEqualTo(List.of("Clinical Trial, Phase III"));
  }

  @Test
  @DisplayName("setValue(String[])：重复参数 → 逐元素列表，元素内逗号保留")
  void arrayBecomesList() {
    RepeatedParamListEditor editor = new RepeatedParamListEditor();
    editor.setValue(new String[] {"Review", "Clinical Trial, Phase III"});
    assertThat(editor.getValue()).isEqualTo(List.of("Review", "Clinical Trial, Phase III"));
  }

  @Test
  @DisplayName("setValue(非字符串) 原样透传；setAsText(null / 空白) → 空列表；数组里的空白元素被丢弃")
  void passThroughAndBlank() {
    RepeatedParamListEditor editor = new RepeatedParamListEditor();
    List<Long> already = List.of(1L, 2L);
    editor.setValue(already);
    assertThat(editor.getValue()).isSameAs(already);
    editor.setAsText(null);
    assertThat(editor.getValue()).isEqualTo(List.of());
    editor.setAsText("   ");
    assertThat(editor.getValue()).isEqualTo(List.of());
    editor.setValue(new String[] {"1", "", "  "});
    assertThat(editor.getValue()).isEqualTo(List.of("1"));
  }
}
