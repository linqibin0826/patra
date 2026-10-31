package dev.patra.identity.domain.account.port.read;

import dev.patra.identity.domain.account.model.read.AvatarOwner;
import dev.patra.identity.domain.account.model.read.PublicUserReadModel;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.read.UserRecipientReadModel;
import dev.patra.identity.domain.account.model.read.UserSummaryReadModel;
import dev.patra.identity.domain.account.model.vo.DisplayNamePrefix;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.UsernamePrefix;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/// 前台用户的读端口。读模型把用户表和资料表拼起来，昵称为空时回退成用户名。
public interface UserReadPort {

  /// 按 ID 读账号信息。
  ///
  /// @param userId 用户 ID
  /// @return 账号信息；不存在时为空
  Optional<UserAccountReadModel> findAccount(long userId);

  /// 按用户名读公开身份卡，包括已注销、已封禁的用户：可见性由调用方判。
  ///
  /// @param username 用户名
  /// @return 身份卡；不存在时为空
  Optional<PublicUserReadModel> findPublicIdentity(Username username);

  /// 按用户 ID 读公开身份卡，给旧名跳转取原主的当前身份卡。
  ///
  /// @param userId 用户 ID
  /// @return 身份卡；不存在时为空
  Optional<PublicUserReadModel> findPublicIdentityByUserId(long userId);

  /// 按用户 ID 批量读摘要原始数据（批量用户摘要接口，PAP-108）：只读用户表与资料表，不读子表。
  ///
  /// @param userIds 用户 ID，调用方已去重
  /// @return 存在的那些，不保证顺序
  List<UserSummaryReadModel> findSummaries(Collection<Long> userIds);

  /// 按用户 ID 批量读收件信息原始数据（批量收件信息接口，PAP-142）：用户表的邮箱与状态、资料表的昵称。
  ///
  /// @param userIds 用户 ID，调用方已去重
  /// @return 存在的那些，不保证顺序
  List<UserRecipientReadModel> findRecipients(Collection<Long> userIds);

  /// 按用户名前缀读正常账号的摘要原始数据（@ 联想，PAP-118）：只含 `ACTIVE`，按用户名的字节序升序，
  /// 最多 `limit` 个。主页对谁可见由调用方用 `UserSummaryReadModel.access()` 判。
  ///
  /// @param prefix 前缀
  /// @param limit 最多几个
  /// @return 摘要原始数据，按用户名字节序
  List<UserSummaryReadModel> findActiveSummariesByUsernamePrefix(UsernamePrefix prefix, int limit);

  /// 按昵称前缀读正常账号的摘要原始数据（@ 联想，PAP-118）：只含 `ACTIVE` 且已设昵称、昵称以前缀开头
  /// （不分大小写）的，按小写昵称的码点序升序、同名按用户名，最多 `limit` 个。没设昵称的人昵称跟随用户名，
  /// 由用户名前缀覆盖。主页对谁可见由调用方判。
  ///
  /// @param prefix 昵称前缀，已转小写
  /// @param limit 最多几个
  /// @return 摘要原始数据，按小写昵称的码点序
  List<UserSummaryReadModel> findActiveSummariesByDisplayNamePrefix(
      DisplayNamePrefix prefix, int limit);

  /// 按当前用户名批量读摘要原始数据（用户名批量解析，PAP-118），四种状态都有：是不是正常账号由调用方判。
  ///
  /// @param usernames 用户名，调用方已去重；可以为空集合
  /// @return 存在的那些，不保证顺序；空集合时返回空列表
  List<UserSummaryReadModel> findSummariesByUsernames(Collection<Username> usernames);

  /// 按用户 ID 读头像归属：给头像读取接口判状态、比对版本。
  ///
  /// @param userId 用户 ID
  /// @return 归属；不存在时为空
  Optional<AvatarOwner> findAvatarOwner(long userId);
}
