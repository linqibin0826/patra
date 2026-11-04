package dev.patra.identity.domain.account.port.oauth;

import java.time.Duration;
import java.util.Optional;

/// 发出去的授权请求的存储，按 `state` 存取。证明「这个 state 是我们发的」，取出即删，同一个回调不能用两次。
///
/// 内容对领域层不透明。存储暂时不可用时抛带 `DEP_UNAVAILABLE` 特征的异常（503）。
public interface AuthorizationRequestStorePort {

  /// 存一个授权请求。
  ///
  /// @param state `state`
  /// @param payload 序列化之后的授权请求
  /// @param ttl 有效期
  void save(String state, String payload, Duration ttl);

  /// 读一个授权请求，不删。
  ///
  /// @param state `state`
  /// @return 序列化之后的授权请求；不存在或已过期时为空
  Optional<String> find(String state);

  /// 原子地取出并删除：并发的两个回调只有一个拿得到。
  ///
  /// @param state `state`
  /// @return 序列化之后的授权请求；不存在、已过期或已被取走时为空
  Optional<String> consume(String state);
}
