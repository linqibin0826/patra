package com.patra.catalog.infra.adapter.venue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.common.error.trait.StandardErrorTrait;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/// OpenAlex Manifest 解析器。
///
/// 提供 manifest JSON 解析和临时文件清理的公共方法，
/// 供 {@link VenueSourceFileAdapter} 和 {@link DefaultVenueSourceFileAdapter} 共享使用。
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

  /// 解析 manifest JSON 文件。
  ///
  /// @param manifestFile manifest 文件路径
  /// @return 解析后的 OpenAlexManifest 对象
  /// @throws FileDownloadException 当解析失败时
  public static OpenAlexManifest parseManifest(Path manifestFile) {
    try {
      ManifestDto dto = OBJECT_MAPPER.readValue(manifestFile.toFile(), ManifestDto.class);
      OpenAlexManifest manifest = toOpenAlexManifest(dto);
      log.info(
          "解析 manifest 完成: {} 个分区文件，共 {} 条记录",
          manifest.entries().size(),
          manifest.totalRecordCount());
      return manifest;
    } catch (IOException e) {
      throw new FileDownloadException(
          "解析 manifest 文件失败: " + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
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

  /// 清理临时文件。
  ///
  /// 静默处理删除失败的情况（仅记录警告日志）。
  ///
  /// @param file 要删除的临时文件路径
  public static void cleanupTempFile(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("清理临时文件失败: {}", file);
    }
  }
}
