package dev.patra.objectstorage.infra.file.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.objectstorage.domain.file.model.aggregate.FileMetadata;
import dev.patra.objectstorage.infra.file.persistence.entity.FileMetadataEntity;
import dev.patra.tool.jpa.json.JsonColumnCodec;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// {@link FileMetadataJpaMapper} 关联数据 JSON 转换测试。
///
/// 用最小 Spring 上下文装配映射器与 {@link JsonColumnCodec}，验证 Map 与 JSON 节点往返时
/// `Long` 保持数字、空值得到空对象。
@SpringJUnitConfig(FileMetadataJpaMapperTest.TestConfig.class)
class FileMetadataJpaMapperTest {

  @Autowired private FileMetadataJpaMapper mapper;

  /// 验证 Long 值写成 JSON 数字并在读回后保持数值。
  @Test
  void should_keep_long_as_number_when_round_trip() {
    JsonNode node = mapper.mapCorrelationData(Map.of("runId", 9007199254740993L, "name", "a"));

    assertThat(node.get("runId").isNumber()).isTrue();
    assertThat(node.get("name").asString()).isEqualTo("a");
    assertThat(mapper.mapCorrelationData(Map.of("runId", 42L)).get("runId").asLong())
        .isEqualTo(42L);
  }

  /// 验证实体里的关联数据 JSON 读回领域 Map，数字仍是数字。
  @Test
  void should_restore_correlation_map_from_entity_json() {
    FileMetadataEntity entity =
        FileMetadataEntity.builder()
            .bucketName("bucket")
            .objectKey("a/b.txt")
            .fileSize(1L)
            .md5Hash("abc")
            .serviceName("svc")
            .businessType("type")
            .businessId("1")
            .correlationData(mapper.mapCorrelationData(Map.of("runId", 7L)))
            .providerType("MINIO")
            .fileStatus("ACTIVE")
            .uploadedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    FileMetadata aggregate = mapper.toAggregate(entity);

    assertThat(aggregate.getContext().correlationData()).containsEntry("runId", 7L);
  }

  /// 验证 null 与空 Map 都得到空对象而不是 null。
  @Test
  void should_return_empty_object_when_source_is_null_or_empty() {
    assertThat(mapper.mapCorrelationData(null).isObject()).isTrue();
    assertThat(mapper.mapCorrelationData(null).isEmpty()).isTrue();
    assertThat(mapper.mapCorrelationData(Map.of()).isObject()).isTrue();
    assertThat(mapper.mapCorrelationData(Map.of()).isEmpty()).isTrue();
  }

  /// 最小测试上下文：容器 JsonMapper、codec 与扫描到的 MapStruct 实现。
  @Configuration
  @ComponentScan(basePackageClasses = FileMetadataJpaMapper.class)
  static class TestConfig {

    /// 提供 JSON 列转换器。
    @Bean
    JsonColumnCodec codec() {
      return new JsonColumnCodec(JsonMapper.builder().build());
    }
  }
}
