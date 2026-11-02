package dev.patra.analytics.simulator.report;

/// 一张表的存储（`system.parts` 里活跃片段的合计）。
///
/// @param table 表名
/// @param rows 行数
/// @param bytesOnDisk 磁盘占用（含索引、标记）
/// @param compressedBytes 数据压缩后
/// @param uncompressedBytes 数据未压缩
/// @param partitions 分区数
public record StorageRow(
    String table,
    long rows,
    long bytesOnDisk,
    long compressedBytes,
    long uncompressedBytes,
    long partitions) {}
