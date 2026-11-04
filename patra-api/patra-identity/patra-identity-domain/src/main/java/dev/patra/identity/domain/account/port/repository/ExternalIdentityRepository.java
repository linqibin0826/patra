package dev.patra.identity.domain.account.port.repository;

import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import java.util.List;
import java.util.Optional;

/// 外部身份的仓储。
public interface ExternalIdentityRepository {

  /// 按平台和主体查：第三方登录靠它找到账号。
  ///
  /// @param provider 平台
  /// @param subject 规范化之后的主体
  /// @return 外部身份；不存在时为空
  Optional<ExternalIdentity> findByProviderAndSubject(IdentityProvider provider, String subject);

  /// 查一个用户在某一家绑的账号。
  ///
  /// @param userId 用户 ID
  /// @param provider 平台
  /// @return 外部身份；没绑时为空
  Optional<ExternalIdentity> findByUserIdAndProvider(long userId, IdentityProvider provider);

  /// 查一个用户绑的全部账号。
  ///
  /// @param userId 用户 ID
  /// @return 外部身份，顺序不保证
  List<ExternalIdentity> findAllByUserId(long userId);

  /// 一个用户绑了几家。
  ///
  /// @param userId 用户 ID
  /// @return 行数
  long countByUserId(long userId);

  /// 写入一行；撞上 `(provider, subject)` 或 `(user_id, provider)` 时什么都不做，事务可以继续。必须在事务里调。
  ///
  /// @param identity 新的外部身份
  /// @return 真的写入时为 `true`
  boolean insertIfAbsent(ExternalIdentity identity);

  /// 保存已有的一行：登录时更新账号标识和最近登录时间。
  ///
  /// @param identity 外部身份
  void save(ExternalIdentity identity);

  /// 解绑。必须在事务里调。
  ///
  /// @param userId 用户 ID
  /// @param provider 平台
  /// @return 真的删掉了一行时为 `true`
  boolean deleteByUserIdAndProvider(long userId, IdentityProvider provider);

  /// 注销到期时删掉这个用户的全部外部身份，同一个第三方账号可以重新注册。必须在事务里调。
  ///
  /// @param userId 用户 ID
  void deleteAllByUserId(long userId);
}
