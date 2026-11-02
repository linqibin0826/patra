package dev.patra.analytics.infra.collect.geolocation;

import dev.patra.analytics.domain.collect.model.vo.GeoLocation;
import dev.patra.analytics.domain.collect.port.geolocation.GeolocationPort;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
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

/// 用 ip2region 离线库查 IP 的国家与省份（采集 spec §4.4）。
///
/// 原始结果五段：`国家|省份|城市|ISP|ISO 国家码`，未知段写 `0`。国家码取第五段；省份只在国家码是 `CN` 时取第二段原文。
/// 数据目录与 identity 共用（`fetch-ip2region.sh` 下载）；文件缺失时启动失败，免得悄悄全变成 `null`。
@Slf4j
@Component
public final class Ip2RegionGeolocationAdapter implements GeolocationPort {

  /// IPv4 数据文件名
  static final String V4_FILE = "ip2region_v4.xdb";

  /// IPv6 数据文件名
  static final String V6_FILE = "ip2region_v6.xdb";

  /// 每个库的查询器个数
  private static final int SEARCHERS = 4;

  /// 正式的两位国家码
  private static final Set<String> ISO_COUNTRIES =
      Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

  private final Ip2Region ip2region;

  /// 打开数据目录里的两个库。
  ///
  /// @param directory 数据目录
  /// @throws IllegalStateException 数据文件缺失或不兼容时
  public Ip2RegionGeolocationAdapter(
      @Value("${patra.analytics.ip-geolocation.directory}") Path directory) {
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

  /// 查地区。只收 IP 字面量（不做 DNS 解析）；不合法、查询出错都返回未知。
  ///
  /// @param ip IP 文本
  /// @return 地区
  @Override
  public GeoLocation locate(String ip) {
    if (ip == null || ip.isBlank()) {
      return GeoLocation.unknown();
    }
    byte[] bytes;
    try {
      bytes = InetAddress.ofLiteral(ip).getAddress();
    } catch (IllegalArgumentException e) {
      return GeoLocation.unknown();
    }
    try {
      return parse(ip2region.search(bytes));
    } catch (InetAddressException | IOException e) {
      log.warn("查 IP 地区失败: {}", e.getMessage());
      return GeoLocation.unknown();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return GeoLocation.unknown();
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

  /// 解析 ip2region 的原始结果。
  ///
  /// @param region `国家|省份|城市|ISP|ISO 国家码`，可为 `null`
  /// @return 地区；国家码不是正式国家码时为未知
  static GeoLocation parse(String region) {
    if (region == null) {
      return GeoLocation.unknown();
    }
    String[] parts = region.split("\\|", -1);
    if (parts.length != 5 || !ISO_COUNTRIES.contains(parts[4])) {
      return GeoLocation.unknown();
    }
    String country = parts[4];
    String province = "CN".equals(country) && !"0".equals(parts[1]) ? parts[1] : null;
    return GeoLocation.of(country, province);
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
