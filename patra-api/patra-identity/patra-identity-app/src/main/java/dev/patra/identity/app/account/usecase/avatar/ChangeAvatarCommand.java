package dev.patra.identity.app.account.usecase.avatar;

import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.tool.cqrs.Command;
import java.util.Arrays;

/// 当前用户换头像。
///
/// @param content 上传的原图字节；为空表示没有文件
public record ChangeAvatarCommand(byte[] content) implements Command<Avatar> {

  /// 拷贝传入的数组，`null` 视为空。
  public ChangeAvatarCommand {
    content = content == null ? new byte[0] : content.clone();
  }

  /// 工厂方法。
  ///
  /// @param content 原图字节
  /// @return 命令
  public static ChangeAvatarCommand of(byte[] content) {
    return new ChangeAvatarCommand(content);
  }

  /// 原图字节的副本。
  ///
  /// @return 字节
  @Override
  public byte[] content() {
    return content.clone();
  }

  /// 按内容比较。
  ///
  /// @param other 另一个对象
  /// @return 字节相同时为 `true`
  @Override
  public boolean equals(Object other) {
    return other instanceof ChangeAvatarCommand that && Arrays.equals(content, that.content);
  }

  /// 按内容算哈希。
  ///
  /// @return 哈希
  @Override
  public int hashCode() {
    return Arrays.hashCode(content);
  }

  /// 只输出长度。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ChangeAvatarCommand[content=" + content.length + "B]";
  }
}
