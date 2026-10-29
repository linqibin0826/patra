package dev.patra.identity.infra.account.geolocation;

import dev.patra.identity.domain.account.port.geolocation.IpGeolocationPort;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.ConfigBuilder;
import org.lionsoul.ip2region.service.InvalidConfigException;
import org.lionsoul.ip2region.service.Ip2Region;
import org.lionsoul.ip2region.xdb.InetAddressException;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.XdbException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/// 用 ip2region 离线库查 IP 属地（PAP-99 设计第 8.3、8.4 节）。
///
/// 数据文件 `ip2region_v4.xdb`、`ip2region_v6.xdb` 不在 jar 里：放在宿主机目录、只读挂进容器，由
/// `patra-infra/scripts/fetch-ip2region.sh` 按 `ip2region.properties` 的 tag 与校验和下载。
/// 用 `VIndexCache` 模式：每个库常驻 512 KB 向量索引、查询读几次文件，登录是低频事件，不把 46 MB 读进堆。
/// 启动时校验两个文件，缺失或不兼容直接启动失败。
@Slf4j
@Component
public final class Ip2RegionGeolocationAdapter implements IpGeolocationPort {

  /// IPv4 数据文件名。
  static final String V4_FILE = "ip2region_v4.xdb";

  /// IPv6 数据文件名。
  static final String V6_FILE = "ip2region_v6.xdb";

  /// 每个库的查询器个数：登录并发很低。
  private static final int SEARCHERS = 4;

  private final Ip2Region ip2region;

  /// 打开数据目录里的两个库。
  ///
  /// @param directory 数据目录
  /// @throws IllegalStateException 数据文件缺失或不兼容时
  public Ip2RegionGeolocationAdapter(
      @Value("${patra.identity.ip-geolocation.directory}") Path directory) {
    File v4 = directory.resolve(V4_FILE).toFile();
    File v6 = directory.resolve(V6_FILE).toFile();
    try {
      Searcher.verifyFromFile(v4);
      Searcher.verifyFromFile(v6);
      this.ip2region = Ip2Region.create(config(v4).asV4(), config(v6).asV6());
    } catch (IOException | XdbException | InvalidConfigException | RuntimeException e) {
      throw new IllegalStateException(
          "ip2region 数据文件不可用（"
              + directory.toAbsolutePath()
              + " 下的 "
              + V4_FILE
              + "、"
              + V6_FILE
              + "）：运行 patra-infra/scripts/fetch-ip2region.sh 下载",
          e);
    }
  }

  /// 查属地。只收 IP 字面量（不做 DNS 解析），查询出错记 WARN 返回空。
  ///
  /// @param ip IP 文本
  /// @return 属地；查不到时为空
  @Override
  public Optional<String> locate(String ip) {
    if (ip == null) {
      return Optional.empty();
    }
    byte[] bytes;
    try {
      bytes = InetAddress.ofLiteral(ip).getAddress();
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(RegionText.normalize(ip2region.search(bytes)));
    } catch (InetAddressException | IOException e) {
      log.warn("查 IP 属地失败: {}", e.getMessage());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  /// 关闭查询器池。
  @PreDestroy
  public void close() {
    try {
      ip2region.close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /// 一个库的配置：向量索引缓存、少量查询器。
  ///
  /// @param file 数据文件
  /// @return 配置构建器
  private static ConfigBuilder config(File file) {
    return Config.custom()
        .setCachePolicy(Config.VIndexCache)
        .setSearchers(SEARCHERS)
        .setXdbFile(file);
  }
}
