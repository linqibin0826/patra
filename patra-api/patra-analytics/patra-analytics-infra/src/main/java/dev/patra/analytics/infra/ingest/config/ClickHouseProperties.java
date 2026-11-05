package dev.patra.analytics.infra.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// ClickHouse 连接配置（`patra.clickhouse.*`，PAP-165 的约定；地址与账号在 Nacos，密码来自 `clickhouse.env`）。
///
/// @param url HTTP 接口地址，如 `http://主机:8123`
/// @param username 账号
/// @param password 密码
/// @param database 默认库
@ConfigurationProperties("patra.clickhouse")
public record ClickHouseProperties(
    String url,
    String username,
    String password,
    @DefaultValue("patra_analytics") String database) {}
