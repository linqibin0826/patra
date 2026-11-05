package dev.patra.identity.domain.account.port.verification;

import dev.patra.identity.domain.account.model.enums.HumanVerificationAction;
import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import dev.patra.identity.domain.account.model.enums.HumanVerificationResult;

/// 人机验证：核对浏览器提交的一次性令牌。每家厂商一个实现，用哪家由配置决定。
///
/// 三种结果都用返回值表达，不抛异常；实现不把令牌写进日志。
public interface HumanVerificationPort {

  /// 这个实现对应的厂商。
  ///
  /// @return 厂商
  HumanVerificationProvider provider();

  /// 核对令牌。
  ///
  /// @param token 浏览器提交的令牌
  /// @param action 这次操作，令牌必须是为它签发的
  /// @return 结果
  HumanVerificationResult verify(String token, HumanVerificationAction action);
}
