package com.patra.catalog.infra.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.enums.DescriptorClass;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;

/// MeshDescriptorItemReader 集成测试。
///
/// 测试 Spring Batch ItemStreamReader 的生命周期和断点续传功能。
///
/// **测试策略**：
///
/// - 单元测试：Mock XmlParserPort，测试 ItemReader 的行为
///   - 测试隔离：每个测试方法独立
///   - Mock 策略：Mock 端口接口
///   - 测试覆盖：open()、read()、update()、close() 生命周期
///
/// **重点测试场景**：
///
/// - open() 正常打开文件
///   - open() 从断点恢复
///   - read() 逐条读取记录
///   - read() 读取完成返回 null
///   - update() 保存进度
///   - close() 释放资源
///   - 断点续传：跳过已处理记录
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("MeshDescriptorItemReader 集成测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class MeshDescriptorItemReaderIT {

  private static final String TEST_MESH_VERSION = "2025";
  private static final String CURRENT_INDEX_KEY = "mesh.descriptor.current.index";

  @Mock private XmlParserPort xmlParserPort;

  @TempDir Path tempDir;

  private Path testXmlFile;
  private MeshDescriptorItemReader itemReader;
  private ExecutionContext executionContext;

  @BeforeEach
  void setUp() throws IOException {
    // 创建测试 XML 文件
    testXmlFile = tempDir.resolve("test-descriptors.xml");
    Files.writeString(
        testXmlFile,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <DescriptorRecordSet LanguageCode="eng">
          <DescriptorRecord DescriptorClass="1">
            <DescriptorUI>D000001</DescriptorUI>
          </DescriptorRecord>
        </DescriptorRecordSet>
        """);

    executionContext = new ExecutionContext();
  }

  /// 创建测试用的 MeshDescriptorAggregate。
  private MeshDescriptorAggregate createTestDescriptor(String ui, String name) {
    return MeshDescriptorAggregate.create(
        MeshUI.of(ui), name, DescriptorClass.TOPICAL, TEST_MESH_VERSION);
  }

  @Nested
  @DisplayName("open() 测试")
  class OpenTest {

    @Test
    @DisplayName("正常打开文件 - 应该成功初始化")
    void open_validFile_shouldInitializeSuccessfully() {
      // Given: Mock XML 解析器返回空流
      when(xmlParserPort.parseDescriptors(any(InputStream.class), eq(TEST_MESH_VERSION)))
          .thenReturn(Stream.empty());

      itemReader =
          new MeshDescriptorItemReader(xmlParserPort, testXmlFile.toString(), TEST_MESH_VERSION);

      // When: 打开 Reader
      itemReader.open(executionContext);

      // Then: 不应该抛出异常
      itemReader.close();
    }

    @Test
    @DisplayName("文件不存在 - 应该抛出 ItemStreamException")
    void open_fileNotExists_shouldThrowItemStreamException() {
      // Given: 不存在的文件路径
      String nonExistentPath = tempDir.resolve("non-existent.xml").toString();
      itemReader = new MeshDescriptorItemReader(xmlParserPort, nonExistentPath, TEST_MESH_VERSION);

      // When & Then: 应该抛出异常
      assertThatThrownBy(() -> itemReader.open(executionContext))
          .isInstanceOf(ItemStreamException.class)
          .hasMessageContaining("无法打开文件");
    }

    @Test
    @DisplayName("从断点恢复 - 应该跳过已处理记录")
    void open_withCheckpoint_shouldSkipProcessedRecords() {
      // Given: ExecutionContext 中有保存的进度
      executionContext.putInt(CURRENT_INDEX_KEY, 2);

      // Mock 返回 5 条记录的流
      Stream<MeshDescriptorAggregate> mockStream =
          Stream.of(
              createTestDescriptor("D000001", "Descriptor 1"),
              createTestDescriptor("D000002", "Descriptor 2"),
              createTestDescriptor("D000003", "Descriptor 3"),
              createTestDescriptor("D000004", "Descriptor 4"),
              createTestDescriptor("D000005", "Descriptor 5"));

      when(xmlParserPort.parseDescriptors(any(InputStream.class), eq(TEST_MESH_VERSION)))
          .thenReturn(mockStream);

      itemReader =
          new MeshDescriptorItemReader(xmlParserPort, testXmlFile.toString(), TEST_MESH_VERSION);

      // When: 打开 Reader（应该跳过前 2 条）
      itemReader.open(executionContext);

      // Then: 第一次 read 应该返回第 3 条记录
      MeshDescriptorAggregate result = itemReader.read();
      assertThat(result).isNotNull();
      assertThat(result.getUi().ui()).isEqualTo("D000003");

      itemReader.close();
    }
  }

  @Nested
  @DisplayName("read() 测试")
  class ReadTest {

    @Test
    @DisplayName("逐条读取 - 应该返回所有记录")
    void read_multipleRecords_shouldReturnAllRecords() {
      // Given: Mock 返回 3 条记录
      Stream<MeshDescriptorAggregate> mockStream =
          Stream.of(
              createTestDescriptor("D000001", "Descriptor 1"),
              createTestDescriptor("D000002", "Descriptor 2"),
              createTestDescriptor("D000003", "Descriptor 3"));

      when(xmlParserPort.parseDescriptors(any(InputStream.class), eq(TEST_MESH_VERSION)))
          .thenReturn(mockStream);

      itemReader =
          new MeshDescriptorItemReader(xmlParserPort, testXmlFile.toString(), TEST_MESH_VERSION);
      itemReader.open(executionContext);

      // When & Then: 逐条读取
      assertThat(itemReader.read().getUi().ui()).isEqualTo("D000001");
      assertThat(itemReader.read().getUi().ui()).isEqualTo("D000002");
      assertThat(itemReader.read().getUi().ui()).isEqualTo("D000003");
      assertThat(itemReader.read()).isNull(); // 读取完成

      itemReader.close();
    }

    @Test
    @DisplayName("空流 - 应该立即返回 null")
    void read_emptyStream_shouldReturnNull() {
      // Given: Mock 返回空流
      when(xmlParserPort.parseDescriptors(any(InputStream.class), eq(TEST_MESH_VERSION)))
          .thenReturn(Stream.empty());

      itemReader =
          new MeshDescriptorItemReader(xmlParserPort, testXmlFile.toString(), TEST_MESH_VERSION);
      itemReader.open(executionContext);

      // When & Then: 应该立即返回 null
      assertThat(itemReader.read()).isNull();

      itemReader.close();
    }
  }

  @Nested
  @DisplayName("update() 测试")
  class UpdateTest {

    @Test
    @DisplayName("保存进度 - 应该更新 ExecutionContext")
    void update_afterReading_shouldUpdateExecutionContext() {
      // Given: Mock 返回 3 条记录
      Stream<MeshDescriptorAggregate> mockStream =
          Stream.of(
              createTestDescriptor("D000001", "Descriptor 1"),
              createTestDescriptor("D000002", "Descriptor 2"),
              createTestDescriptor("D000003", "Descriptor 3"));

      when(xmlParserPort.parseDescriptors(any(InputStream.class), eq(TEST_MESH_VERSION)))
          .thenReturn(mockStream);

      itemReader =
          new MeshDescriptorItemReader(xmlParserPort, testXmlFile.toString(), TEST_MESH_VERSION);
      itemReader.open(executionContext);

      // When: 读取 2 条后保存进度
      itemReader.read();
      itemReader.read();
      itemReader.update(executionContext);

      // Then: ExecutionContext 应该保存当前索引
      assertThat(executionContext.getInt(CURRENT_INDEX_KEY)).isEqualTo(2);

      itemReader.close();
    }
  }

  @Nested
  @DisplayName("close() 测试")
  class CloseTest {

    @Test
    @DisplayName("关闭 Reader - 应该释放资源")
    void close_afterOpen_shouldReleaseResources() {
      // Given: Mock 返回空流
      when(xmlParserPort.parseDescriptors(any(InputStream.class), eq(TEST_MESH_VERSION)))
          .thenReturn(Stream.empty());

      itemReader =
          new MeshDescriptorItemReader(xmlParserPort, testXmlFile.toString(), TEST_MESH_VERSION);
      itemReader.open(executionContext);

      // When & Then: 关闭不应该抛出异常
      itemReader.close();
    }

    @Test
    @DisplayName("重复关闭 - 不应该抛出异常")
    void close_calledTwice_shouldNotThrowException() {
      // Given: Mock 返回空流
      when(xmlParserPort.parseDescriptors(any(InputStream.class), eq(TEST_MESH_VERSION)))
          .thenReturn(Stream.empty());

      itemReader =
          new MeshDescriptorItemReader(xmlParserPort, testXmlFile.toString(), TEST_MESH_VERSION);
      itemReader.open(executionContext);

      // When & Then: 重复关闭不应该抛出异常
      itemReader.close();
      itemReader.close();
    }
  }

  @Nested
  @DisplayName("断点续传完整流程测试")
  class CheckpointResumeTest {

    @Test
    @DisplayName("完整断点续传流程 - 应该正确恢复并继续处理")
    void checkpointResume_fullWorkflow_shouldResumeCorrectly() {
      // === 第一阶段：模拟处理中断 ===

      // Given: Mock 返回 5 条记录
      Stream<MeshDescriptorAggregate> mockStream1 =
          Stream.of(
              createTestDescriptor("D000001", "Descriptor 1"),
              createTestDescriptor("D000002", "Descriptor 2"),
              createTestDescriptor("D000003", "Descriptor 3"),
              createTestDescriptor("D000004", "Descriptor 4"),
              createTestDescriptor("D000005", "Descriptor 5"));

      when(xmlParserPort.parseDescriptors(any(InputStream.class), eq(TEST_MESH_VERSION)))
          .thenReturn(mockStream1);

      ExecutionContext context1 = new ExecutionContext();
      MeshDescriptorItemReader reader1 =
          new MeshDescriptorItemReader(xmlParserPort, testXmlFile.toString(), TEST_MESH_VERSION);
      reader1.open(context1);

      // 读取 3 条记录后"中断"
      reader1.read(); // D000001
      reader1.read(); // D000002
      reader1.read(); // D000003
      reader1.update(context1); // 保存进度
      reader1.close();

      // 验证进度已保存
      assertThat(context1.getInt(CURRENT_INDEX_KEY)).isEqualTo(3);

      // === 第二阶段：从断点恢复 ===

      // 创建新的 Mock 流
      Stream<MeshDescriptorAggregate> mockStream2 =
          Stream.of(
              createTestDescriptor("D000001", "Descriptor 1"),
              createTestDescriptor("D000002", "Descriptor 2"),
              createTestDescriptor("D000003", "Descriptor 3"),
              createTestDescriptor("D000004", "Descriptor 4"),
              createTestDescriptor("D000005", "Descriptor 5"));

      when(xmlParserPort.parseDescriptors(any(InputStream.class), eq(TEST_MESH_VERSION)))
          .thenReturn(mockStream2);

      MeshDescriptorItemReader reader2 =
          new MeshDescriptorItemReader(xmlParserPort, testXmlFile.toString(), TEST_MESH_VERSION);
      reader2.open(context1); // 使用保存的 context 恢复

      // Then: 应该从第 4 条开始读取
      MeshDescriptorAggregate result1 = reader2.read();
      assertThat(result1).isNotNull();
      assertThat(result1.getUi().ui()).isEqualTo("D000004");

      MeshDescriptorAggregate result2 = reader2.read();
      assertThat(result2).isNotNull();
      assertThat(result2.getUi().ui()).isEqualTo("D000005");

      // 读取完成
      assertThat(reader2.read()).isNull();

      reader2.close();
    }
  }
}
