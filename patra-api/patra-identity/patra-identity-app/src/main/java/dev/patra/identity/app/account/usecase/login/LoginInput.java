package dev.patra.identity.app.account.usecase.login;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.tool.core.error.field.FieldViolation;
import java.util.ArrayList;
import java.util.List;

/// 解析过的登录字段，拿到它就说明字段都合法。规则在各值对象里，这里只负责一次报全所有字段错误。
///
/// @param email 邮箱
/// @param password 密码
/// @param client 客户端信息
record LoginInput(EmailAddress email, PlainPassword password, LoginClient client) {

  /// 解析命令里的原始输入：邮箱用注册时的规则，密码只查非空和 Unicode 合法，客户端类型和设备标识一起报。
  ///
  /// @param command 命令
  /// @return 合法的值对象
  /// @throws InvalidUserFieldsException 任一字段不合法，带全部字段错误
  static LoginInput parse(LoginUserCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    EmailAddress.validate(command.email()).ifPresent(violations::add);
    PlainPassword.validate(command.password()).ifPresent(violations::add);
    violations.addAll(LoginClient.validate(command.clientType(), command.deviceId()));
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    return new LoginInput(
        EmailAddress.of(command.email()),
        PlainPassword.of(command.password()),
        LoginClient.of(command.clientType(), command.deviceId()));
  }
}
