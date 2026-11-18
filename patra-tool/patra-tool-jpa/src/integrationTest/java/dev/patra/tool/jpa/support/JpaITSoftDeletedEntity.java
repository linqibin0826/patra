package dev.patra.tool.jpa.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

/// 集成测试专用实体：映射了 Hibernate `@SoftDelete`（项目已禁用，catalog 的文献、期刊以前用它），
/// `SoftDeleteBulkDeleteIT` 用它演示禁用的原因。
@Getter
@Setter
@Entity
@Table(name = "jpa_it_soft_deleted")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
public class JpaITSoftDeletedEntity {

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "content")
  private String content;
}
