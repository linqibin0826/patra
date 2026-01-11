package com.patra.registry.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import com.patra.registry.domain.model.vo.dictionary.DictionaryType;
import com.patra.registry.infra.adapter.persistence.dao.dictionary.SysDictItemAliasDao;
import com.patra.registry.infra.adapter.persistence.dao.dictionary.SysDictItemDao;
import com.patra.registry.infra.adapter.persistence.dao.dictionary.SysDictTypeDao;
import com.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictItemAliasEntity;
import com.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictItemEntity;
import com.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictTypeEntity;
import com.patra.registry.infra.config.RegistryMySQLContainerInitializer;
import com.patra.starter.jpa.autoconfig.JpaAuditingConfig;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
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
@ContextConfiguration(initializers = RegistryMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DictionaryRepositoryAdapter.class, JpaAuditingConfig.class})
@ComponentScan(basePackages = "com.patra.registry.infra.adapter.persistence.converter.mapper")
@ActiveProfiles("test")
@DisplayName("DictionaryRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DictionaryRepositoryAdapterIT {

  @Autowired private DictionaryRepositoryAdapter repository;
  @Autowired private SysDictTypeDao typeDao;
  @Autowired private SysDictItemDao itemDao;
  @Autowired private SysDictItemAliasDao aliasDao;

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
      SysDictTypeEntity deletedType = new SysDictTypeEntity();
      deletedType.setId(SnowflakeIdGenerator.getId());
      deletedType.setTypeCode("deleted_type");
      deletedType.setTypeName("已删除类型");
      deletedType.setDeletedAt(Instant.now());
      typeDao.save(deletedType);

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
      SysDictItemEntity deletedItem = new SysDictItemEntity();
      deletedItem.setId(SnowflakeIdGenerator.getId());
      deletedItem.setTypeId(testTypeId);
      deletedItem.setItemCode("DELETED");
      deletedItem.setItemName("已删除项");
      deletedItem.setEnabled(true);
      deletedItem.setDeletedAt(Instant.now());
      itemDao.save(deletedItem);

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
      createAlias(itemId, "ISO_3166_1_ALPHA2", "CN");
      createAlias(itemId, "ISO_3166_1_ALPHA3", "CHN");

      Map<String, DictionaryItem> result =
          repository.findItemsByAliases(testTypeId, "ISO_3166_1_ALPHA2", Set.of("CN"));

      assertThat(result).hasSize(1);
      assertThat(result.get("CN").itemCode()).isEqualTo("CN");
    }

    @Test
    @DisplayName("不同来源标准的别名应隔离查询")
    void shouldIsolateBySourceStandard() {
      Long cnItemId = createItem("CN", "中国", 10);
      Long usItemId = createItem("US", "美国", 20);
      createAlias(cnItemId, "ISO_3166_1_ALPHA2", "CN");
      createAlias(usItemId, "NAME_EN", "United States");

      Map<String, DictionaryItem> result =
          repository.findItemsByAliases(testTypeId, "NAME_EN", Set.of("CN", "United States"));

      // 只能找到 NAME_EN 来源的别名
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
      typeDao.save(otherType);

      // 在其他类型下创建字典项和别名
      SysDictItemEntity otherItem = new SysDictItemEntity();
      otherItem.setId(SnowflakeIdGenerator.getId());
      otherItem.setTypeId(otherType.getId());
      otherItem.setItemCode("ZH");
      otherItem.setItemName("中文");
      otherItem.setEnabled(true);
      itemDao.save(otherItem);
      createAlias(otherItem.getId(), "ISO_639_1", "zh");

      // 使用 country 类型查询 language 类型的别名
      Map<String, DictionaryItem> result =
          repository.findItemsByAliases(testTypeId, "ISO_639_1", Set.of("zh"));

      assertThat(result).isEmpty();
    }
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
