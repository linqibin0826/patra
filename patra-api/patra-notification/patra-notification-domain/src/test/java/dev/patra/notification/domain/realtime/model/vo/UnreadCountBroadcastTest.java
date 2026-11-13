package dev.patra.notification.domain.realtime.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/// 未读数广播：哪个实例发的、哪些用户的未读数变了。
class UnreadCountBroadcastTest {

  /// 带发送实例的标识；用户 ID 拷贝一份，调用方之后改自己的集合不影响广播。
  @Test
  void should_copy_user_ids_and_carry_origin_value() {
    Set<Long> ids = new HashSet<>(Set.of(7L, 8L));

    UnreadCountBroadcast broadcast = UnreadCountBroadcast.of(new InstanceId("a"), ids);
    ids.add(9L);

    assertThat(broadcast.origin()).isEqualTo("a");
    assertThat(broadcast.userIds()).containsExactlyInAnyOrder(7L, 8L);
  }

  /// 接收方据此跳过自己发的广播。
  @Test
  void should_tell_whether_it_was_sent_by_an_instance() {
    UnreadCountBroadcast broadcast = UnreadCountBroadcast.of(new InstanceId("a"), Set.of(7L));

    assertThat(broadcast.sentBy(new InstanceId("a"))).isTrue();
    assertThat(broadcast.sentBy(new InstanceId("b"))).isFalse();
  }
}
