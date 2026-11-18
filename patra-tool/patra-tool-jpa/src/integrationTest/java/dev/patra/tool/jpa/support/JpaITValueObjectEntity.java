package dev.patra.tool.jpa.support;

import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 集成测试专用实体：继承值对象基类，映射到表 `jpa_it_value_object`。
@Getter
@Setter
@Entity
@Table(name = "jpa_it_value_object")
public class JpaITValueObjectEntity extends ValueObjectJpaEntity {

  @Column(name = "content")
  private String content;
}
