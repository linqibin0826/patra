package dev.patra.interaction.domain.post.model.vo;

import dev.patra.interaction.domain.post.exception.InvalidPostRequestException;
import java.util.regex.Pattern;

/// 帖子 ID（Snowflake）：对外的经验 ID，也是登记表 `POST` 行的 `source_id`。
///
/// @param value ID
public record PostId(long value) {

  private static final Pattern DECIMAL = Pattern.compile("[1-9][0-9]{0,18}");

  /// 创建。
  ///
  /// @param value ID
  /// @return 帖子 ID
  public static PostId of(long value) {
    return new PostId(value);
  }

  /// 解析分页游标（上一页最后一条的 ID）。
  ///
  /// @param raw 游标，可空
  /// @return 帖子 ID；没给为 `null`
  /// @throws InvalidPostRequestException 不是规范的十进制正整数
  public static PostId parseCursor(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    if (DECIMAL.matcher(raw).matches()) {
      try {
        return new PostId(Long.parseLong(raw));
      } catch (NumberFormatException ex) {
        // 19 位但超过 long 上限，落到下面
      }
    }
    throw new InvalidPostRequestException("分页游标不合法：" + raw);
  }
}
