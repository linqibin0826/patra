package dev.patra.identity.domain.session.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// IpAddressScope 单元测试：哪些地址算内网（PAP-99 设计第 8.1 节）。
@DisplayName("IpAddressScope 单元测试")
class IpAddressScopeTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "127.0.0.1",
        "10.0.0.1",
        "172.16.0.1",
        "172.31.255.255",
        "192.168.97.1",
        "100.64.0.0",
        "100.127.255.255",
        "169.254.1.1",
        "0.0.0.0",
        "::1",
        "::",
        "fd00::1",
        "fc00::1",
        "fe80::1"
      })
  @DisplayName("回环、私网段、CGNAT、链路本地、未指定地址、IPv6 ULA 都是内网")
  void should_treat_private_ranges_as_private(String ip) {
    assertThat(IpAddressScope.isPrivate(ip)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "172.15.255.255",
        "172.32.0.0",
        "100.63.255.255",
        "100.128.0.0",
        "8.8.8.8",
        "113.92.157.29",
        "2001:4860:4860::8888",
        "240e:3b7:3272:d8d0:db09:c067:8d59:539e"
      })
  @DisplayName("段外紧邻的地址与公网地址不是内网")
  void should_treat_public_addresses_as_public(String ip) {
    assertThat(IpAddressScope.isPrivate(ip)).isFalse();
  }

  @Test
  @DisplayName("null 与非字面量不是内网，不做 DNS 解析")
  void should_not_treat_unparseable_input_as_private() {
    assertThat(IpAddressScope.isPrivate(null)).isFalse();
    assertThat(IpAddressScope.isPrivate("abc")).isFalse();
    assertThat(IpAddressScope.isPrivate("localhost")).isFalse();
  }
}
