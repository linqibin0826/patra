package dev.patra.identity.domain.account.model.vo.profile;

import dev.patra.identity.domain.account.model.vo.DisplayName;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/// 规范化之后的简介：NFC、换行统一成 `\n`、每行内的空格类字符折成一个空格并去掉行首行尾空白、去掉首尾空行。
///
/// 长度、行数、拒绝字符由 `ProfilePolicy` 判，这里只守「已规范化且非空」。
///
/// @param value 规范化之后的简介
public record Bio(String value) {

  /// 只接受已经规范化的非空值，用于从库里恢复。
  ///
  /// @param value 规范化之后的简介
  public Bio {
    Objects.requireNonNull(value, "value 不能为 null");
    if (value.isEmpty() || !value.equals(normalize(value))) {
      throw new IllegalArgumentException("简介必须是规范化之后的非空值");
    }
  }

  /// 规范化：NFC → `\r\n` 与单独的 `\r` 换成 `\n` → 逐行折叠空格类字符并去掉行首行尾空白 → 去掉首尾空行。
  /// 中间的空行保留（算行数）。
  ///
  /// @param raw 用户输入
  /// @return 规范化之后的值，可能为空串
  public static String normalize(String raw) {
    String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
    String unified = nfc.replace("\r\n", "\n").replace('\r', '\n');
    String joined =
        Arrays.stream(unified.split("\n", -1))
            .map(DisplayName::normalize)
            .collect(Collectors.joining("\n"));
    return stripBlankLines(joined);
  }

  /// 去掉首尾的换行。每行已经去过首尾空白，所以首尾空行就是首尾连续的 `\n`。
  ///
  /// @param text 逐行规范化之后的文本
  /// @return 去掉首尾空行的文本
  private static String stripBlankLines(String text) {
    int start = 0;
    int end = text.length();
    while (start < end && text.charAt(start) == '\n') {
      start++;
    }
    while (end > start && text.charAt(end - 1) == '\n') {
      end--;
    }
    return text.substring(start, end);
  }
}
