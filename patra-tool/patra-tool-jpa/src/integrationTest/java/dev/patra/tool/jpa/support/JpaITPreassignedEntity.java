package dev.patra.tool.jpa.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 集成测试专用实体：反例。主键预先分配、没有 version，也不实现 `Persistable`。
///
/// 值对象基类改造前就是这种写法，用来和 `JpaITValueObjectEntity` 对照 `saveAll` 是不是每行先查一次。
@Getter
@Setter
@Entity
@Table(name = "jpa_it_preassigned")
public class JpaITPreassignedEntity {

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "content")
  private String content;
}
