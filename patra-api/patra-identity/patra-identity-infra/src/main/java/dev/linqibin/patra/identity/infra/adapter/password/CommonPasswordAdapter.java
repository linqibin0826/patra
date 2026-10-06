package dev.linqibin.patra.identity.infra.adapter.password;

import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.port.password.CommonPasswordPort;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/// 常见密码名单：Django `CommonPasswordValidator` 自带的 2 万条（Royce Williams 整理，BSD 许可）。
///
/// 启动时整份读进内存，每条转成比较键。不按长度过滤：长度按原始输入算，比较前却要先规范化，
/// ` 123456 ` 这类输入的比较键会比 8 位短。
@Component
public final class CommonPasswordAdapter implements CommonPasswordPort {

  /// 默认名单的位置。
  static final String DEFAULT_LOCATION = "password/common-passwords.txt.gz";

  private final Set<String> comparisonKeys;

  /// 加载默认名单。
  public CommonPasswordAdapter() {
    this(DEFAULT_LOCATION);
  }

  /// 加载指定位置的名单（gzip，一行一条）。
  ///
  /// @param classpathLocation classpath 上的位置
  CommonPasswordAdapter(String classpathLocation) {
    this.comparisonKeys = load(classpathLocation);
  }

  /// 比较键是否在名单里。
  ///
  /// @param comparisonKey 比较键
  /// @return 在名单里时为 `true`
  @Override
  public boolean isCommon(String comparisonKey) {
    return comparisonKeys.contains(comparisonKey);
  }

  /// 读名单：去掉首尾空白、跳过空行、转成比较键。
  ///
  /// @param location classpath 上的位置
  /// @return 比较键集合
  private static Set<String> load(String location) {
    try (InputStream raw = new ClassPathResource(location).getInputStream();
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::strip)
          .filter(line -> !line.isEmpty())
          .map(PlainPassword::comparisonKeyOf)
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new UncheckedIOException("读不到常见密码名单: " + location, e);
    }
  }
}
