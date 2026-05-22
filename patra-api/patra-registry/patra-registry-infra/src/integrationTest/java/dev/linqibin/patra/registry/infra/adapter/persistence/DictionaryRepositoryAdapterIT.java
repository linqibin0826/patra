package dev.linqibin.patra.registry.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryItemSummary;
import dev.linqibin.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import dev.linqibin.patra.registry.domain.model.vo.dictionary.DictionaryType;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.dictionary.SysDictItemAliasDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.dictionary.SysDictItemDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.dao.dictionary.SysDictTypeDao;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictItemAliasEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictItemEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictTypeEntity;
import dev.linqibin.patra.registry.infra.config.RegistryITPostgreSQLContainerInitializer;
import dev.linqibin.starter.jpa.autoconfig.HibernatePropertiesCustomizer;
import dev.linqibin.starter.jpa.autoconfig.JpaAuditingConfig;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// DictionaryRepositoryAdapter 集成测试。
///
/// 验证字典类型、字典项和别名的查询功能。
///
/// @author linqibin
/// @since 0.1.0
@DataJpaTest
@ContextConfiguration(initializers = RegistryITPostgreSQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
  DictionaryRepositoryAdapter.class,
  JpaAuditingConfig.class,
  HibernatePropertiesCustomizer.class
})
@ComponentScan(
    basePackages = "dev.linqibin.patra.registry.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("DictionaryRepositoryAdapter 集成测试")
class DictionaryRepositoryAdapterIT {

  @Autowired private DictionaryRepositoryAdapter repository;
  @Autowired private SysDictTypeDao typeDao;
  @Autowired private SysDictItemDao itemDao;
  @Autowired private SysDictItemAliasDao aliasDao;
  @Autowired private EntityManager entityManager;

  private Long testTypeId;

  @BeforeEach
  void setUp() {
    aliasDao.deleteAllInBatch();
    itemDao.deleteAllInBatch();
    typeDao.deleteAllInBatch();

    // 创建测试字典类型
    SysDictTypeEntity type = new SysDictTypeEntity();
    type.setId(SnowflakeIdGenerator.getId());
    type.setTypeCode("country");
    type.setTypeName("国家/地区");
    type.setIsSystem(true);
    type.setAllowCustomItems(false);
    typeDao.save(type);
    testTypeId = type.getId();
  }

  @Nested
  @DisplayName("findTypeByCode")
  class FindTypeByCodeTests {

    @Test
    @DisplayName("应能查询存在的字典类型")
    void shouldFindExistingType() {
      Optional<DictionaryType> result = repository.findTypeByCode("country");

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().id()).isEqualTo(testTypeId);
      assertThat(result.orElseThrow().typeCode()).isEqualTo("country");
    }

    @Test
    @DisplayName("查询不存在的类型应返回空")
    void shouldReturnEmptyForNonExistentType() {
      Optional<DictionaryType> result = repository.findTypeByCode("non_existent");

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("已软删除的类型不应被查询到")
    void shouldIgnoreDeletedType() {
      // 创建并保存类型
      SysDictTypeEntity deletedType = new SysDictTypeEntity();
      Long deletedTypeId = SnowflakeIdGenerator.getId();
      deletedType.setId(deletedTypeId);
      deletedType.setTypeCode("deleted_type");
      deletedType.setTypeName("已删除类型");
      deletedType.setIsSystem(true);
      deletedType.setAllowCustomItems(false);
      typeDao.saveAndFlush(deletedType);

      // 使用 Native SQL 标记为已删除（@SoftDelete 由 Hibernate 自动管理，不暴露 setter）
      entityManager
          .createNativeQuery("UPDATE sys_dict_type SET deleted_at = NOW() WHERE id = :id")
          .setParameter("id", deletedTypeId)
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();

      Optional<DictionaryType> result = repository.findTypeByCode("deleted_type");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findItemsByTypeAndCodes")
  class FindItemsByTypeAndCodesTests {

    @Test
    @DisplayName("应能批量查询字典项")
    void shouldFindItemsByCodes() {
      createItem("CN", "中国", 10);
      createItem("US", "美国", 20);
      createItem("JP", "日本", 30);

      Map<String, DictionaryItem> result =
          repository.findItemsByTypeAndCodes(testTypeId, Set.of("CN", "US"));

      assertThat(result).hasSize(2);
      assertThat(result.get("CN").itemName()).isEqualTo("中国");
      assertThat(result.get("US").itemName()).isEqualTo("美国");
    }

    @Test
    @DisplayName("查询空代码集合应返回空Map")
    void shouldReturnEmptyMapForEmptyCodes() {
      Map<String, DictionaryItem> result = repository.findItemsByTypeAndCodes(testTypeId, Set.of());

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("已软删除的字典项不应被查询到")
    void shouldIgnoreDeletedItems() {
      // 创建并保存字典项
      SysDictItemEntity deletedItem = new SysDictItemEntity();
      Long deletedItemId = SnowflakeIdGenerator.getId();
      deletedItem.setId(deletedItemId);
      deletedItem.setTypeId(testTypeId);
      deletedItem.setItemCode("DELETED");
      deletedItem.setItemName("已删除项");
      deletedItem.setDisplayOrder(100);
      deletedItem.setEnabled(true);
      deletedItem.setIsDefault(false);
      itemDao.saveAndFlush(deletedItem);

      // 使用 Native SQL 标记为已删除（@SoftDelete 由 Hibernate 自动管理，不暴露 setter）
      entityManager
          .createNativeQuery("UPDATE sys_dict_item SET deleted_at = NOW() WHERE id = :id")
          .setParameter("id", deletedItemId)
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();

      Map<String, DictionaryItem> result =
          repository.findItemsByTypeAndCodes(testTypeId, Set.of("DELETED"));

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findItemsByAliases")
  class FindItemsByAliasesTests {

    @Test
    @DisplayName("应能通过别名查询字典项")
    void shouldFindItemsByAliases() {
      Long itemId = createItem("CN", "中国", 10);
      // source_standard 使用小写（PG CHECK 约束：^[a-z0-9_\-]{1,64}$）
      createAlias(itemId, "iso_3166_1_alpha2", "CN");
      createAlias(itemId, "iso_3166_1_alpha3", "CHN");

      Map<String, DictionaryItem> result =
          repository.findItemsByAliases(testTypeId, "iso_3166_1_alpha2", Set.of("CN"));

      assertThat(result).hasSize(1);
      assertThat(result.get("CN").itemCode()).isEqualTo("CN");
    }

    @Test
    @DisplayName("不同来源标准的别名应隔离查询")
    void shouldIsolateBySourceStandard() {
      Long cnItemId = createItem("CN", "中国", 10);
      Long usItemId = createItem("US", "美国", 20);
      createAlias(cnItemId, "iso_3166_1_alpha2", "CN");
      createAlias(usItemId, "name_en", "United States");

      Map<String, DictionaryItem> result =
          repository.findItemsByAliases(testTypeId, "name_en", Set.of("CN", "United States"));

      // 只能找到 name_en 来源的别名
      assertThat(result).hasSize(1);
      assertThat(result.get("United States").itemCode()).isEqualTo("US");
    }

    // 注：软删除测试已移除 - SysDictItemAliasEntity 现在继承 BaseJpaEntity，使用物理删除

    @Test
    @DisplayName("类型不匹配的别名应被过滤")
    void shouldFilterByTypeId() {
      // 创建另一个类型
      SysDictTypeEntity otherType = new SysDictTypeEntity();
      otherType.setId(SnowflakeIdGenerator.getId());
      otherType.setTypeCode("language");
      otherType.setTypeName("语言");
      otherType.setIsSystem(true);
      otherType.setAllowCustomItems(false);
      typeDao.save(otherType);

      // 在其他类型下创建字典项和别名
      SysDictItemEntity otherItem = new SysDictItemEntity();
      otherItem.setId(SnowflakeIdGenerator.getId());
      otherItem.setTypeId(otherType.getId());
      otherItem.setItemCode("ZH");
      otherItem.setItemName("中文");
      otherItem.setDisplayOrder(100);
      otherItem.setEnabled(true);
      otherItem.setIsDefault(false);
      itemDao.save(otherItem);
      createAlias(otherItem.getId(), "iso_639_1", "zh");

      // 使用 country 类型查询 language 类型的别名
      Map<String, DictionaryItem> result =
          repository.findItemsByAliases(testTypeId, "iso_639_1", Set.of("zh"));

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findAllEnabledItems")
  class FindAllEnabledItemsTests {

    @Test
    @DisplayName("无 labelStandard 时应返回仅含 code 和 name 的字典项列表")
    void findAllEnabledItems_withoutLabel_shouldReturnItemsOnly() {
      createItem("CN", "China", 156);
      createItem("US", "United States of America", 840);
      createDisabledItem("XX", "Disabled Country", 999);

      List<DictionaryItemSummary> result = repository.findAllEnabledItems(testTypeId, null);

      assertThat(result).hasSize(2);
      assertThat(result.getFirst().code()).isEqualTo("CN");
      assertThat(result.getFirst().name()).isEqualTo("China");
      assertThat(result.getFirst().label()).isNull();
      assertThat(result.getFirst().displayOrder()).isEqualTo(156);
      assertThat(result.get(1).code()).isEqualTo("US");
    }

    @Test
    @DisplayName("有 labelStandard 时应关联查询别名作为 label")
    void findAllEnabledItems_withLabel_shouldJoinAliases() {
      Long cnId = createItem("CN", "China", 156);
      Long usId = createItem("US", "United States of America", 840);
      createAlias(cnId, "name_zh", "中国");
      createAlias(usId, "name_zh", "美国");

      List<DictionaryItemSummary> result = repository.findAllEnabledItems(testTypeId, "name_zh");

      assertThat(result).hasSize(2);
      assertThat(result.getFirst().code()).isEqualTo("CN");
      assertThat(result.getFirst().label()).isEqualTo("中国");
      assertThat(result.get(1).code()).isEqualTo("US");
      assertThat(result.get(1).label()).isEqualTo("美国");
    }

    @Test
    @DisplayName("labelStandard 不存在别名时 label 应为 null")
    void findAllEnabledItems_withNoMatchingAliases_shouldHaveNullLabel() {
      createItem("CN", "China", 156);

      List<DictionaryItemSummary> result =
          repository.findAllEnabledItems(testTypeId, "name_nonexistent");

      assertThat(result).hasSize(1);
      assertThat(result.getFirst().code()).isEqualTo("CN");
      assertThat(result.getFirst().label()).isNull();
    }

    @Test
    @DisplayName("无启用项时应返回空列表")
    void findAllEnabledItems_noEnabledItems_shouldReturnEmpty() {
      createDisabledItem("XX", "Disabled", 999);

      List<DictionaryItemSummary> result = repository.findAllEnabledItems(testTypeId, null);

      assertThat(result).isEmpty();
    }
  }

  /// 创建已禁用的测试字典项。
  private void createDisabledItem(String code, String name, int order) {
    SysDictItemEntity item = new SysDictItemEntity();
    item.setId(SnowflakeIdGenerator.getId());
    item.setTypeId(testTypeId);
    item.setItemCode(code);
    item.setItemName(name);
    item.setDisplayOrder(order);
    item.setEnabled(false);
    item.setIsDefault(false);
    itemDao.save(item);
  }

  /// 创建测试字典项。
  private Long createItem(String code, String name, int order) {
    SysDictItemEntity item = new SysDictItemEntity();
    item.setId(SnowflakeIdGenerator.getId());
    item.setTypeId(testTypeId);
    item.setItemCode(code);
    item.setItemName(name);
    item.setDisplayOrder(order);
    item.setEnabled(true);
    item.setIsDefault(false);
    itemDao.save(item);
    return item.getId();
  }

  /// 创建测试别名。
  private void createAlias(Long itemId, String sourceStandard, String externalCode) {
    SysDictItemAliasEntity alias = new SysDictItemAliasEntity();
    alias.setId(SnowflakeIdGenerator.getId());
    alias.setItemId(itemId);
    alias.setSourceStandard(sourceStandard);
    alias.setExternalCode(externalCode);
    aliasDao.save(alias);
  }
}
