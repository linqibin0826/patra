package dev.patra.catalog.domain.publication.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.catalog.domain.publication.model.enums.AbstractType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PublicationAbstract 值对象单元测试。
///
/// @author linqibin
/// @since 0.6.0
@DisplayName("PublicationAbstract 值对象")
class PublicationAbstractTest {

  @Test
  @DisplayName("段落列表应保持顺序、重复标签与无标签段")
  void ofBoth_shouldKeepSectionOrderAndDuplicateLabels() {
    List<PublicationAbstractSection> sections =
        List.of(
            PublicationAbstractSection.of("INTRO", "a"),
            PublicationAbstractSection.of("METHODS", "b"),
            PublicationAbstractSection.of("METHODS", "c"),
            PublicationAbstractSection.of(null, "d"));

    PublicationAbstract abs = PublicationAbstract.ofBoth("plain", sections, "©X");

    assertThat(abs.abstractType()).isEqualTo(AbstractType.STRUCTURED);
    assertThat(abs.structuredSections()).hasSize(4);
    assertThat(abs.structuredSections().get(2).text()).isEqualTo("c");
    assertThat(abs.findSectionsByLabel("methods")).hasSize(2);
    assertThat(abs.findSectionsByLabel("  methods  ")).hasSize(2);
    assertThat(abs.findSectionsByLabel("NONEXIST")).isEmpty();
  }

  @Test
  @DisplayName("纯文本摘要段落列表应为空且类型为 UNSTRUCTURED")
  void ofPlainText_shouldHaveEmptySectionsAndUnstructuredType() {
    PublicationAbstract abs = PublicationAbstract.ofPlainText("text only", "©");

    assertThat(abs.structuredSections()).isEmpty();
    assertThat(abs.abstractType()).isEqualTo(AbstractType.UNSTRUCTURED);
    assertThat(abs.hasContent()).isTrue();
  }

  @Test
  @DisplayName("紧凑构造器应推断类型并做防御性拷贝")
  void compactConstructor_shouldInferTypeAndDefensiveCopy() {
    List<PublicationAbstractSection> mutable = new ArrayList<>();
    mutable.add(PublicationAbstractSection.of("A", "t"));

    PublicationAbstract abs = new PublicationAbstract(null, mutable, null, null);
    mutable.clear();

    assertThat(abs.structuredSections()).hasSize(1);
    assertThat(abs.abstractType()).isEqualTo(AbstractType.STRUCTURED);
  }

  @Test
  @DisplayName("未指定摘要类型时应按段落列表与纯文本推断三种类型")
  void compactConstructor_shouldInferAllTypeBranches() {
    assertThat(
            PublicationAbstract.builder()
                .structuredSections(List.of(PublicationAbstractSection.of("A", "t")))
                .build()
                .abstractType())
        .isEqualTo(AbstractType.STRUCTURED);
    assertThat(PublicationAbstract.builder().plainText("t").build().abstractType())
        .isEqualTo(AbstractType.UNSTRUCTURED);
    assertThat(PublicationAbstract.builder().build().abstractType()).isEqualTo(AbstractType.NONE);
  }

  @Test
  @DisplayName("结构化摘要工厂方法传空列表应抛异常")
  void ofStructured_emptySections_shouldThrow() {
    assertThatThrownBy(() -> PublicationAbstract.ofStructured(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PublicationAbstract.ofStructured(List.of(), "©"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("结构化摘要全文应按顺序拼接段落内容")
  void getFullText_structured_shouldJoinSectionTexts() {
    PublicationAbstract abs =
        PublicationAbstract.ofStructured(
            List.of(
                PublicationAbstractSection.of("A", "x"), PublicationAbstractSection.of(null, "y")),
            null);

    assertThat(abs.getFullText()).isEqualTo("x y");
  }

  @Test
  @DisplayName("段落数量应等于列表长度")
  void getSectionCount_shouldEqualListSize() {
    PublicationAbstract abs =
        PublicationAbstract.ofStructured(
            List.of(
                PublicationAbstractSection.of("A", "x"), PublicationAbstractSection.of("A", "y")));

    assertThat(abs.getSectionCount()).isEqualTo(2);
    assertThat(abs.isStructured()).isTrue();
  }

  @Test
  @DisplayName("空摘要应无内容且类型为 NONE")
  void empty_shouldHaveNoContent() {
    PublicationAbstract abs = PublicationAbstract.empty();

    assertThat(abs.hasContent()).isFalse();
    assertThat(abs.abstractType()).isEqualTo(AbstractType.NONE);
    assertThat(abs.getFullText()).isEmpty();
  }
}
