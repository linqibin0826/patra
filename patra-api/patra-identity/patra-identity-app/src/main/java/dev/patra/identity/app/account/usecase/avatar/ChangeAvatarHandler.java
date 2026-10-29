package dev.patra.identity.app.account.usecase.avatar;

import dev.patra.identity.domain.account.exception.InvalidAvatarException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRejection;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRenditions;
import dev.patra.identity.domain.account.port.avatar.AvatarImagePort;
import dev.patra.identity.domain.account.port.avatar.AvatarStoragePort;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 当前用户换头像：预检状态 → 出图 → 写新对象 → 事务里加用户行锁、复核状态、换资料里的键 → 提交后删旧对象。
///
/// 出图与写对象都在事务外，不占用户行锁和数据库连接。对象存储不参与事务：提交失败时删掉刚写的新对象，提交后
/// 删旧对象失败只记日志（旧对象留在本用户前缀下，注销到期时按前缀一并删掉）。同一张图重复上传时哈希相同，
/// 直接返回，不写对象、不动库，也不会把正在用的对象当旧对象删掉。
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeAvatarHandler implements CommandHandler<ChangeAvatarCommand, Avatar> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserProfileRepository profiles;
  private final AvatarImagePort images;
  private final AvatarStoragePort storage;
  private final TransactionOperations transactions;

  /// 换头像。
  ///
  /// @param command 命令
  /// @return 生效的头像
  @Override
  public Avatar handle(ChangeAvatarCommand command) {
    long userId = currentUserPort.require().userId();
    byte[] content = command.content();
    if (content.length == 0) {
      throw new InvalidAvatarException(AvatarRejection.REQUIRED);
    }
    User current = users.findById(userId).orElseThrow(AuthenticationRequiredException::new);
    current.requireActive();
    Optional<Avatar> existing = profileOf(userId).getAvatar();

    AvatarRenditions renditions = images.render(content);
    Avatar avatar = Avatar.forContent(userId, renditions.large());
    if (existing.filter(avatar::equals).isPresent()) {
      return avatar;
    }
    storage.store(avatar, renditions);

    Optional<Avatar> previous;
    try {
      previous = Objects.requireNonNull(transactions.execute(status -> swap(userId, avatar)));
    } catch (RuntimeException e) {
      deleteQuietly(avatar, "提交失败后删除新头像对象失败");
      throw e;
    }
    previous.filter(old -> !old.equals(avatar)).ifPresent(old -> deleteQuietly(old, "删除旧头像对象失败"));
    log.info("头像已更换: userId={}, hash={}", userId, avatar.hash());
    return avatar;
  }

  /// 锁内复核状态并换资料里的键。
  ///
  /// @param userId 用户 ID
  /// @param avatar 新头像
  /// @return 被替换的旧头像
  private Optional<Avatar> swap(long userId, Avatar avatar) {
    User user = users.findByIdForUpdate(userId).orElseThrow(AuthenticationRequiredException::new);
    user.requireActive();
    UserProfile profile = profileOf(userId);
    Optional<Avatar> previous = profile.changeAvatar(avatar);
    profiles.save(profile);
    return previous;
  }

  /// 读资料；缺行是数据不一致。
  ///
  /// @param userId 用户 ID
  /// @return 资料
  private UserProfile profileOf(long userId) {
    return profiles
        .findByUserId(userId)
        .orElseThrow(() -> new IllegalStateException("用户 " + userId + " 没有资料行"));
  }

  /// 尽力删除对象，失败只记日志。
  ///
  /// @param avatar 头像
  /// @param message 日志前缀
  private void deleteQuietly(Avatar avatar, String message) {
    try {
      storage.delete(avatar);
    } catch (RuntimeException e) {
      log.warn("{}: userId={}, hash={}", message, avatar.userId(), avatar.hash(), e);
    }
  }
}
