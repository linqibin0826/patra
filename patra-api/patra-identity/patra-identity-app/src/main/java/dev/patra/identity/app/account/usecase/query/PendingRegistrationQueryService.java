package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.port.oauth.PendingRegistrationPort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 按待补填凭证读记录。凭证没带、过期或已用完都是 422，门户让用户回登录页重走。
@Component
@RequiredArgsConstructor
public class PendingRegistrationQueryService {

  private final PendingRegistrationPort pendingRegistrations;

  /// 读待补填记录。
  ///
  /// @param ticket 凭证原文，可以为 `null`
  /// @return 记录
  /// @throws InvalidUserFieldsException 凭证没带或不可用
  public PendingRegistration require(String ticket) {
    if (ticket == null || ticket.isBlank()) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.ticketRequired()));
    }
    return pendingRegistrations
        .find(ticket)
        .orElseThrow(
            () -> new InvalidUserFieldsException(List.of(UserFieldViolations.ticketInvalid())));
  }

  /// 读补填页要显示的内容。
  ///
  /// @param ticket 凭证原文，可以为 `null`
  /// @return 视图
  /// @throws InvalidUserFieldsException 凭证没带或不可用
  public PendingRegistrationView view(String ticket) {
    PendingRegistration registration = require(ticket);
    return PendingRegistrationView.of(
        registration, pendingRegistrations.findCode(ticket).orElse(null));
  }
}
