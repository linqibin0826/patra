package dev.linqibin.patra.identity.infra.adapter.read;

import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.read.UserAccountReadModel;
import dev.linqibin.patra.identity.domain.port.read.UserReadPort;
import dev.linqibin.patra.identity.infra.adapter.persistence.dao.UserDao;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 前台用户读端口的 JPA 实现。
@Component
@RequiredArgsConstructor
public class UserReadAdapter implements UserReadPort {

  private final UserDao dao;

  /// 按 ID 读账号信息。
  ///
  /// @param userId 用户 ID
  /// @return 账号信息
  @Override
  public Optional<UserAccountReadModel> findAccount(long userId) {
    return dao.findById(userId)
        .map(
            entity ->
                UserAccountReadModel.of(
                    entity.getId(), entity.getEmail(), UserStatus.valueOf(entity.getStatus())));
  }
}
