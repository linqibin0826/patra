package dev.linqibin.patra.identity.session;

import io.lettuce.core.RedisBusyException;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisLoadingException;
import io.lettuce.core.RedisReadOnlyException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;

/// 判断一个 Redis 异常是不是「过一会儿再试就好」的暂时失败。
///
/// 暂时失败：连不上、超时，Redis 处于 `LOADING`、`READONLY`、`BUSY`、`MASTERDOWN` 状态，
/// 以及连接层的 `RedisException`（Redis 重启瞬间在途命令的「Connection closed」、命令被中断）。
/// 其余服务端错误回复（脚本写错、`WRONGTYPE`、`NOAUTH`）是缺陷或配置错，不算暂时，调用方原样抛出成 500。
public final class TransientRedisFailures {

  /// 没有专门异常类的状态，按回复的前缀识别。
  private static final String MASTER_DOWN_PREFIX = "MASTERDOWN";

  /// 工具类，不允许实例化。
  private TransientRedisFailures() {}

  /// 判断是否暂时失败。
  ///
  /// @param failure Spring Data Redis 抛出的异常
  /// @return 暂时失败时为 `true`
  public static boolean isTransient(RuntimeException failure) {
    if (failure instanceof DataAccessResourceFailureException
        || failure instanceof QueryTimeoutException) {
      return true;
    }
    if (!(failure instanceof RedisSystemException)) {
      return false;
    }
    Throwable cause = failure.getCause();
    if (cause instanceof RedisLoadingException
        || cause instanceof RedisReadOnlyException
        || cause instanceof RedisBusyException) {
      return true;
    }
    if (cause instanceof RedisCommandExecutionException) {
      // 服务端回了错误：除了 MASTERDOWN，其余（NOAUTH、WRONGTYPE、NOSCRIPT）是缺陷或配置错
      return cause.getMessage() != null && cause.getMessage().startsWith(MASTER_DOWN_PREFIX);
    }
    // 其余直接继承 RedisException 的都是连接层的事：连接关闭、命令被中断
    return cause instanceof RedisException;
  }
}
