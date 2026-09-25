package dev.linqibin.patra.catalog.domain.model.vo.publication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InlineMarkup 可见纯文本派生")
class InlineMarkupTest {

  @Test
  @DisplayName("剥白名单排版标签：CO<sub>2</sub> → CO2，<i>E. coli</i> → E. coli")
  void stripsFormattingTags() {
    assertThat(InlineMarkup.toPlainText("CO<sub>2</sub> fixation")).isEqualTo("CO2 fixation");
    assertThat(InlineMarkup.toPlainText("<i>E. coli</i> strains")).isEqualTo("E. coli strains");
    assertThat(InlineMarkup.toPlainText("<sup>18</sup>F-FDG <b>PET</b>")).isEqualTo("18F-FDG PET");
  }

  @Test
  @DisplayName("字面尖括号不是标签：x<y and z>0、age < 5 原样保留")
  void keepsLiteralAngleBrackets() {
    assertThat(InlineMarkup.toPlainText("x<y and z>0")).isEqualTo("x<y and z>0");
    assertThat(InlineMarkup.toPlainText("age < 5 versus age > 10"))
        .isEqualTo("age < 5 versus age > 10");
    assertThat(InlineMarkup.toPlainText("<br> is not allowed")).isEqualTo("<br> is not allowed");
  }

  @Test
  @DisplayName("带属性与自闭合的白名单标签也剥掉")
  void stripsTagsWithAttributesAndSelfClosing() {
    assertThat(InlineMarkup.toPlainText("<math display=\"inline\"><mi>x</mi></math>"))
        .isEqualTo("x");
    assertThat(InlineMarkup.toPlainText("a<mspace width=\"1em\"/>b")).isEqualTo("ab");
    assertThat(InlineMarkup.toPlainText("<SUB>2</SUB>")).isEqualTo("2");
  }

  @Test
  @DisplayName("null 透传")
  void nullPassesThrough() {
    assertThat(InlineMarkup.toPlainText(null)).isNull();
  }

  @Test
  @DisplayName("TAG_PATTERN 可被 java.util.regex 编译且不含 PG 不支持的语法")
  void tagPatternCompiles() {
    assertThat(Pattern.compile(InlineMarkup.TAG_PATTERN, Pattern.CASE_INSENSITIVE)).isNotNull();
    assertThat(InlineMarkup.TAG_PATTERN).doesNotContain("(?i)").doesNotContain("\\p{");
  }

  @Test
  @DisplayName("已闭合的 annotation 子树整体删除：公式不显示两遍")
  void dropsClosedAnnotationSubtree() {
    String markup =
        "<math><semantics><mrow><mi>x</mi></mrow>"
            + "<annotation encoding=\"application/x-tex\">x</annotation></semantics></math> value";
    assertThat(InlineMarkup.toPlainText(markup)).isEqualTo("x value");
  }

  @Test
  @DisplayName("未闭合的 annotation 只展开不删：上游截断时不吞正文")
  void keepsUnclosedAnnotationText() {
    assertThat(InlineMarkup.toPlainText("<annotation>tail text")).isEqualTo("tail text");
  }

  @Test
  @DisplayName("解码命名 / 十进制 / 十六进制实体")
  void decodesEntities() {
    assertThat(InlineMarkup.toPlainText("A &amp; B &lt; C &gt; D &quot;E&quot; &apos;F&apos;"))
        .isEqualTo("A & B < C > D \"E\" 'F'");
    assertThat(InlineMarkup.toPlainText("&#8722;5 &#x2212;5")).isEqualTo("\u22125 \u22125");
    assertThat(InlineMarkup.toPlainText("&unknown; stays")).isEqualTo("&unknown; stays");
  }

  @Test
  @DisplayName("解码出的尖括号是文本，不会被当成标签二次处理")
  void decodedAngleBracketsStayLiteral() {
    assertThat(InlineMarkup.toPlainText("&lt;sub&gt;2&lt;/sub&gt;")).isEqualTo("<sub>2</sub>");
  }

  @Test
  @DisplayName("truncate 按码点截断，不切代理对")
  void truncateByCodePoints() {
    String emoji = "\uD83E\uDDEC"; // 🧬 一个码点、两个 char
    String text = "ab" + emoji + "cd";
    assertThat(InlineMarkup.truncate(text, 3)).isEqualTo("ab" + emoji);
    assertThat(InlineMarkup.truncate(text, 2)).isEqualTo("ab");
    assertThat(InlineMarkup.truncate(text, 10)).isEqualTo(text);
    assertThat(InlineMarkup.truncate(null, 3)).isNull();
  }

  @Test
  @DisplayName("先解实体再截断：第 299 字后接 &amp; 不会截出残缺实体")
  void decodeBeforeTruncate() {
    String markup = "a".repeat(299) + "&amp;" + "b".repeat(50);
    String snippet = InlineMarkup.truncate(InlineMarkup.toPlainText(markup), 300);
    assertThat(snippet).hasSize(300).endsWith("a&");
  }
}
