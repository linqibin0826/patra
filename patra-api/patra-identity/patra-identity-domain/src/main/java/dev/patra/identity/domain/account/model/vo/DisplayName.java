package dev.patra.identity.domain.account.model.vo;

import java.text.Normalizer;
import java.util.Objects;

/// 规范化之后的昵称：NFC、连续的空格类字符折成一个 U+0020、去首尾空白。
///
/// 长度、拒绝字符、形态、拒绝词由 `DisplayNamePolicy` 判，这里只守「已规范化且非空」。
/// 没有「默认值」工厂：资料里昵称为空表示跟随用户名，由读模型回退成当前用户名。
///
/// @param value 规范化之后的昵称
public record DisplayName(String value) {

  /// 只接受已经规范化的非空值，用于从库里恢复。
  ///
  /// @param value 规范化之后的昵称
  public DisplayName {
    Objects.requireNonNull(value, "value 不能为 null");
    if (value.isEmpty() || !value.equals(normalize(value))) {
      throw new IllegalArgumentException("昵称必须是规范化之后的非空值");
    }
  }

  /// 规范化：NFC → 连续的空格类字符（Zs，含全角空格、不换行空格）折成一个 U+0020 → 去首尾空白。
  ///
  /// 行分隔符、段分隔符、制表符不是空格类字符，留给规则按拒绝字符处理。
  ///
  /// @param raw 用户输入
  /// @return 规范化之后的值，可能为空串
  public static String normalize(String raw) {
    String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
    StringBuilder out = new StringBuilder(nfc.length());
    boolean pendingSpace = false;
    int i = 0;
    while (i < nfc.length()) {
      int cp = nfc.codePointAt(i);
      if (Character.getType(cp) == Character.SPACE_SEPARATOR) {
        pendingSpace = true;
      } else {
        if (pendingSpace) {
          out.append(' ');
          pendingSpace = false;
        }
        out.appendCodePoint(cp);
      }
      i += Character.charCount(cp);
    }
    return out.toString().strip();
  }
}
