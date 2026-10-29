package dev.patra.identity.domain.session.model.vo;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/// 判断一个 IP 是不是内网地址：回环、私网段、链路本地、未指定地址、CGNAT（`100.64.0.0/10`，tailnet 用它）、
/// IPv6 唯一本地地址（`fc00::/7`）。内网地址不查属地，列表里显示「内网」。
public final class IpAddressScope {

  /// 只有静态方法。
  private IpAddressScope() {}

  /// 是否内网地址。只认字面量，不做 DNS 解析；解析不了按「不是内网」。
  ///
  /// @param ip IP 文本，可以为 `null`
  /// @return 是内网地址时为 `true`
  public static boolean isPrivate(String ip) {
    if (ip == null) {
      return false;
    }
    InetAddress address;
    try {
      address = InetAddress.ofLiteral(ip);
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (address.isLoopbackAddress()
        || address.isSiteLocalAddress()
        || address.isLinkLocalAddress()
        || address.isAnyLocalAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    if (address instanceof Inet4Address) {
      // 100.64.0.0/10：第一段 100，第二段高两位是 01
      return (bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 0x40;
    }
    // fc00::/7：第一个字节高七位是 1111110
    return address instanceof Inet6Address && (bytes[0] & 0xfe) == 0xfc;
  }
}
