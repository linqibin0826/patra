package dev.patra.identity.infra.account.throttle;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

/// 客户端网络的计数键：IPv4 按整个地址，IPv6 按前 64 位，再做 SHA-256，Redis 里不放 IP 原文。
///
/// IPv6 按 /64 计，因为一个用户通常握有整段地址，按单个地址计数等于没限。
final class ClientNetworkKey {

  /// IPv6 网络前缀的字节数。
  private static final int IPV6_NETWORK_BYTES = 8;

  /// 只有静态方法。
  private ClientNetworkKey() {}

  /// 算计数键。解析不触发 DNS。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 十六进制的键；不是 IP 字面量时为空
  static Optional<String> of(String clientIp) {
    if (clientIp == null) {
      return Optional.empty();
    }
    InetAddress address;
    try {
      address = InetAddress.ofLiteral(clientIp);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    byte[] network =
        address instanceof Inet6Address
            ? Arrays.copyOf(address.getAddress(), IPV6_NETWORK_BYTES)
            : address.getAddress();
    return Optional.of(sha256Hex(network));
  }

  /// 计算 SHA-256 的十六进制形式。
  ///
  /// @param value 输入
  /// @return 64 位十六进制
  private static String sha256Hex(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JDK 缺少 SHA-256", e);
    }
  }
}
