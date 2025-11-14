package com.patra.storage.domain.model.aggregate;

import static org.assertj.core.api.Assertions.*;

import com.patra.storage.domain.model.enums.FileStatus;
import com.patra.storage.domain.model.enums.StorageProvider;
import com.patra.storage.domain.model.vo.BusinessContext;
import com.patra.storage.domain.model.vo.FileChecksum;
import com.patra.storage.domain.model.vo.FileSize;
import com.patra.storage.domain.model.vo.StorageKey;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * FileMetadata 聚合根单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>使用 TestDataBuilder 模式构建测试数据
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 * </ul>
 *
 * <p>测试范围：
 *
 * <ul>
 *   <li>✅ 工厂方法（创建和恢复）
 *   <li>✅ 必填字段验证
 *   <li>✅ 状态转换逻辑（ACTIVE → DELETED）
 *   <li>✅ 业务规则（软删除、过期检查、审计更新）
 *   <li>✅ 不变性保证
 *   <li>✅ 边界条件处理
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("FileMetadata 单元测试")
class FileMetadataTest {

  // ========== 工厂方法测试 ==========

  @Nested
  @DisplayName("create() 工厂方法")
  class CreateFactoryMethodTests {

    @Test
    @DisplayName("应该成功创建新文件元数据并初始化为 ACTIVE 状态")
    void shouldCreateNewFileMetadataWithActiveStatus() {
      // Given
      StorageKey storageKey = new StorageKey("literature-files", "2024/01/article.pdf");
      FileSize fileSize = new FileSize(1024 * 1024); // 1 MB
      FileChecksum checksum = new FileChecksum("d41d8cd98f00b204e9800998ecf8427e", null);
      BusinessContext context =
          new BusinessContext("patra-ingest", "literature_batch", "batch-001", null);
      StorageProvider provider = StorageProvider.MINIO;

      // When
      FileMetadata metadata =
          FileMetadata.create(storageKey, fileSize, checksum, context, provider);

      // Then
      assertThat(metadata).isNotNull();
      assertThat(metadata.getId()).isNull(); // 新创建的聚合根 ID 为 null
      assertThat(metadata.getStorageKey()).isEqualTo(storageKey);
      assertThat(metadata.getFileSize()).isEqualTo(fileSize);
      assertThat(metadata.getChecksum()).isEqualTo(checksum);
      assertThat(metadata.getContext()).isEqualTo(context);
      assertThat(metadata.getProvider()).isEqualTo(provider);
      assertThat(metadata.getStatus()).isEqualTo(FileStatus.ACTIVE);
      assertThat(metadata.getUploadedAt()).isNotNull();
      assertThat(metadata.getVersion()).isEqualTo(0L);
      assertThat(metadata.getDeleted()).isFalse();
      assertThat(metadata.getCreatedAt()).isNotNull();
      assertThat(metadata.getUpdatedAt()).isNotNull();
      assertThat(metadata.getCreatedAt()).isEqualTo(metadata.getUploadedAt());
      assertThat(metadata.getUpdatedAt()).isEqualTo(metadata.getUploadedAt());
    }

    @Test
    @DisplayName("应该抛出异常当 storageKey 为 null")
    void shouldThrowExceptionWhenStorageKeyIsNull() {
      // Given
      StorageKey storageKey = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  FileMetadata.create(
                      storageKey,
                      new FileSize(1024),
                      new FileChecksum("hash", null),
                      new BusinessContext("service", "type", "id", null),
                      StorageProvider.MINIO))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("storageKey 不能为 null");
    }

