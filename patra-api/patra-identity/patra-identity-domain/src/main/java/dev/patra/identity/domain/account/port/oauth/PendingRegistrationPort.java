package dev.patra.identity.domain.account.port.oauth;

import dev.patra.identity.domain.account.model.vo.CodeCheck;
import dev.patra.identity.domain.account.model.vo.PendingCode;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.RegistrationCode;
import java.time.Duration;
import java.util.Optional;

/// 待补填记录的存储。凭证 `ticket` 只给发起的浏览器（门户写进 HttpOnly Cookie），存储里只有它的哈希。
///
/// 存储暂时不可用时抛带 `DEP_UNAVAILABLE` 特征的异常（503）。
public interface PendingRegistrationPort {

  /// 建一条待补填记录。
  ///
  /// @param registration 记录
  /// @param ttl 有效期
  /// @return 凭证原文
  String create(PendingRegistration registration, Duration ttl);

  /// 按凭证读。
  ///
  /// @param ticket 凭证原文，可以为 `null`
  /// @return 记录；凭证格式不对、不存在或已过期时为空
  Optional<PendingRegistration> find(String ticket);

  /// 写一个验证码，覆盖旧的：旧码作废，错误次数重新算。
  ///
  /// @param ticket 凭证原文
  /// @param pendingCode 绑定的用户名、邮箱和时间
  /// @param code 验证码，存储里只有哈希
  /// @param ttl 有效期
  void storeCode(String ticket, PendingCode pendingCode, RegistrationCode code, Duration ttl);

  /// 读发出去还没用掉的验证码。
  ///
  /// @param ticket 凭证原文
  /// @return 公开部分；没有时为空
  Optional<PendingCode> findCode(String ticket);

  /// 原子地校验验证码：对了立刻用掉，错满次数作废。
  ///
  /// @param ticket 凭证原文
  /// @param code 提交的验证码
  /// @param maxAttempts 最多错几次
  /// @return 校验结果
  CodeCheck verifyCode(String ticket, RegistrationCode code, int maxAttempts);

  /// 删掉待补填记录和它的验证码。
  ///
  /// @param ticket 凭证原文
  void delete(String ticket);
}
