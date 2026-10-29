package dev.patra.identity.app.account.usecase.avatar;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
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

/// 当前用户移除头像：事务里加用户行锁、复核状态、清资料里的键 → 提交后删对象（失败只记日志）。
/// 没有头像也算成功。
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoveAvatarHandler implements CommandHandler<RemoveAvatarCommand, Void> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserProfileRepository profiles;
  private final AvatarStoragePort storage;
  private final TransactionOperations transactions;

  /// 移除头像。
  ///
  /// @param command 命令
  /// @return 无
  @Override
  public Void handle(RemoveAvatarCommand command) {
    long userId = currentUserPort.require().userId();
    Optional<Avatar> removed =
        Objects.requireNonNull(transactions.execute(status -> clear(userId)));
    removed.ifPresent(
        avatar -> {
          try {
            storage.delete(avatar);
          } catch (RuntimeException e) {
            log.warn("删除头像对象失败: userId={}, hash={}", userId, avatar.hash(), e);
          }
          log.info("头像已移除: userId={}", userId);
        });
    return null;
  }

  /// 锁内复核状态并清键。
  ///
  /// @param userId 用户 ID
  /// @return 被移除的头像
  private Optional<Avatar> clear(long userId) {
    User user = users.findByIdForUpdate(userId).orElseThrow(AuthenticationRequiredException::new);
    user.requireActive();
    UserProfile profile =
        profiles
            .findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("用户 " + userId + " 没有资料行"));
    Optional<Avatar> removed = profile.removeAvatar();
    if (removed.isPresent()) {
      profiles.save(profile);
    }
    return removed;
  }
}