    @Test
    @DisplayName("应该抛出异常当 fileSize 为 null")
    void shouldThrowExceptionWhenFileSizeIsNull() {
      // Given
      FileSize fileSize = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  FileMetadata.create(
                      new StorageKey("bucket", "key"),
                      fileSize,
                      new FileChecksum("hash", null),
                      new BusinessContext("service", "type", "id", null),
                      StorageProvider.MINIO))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fileSize 不能为 null");
    }

    @Test
    @DisplayName("应该抛出异常当 checksum 为 null")
    void shouldThrowExceptionWhenChecksumIsNull() {
      // Given
      FileChecksum checksum = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  FileMetadata.create(
                      new StorageKey("bucket", "key"),
                      new FileSize(1024),
                      checksum,
                      new BusinessContext("service", "type", "id", null),
                      StorageProvider.MINIO))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("checksum 不能为 null");
    }

    @Test
    @DisplayName("应该抛出异常当 context 为 null")
    void shouldThrowExceptionWhenContextIsNull() {
      // Given
      BusinessContext context = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  FileMetadata.create(
                      new StorageKey("bucket", "key"),
                      new FileSize(1024),
                      new FileChecksum("hash", null),
                      context,
                      StorageProvider.MINIO))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("context 不能为 null");
    }

    @Test
    @DisplayName("应该抛出异常当 provider 为 null")
    void shouldThrowExceptionWhenProviderIsNull() {
      // Given
      StorageProvider provider = null;

      // When & Then
      assertThatThrownBy(
              () ->
                  FileMetadata.create(
                      new StorageKey("bucket", "key"),
                      new FileSize(1024),
                      new FileChecksum("hash", null),
                      new BusinessContext("service", "type", "id", null),
                      provider))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("provider 不能为 null");
    }
  }

  @Nested
  @DisplayName("restore() 工厂方法")
  class RestoreFactoryMethodTests {

    @Test
    @DisplayName("应该从持久化状态成功重建文件元数据")
    void shouldRestoreFileMetadataFromPersistentState() {
      // Given
      Long id = 100L;
      StorageKey storageKey = new StorageKey("literature-files", "2024/01/article.pdf");
      FileSize fileSize = new FileSize(2 * 1024 * 1024); // 2 MB
      String contentType = "application/pdf";
      FileChecksum checksum =
          new FileChecksum(
              "d41d8cd98f00b204e9800998ecf8427e",
              "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
      BusinessContext context =
          new BusinessContext(
              "patra-ingest",
              "literature_batch",
              "batch-001",
              Map.of("source", "pubmed", "year", 2024));
      StorageProvider provider = StorageProvider.S3;
      FileStatus status = FileStatus.ACTIVE;
      Instant uploadedAt = Instant.parse("2024-01-01T10:00:00Z");
      Instant expiresAt = Instant.parse("2025-01-01T10:00:00Z");
      Instant deletedAt = null;
      String recordRemarks = "{\"note\":\"test file\"}";
      Long version = 5L;
      byte[] ipAddress = new byte[] {(byte) 192, (byte) 168, 1, 100}; // 192.168.1.100
      Instant createdAt = Instant.parse("2024-01-01T09:55:00Z");
      Long createdBy = 1001L;
      String createdByName = "张三";
      Instant updatedAt = Instant.parse("2024-01-01T10:05:00Z");
      Long updatedBy = 1002L;
      String updatedByName = "李四";
      Boolean deleted = Boolean.FALSE;

      // When
      FileMetadata metadata =
          FileMetadata.restore(
              id,
              storageKey,
              fileSize,
              contentType,
              checksum,
              context,
              provider,
              status,
              uploadedAt,
              expiresAt,
              deletedAt,
              recordRemarks,
              version,
              ipAddress,
              createdAt,
              createdBy,
              createdByName,
              updatedAt,
              updatedBy,
              updatedByName,
              deleted);

      // Then
      assertThat(metadata).isNotNull();
      assertThat(metadata.getId()).isEqualTo(id);
      assertThat(metadata.getStorageKey()).isEqualTo(storageKey);
      assertThat(metadata.getFileSize()).isEqualTo(fileSize);
      assertThat(metadata.getContentType()).isEqualTo(contentType);
      assertThat(metadata.getChecksum()).isEqualTo(checksum);
      assertThat(metadata.getContext()).isEqualTo(context);
      assertThat(metadata.getProvider()).isEqualTo(provider);
      assertThat(metadata.getStatus()).isEqualTo(status);
      assertThat(metadata.getUploadedAt()).isEqualTo(uploadedAt);
      assertThat(metadata.getExpiresAt()).isEqualTo(expiresAt);
      assertThat(metadata.getDeletedAt()).isNull();
      assertThat(metadata.getRecordRemarks()).isEqualTo(recordRemarks);
      assertThat(metadata.getVersion()).isEqualTo(version);
      assertThat(metadata.getIpAddress()).isEqualTo(ipAddress);
      assertThat(metadata.getCreatedAt()).isEqualTo(createdAt);
      assertThat(metadata.getCreatedBy()).isEqualTo(createdBy);
      assertThat(metadata.getCreatedByName()).isEqualTo(createdByName);
      assertThat(metadata.getUpdatedAt()).isEqualTo(updatedAt);
      assertThat(metadata.getUpdatedBy()).isEqualTo(updatedBy);
      assertThat(metadata.getUpdatedByName()).isEqualTo(updatedByName);
      assertThat(metadata.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("应该正确处理 null 的可选字段")
    void shouldHandleNullOptionalFields() {
      // Given - 所有可选字段为 null
      String contentType = null;
      Instant expiresAt = null;
      Instant deletedAt = null;
      String recordRemarks = null;
      byte[] ipAddress = null;
      Long createdBy = null;
      String createdByName = null;
      Long updatedBy = null;
      String updatedByName = null;

      // When
      FileMetadata metadata =
          FileMetadata.restore(
              100L,
              new StorageKey("bucket", "key"),
              new FileSize(1024),
              contentType,
              new FileChecksum("hash", null),
              new BusinessContext("service", "type", "id", null),
              StorageProvider.MINIO,
              FileStatus.ACTIVE,
              Instant.now(),
              expiresAt,
              deletedAt,
              recordRemarks,
              0L,
              ipAddress,
              Instant.now(),
              createdBy,
              createdByName,
              Instant.now(),
              updatedBy,
              updatedByName,
              Boolean.FALSE);

      // Then - 应该成功恢复
      assertThat(metadata).isNotNull();
      assertThat(metadata.getContentType()).isNull();
      assertThat(metadata.getExpiresAt()).isNull();
      assertThat(metadata.getDeletedAt()).isNull();
      assertThat(metadata.getRecordRemarks()).isNull();
      assertThat(metadata.getIpAddress()).isNull();
      assertThat(metadata.getCreatedBy()).isNull();
      assertThat(metadata.getCreatedByName()).isNull();
      assertThat(metadata.getUpdatedBy()).isNull();
      assertThat(metadata.getUpdatedByName()).isNull();
    }
  }

  // ========== 聚合根标识分配测试 ==========

  @Nested
  @DisplayName("聚合根标识分配")
  class AggregateIdAssignmentTests {

    @Test
    @DisplayName("应该成功为新聚合根分配 ID")
    void shouldAssignIdToNewAggregate() {
      // Given - 新创建的聚合根
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();
      assertThat(metadata.getId()).isNull();

      // When - 分配 ID
      Long assignedId = 100L;
      metadata.assignId(assignedId);

      // Then
      assertThat(metadata.getId()).isEqualTo(assignedId);
    }

    @Test
    @DisplayName("应该抛出异常当分配 null ID")
    void shouldThrowExceptionWhenAssigningNullId() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();

      // When & Then
      assertThatThrownBy(() -> metadata.assignId(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("id 不能为 null");
    }

    @Test
    @DisplayName("应该抛出异常当聚合根已有 ID")
    void shouldThrowExceptionWhenAggregateAlreadyHasId() {
      // Given - 已持久化的聚合根
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().id(100L).buildRestored();
      assertThat(metadata.getId()).isNotNull();

      // When & Then
      assertThatThrownBy(() -> metadata.assignId(200L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("聚合根已经有ID");
    }
  }

  // ========== 版本管理测试 ==========

  @Nested
  @DisplayName("乐观锁版本管理")
  class VersionManagementTests {

    @Test
    @DisplayName("应该成功更新版本号")
    void shouldUpdateVersion() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().version(0L).build();
      assertThat(metadata.getVersion()).isEqualTo(0L);

      // When
      metadata.updateVersion(1L);

      // Then
      assertThat(metadata.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该抛出异常当更新版本为 null")
    void shouldThrowExceptionWhenUpdatingVersionToNull() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();

      // When & Then
      assertThatThrownBy(() -> metadata.updateVersion(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("version 不能为 null");
    }
  }

  // ========== 可选字段配置测试 ==========

  @Nested
  @DisplayName("可选字段配置")
  class OptionalFieldConfigurationTests {

    @Test
    @DisplayName("应该成功配置 ContentType")
    void shouldConfigureContentType() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();
      assertThat(metadata.getContentType()).isNull();

      // When
      String contentType = "application/pdf";
      metadata.withContentType(contentType);

      // Then
      assertThat(metadata.getContentType()).isEqualTo(contentType);
    }

    @Test
    @DisplayName("应该成功配置过期时间")
    void shouldConfigureExpiresAt() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();
      assertThat(metadata.getExpiresAt()).isNull();

      // When
      Instant expiresAt = Instant.now().plusSeconds(86400); // 1 天后
      metadata.withExpiresAt(expiresAt);

      // Then
      assertThat(metadata.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("应该成功配置审计备注")
    void shouldConfigureRecordRemarks() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();
      assertThat(metadata.getRecordRemarks()).isNull();

      // When
      String remarks = "{\"note\":\"重要文件\"}";
      metadata.withRecordRemarks(remarks);

      // Then
      assertThat(metadata.getRecordRemarks()).isEqualTo(remarks);
    }

    @Test
    @DisplayName("应该成功配置 IP 地址")
    void shouldConfigureIpAddress() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();
      assertThat(metadata.getIpAddress()).isNull();

      // When
      byte[] ipAddress = new byte[] {(byte) 192, (byte) 168, 1, 100};
      metadata.withIpAddress(ipAddress);

      // Then
      assertThat(metadata.getIpAddress()).isEqualTo(ipAddress);
    }

    @Test
    @DisplayName("应该返回 IP 地址的防御性副本")
    void shouldReturnDefensiveCopyOfIpAddress() {
      // Given
      byte[] originalIp = new byte[] {(byte) 192, (byte) 168, 1, 100};
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();
      metadata.withIpAddress(originalIp);

      // When - 修改返回的副本
      byte[] returnedIp = metadata.getIpAddress();
      returnedIp[0] = (byte) 10; // 尝试修改

      // Then - 原始值应该不受影响
      assertThat(metadata.getIpAddress()[0]).isEqualTo((byte) 192);
    }

    @Test
    @DisplayName("应该支持链式调用配置可选字段")
    void shouldSupportMethodChainingForOptionalFields() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();

      // When - 链式调用
      metadata
          .withContentType("application/pdf")
          .withExpiresAt(Instant.now().plusSeconds(86400))
          .withRecordRemarks("{\"note\":\"test\"}")
          .withIpAddress(new byte[] {127, 0, 0, 1});

      // Then
      assertThat(metadata.getContentType()).isEqualTo("application/pdf");
      assertThat(metadata.getExpiresAt()).isNotNull();
      assertThat(metadata.getRecordRemarks()).isEqualTo("{\"note\":\"test\"}");
      assertThat(metadata.getIpAddress()).isNotNull();
    }
  }

  // ========== 状态转换测试 ==========

  @Nested
  @DisplayName("状态转换")
  class StatusTransitionTests {

    @Test
    @DisplayName("应该成功将文件标记为已删除")
    void shouldMarkFileAsDeleted() {
      // Given - ACTIVE 状态的文件
      FileMetadata metadata =
          FileMetadataTestDataBuilder.anActiveFile().status(FileStatus.ACTIVE).buildRestored();
      assertThat(metadata.getStatus()).isEqualTo(FileStatus.ACTIVE);
      assertThat(metadata.getDeleted()).isFalse();
      assertThat(metadata.getDeletedAt()).isNull();

      // When - 标记为已删除
      Long operatorId = 1001L;
      String operatorName = "张三";
      metadata.markAsDeleted(operatorId, operatorName);

      // Then
      assertThat(metadata.getStatus()).isEqualTo(FileStatus.DELETED);
      assertThat(metadata.getDeleted()).isTrue();
      assertThat(metadata.getDeletedAt()).isNotNull();
      assertThat(metadata.getUpdatedBy()).isEqualTo(operatorId);
      assertThat(metadata.getUpdatedByName()).isEqualTo(operatorName);
      assertThat(metadata.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("应该抛出异常当文件已被删除")
    void shouldThrowExceptionWhenFileAlreadyDeleted() {
      // Given - 已删除的文件
      FileMetadata metadata =
          FileMetadataTestDataBuilder.aDeletedFile().status(FileStatus.DELETED).buildRestored();

      // When & Then
      assertThatThrownBy(() -> metadata.markAsDeleted(1001L, "张三"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("文件已被删除");
    }
  }

  // ========== 业务规则测试 ==========

  @Nested
  @DisplayName("业务规则")
  class BusinessRuleTests {

    @Test
    @DisplayName("应该正确判断文件是否过期")
    void shouldCorrectlyDetermineIfFileIsExpired() {
      // Given - 过期时间设置为过去
      Instant pastTime = Instant.now().minusSeconds(3600); // 1 小时前
      FileMetadata metadata =
          FileMetadataTestDataBuilder.anActiveFile().expiresAt(pastTime).buildRestored();

      // When & Then
      assertThat(metadata.isExpired()).isTrue();
    }

    @Test
    @DisplayName("应该判断文件未过期当过期时间在未来")
    void shouldDetermineFileIsNotExpiredWhenExpiresAtIsInFuture() {
      // Given - 过期时间设置为未来
      Instant futureTime = Instant.now().plusSeconds(3600); // 1 小时后
      FileMetadata metadata =
          FileMetadataTestDataBuilder.anActiveFile().expiresAt(futureTime).buildRestored();

      // When & Then
      assertThat(metadata.isExpired()).isFalse();
    }

    @Test
    @DisplayName("应该判断文件未过期当未设置过期时间")
    void shouldDetermineFileIsNotExpiredWhenExpiresAtIsNull() {
      // Given - 未设置过期时间
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();
      assertThat(metadata.getExpiresAt()).isNull();

      // When & Then
      assertThat(metadata.isExpired()).isFalse();
    }

    @Test
    @DisplayName("应该正确更新审计元数据")
    void shouldCorrectlyUpdateAuditMetadata() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().buildRestored();
      Instant beforeUpdate = metadata.getUpdatedAt();

      // When - 更新审计信息
      Long operatorId = 2001L;
      String operatorName = "李四";
      metadata.touchAudit(operatorId, operatorName);

      // Then
      assertThat(metadata.getUpdatedBy()).isEqualTo(operatorId);
      assertThat(metadata.getUpdatedByName()).isEqualTo(operatorName);
      assertThat(metadata.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @Test
    @DisplayName("应该允许 null 操作员信息更新审计元数据")
    void shouldAllowNullOperatorInfoWhenUpdatingAudit() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().buildRestored();

      // When
      metadata.touchAudit(null, null);

      // Then - 应该成功更新，但操作员信息为 null
      assertThat(metadata.getUpdatedBy()).isNull();
      assertThat(metadata.getUpdatedByName()).isNull();
      assertThat(metadata.getUpdatedAt()).isNotNull();
    }
  }

  // ========== 不变性测试 ==========

  @Nested
  @DisplayName("不变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("应该保证核心字段在生命周期中保持不可变")
    void shouldEnsureCoreFieldsRemainImmutableThroughLifecycle() {
      // Given - 新创建的文件元数据
      StorageKey originalStorageKey = new StorageKey("bucket", "key");
      FileSize originalFileSize = new FileSize(1024);
      FileChecksum originalChecksum = new FileChecksum("hash123", null);
      BusinessContext originalContext =
          new BusinessContext("service", "type", "id", Map.of("key", "value"));
      StorageProvider originalProvider = StorageProvider.MINIO;

      FileMetadata metadata =
          FileMetadataTestDataBuilder.anActiveFile()
              .storageKey(originalStorageKey)
              .fileSize(originalFileSize)
              .checksum(originalChecksum)
              .context(originalContext)
              .provider(originalProvider)
              .build();

      // When - 执行各种操作
      metadata.withContentType("application/pdf");
      metadata.withExpiresAt(Instant.now().plusSeconds(86400));
      metadata.withRecordRemarks("{\"note\":\"test\"}");
      metadata.touchAudit(1001L, "张三");

      // Then - 核心字段应该保持不变
      assertThat(metadata.getStorageKey()).isEqualTo(originalStorageKey);
      assertThat(metadata.getFileSize()).isEqualTo(originalFileSize);
      assertThat(metadata.getChecksum()).isEqualTo(originalChecksum);
      assertThat(metadata.getContext()).isEqualTo(originalContext);
      assertThat(metadata.getProvider()).isEqualTo(originalProvider);
    }

    @Test
    @DisplayName("应该保证上传时间在创建后不可变")
    void shouldEnsureUploadedAtRemainsImmutableAfterCreation() {
      // Given
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();
      Instant originalUploadedAt = metadata.getUploadedAt();

      // When - 执行各种操作
      metadata.withContentType("application/pdf");
      metadata.touchAudit(1001L, "张三");

      // Then - 上传时间应该保持不变
      assertThat(metadata.getUploadedAt()).isEqualTo(originalUploadedAt);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理空文件")
    void shouldHandleEmptyFile() {
      // Given - 0 字节文件
      FileSize zeroSize = new FileSize(0L);

      // When
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().fileSize(zeroSize).build();

      // Then
      assertThat(metadata.getFileSize().bytes()).isEqualTo(0L);
      assertThat(metadata.getFileSize().humanReadable()).isEqualTo("0 B");
    }

    @Test
    @DisplayName("应该处理超大文件")
    void shouldHandleVeryLargeFile() {
      // Given - 10 GB 文件
      FileSize largeSize = new FileSize(10L * 1024 * 1024 * 1024);

      // When
      FileMetadata metadata =
          FileMetadataTestDataBuilder.anActiveFile().fileSize(largeSize).build();

      // Then
      assertThat(metadata.getFileSize().bytes()).isEqualTo(10L * 1024 * 1024 * 1024);
      assertThat(metadata.getFileSize().humanReadable()).contains("GB");
    }

    @Test
    @DisplayName("应该处理特殊字符的对象键")
    void shouldHandleSpecialCharactersInObjectKey() {
      // Given - 包含特殊字符的对象键
      StorageKey specialKey = new StorageKey("bucket", "文献/2024/文章 (副本).pdf");

      // When
      FileMetadata metadata =
          FileMetadataTestDataBuilder.anActiveFile().storageKey(specialKey).build();

      // Then
      assertThat(metadata.getStorageKey().objectKey()).contains("文献");
      assertThat(metadata.getStorageKey().objectKey()).contains("(副本)");
    }

    @Test
    @DisplayName("应该处理极长的 JSON 备注")
    void shouldHandleVeryLongRecordRemarks() {
      // Given - 极长的 JSON 字符串
      String longRemarks = "{\"note\":\"" + "x".repeat(10000) + "\"}";

      // When
      FileMetadata metadata =
          FileMetadataTestDataBuilder.anActiveFile().recordRemarks(longRemarks).build();

      // Then
      assertThat(metadata.getRecordRemarks()).hasSize(longRemarks.length());
    }

    @Test
    @DisplayName("应该处理极端的过期时间边界")
    void shouldHandleExtremeExpirationTimeBoundaries() {
      // Given - 极远未来的过期时间
      Instant farFuture = Instant.parse("2099-12-31T23:59:59Z");

      // When
      FileMetadata metadata =
          FileMetadataTestDataBuilder.anActiveFile().expiresAt(farFuture).buildRestored();

      // Then
      assertThat(metadata.getExpiresAt()).isEqualTo(farFuture);
      assertThat(metadata.isExpired()).isFalse();
    }
  }

  // ========== 值对象集成测试 ==========

  @Nested
  @DisplayName("值对象集成测试")
  class ValueObjectIntegrationTests {

    @Test
    @DisplayName("应该正确使用 StorageKey 的 fullKey() 方法")
    void shouldCorrectlyUseStorageKeyFullKeyMethod() {
      // Given
      StorageKey storageKey = new StorageKey("literature-files", "2024/01/article.pdf");
      FileMetadata metadata =
          FileMetadataTestDataBuilder.anActiveFile().storageKey(storageKey).build();

      // When
      String fullKey = metadata.getStorageKey().fullKey();

      // Then
      assertThat(fullKey).isEqualTo("literature-files/2024/01/article.pdf");
    }

    @Test
    @DisplayName("应该正确使用 FileSize 的 humanReadable() 方法")
    void shouldCorrectlyUseFileSizeHumanReadableMethod() {
      // Given
      FileSize fileSize = new FileSize(2 * 1024 * 1024); // 2 MB
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().fileSize(fileSize).build();

      // When
      String readable = metadata.getFileSize().humanReadable();

      // Then
      assertThat(readable).isEqualTo("2.00 MB");
    }

    @Test
    @DisplayName("应该正确使用 FileChecksum 的哈希值")
    void shouldCorrectlyUseFileChecksumHashValues() {
      // Given - MD5 和 SHA-256 都提供
      FileChecksum checksum =
          new FileChecksum(
              "D41D8CD98F00B204E9800998ECF8427E",
              "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855");
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().checksum(checksum).build();

      // When & Then - 哈希值应该被标准化为小写
      assertThat(metadata.getChecksum().md5Hash()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
      assertThat(metadata.getChecksum().sha256Hash())
          .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("应该正确使用 BusinessContext 的关联数据")
    void shouldCorrectlyUseBusinessContextCorrelationData() {
      // Given
      Map<String, Object> correlationData =
          Map.of("source", "pubmed", "year", 2024, "importBatchId", "batch-001");
      BusinessContext context =
          new BusinessContext("patra-ingest", "literature_batch", "id-001", correlationData);
      FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().context(context).build();

      // When & Then
      assertThat(metadata.getContext().correlationData()).containsEntry("source", "pubmed");
      assertThat(metadata.getContext().correlationData()).containsEntry("year", 2024);
      assertThat(metadata.getContext().correlationData())
          .containsEntry("importBatchId", "batch-001");
    }
  }

  // ========== 完整生命周期测试 ==========

  @Nested
  @DisplayName("完整生命周期测试")
  class FullLifecycleTests {

    @Test
    @DisplayName("应该成功完成 ACTIVE → DELETED 完整流程")
    void shouldCompleteFullLifecycleFromActiveToDeleted() {
      // Given - 新上传的文件
      FileMetadata metadata =
          FileMetadataTestDataBuilder.anActiveFile()
              .storageKey(new StorageKey("bucket", "file.pdf"))
              .fileSize(new FileSize(1024 * 1024))
              .build();

      assertThat(metadata.getStatus()).isEqualTo(FileStatus.ACTIVE);
      assertThat(metadata.getId()).isNull();

      // When - 持久化后分配 ID
      metadata.assignId(100L);
      assertThat(metadata.getId()).isEqualTo(100L);

      // When - 配置可选字段
      metadata
          .withContentType("application/pdf")
          .withExpiresAt(Instant.now().plusSeconds(86400))
          .withRecordRemarks("{\"note\":\"重要文件\"}");

      // When - 更新审计信息
      metadata.touchAudit(1001L, "张三");

      // When - 标记为已删除
      metadata.markAsDeleted(1002L, "李四");

      // Then - 验证最终状态
      assertThat(metadata.getStatus()).isEqualTo(FileStatus.DELETED);
      assertThat(metadata.getDeleted()).isTrue();
      assertThat(metadata.getDeletedAt()).isNotNull();
      assertThat(metadata.getUpdatedBy()).isEqualTo(1002L);
      assertThat(metadata.getUpdatedByName()).isEqualTo("李四");

      // 核心字段应该保持不变
      assertThat(metadata.getStorageKey().bucket()).isEqualTo("bucket");
      assertThat(metadata.getFileSize().bytes()).isEqualTo(1024 * 1024);
    }
  }
}
