package dev.patra.tool.jpa.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/// 集成测试专用实体：主键预先分配，并实现 `Persistable` 自己告诉 Spring Data 是不是新行。
///
/// 新建的对象 `fresh` 为真；从库里加载或插入之后置为假，之后再保存就走更新。
@Getter
@Setter
@Entity
@Table(name = "jpa_it_persistable")
public class JpaITPersistableEntity implements Persistable<Long> {

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "content")
  private String content;

  @Transient private boolean fresh = true;

  @Override
  public boolean isNew() {
    return fresh;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    fresh = false;
  }
}
