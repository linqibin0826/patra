package dev.patra.identity.infra.account.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 客户端网络的计数键：IPv4 按整个地址，IPv6 按前 64 位。
@DisplayName("ClientNetworkKey")
class ClientNetworkKeyTest {

  @Test
  @DisplayName("IPv4 按整个地址计")
  void ipv4_uses_whole_address() {
    assertThat(ClientNetworkKey.of("1.2.3.4")).isNotEqualTo(ClientNetworkKey.of("1.2.3.5"));
  }

  @Test
  @DisplayName("IPv6 按前 64 位计")
  void ipv6_uses_first_64_bits() {
    assertThat(ClientNetworkKey.of("2001:db8:0:0::1"))
        .isEqualTo(ClientNetworkKey.of("2001:db8:0:0:ffff::2"));
    assertThat(ClientNetworkKey.of("2001:db8:0:0::1"))
        .isNotEqualTo(ClientNetworkKey.of("2001:db8:0:1::1"));
  }

  @Test
  @DisplayName("IPv6 的不同写法算同一个键")
  void ipv6_spellings_are_equivalent() {
    assertThat(ClientNetworkKey.of("2001:DB8::1"))
        .isEqualTo(ClientNetworkKey.of("2001:db8:0:0::1"));
  }

  @Test
  @DisplayName("IPv4 映射地址和 IPv4 算同一个键")
  void ipv4_mapped_ipv6_equals_ipv4() {
    assertThat(ClientNetworkKey.of("::ffff:1.2.3.4")).isEqualTo(ClientNetworkKey.of("1.2.3.4"));
  }

  @Test
  @DisplayName("不是 IP 字面量时没有键")
  void returns_empty_for_invalid_literal() {
    assertThat(ClientNetworkKey.of("not-an-ip")).isEmpty();
    assertThat(ClientNetworkKey.of(null)).isEmpty();
  }

  @Test
  @DisplayName("键里不含 IP 原文")
  void key_does_not_contain_raw_ip() {
    assertThat(ClientNetworkKey.of("1.2.3.4").orElseThrow()).doesNotContain("1.2.3.4").hasSize(64);
  }
}
