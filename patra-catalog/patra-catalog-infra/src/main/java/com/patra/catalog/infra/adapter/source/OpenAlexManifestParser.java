package com.patra.catalog.infra.adapter.source;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.common.error.trait.StandardErrorTrait;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/// OpenAlex Manifest 解析器。
///
/// 提供 manifest JSON 解析方法，供 {@link VenueSourceFileAdapter} 使用。
///
/// **流式处理特性**：
///
/// - 直接从 InputStream 解析 JSON，无磁盘落盘
/// - 调用方负责管理 InputStream 生命周期
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public final class OpenAlexManifestParser {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private OpenAlexManifestParser() {
    // 工具类禁止实例化
  }

  /// 解析 manifest JSON 输入流。
  ///
  /// **注意**：此方法不关闭 InputStream，调用方负责管理流的生命周期。
  ///
  /// @param inputStream manifest JSON 输入流
  /// @return 解析后的 OpenAlexManifest 对象
  /// @throws FileDownloadException 当解析失败时
  public static OpenAlexManifest parseManifest(InputStream inputStream) {
    try {
      ManifestDto dto = OBJECT_MAPPER.readValue(inputStream, ManifestDto.class);
      OpenAlexManifest manifest = toOpenAlexManifest(dto);
      log.info(
          "解析 manifest 完成: {} 个分区文件，共 {} 条记录",
          manifest.entries().size(),
          manifest.totalRecordCount());
      return manifest;
    } catch (IOException e) {
      throw new FileDownloadException(
          "解析 manifest 输入流失败: " + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  /// 将 DTO 转换为领域对象。
  private static OpenAlexManifest toOpenAlexManifest(ManifestDto dto) {
    List<OpenAlexManifest.Entry> entries =
        dto.entries().stream()
            .map(
                e ->
                    new OpenAlexManifest.Entry(
                        e.url(),
                        e.meta() != null ? e.meta().contentLength() : 0L,
                        e.meta() != null ? e.meta().recordCount() : 0))
            .toList();

    return new OpenAlexManifest(
        entries,
        dto.meta() != null ? dto.meta().contentLength() : 0L,
        dto.meta() != null ? dto.meta().recordCount() : 0);
  }

  /// Manifest JSON 结构映射 DTO。
  private record ManifestDto(List<EntryDto> entries, MetaDto meta) {}

  /// 条目 DTO。
  private record EntryDto(String url, MetaDto meta) {}

  /// 元数据 DTO。
  private record MetaDto(Long contentLength, int recordCount) {}
}
