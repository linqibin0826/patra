package com.patra.registry.infra.persistence.entity.provenance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 数据库实体,映射到表 `reg_prov_pagination_cfg`。
/// 
/// 存储特定数据源和操作类型的分页或游标提取参数。
/// 
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_pagination_cfg")
public class RegProvPaginationCfgDO extends BaseDO {

  /// 外键,引用 `reg_provenance.id`。
  @TableField("provenance_id")
  private Long provenanceId;

  /// 控制配置作用域的操作类型鉴别器。
  @TableField("operation_type")
  private String operationType;

  /// 标记分页规则生效时的包含时间戳。
  @TableField("effective_from")
  private Instant effectiveFrom;

  /// 标记分页规则过期时的排除时间戳。
  @TableField("effective_to")
  private Instant effectiveTo;

  /// 描述分页策略的代码(PAGE_NUMBER/CURSOR/TOKEN/SCROLL)。
  @TableField("pagination_mode_code")
  private String paginationModeCode;

  /// 发出请求时使用的默认页大小。
  @TableField("page_size_value")
  private Integer pageSizeValue;

  /// 单次执行期间推进的最大页数。
  @TableField("max_pages_per_execution")
  private Integer maxPagesPerExecution;

  /// 控制服务器端排序的请求参数名称。
  @TableField("sort_field_param_name")
  private String sortFieldParamName;

  /// 排序方向标志(`1` 表示升序,`0` 表示降序)。
  @TableField("sorting_direction")
  private Integer sortingDirection;

  /// 生命周期状态代码,有效记录通常为 `ACTIVE`。
  @TableField("lifecycle_status_code")
  private String lifecycleStatusCode;
}
