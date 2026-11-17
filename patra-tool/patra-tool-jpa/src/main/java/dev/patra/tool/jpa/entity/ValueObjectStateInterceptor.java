package dev.patra.tool.jpa.entity;

import java.io.Serial;
import java.io.Serializable;
import org.hibernate.Interceptor;

/// 让 Hibernate 按 `ValueObjectJpaEntity` 的「新行」标记判断一行是新是旧。
///
/// 值对象行的主键是预先分配的雪花 ID，又没有 version，Hibernate 自己判断不了新旧：父实体级联合并时，
/// 每个新子行都要先查一次库才敢插入，整组替换子表时语句数随子行数增长。`Persistable` 只管 Spring Data 的
/// `save`，管不到 Hibernate 的级联，所以在这里把同一个标记交给 Hibernate。其他实体返回空，照 Hibernate 默认判断。
public class ValueObjectStateInterceptor implements Interceptor, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Override
  public Boolean isTransient(Object entity) {
    return entity instanceof ValueObjectJpaEntity row ? row.isNew() : null;
  }
}
