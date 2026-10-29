package dev.patra.identity.domain.account.port.repository;

import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.session.exception.UserModifiedConcurrentlyException;
import java.time.Instant;
import java.util.Optional;

/// 一次性邮件令牌的仓储。持久层只见哈希，任何方法都拿不到令牌原文。
///
/// 「同一用户同一用途只留一个有效令牌」靠签发前在同一事务里 {@link #revokeActive} 守，
/// 表上的部分唯一索引只是兜底；改邮箱的撤销令牌（`EMAIL_CHANGE_REVERT`）不受这条规则约束，
/// 也不被 {@link #revokeAllActive} 作废：连续改两次邮箱时，两个旧邮箱里的「不是我」链接要各自活满 7 天；
/// 消费一条时只作废比它更晚签发的（{@link #revokeRevertTokensIssuedAfter}）。
public interface EmailTokenRepository {

  /// 保存令牌，新令牌在这里分配雪花 ID。
  ///
  /// @param token 令牌
  /// @return 保存后的令牌，带 ID 和版本
  /// @throws UserModifiedConcurrentlyException 撞上「同用户同用途只留一个有效令牌」或哈希唯一约束
  EmailToken save(EmailToken token);

  /// 按哈希查令牌，不加锁。验证流程先用它找到用户、锁用户行，再做条件消费，和重发、封禁的加锁顺序一致。
  ///
  /// @param tokenHash 令牌原文的 SHA-256 十六进制
  /// @return 令牌；不存在时为空
  Optional<EmailToken> findByTokenHash(String tokenHash);

  /// 查一个用户某种用途的有效令牌：未消费、未作废、`expires_at > now`。同用途至多一条（部分唯一索引），
  /// 改邮箱的「待确认」状态就是这么查出来的；撤销令牌可并存，不用它查。
  ///
  /// @param userId 用户 ID
  /// @param purpose 用途
  /// @param now 当前时间，判过期的基准
  /// @return 有效令牌；没有时为空
  Optional<EmailToken> findActive(long userId, EmailTokenPurpose purpose, Instant now);

  /// 消费一枚令牌：一条带条件的 `UPDATE … RETURNING`，只有哈希、用途都对且没消费、没作废、没过期的行才会被
  /// 更新。两个请求同时消费同一枚令牌只有一个成功。
  ///
  /// @param tokenHash 令牌原文的 SHA-256 十六进制
  /// @param purpose 要求的用途
  /// @param now 当前时间，既是消费时间也是判过期的基准
  /// @return 消费后的令牌；没命中（不存在、用途不符、已过期、已消费、已作废）时为空，不区分原因
  Optional<EmailToken> consume(String tokenHash, EmailTokenPurpose purpose, Instant now);

  /// 作废一个用户某种用途的全部有效令牌。签发前调用。
  ///
  /// @param userId 用户 ID
  /// @param purpose 用途
  /// @param now 作废时间
  /// @return 作废了几行
  int revokeActive(long userId, EmailTokenPurpose purpose, Instant now);

  /// 作废一个用户除 `EMAIL_CHANGE_REVERT` 之外的全部有效令牌。封禁、改密码、重设密码、改邮箱生效时调用。
  ///
  /// @param userId 用户 ID
  /// @param now 作废时间
  /// @return 作废了几行
  int revokeAllActive(long userId, Instant now);

  /// 消费一条撤销令牌后调用：只作废该用户比它**更晚签发**的撤销令牌（ID 更大，雪花 ID 按时间递增），
  /// 更早的保留——越早的地址越可信。X → Y → Z 时攻击者先点 Y 的链接改回 Y，原主再点 X 的仍然胜出；
  /// 原主先点 X 的，Y 的随之作废。
  ///
  /// @param userId 用户 ID
  /// @param tokenId 刚消费的那条撤销令牌的 ID
  /// @param now 作废时间
  /// @return 作废了几行
  int revokeRevertTokensIssuedAfter(long userId, long tokenId, Instant now);

  /// 删掉过期时间早于某一刻的令牌，一次最多删一批。清理任务循环调用直到一批不足。
  ///
  /// @param cutoff 过期时间早于它的行才删
  /// @param batchSize 一批最多删几行
  /// @return 删了几行
  int deleteExpiredBefore(Instant cutoff, int batchSize);

  /// 删掉一个用户的全部令牌行（含改邮箱撤销令牌）：注销到期执行时用，行里有发往的邮箱和申请方 IP，作废不够。
  ///
  /// @param userId 用户 ID
  /// @return 删了几行
  int deleteAllByUserId(long userId);
}
