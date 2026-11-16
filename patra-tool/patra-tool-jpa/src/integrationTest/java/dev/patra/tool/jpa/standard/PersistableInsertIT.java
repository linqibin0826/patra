package dev.patra.tool.jpa.standard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.jpa.id.SnowflakeIdGenerator;
import dev.patra.tool.jpa.support.JpaITPreassignedDao;
import dev.patra.tool.jpa.support.JpaITPreassignedEntity;
import dev.patra.tool.jpa.support.JpaITValueObjectDao;
import dev.patra.tool.jpa.support.JpaITValueObjectEntity;
import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/// 验证库表规范依赖的一个前提（设计文档推断 5）：主键预先分配的实体，Spring Data 当成「已存在」去合并，每行先查一次；
/// 值对象基类实现 `Persistable` 后改为直接插入。
///
/// Spring Data 判断新旧的默认规则是看主键是不是空（有 version 时看 version）。雪花 ID 在保存前
/// 就分配好了，没有 version 的子表实体于是都被当成已存在的行，`saveAll` 对每行先 SELECT 再
/// INSERT。判断依据是 Hibernate 统计里「准备过几条语句」：10 次查询各准备一次，批量插入准备一次。
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@DisplayName("库表规范推断 5：预分配主键与 Persistable")
class PersistableInsertIT {

  private static final int ROWS = 10;

  @Autowired private JpaITPreassignedDao preassignedDao;
  @Autowired private JpaITValueObjectDao valueObjectDao;
  @Autowired private EntityManagerFactory emf;
  @Autowired private TransactionTemplate tx;

  @Test
  @DisplayName("不实现 Persistable 的预分配实体：saveAll 10 行，先查 10 次，再批量插入 1 次")
  void should_select_each_row_before_insert_when_id_is_preassigned() {
    List<JpaITPreassignedEntity> entities = new ArrayList<>();
    for (int i = 0; i < ROWS; i++) {
      JpaITPreassignedEntity entity = new JpaITPreassignedEntity();
      entity.setId(SnowflakeIdGenerator.getId());
      entity.setContent("a");
      entities.add(entity);
    }
    statistics().clear();

    tx.executeWithoutResult(status -> preassignedDao.saveAll(entities));

    assertThat(statistics().getPrepareStatementCount()).isEqualTo(ROWS + 1L);
    assertThat(preassignedDao.count()).isGreaterThanOrEqualTo(ROWS);
  }

  @Test
  @DisplayName("值对象基类：saveAll 10 行，只批量插入 1 次，插入后不再是新行")
  void should_insert_directly_when_entity_extends_value_object_base() {
    List<JpaITValueObjectEntity> entities = new ArrayList<>();
    for (int i = 0; i < ROWS; i++) {
      JpaITValueObjectEntity entity = new JpaITValueObjectEntity();
      entity.setId(SnowflakeIdGenerator.getId());
      entity.setContent("a");
      entities.add(entity);
    }
    statistics().clear();

    tx.executeWithoutResult(status -> valueObjectDao.saveAll(entities));

    assertThat(statistics().getPrepareStatementCount()).isEqualTo(1);
    assertThat(entities).allSatisfy(entity -> assertThat(entity.isNew()).isFalse());
  }

  @Test
  @DisplayName("值对象基类：查出来改字段再保存，只发 UPDATE、不插入")
  void should_update_loaded_value_object_row() {
    JpaITValueObjectEntity entity = new JpaITValueObjectEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setContent("before");
    valueObjectDao.saveAndFlush(entity);
    statistics().clear();

    tx.executeWithoutResult(
        status -> {
          JpaITValueObjectEntity loaded = valueObjectDao.findById(entity.getId()).orElseThrow();
          loaded.setContent("after");
          valueObjectDao.save(loaded);
        });

    assertThat(statistics().getPrepareStatementCount()).isEqualTo(2);
    assertThat(valueObjectDao.findById(entity.getId()).orElseThrow().getContent())
        .isEqualTo("after");
  }

  private Statistics statistics() {
    return emf.unwrap(SessionFactory.class).getStatistics();
  }
}
