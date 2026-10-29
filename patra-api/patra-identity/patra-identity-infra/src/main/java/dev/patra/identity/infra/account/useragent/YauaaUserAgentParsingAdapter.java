package dev.patra.identity.infra.account.useragent;

import dev.patra.identity.domain.account.model.vo.DeviceProfile;
import dev.patra.identity.domain.account.port.useragent.UserAgentParsingPort;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import java.util.Map;
import java.util.Set;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Component;

/// 用 Yauaa 把 User-Agent 解析成「浏览器主版本 · 系统族」与端（PAP-99 设计第 8.2 节）。
///
/// 只取四个字段、启动时就建好查找表（首个登录不用等）、丢掉内置测试用例。分析器线程安全，
/// 命中缓存不加锁。
///
/// 分析器常驻约 108 MB 堆（本机实测），整个 JVM 只建一个：放在懒加载的静态持有类里，构造器里触发初始化。
/// 每个 Spring 上下文各建一个的话，集成测试缓存的几个上下文就会撑爆测试 JVM。Yauaa 文档反对静态持有，
/// 针对的是 servlet 容器热卸载应用时的泄漏，可执行 jar 没有这个场景。
@Component
public class YauaaUserAgentParsingAdapter implements UserAgentParsingPort {

  /// 设备名最多多少个字符（登录记录的列宽）。
  static final int NAME_MAX_LENGTH = 64;

  /// 不计入设备名的取值：Yauaa 的「未知」与机器人 / 攻击串的占位值。
  private static final Set<String> UNKNOWN_VALUES =
      Set.of("Unknown", "??", "Hacker", "Cloud", "Anonymized");

  /// 系统名到系统族：Yauaa 的系统名是细名，Windows 各代都是 `Windows NT`。
  private static final Map<String, String> OS_FAMILIES =
      Map.of(
          "Mac OS", "macOS",
          "Windows NT", "Windows",
          "Windows 9x", "Windows",
          "Chrome OS", "ChromeOS");

  private final UserAgentAnalyzer analyzer;

  /// 创建适配器，触发（JVM 内只一次的）分析器初始化。
  public YauaaUserAgentParsingAdapter() {
    this.analyzer = AnalyzerHolder.ANALYZER;
  }

  /// 解析。`null` 进则名字为空、端未知；Yauaa 判成攻击串（空串、注入）的名字也为空。
  ///
  /// @param userAgent User-Agent，可以为 `null`
  /// @return 设备
  @Override
  public DeviceProfile parse(String userAgent) {
    if (userAgent == null) {
      return DeviceProfile.unknown();
    }
    UserAgent parsed = analyzer.parse(userAgent);
    String deviceClass = parsed.getValue(UserAgent.DEVICE_CLASS);
    String name =
        "Hacker".equals(deviceClass)
            ? null
            : deviceName(
                parsed.getValue(UserAgent.AGENT_NAME),
                parsed.getValue(UserAgent.AGENT_VERSION_MAJOR),
                parsed.getValue(UserAgent.OPERATING_SYSTEM_NAME));
    return DeviceProfile.of(name, deviceClassOf(deviceClass));
  }

  /// 拼设备名：浏览器段 + ` · ` + 系统段，缺哪段省哪段，两段都缺为 `null`；超长截断。
  ///
  /// @param agent 浏览器名
  /// @param major 浏览器主版本
  /// @param os 系统名
  /// @return 设备名；什么都没有时为 `null`
  static String deviceName(String agent, String major, String os) {
    String browser = null;
    if (isKnown(agent)) {
      browser = isKnown(major) ? agent + " " + major : agent;
    }
    String family = osFamily(os);
    String name;
    if (browser == null && family == null) {
      return null;
    } else if (browser == null) {
      name = family;
    } else if (family == null) {
      name = browser;
    } else {
      name = browser + " · " + family;
    }
    return name.length() > NAME_MAX_LENGTH ? name.substring(0, NAME_MAX_LENGTH) : name;
  }

  /// 系统名归一成系统族；未知值返回 `null`。
  ///
  /// @param os Yauaa 的系统名
  /// @return 系统族；未知时为 `null`
  static String osFamily(String os) {
    if (!isKnown(os)) {
      return null;
    }
    return OS_FAMILIES.getOrDefault(os, os);
  }

  /// Yauaa 的设备类映射成端。
  ///
  /// @param deviceClass Yauaa 的 `DeviceClass`
  /// @return 端
  private static DeviceClass deviceClassOf(String deviceClass) {
    if (deviceClass == null) {
      return DeviceClass.UNKNOWN;
    }
    return switch (deviceClass) {
      case "Desktop" -> DeviceClass.DESKTOP;
      case "Phone", "Mobile" -> DeviceClass.MOBILE;
      case "Tablet", "eReader" -> DeviceClass.TABLET;
      default -> DeviceClass.UNKNOWN;
    };
  }

  /// 是否是有意义的取值。
  ///
  /// @param value Yauaa 的字段值
  /// @return 非空且不是未知占位值时为 `true`
  private static boolean isKnown(String value) {
    return value != null && !value.isBlank() && !UNKNOWN_VALUES.contains(value);
  }

  /// 分析器的懒加载持有类：第一次访问时建，JVM 内只建一次。
  private static final class AnalyzerHolder {

    /// 只取四个字段的分析器。
    static final UserAgentAnalyzer ANALYZER =
        UserAgentAnalyzer.newBuilder()
            .hideMatcherLoadStats()
            .withFields(
                UserAgent.DEVICE_CLASS,
                UserAgent.AGENT_NAME,
                UserAgent.AGENT_VERSION_MAJOR,
                UserAgent.OPERATING_SYSTEM_NAME)
            .withCache(10_000)
            .immediateInitialization()
            .dropTests()
            .build();

    /// 只有静态字段。
    private AnalyzerHolder() {}
  }
}
