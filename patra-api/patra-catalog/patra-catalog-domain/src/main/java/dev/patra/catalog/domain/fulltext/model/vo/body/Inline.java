package dev.patra.catalog.domain.fulltext.model.vo.body;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/// 行内节点，JSON 里以 `kind` 区分。
///
/// 类型名只写在 `@JsonSubTypes.Type(name = …)` 里，record 本身没有 `kind` 字段。
///
/// @author linqibin
/// @since 0.11.0
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Inline.Text.class, name = "text"),
  @JsonSubTypes.Type(value = Inline.Styled.class, name = "style"),
  @JsonSubTypes.Type(value = Inline.Xref.class, name = "xref"),
  @JsonSubTypes.Type(value = Inline.Link.class, name = "link"),
  @JsonSubTypes.Type(value = Inline.MathInline.class, name = "math"),
  @JsonSubTypes.Type(value = Inline.Break.class, name = "break")
})
public sealed interface Inline {

  /// 纯文本。
  ///
  /// @param text 文本
  record Text(String text) implements Inline {}

  /// 样式包裹。
  ///
  /// @param style italic / bold / sup / sub / underline / smallcaps / monospace
  /// @param children 子节点
  record Styled(String style, List<Inline> children) implements Inline {

    /// 防御性拷贝，`null` 视为空。
    public Styled {
      children = children != null ? List.copyOf(children) : List.of();
    }
  }

  /// 页内交叉引用。
  ///
  /// @param refType bibr / fig / table / disp-formula / sec / fn / boxed-text
  /// @param targets 目标 id
  /// @param children 显示文本
  record Xref(String refType, List<String> targets, List<Inline> children) implements Inline {

    /// 防御性拷贝，`null` 视为空。
    public Xref {
      targets = targets != null ? List.copyOf(targets) : List.of();
      children = children != null ? List.copyOf(children) : List.of();
    }
  }

  /// 外链（只保留 http / https / doi）。
  ///
  /// @param href 地址
  /// @param children 显示文本
  record Link(String href, List<Inline> children) implements Inline {

    /// 防御性拷贝，`null` 视为空。
    public Link {
      children = children != null ? List.copyOf(children) : List.of();
    }
  }

  /// 行内公式（MathML 字符串，已去命名空间前缀）。
  ///
  /// @param mathml MathML
  record MathInline(String mathml) implements Inline {}

  /// 换行。
  record Break() implements Inline {}
}
