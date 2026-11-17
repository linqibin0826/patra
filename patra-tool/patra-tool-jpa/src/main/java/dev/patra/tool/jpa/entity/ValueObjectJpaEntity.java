package dev.patra.tool.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.domain.Persistable;

/// 其他表实体的基类：只映射 `id`（库表规范第 3 节）。
///
/// 其他表是主表以外的表：随聚合整组替换或只追加的子表、关联表、值对象表。它们没有乐观锁，
/// `created_at` / `updated_at` 由数据库触发器填，实体不映射。
///
/// 实现 `Persistable`：主键是保存前就分配好的雪花 ID，又没有 version，Spring Data 默认会把它当成已存在的行，
/// `saveAll` 对每行先 SELECT 一次再插入。这里改成看对象自己的来历：新建的对象是新行，直接插入；从库里加载或插入过之后
/// 不再是新行，保存时走合并。由此也有一条约束：不要为已存在的行 new 一个带旧 `id` 的对象再保存，那会直接插入、撞主键；
/// 改已有的行先查出来再改，或整组删掉再插新行。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@MappedSuperclass
public abstract class ValueObjectJpaEntity
    implements Serializable, IdAwareEntity, Persistable<Long> {

  @Serial private static final long serialVersionUID = 1L;

  /// 主键，应用预分配的雪花 ID（`SnowflakeIdGenerator.getId()`）。
  @Id
  @Column(name = "id")
  private Long id;

  /// 这个对象是不是还没进过库；不映射到列，不参与相等判断。
  @Transient
  @Builder.Default
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private boolean fresh = true;

  /// 新建的对象是新行；加载过或插入过的不是。
  ///
  /// @return 是否新行
  @Override
  public boolean isNew() {
    return fresh;
  }

  /// 从库里加载或插入之后，不再是新行。
  @PostLoad
  @PostPersist
  void markNotNew() {
    fresh = false;
  }
}
