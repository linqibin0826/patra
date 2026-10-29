package dev.patra.interaction.domain.discussion.port.mention;

import dev.patra.interaction.domain.discussion.model.read.MentionTarget;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// identity 的 @ 提及支撑（PAP-118）：用户名批量解析与前缀联想。只有讨论解析 @，所以放讨论自己的端口。
public interface MentionDirectoryPort {

  /// 一次最多解析多少个用户名（identity 接口的上限）。
  int MAX_USERNAMES = 100;

  /// 一次联想最多要几位（identity 接口的上限）。
  int MAX_SUGGESTIONS = 20;

  /// 批量解析用户名：先当前用户名，再 90 天保留期内的旧名；四种账号状态都返回。
  ///
  /// @param usernames 用户名原文（不带 `@`），1 ~ {@value #MAX_USERNAMES} 个
  /// @return 原文 → 解析结果；不存在、旧名已过期、不合语法的不在结果里
  /// @throws UserDirectoryUnavailableException identity 不可用
  /// @throws IllegalArgumentException 个数越界
  Map<String, MentionTarget> resolve(Collection<String> usernames);

  /// 按用户名或昵称前缀联想：只有主页对访客可见的正常账号，不含访客本人。
  ///
  /// @param prefix `@` 后面已输入的原文
  /// @param viewerUserId 访客
  /// @param limit 最多几位，1 ~ {@value #MAX_SUGGESTIONS}
  /// @return 名片，用户名命中的在前、昵称命中的在后
  /// @throws UserDirectoryUnavailableException identity 不可用
  /// @throws IllegalArgumentException 条数越界
  List<UserCard> suggest(String prefix, long viewerUserId, int limit);
}
