package dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance;

import dev.linqibin.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/// 分页配置 JPA 实体，映射到表 `reg_prov_pagination_cfg`。
///
/// 存储特定数据源和操作类型的分页或游标提取参数。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_prov_pagination_cfg")
public class ProvPaginationCfgEntity extends ValueObjectJpaEntity {

  /// 外键，引用 `reg_provenance.id`。
  @Column(name = "provenance_id", nullable = false)
  private Long provenanceId;

  /// 控制配置作用域的操作类型鉴别器。
  @Column(name = "operation_type", nullable = false, length = 20)
  private String operationType;

  /// 标记分页规则生效时的包含时间戳。
  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  /// 标记分页规则过期时的排除时间戳。
  @Column(name = "effective_to")
  private Instant effectiveTo;

  /// 描述分页策略的代码(PAGE_NUMBER/CURSOR/TOKEN/SCROLL)。
  @Column(name = "pagination_mode_code", length = 20)
  private String paginationModeCode;

  /// 发出请求时使用的默认页大小。
  @Column(name = "page_size_value")
  private Integer pageSizeValue;

  /// 单次执行期间推进的最大页数。
  @Column(name = "max_pages_per_execution")
  private Integer maxPagesPerExecution;

  /// 控制服务器端排序的请求参数名称。
  @Column(name = "sort_field_param_name", length = 50)
  private String sortFieldParamName;

  /// 排序方向标志（`true` 表示升序 ASC，`false` 表示降序 DESC）。
  @Column(name = "sorting_direction")
  private Boolean sortingDirection;

  /// 生命周期状态代码，有效记录通常为 `ACTIVE`。
  @Column(name = "lifecycle_status_code", length = 20)
  private String lifecycleStatusCode;
}
