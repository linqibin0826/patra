package dev.patra.tool.jpa.support;

import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 集成测试专用实体：继承现有的值对象基类，主键预先分配、没有 version。
///
/// 和 catalog 的子表实体是同一种写法，用来观察 `saveAll` 是不是每行先查一次。
@Getter
@Setter
@Entity
@Table(name = "jpa_it_preassigned")
public class JpaITPreassignedEntity extends ValueObjectJpaEntity {

  @Column(name = "content")
  private String content;
}
