package dev.patra.notification.app.realtime.service;

import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.port.gateway.PresenceGateway;
import dev.patra.tool.core.security.CurrentUserPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/// 推送连接表（PAP-145）：「用户 → 连接」。
@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadCountStreamRegistry {

  private final InboxReadPort inboxReadPort;
  private final PresenceGateway presenceGateway;
  private final CurrentUserPort currentUser;

  /// 每个用户开着的连接；集合本身也是这个用户的锁。
  private final ConcurrentMap<Long, Set<UnreadCountStream>> streams = new ConcurrentHashMap<>();

  /// 给当前用户登记一条连接（要登录，没有身份断言时抛出，安全 starter 回 401）。
  ///
  /// @param stream 连接
  /// @return 当前用户 ID：连接结束时注销要用
  public long openForCurrentUser(UnreadCountStream stream) {
    long userId = currentUser.require().userId();
    open(userId, stream);
    return userId;
  }

  /// 登记一条连接：放进表、推一次当前值、记一次在站。
  ///
  /// 先放进表再读数：读之后提交的变化一定会再推一次，不会漏。读数或推送失败时撤下这条连接、原样抛出，
  /// 不返回事件流。
  ///
  /// @param userId 用户
  /// @param stream 连接
  void open(long userId, UnreadCountStream stream) {
    Set<UnreadCountStream> userStreams =
        streams.compute(
            userId,
            (id, current) -> {
              Set<UnreadCountStream> set =
                  current != null ? current : ConcurrentHashMap.newKeySet();
              set.add(stream);
              return set;
            });
    synchronized (userStreams) {
      try {
        stream.sendCounts(inboxReadPort.unreadCounts(userId));
      } catch (RuntimeException e) {
        close(userId, stream);
        throw e;
      }
    }
    presenceGateway.markSeen(userId);
  }

  /// 注销一条连接（浏览器走了、到了寿命、出错）。重复注销不报错。
  ///
  /// @param userId 用户
  /// @param stream 连接
  public void close(long userId, UnreadCountStream stream) {
    streams.computeIfPresent(
        userId,
        (id, current) -> {
          current.remove(stream);
          return current.isEmpty() ? null : current;
        });
  }

  /// 给这些用户的每条连接推一次当前的三个数。
  ///
  /// 读数放在该用户的锁里：两次变化在两个线程里同时推时，后拿到锁的读到的一定是两次提交之后的状态，
  /// 最后推出去的总是最新值。没有连接的用户不读库。写不进去的连接被剔除；读数失败只记日志，等下一次变化
  /// 或下一次重连。
  ///
  /// @param userIds 未读数变了的用户
  public void push(Collection<Long> userIds) {
    for (long userId : userIds) {
      Set<UnreadCountStream> userStreams = streams.get(userId);
      if (userStreams == null) {
        continue;
      }
      synchronized (userStreams) {
        UnreadCounts counts;
        try {
          counts = inboxReadPort.unreadCounts(userId);
        } catch (RuntimeException e) {
          log.warn("推送时读未读数失败，这一轮不推：userId={}", userId, e);
          continue;
        }
        for (UnreadCountStream stream : List.copyOf(userStreams)) {
          try {
            stream.sendCounts(counts);
          } catch (RuntimeException e) {
            drop(userId, stream, e);
          }
        }
      }
    }
  }

  /// 心跳一轮：给每条连接写一次心跳，写不进去的剔除；仍有连接的用户各记一次在站（决策 H）。
  ///
  /// 所有连接一起发，一条连接的最长静默就是一轮的间隔（25 秒）。
  public void heartbeat() {
    List<Long> online = new ArrayList<>();
    streams.forEach(
        (userId, userStreams) -> {
          synchronized (userStreams) {
            for (UnreadCountStream stream : List.copyOf(userStreams)) {
              try {
                stream.sendHeartbeat();
              } catch (RuntimeException e) {
                drop(userId, stream, e);
              }
            }
            if (!userStreams.isEmpty()) {
              online.add(userId);
            }
          }
        });
    online.forEach(presenceGateway::markSeen);
  }

  /// 停机开始时结束所有连接、清空表。
  ///
  /// 连接永远不会自己结束，不先结束它们，Boot 的优雅停机会白等 30 秒再强关。`ContextClosedEvent` 先于优雅停机的
  /// 等待发出；浏览器看到流正常结束，3 秒后重连（新实例起来之前拿到 503，前端退回轮询）。
  @EventListener(ContextClosedEvent.class)
  public void closeAll() {
    streams.forEach(
        (userId, userStreams) -> {
          for (UnreadCountStream stream : List.copyOf(userStreams)) {
            try {
              stream.close();
            } catch (RuntimeException e) {
              log.debug("停机时结束推送连接失败，忽略：userId={}", userId, e);
            }
          }
        });
    streams.clear();
  }

  /// 剔除一条写不进去的连接：从表里注销，再结束它。
  ///
  /// @param userId 用户
  /// @param stream 连接
  /// @param cause 发送失败的原因
  private void drop(long userId, UnreadCountStream stream, RuntimeException cause) {
    log.debug("推送连接写不进去，剔除：userId={}", userId, cause);
    close(userId, stream);
    try {
      stream.close();
    } catch (RuntimeException e) {
      log.debug("结束推送连接失败，忽略：userId={}", userId, e);
    }
  }
}
