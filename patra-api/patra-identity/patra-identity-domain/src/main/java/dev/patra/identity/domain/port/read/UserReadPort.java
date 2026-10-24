package dev.patra.identity.domain.port.read;

import dev.patra.identity.domain.model.read.AvatarOwner;
import dev.patra.identity.domain.model.read.PublicUserReadModel;
import dev.patra.identity.domain.model.read.UserAccountReadModel;
import dev.patra.identity.domain.model.read.UserSummaryReadModel;
import dev.patra.identity.domain.model.vo.Username;
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

  /// 按用户 ID 读头像归属：给头像读取接口判状态、比对版本。
  ///
  /// @param userId 用户 ID
  /// @return 归属；不存在时为空
  Optional<AvatarOwner> findAvatarOwner(long userId);
}
