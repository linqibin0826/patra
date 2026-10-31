package dev.patra.notification.domain.kernel.model.read;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/// 分段的摘录：文字段与提及段交替，邮件要给 `@名字` 加强调（PAP-146 spec 8.2）。拼起来与 {@link RenderedExcerpt} 的文字相同。
///
/// @param segments 段，按原文顺序
/// @param truncated 是否截掉了东西
public record SegmentedExcerpt(List<Segment> segments, boolean truncated) {

  /// 防御性拷贝。
  public SegmentedExcerpt {
    segments = List.copyOf(segments);
  }

  /// 构造。
  ///
  /// @param segments 段
  /// @param truncated 是否截断过
  /// @return 摘录
  public static SegmentedExcerpt of(List<Segment> segments, boolean truncated) {
    return new SegmentedExcerpt(segments, truncated);
  }

  /// 拼成纯文字。
  ///
  /// @return 文字
  public String text() {
    return segments.stream().map(Segment::text).collect(Collectors.joining());
  }

  /// 一段。
  ///
  /// @param text 文字；提及段是 `@名字`
  /// @param mention 是否提及
  public record Segment(String text, boolean mention) {

    /// 校验非空。
    public Segment {
      Objects.requireNonNull(text, "text 不能为 null");
    }

    /// 构造。
    ///
    /// @param text 文字
    /// @param mention 是否提及
    /// @return 段
    public static Segment of(String text, boolean mention) {
      return new Segment(text, mention);
    }
  }
}
