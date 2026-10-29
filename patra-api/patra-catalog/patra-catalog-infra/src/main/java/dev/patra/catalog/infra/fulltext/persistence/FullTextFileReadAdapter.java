package dev.patra.catalog.infra.fulltext.persistence;

import dev.patra.catalog.domain.fulltext.model.read.FullTextFileRef;
import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.port.read.FullTextFileReadPort;
import dev.patra.catalog.domain.fulltext.port.storage.FullTextObjectStoragePort;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import dev.patra.catalog.domain.kernel.model.read.FullTextAvailability;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/// 门户全文文件的读端实现：PDF 信息取自全文记录，图片按 `media` 清单判断归属后再查对象存储。
///
/// @author linqibin
/// @since 0.11.0
@Component
public class FullTextFileReadAdapter implements FullTextFileReadPort {

  private static final TypeReference<List<FullTextMedia>> MEDIA_LIST = new TypeReference<>() {};

  @PersistenceContext private EntityManager em;

  private final JsonMapper jsonMapper;
  private final FullTextObjectStoragePort storage;

  /// 创建读适配器。
  ///
  /// @param jsonMapper `media` JSON 读取器
  /// @param storage 对象存储
  public FullTextFileReadAdapter(JsonMapper jsonMapper, FullTextObjectStoragePort storage) {
    this.jsonMapper = jsonMapper;
    this.storage = storage;
  }

  /// PDF。
  ///
  /// @param publicationId 文献 ID
  /// @return 文件
  @Override
  public Optional<FullTextFileRef> findPdf(long publicationId) {
    return row(publicationId)
        .filter(r -> r.availability().pdfDownloadable())
        .map(
            r ->
                FullTextFileRef.builder()
                    .objectKey(r.pdfObjectKey())
                    .fileName(r.pmcid() + ".pdf")
                    .contentType("application/pdf")
                    .size(r.pdfSize() != null ? r.pdfSize() : 0L)
                    .etag(r.pdfMd5())
                    .build());
  }

  /// 正文图片。
  ///
  /// @param publicationId 文献 ID
  /// @param fileName 文件名
  /// @return 文件
  @Override
  public Optional<FullTextFileRef> findFigure(long publicationId, String fileName) {
    return row(publicationId)
        .filter(r -> r.availability().readable())
        .filter(r -> r.media().stream().anyMatch(m -> m.name().equals(fileName)))
        .flatMap(
            r -> {
              String key = r.objectPrefix() + fileName;
              return storage
                  .stat(key)
                  .map(
                      info ->
                          FullTextFileRef.builder()
                              .objectKey(key)
                              .fileName(fileName)
                              .contentType(info.contentType())
                              .size(info.size())
                              .etag(info.etag())
                              .build());
            });
  }

  /// 读全文记录与撤稿判定。
  private Optional<FileRow> row(long publicationId) {
    List<?> rows =
        em.createNativeQuery(
                """
                SELECT f.status, f.pdf_object_key, f.pdf_size, f.pdf_md5, f.pmcid, f.object_prefix,
                       f.media::text,
                       EXISTS (SELECT 1 FROM cat_publication_type rt
                                WHERE rt.publication_id = p.id AND rt.type_id = :retractedType)
                  FROM cat_publication p
                  JOIN cat_publication_fulltext f ON f.publication_id = p.id
                 WHERE p.id = :id AND p.deleted_at IS NULL
                """)
            .setParameter("id", publicationId)
            .setParameter("retractedType", FullTextAvailability.RETRACTED_PUBLICATION_TYPE_ID)
            .getResultList();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    Object[] r = (Object[]) rows.getFirst();
    String pdfKey = (String) r[1];
    FullTextAvailability availability =
        FullTextAvailability.of(
            FullTextStatus.valueOf((String) r[0]), pdfKey != null, (Boolean) r[7]);
    List<FullTextMedia> media =
        r[6] == null ? List.of() : jsonMapper.readValue((String) r[6], MEDIA_LIST);
    return Optional.of(
        new FileRow(
            availability,
            pdfKey,
            r[2] == null ? null : ((Number) r[2]).longValue(),
            r[3] == null ? null : ((String) r[3]).strip(),
            (String) r[4],
            (String) r[5],
            media));
  }

  /// 一行全文记录。
  private record FileRow(
      FullTextAvailability availability,
      String pdfObjectKey,
      Long pdfSize,
      String pdfMd5,
      String pmcid,
      String objectPrefix,
      List<FullTextMedia> media) {}
}
