package dev.patra.catalog.domain.fulltext.model.read;

import lombok.Builder;

/// 一个可下发的全文文件（PDF 或正文图片）。
///
/// @param objectKey 对象键
/// @param fileName 下发时用的文件名
/// @param contentType 内容类型
/// @param size 字节数
/// @param etag ETag 值（不带引号）
/// @author linqibin
/// @since 0.11.0
@Builder
public record FullTextFileRef(
    String objectKey, String fileName, String contentType, long size, String etag) {}
