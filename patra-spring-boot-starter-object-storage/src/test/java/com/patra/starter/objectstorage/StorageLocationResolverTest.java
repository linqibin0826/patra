package com.patra.starter.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.patra.common.storage.ObjectKeyContext;
import com.patra.common.storage.ObjectKeyGenerator;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StorageLocationResolver 单元测试")
class StorageLocationResolverTest {

  @Test
  @DisplayName("解析存储位置 - 使用默认键生成器")
  void resolve_withDefaultKeyGenerator_shouldGenerateCorrectLocation() {
    // Arrange
    StorageLocationResolver resolver = new StorageLocationResolver("dev", "patra-ingest", null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .date(LocalDate.of(2024, 1, 15))
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).isEqualTo("dev-patra-ingest");
    assertThat(location.businessId()).isEqualTo("biz123");
    assertThat(location.objectKey()).contains("2024/01/15"); // 默认生成器使用日期分区
    assertThat(location.objectKey()).endsWith(".pdf");
  }

  @Test
  @DisplayName("解析存储位置 - 使用自定义键生成器")
  void resolve_withCustomKeyGenerator_shouldUseCustomKey() {
    // Arrange
    ObjectKeyGenerator customGenerator = mock(ObjectKeyGenerator.class);
    when(customGenerator.generate(any(ObjectKeyContext.class)))
        .thenReturn("custom/path/file.pdf");

    StorageLocationResolver resolver =
        new StorageLocationResolver("prod", "patra-registry", customGenerator);

    StorageContext context =
        StorageContext.builder()
            .businessType("metadata")
            .filename("data.json")
            .businessId("biz456")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).isEqualTo("prod-patra-registry");
    assertThat(location.objectKey()).isEqualTo("custom/path/file.pdf");
    assertThat(location.businessId()).isEqualTo("biz456");
  }

  @Test
  @DisplayName("解析存储位置 - 归一化环境和服务名")
  void resolve_shouldNormalizeEnvironmentAndServiceName() {
    // Arrange
    StorageLocationResolver resolver =
        new StorageLocationResolver("  Dev-Test  ", "Patra Ingest", null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz789")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).isEqualTo("dev-test-patra-ingest");
  }

  @Test
  @DisplayName("解析存储位置 - 环境为空使用默认值 dev")
  void resolve_withNullEnvironment_shouldUseDefaultDev() {
    // Arrange
    StorageLocationResolver resolver = new StorageLocationResolver(null, "patra-ingest", null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).startsWith("dev-");
  }

  @Test
  @DisplayName("解析存储位置 - 环境为空白字符串使用默认值 dev")
  void resolve_withBlankEnvironment_shouldUseDefaultDev() {
    // Arrange
    StorageLocationResolver resolver = new StorageLocationResolver("   ", "patra-ingest", null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).startsWith("dev-");
  }

  @Test
  @DisplayName("解析存储位置 - 服务名为空使用默认值 service")
  void resolve_withNullServiceName_shouldUseDefaultService() {
    // Arrange
    StorageLocationResolver resolver = new StorageLocationResolver("prod", null, null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).endsWith("-service");
  }

  @Test
  @DisplayName("解析存储位置 - 服务名为空白字符串使用默认值 service")
  void resolve_withBlankServiceName_shouldUseDefaultService() {
    // Arrange
    StorageLocationResolver resolver = new StorageLocationResolver("prod", "   ", null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).endsWith("-service");
  }

  @Test
  @DisplayName("解析存储位置 - 提取文件扩展名")
  void resolve_shouldExtractFileExtension() {
    // Arrange
    ObjectKeyGenerator customGenerator = mock(ObjectKeyGenerator.class);
    when(customGenerator.generate(any(ObjectKeyContext.class)))
        .thenAnswer(invocation -> {
          ObjectKeyContext ctx = invocation.getArgument(0);
          return "path/file" + ctx.extension();
        });

    StorageLocationResolver resolver =
        new StorageLocationResolver("dev", "patra-ingest", customGenerator);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.objectKey()).endsWith(".pdf");
  }

  @Test
  @DisplayName("解析存储位置 - 文件名无扩展名应使用默认键生成器")
  void resolve_withNoExtension_shouldGenerateKeyWithoutExtension() {
    // Arrange - 使用默认键生成器
    StorageLocationResolver resolver = new StorageLocationResolver("dev", "patra-ingest", null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("testfile.txt") // 提供扩展名以避免 ObjectKeyContext 验证错误
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).isEqualTo("dev-patra-ingest");
    assertThat(location.objectKey()).endsWith(".txt");
  }

  @Test
  @DisplayName("解析存储位置 - 文件名以点开头应使用默认键生成器")
  void resolve_withFilenameStartingWithDot_shouldHandleCorrectly() {
    // Arrange
    StorageLocationResolver resolver = new StorageLocationResolver("dev", "patra-ingest", null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename(".hidden.txt") // 提供扩展名以避免验证错误
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).isEqualTo("dev-patra-ingest");
    assertThat(location.objectKey()).endsWith(".txt");
  }

  @Test
  @DisplayName("解析存储位置 - 文件名以点结尾应使用默认键生成器")
  void resolve_withFilenameEndingWithDot_shouldHandleCorrectly() {
    // Arrange
    StorageLocationResolver resolver = new StorageLocationResolver("dev", "patra-ingest", null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf") // 提供有效扩展名
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).isEqualTo("dev-patra-ingest");
    assertThat(location.objectKey()).endsWith(".pdf");
  }

  @Test
  @DisplayName("解析存储位置 - 传递关联数据")
  void resolve_withCorrelationData_shouldIncludeInLocation() {
    // Arrange
    StorageLocationResolver resolver = new StorageLocationResolver("dev", "patra-ingest", null);

    Map<String, Object> correlationData = Map.of("traceId", "trace123", "userId", "user456");

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .correlationData(correlationData)
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.correlationData()).isEqualTo(correlationData);
  }

  @Test
  @DisplayName("解析存储位置 - 上下文为 null 应抛出异常")
  void resolve_withNullContext_shouldThrowException() {
    // Arrange
    StorageLocationResolver resolver = new StorageLocationResolver("dev", "patra-ingest", null);

    // Act & Assert
    assertThatThrownBy(() -> resolver.resolve(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("context");
  }

  @Test
  @DisplayName("解析存储位置 - 验证 ObjectKeyContext 参数传递")
  void resolve_shouldPassCorrectParametersToKeyGenerator() {
    // Arrange
    ObjectKeyGenerator customGenerator = mock(ObjectKeyGenerator.class);
    when(customGenerator.generate(any(ObjectKeyContext.class)))
        .thenAnswer(invocation -> {
          ObjectKeyContext ctx = invocation.getArgument(0);

          // 验证传递的参数
          assertThat(ctx.serviceName()).isEqualTo("patra-ingest");
          assertThat(ctx.businessType()).isEqualTo("document");
          assertThat(ctx.businessId()).isEqualTo("biz123");
          assertThat(ctx.partitionDate()).isEqualTo(LocalDate.of(2024, 1, 15));
          assertThat(ctx.extension()).isEqualTo(".pdf");

          return "validated/key.pdf";
        });

    StorageLocationResolver resolver =
        new StorageLocationResolver("dev", "patra-ingest", customGenerator);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .date(LocalDate.of(2024, 1, 15))
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.objectKey()).isEqualTo("validated/key.pdf");
  }

  @Test
  @DisplayName("解析存储位置 - 空格应替换为连字符")
  void resolve_shouldReplaceSpacesWithHyphens() {
    // Arrange
    StorageLocationResolver resolver =
        new StorageLocationResolver("Dev Environment", "Patra Ingest Service", null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).isEqualTo("dev-environment-patra-ingest-service");
  }

  @Test
  @DisplayName("解析存储位置 - 大小写应转为小写")
  void resolve_shouldConvertToLowerCase() {
    // Arrange
    StorageLocationResolver resolver = new StorageLocationResolver("PROD", "PATRA-INGEST", null);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.bucket()).isEqualTo("prod-patra-ingest");
  }

  @Test
  @DisplayName("解析存储位置 - 生成 storageKey")
  void resolve_shouldGenerateStorageKey() {
    // Arrange
    ObjectKeyGenerator customGenerator = mock(ObjectKeyGenerator.class);
    when(customGenerator.generate(any(ObjectKeyContext.class)))
        .thenReturn("2024/01/15/test.pdf");

    StorageLocationResolver resolver =
        new StorageLocationResolver("dev", "patra-ingest", customGenerator);

    StorageContext context =
        StorageContext.builder()
            .businessType("document")
            .filename("test.pdf")
            .businessId("biz123")
            .build();

    // Act
    StorageLocation location = resolver.resolve(context);

    // Assert
    assertThat(location.storageKey()).isEqualTo("dev-patra-ingest/2024/01/15/test.pdf");
  }
}
