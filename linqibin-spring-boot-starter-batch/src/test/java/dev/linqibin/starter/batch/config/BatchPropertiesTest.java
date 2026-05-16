package dev.linqibin.starter.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// {@link BatchProperties} 单元测试。
///
/// @author Patra Team
/// @since 0.1.0
class BatchPropertiesTest {

  @Test
  @DisplayName("默认值：应正确初始化")
  void defaultValues_ShouldBeCorrect() {
    // When
    BatchProperties properties = new BatchProperties();

    // Then
    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getTablePrefix()).isEqualTo("BATCH_");
    assertThat(properties.getChunk()).isNotNull();
  }

  @Test
  @DisplayName("Chunk 配置：应具有正确的默认值")
  void chunkProperties_ShouldHaveCorrectDefaults() {
    // When
    BatchProperties properties = new BatchProperties();
    BatchProperties.ChunkProperties chunk = properties.getChunk();

    // Then
    assertThat(chunk.getDefaultSize()).isEqualTo(5000);
    assertThat(chunk.getMaxSize()).isEqualTo(10000);
  }

  @Test
  @DisplayName("Setter 方法：应正确设置属性值")
  void setters_ShouldWorkCorrectly() {
    // Given
    BatchProperties properties = new BatchProperties();

    // When
    properties.setEnabled(false);
    properties.setTablePrefix("TEST_");
    properties.getChunk().setDefaultSize(500);
    properties.getChunk().setMaxSize(5000);

    // Then
    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getTablePrefix()).isEqualTo("TEST_");
    assertThat(properties.getChunk().getDefaultSize()).isEqualTo(500);
    assertThat(properties.getChunk().getMaxSize()).isEqualTo(5000);
  }

  @Test
  @DisplayName("数据源配置：应具有正确的默认值")
  void datasourceProperties_ShouldHaveCorrectDefaults() {
    // When
    BatchProperties properties = new BatchProperties();
    BatchProperties.DataSourceProperties datasource = properties.getDatasource();

    // Then
    assertThat(datasource).isNotNull();
    assertThat(datasource.getUrl()).isNull();
    assertThat(datasource.getUsername()).isNull();
    assertThat(datasource.getPassword()).isNull();
    assertThat(datasource.getDriverClassName()).isNull();
    assertThat(datasource.isConfigured()).isFalse();
  }

  @Test
  @DisplayName("数据源配置：设置 URL 后 isConfigured() 应返回 true")
  void datasourceProperties_isConfigured_ShouldReturnTrueWhenUrlIsSet() {
    // Given
    BatchProperties properties = new BatchProperties();

    // When
    properties.getDatasource().setUrl("jdbc:mysql://localhost:3306/batch_meta");

    // Then
    assertThat(properties.getDatasource().isConfigured()).isTrue();
  }

  @Test
  @DisplayName("数据源配置：URL 为空白时 isConfigured() 应返回 false")
  void datasourceProperties_isConfigured_ShouldReturnFalseWhenUrlIsBlank() {
    // Given
    BatchProperties properties = new BatchProperties();

    // When
    properties.getDatasource().setUrl("   ");

    // Then
    assertThat(properties.getDatasource().isConfigured()).isFalse();
  }

  @Test
  @DisplayName("Hikari 配置：应具有正确的默认值")
  void hikariProperties_ShouldHaveCorrectDefaults() {
    // When
    BatchProperties properties = new BatchProperties();
    BatchProperties.HikariProperties hikari = properties.getDatasource().getHikari();

    // Then
    assertThat(hikari).isNotNull();
    assertThat(hikari.getMaximumPoolSize()).isEqualTo(5);
    assertThat(hikari.getMinimumIdle()).isEqualTo(2);
    assertThat(hikari.getConnectionTimeout()).isEqualTo(30000L);
    assertThat(hikari.getIdleTimeout()).isEqualTo(600000L);
    assertThat(hikari.getMaxLifetime()).isEqualTo(1800000L);
  }

  @Test
  @DisplayName("Hikari 配置：Setter 方法应正确设置属性值")
  void hikariProperties_Setters_ShouldWorkCorrectly() {
    // Given
    BatchProperties properties = new BatchProperties();
    BatchProperties.HikariProperties hikari = properties.getDatasource().getHikari();

    // When
    hikari.setMaximumPoolSize(10);
    hikari.setMinimumIdle(3);
    hikari.setConnectionTimeout(60000L);
    hikari.setIdleTimeout(300000L);
    hikari.setMaxLifetime(900000L);

    // Then
    assertThat(hikari.getMaximumPoolSize()).isEqualTo(10);
    assertThat(hikari.getMinimumIdle()).isEqualTo(3);
    assertThat(hikari.getConnectionTimeout()).isEqualTo(60000L);
    assertThat(hikari.getIdleTimeout()).isEqualTo(300000L);
    assertThat(hikari.getMaxLifetime()).isEqualTo(900000L);
  }
}
