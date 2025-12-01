package com.patra.catalog.infra.adapter.mesh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.port.FileDownloadPort;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// DefaultMeshSourceFileAdapter 单元测试。
///
/// **测试策略**：
///
/// - 单元测试：Mock FileDownloadPort 依赖
/// - 验证委托行为：确保方法正确委托给 FileDownloadPort
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultMeshSourceFileAdapter 单元测试")
class DefaultMeshSourceFileAdapterTest {

  private static final String MESH_VERSION = "2025";
  private static final URI DESCRIPTOR_URL =
      URI.create("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");
  private static final URI QUALIFIER_URL =
      URI.create("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml");
  private static final Path TEMP_FILE_PATH = Path.of("/tmp/mesh-temp-12345.xml");

  @Mock private FileDownloadPort fileDownloadPort;

  private DefaultMeshSourceFileAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new DefaultMeshSourceFileAdapter(fileDownloadPort);
  }

  @Test
  @DisplayName("fetchDescriptorFile - 应委托给 FileDownloadPort.downloadToTemp")
  void fetchDescriptorFile_shouldDelegateToFileDownloadPort() {
    // Arrange
    when(fileDownloadPort.downloadToTemp(DESCRIPTOR_URL)).thenReturn(TEMP_FILE_PATH);

    // Act
    Path result = adapter.fetchDescriptorFile(MESH_VERSION, DESCRIPTOR_URL);

    // Assert
    assertThat(result).isEqualTo(TEMP_FILE_PATH);
    verify(fileDownloadPort).downloadToTemp(DESCRIPTOR_URL);
  }

  @Test
  @DisplayName("fetchQualifierFile - 应委托给 FileDownloadPort.downloadToTemp")
  void fetchQualifierFile_shouldDelegateToFileDownloadPort() {
    // Arrange
    when(fileDownloadPort.downloadToTemp(QUALIFIER_URL)).thenReturn(TEMP_FILE_PATH);

    // Act
    Path result = adapter.fetchQualifierFile(MESH_VERSION, QUALIFIER_URL);

    // Assert
    assertThat(result).isEqualTo(TEMP_FILE_PATH);
    verify(fileDownloadPort).downloadToTemp(QUALIFIER_URL);
  }

  @Test
  @DisplayName("fetchDescriptorFile - meshVersion 参数不影响委托行为")
  void fetchDescriptorFile_meshVersionShouldNotAffectDelegation() {
    // Arrange
    String differentVersion = "2024";
    when(fileDownloadPort.downloadToTemp(DESCRIPTOR_URL)).thenReturn(TEMP_FILE_PATH);

    // Act
    Path result = adapter.fetchDescriptorFile(differentVersion, DESCRIPTOR_URL);

    // Assert - meshVersion 不影响委托，只使用 remoteUrl
    assertThat(result).isEqualTo(TEMP_FILE_PATH);
    verify(fileDownloadPort).downloadToTemp(DESCRIPTOR_URL);
  }

  @Test
  @DisplayName("fetchQualifierFile - meshVersion 参数不影响委托行为")
  void fetchQualifierFile_meshVersionShouldNotAffectDelegation() {
    // Arrange
    String differentVersion = "2024";
    when(fileDownloadPort.downloadToTemp(QUALIFIER_URL)).thenReturn(TEMP_FILE_PATH);

    // Act
    Path result = adapter.fetchQualifierFile(differentVersion, QUALIFIER_URL);

    // Assert - meshVersion 不影响委托，只使用 remoteUrl
    assertThat(result).isEqualTo(TEMP_FILE_PATH);
    verify(fileDownloadPort).downloadToTemp(QUALIFIER_URL);
  }
}
