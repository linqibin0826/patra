package dev.patra.analytics.simulator.report;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.util.List;

/// 查各表的存储（D8：压缩后每行字节数与总磁盘占用）。
public final class StorageStats {

  private static final String SQL =
      """
      SELECT table,
             sum(rows) AS rows,
             sum(bytes_on_disk) AS bytes_on_disk,
             sum(data_compressed_bytes) AS compressed,
             sum(data_uncompressed_bytes) AS uncompressed,
             uniqExact(partition) AS partitions
      FROM system.parts
      WHERE active AND database = currentDatabase()
      GROUP BY table
      ORDER BY bytes_on_disk DESC
      """;

  private StorageStats() {}

  /// 查询。
  ///
  /// @param client ClickHouse 客户端（默认库 `patra_analytics`）
  /// @return 各表，按磁盘占用降序
  public static List<StorageRow> query(Client client) {
    return client.queryAll(SQL).stream().map(StorageStats::row).toList();
  }

  /// 转成一行。
  ///
  /// @param record 查询结果
  /// @return 行
  private static StorageRow row(GenericRecord record) {
    return new StorageRow(
        record.getString("table"),
        record.getLong("rows"),
        record.getLong("bytes_on_disk"),
        record.getLong("compressed"),
        record.getLong("uncompressed"),
        record.getLong("partitions"));
  }
}
