package dev.linqibin.patra.registry.infra.adapter.persistence.entity.expr;

import dev.linqibin.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// API 参数映射 JPA 实体，映射到表 `reg_prov_api_param_map`。
///
/// 跟踪标准化查询键的提供方特定参数名称。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_prov_api_param_map")
public class ProvApiParamMapEntity extends ValueObjectJpaEntity {

  /// 外键，引用 `reg_provenance.id`。
  @Column(name = "provenance_id", nullable = false)
  private Long provenanceId;

  /// 操作类型鉴别器。
  @Column(name = "operation_type", nullable = false, length = 20)
  private String operationType;

  /// 生命周期状态代码。
  @Column(name = "lifecycle_status_code", length = 20)
  private String lifecycleStatusCode;

  /// 此映射适用的端点名称；`null` 表示所有端点。
  @Column(name = "endpoint_name", length = 50)
  private String endpointName;

  /// 由表达式渲染解析的标准化键(例如，`from`，`term`)。
  @Column(name = "std_key", nullable = false, length = 50)
  private String stdKey;

  /// 与标准键对齐的提供方特定参数名称。
  @Column(name = "provider_param_name", nullable = false, length = 100)
  private String providerParamName;

  /// 可选的转换，在发送到提供方之前应用于值。
  @Column(name = "transform_code", length = 50)
  private String transformCode;

  /// 自由格式 JSON 备注，描述特殊处理。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "notes", columnDefinition = "JSON")
  private JsonNode notes;

  /// 包含时间戳，映射生效时间。
  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  /// 排除时间戳，映射过期时间。
  @Column(name = "effective_to")
  private Instant effectiveTo;
}
