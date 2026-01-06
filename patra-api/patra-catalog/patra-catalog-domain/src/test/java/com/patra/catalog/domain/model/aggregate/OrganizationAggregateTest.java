package com.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.enums.ExternalIdType;
import com.patra.catalog.domain.model.enums.OrganizationNameType;
import com.patra.catalog.domain.model.enums.OrganizationRelationType;
import com.patra.catalog.domain.model.enums.OrganizationStatus;
import com.patra.catalog.domain.model.enums.OrganizationType;
import com.patra.catalog.domain.model.vo.organization.AdminInfo;
import com.patra.catalog.domain.model.vo.organization.ExternalId;
import com.patra.catalog.domain.model.vo.organization.GeoLocation;
import com.patra.catalog.domain.model.vo.organization.OrganizationId;
import com.patra.catalog.domain.model.vo.organization.OrganizationLink;
import com.patra.catalog.domain.model.vo.organization.OrganizationName;
import com.patra.catalog.domain.model.vo.organization.OrganizationRelation;
import com.patra.catalog.domain.model.vo.organization.RorId;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OrganizationAggregate 聚合根单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationAggregate 聚合根")
class OrganizationAggregateTest {

  @Nested
  @DisplayName("创建测试")
  class CreationTest {

    @Test
    @DisplayName("应从 ROR 数据创建机构")
    void shouldCreateFromRor() {
      RorId rorId = RorId.of("https://ror.org/03vek6s52");

      OrganizationAggregate org =
          OrganizationAggregate.fromRor(rorId, "Harvard University", OrganizationStatus.ACTIVE);

      assertThat(org.getRorId()).isEqualTo(rorId);
      assertThat(org.getDisplayName()).isEqualTo("Harvard University");
      assertThat(org.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
      assertThat(org.getId()).isNull(); // 未持久化
      assertThat(org.isTransient()).isTrue();
    }

    @Test
    @DisplayName("应从持久化状态重建机构")
    void shouldRestore() {
      OrganizationId id = OrganizationId.of(12345L);
      RorId rorId = RorId.of("https://ror.org/03vek6s52");

      OrganizationAggregate org =
          OrganizationAggregate.restore(
              id, rorId, "Harvard University", OrganizationStatus.ACTIVE, 5L);

      assertThat(org.getId()).isEqualTo(id);
      assertThat(org.getRorId()).isEqualTo(rorId);
      assertThat(org.getVersion()).isEqualTo(5L);
      assertThat(org.isTransient()).isFalse();
    }

    @Test
    @DisplayName("null ROR ID 应抛出异常")
    void shouldThrowWhenRorIdIsNull() {
      assertThatThrownBy(
              () -> OrganizationAggregate.fromRor(null, "Harvard", OrganizationStatus.ACTIVE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ROR ID 不能为空");
    }

    @Test
    @DisplayName("空白显示名称应抛出异常")
    void shouldThrowWhenDisplayNameIsBlank() {
      RorId rorId = RorId.of("https://ror.org/03vek6s52");

      assertThatThrownBy(() -> OrganizationAggregate.fromRor(rorId, "", OrganizationStatus.ACTIVE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("显示名称不能为空");
    }

    @Test
    @DisplayName("null 状态应抛出异常")
    void shouldThrowWhenStatusIsNull() {
      RorId rorId = RorId.of("https://ror.org/03vek6s52");

      assertThatThrownBy(() -> OrganizationAggregate.fromRor(rorId, "Harvard", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("状态不能为空");
    }
  }

  @Nested
  @DisplayName("基本属性测试")
  class BasicPropertiesTest {

    @Test
    @DisplayName("应设置成立年份")
    void shouldSetEstablished() {
      OrganizationAggregate org = createTestOrganization();

      org.withEstablished(1636);

      assertThat(org.getEstablished()).isEqualTo(1636);
      assertThat(org.isDirty()).isTrue();
    }

    @Test
    @DisplayName("应设置管理元数据")
    void shouldSetAdminInfo() {
      OrganizationAggregate org = createTestOrganization();
      AdminInfo adminInfo =
          AdminInfo.of(
              LocalDate.of(2019, 1, 20), "1.0",
              LocalDate.of(2024, 12, 11), "2.1");

      org.withAdminInfo(adminInfo);

      assertThat(org.getAdminInfo()).isEqualTo(adminInfo);
      assertThat(org.isDirty()).isTrue();
    }
  }

  @Nested
  @DisplayName("机构类型测试")
  class TypesTest {

    @Test
    @DisplayName("应添加机构类型")
    void shouldAddType() {
      OrganizationAggregate org = createTestOrganization();

      org.addType(OrganizationType.EDUCATION);
      org.addType(OrganizationType.NONPROFIT);

      assertThat(org.getTypes())
          .containsExactlyInAnyOrder(OrganizationType.EDUCATION, OrganizationType.NONPROFIT);
      assertThat(org.isDirty()).isTrue();
    }

    @Test
    @DisplayName("重复添加类型应忽略")
    void shouldIgnoreDuplicateType() {
      OrganizationAggregate org = createTestOrganization();

      org.addType(OrganizationType.EDUCATION);
      org.addType(OrganizationType.EDUCATION);

      assertThat(org.getTypes()).hasSize(1);
    }

    @Test
    @DisplayName("应批量设置机构类型")
    void shouldSetTypes() {
      OrganizationAggregate org = createTestOrganization();

      org.withTypes(Set.of(OrganizationType.EDUCATION, OrganizationType.NONPROFIT));

      assertThat(org.getTypes()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("域名测试")
  class DomainsTest {

    @Test
    @DisplayName("应添加域名")
    void shouldAddDomain() {
      OrganizationAggregate org = createTestOrganization();

      org.addDomain("harvard.edu");
      org.addDomain("hms.harvard.edu");

      assertThat(org.getDomains()).containsExactly("harvard.edu", "hms.harvard.edu");
      assertThat(org.isDirty()).isTrue();
    }

    @Test
    @DisplayName("重复添加域名应忽略")
    void shouldIgnoreDuplicateDomain() {
      OrganizationAggregate org = createTestOrganization();

      org.addDomain("harvard.edu");
      org.addDomain("harvard.edu");

      assertThat(org.getDomains()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("链接测试")
  class LinksTest {

    @Test
    @DisplayName("应添加链接")
    void shouldAddLink() {
      OrganizationAggregate org = createTestOrganization();

      org.addLink(OrganizationLink.website("https://www.harvard.edu"));
      org.addLink(OrganizationLink.wikipedia("https://en.wikipedia.org/wiki/Harvard_University"));

      assertThat(org.getLinks()).hasSize(2);
      assertThat(org.isDirty()).isTrue();
    }

    @Test
    @DisplayName("getWebsiteUrl() 应返回官网链接")
    void shouldGetWebsiteUrl() {
      OrganizationAggregate org = createTestOrganization();
      org.addLink(OrganizationLink.website("https://www.harvard.edu"));
      org.addLink(OrganizationLink.wikipedia("https://en.wikipedia.org/wiki/Harvard"));

      assertThat(org.getWebsiteUrl()).contains("https://www.harvard.edu");
    }

    @Test
    @DisplayName("getWikipediaUrl() 应返回 Wikipedia 链接")
    void shouldGetWikipediaUrl() {
      OrganizationAggregate org = createTestOrganization();
      org.addLink(OrganizationLink.website("https://www.harvard.edu"));
      org.addLink(OrganizationLink.wikipedia("https://en.wikipedia.org/wiki/Harvard"));

      assertThat(org.getWikipediaUrl()).contains("https://en.wikipedia.org/wiki/Harvard");
    }
  }

  @Nested
  @DisplayName("名称管理测试")
  class NamesTest {

    @Test
    @DisplayName("应添加名称")
    void shouldAddName() {
      OrganizationAggregate org = createTestOrganization();
      OrganizationName name =
          OrganizationName.create("哈佛大学", Set.of(OrganizationNameType.LABEL), "zh");

      org.addName(name);

      assertThat(org.getNames()).hasSize(1);
      assertThat(org.isDirty()).isTrue();
      assertThat(org.hasChildChanges()).isTrue();
    }

    @Test
    @DisplayName("应移除名称")
    void shouldRemoveName() {
      OrganizationAggregate org = createTestOrganization();
      OrganizationName name =
          OrganizationName.create("哈佛大学", Set.of(OrganizationNameType.LABEL), "zh");
      org.addName(name);
      org.pullChildChanges(); // 清空变更记录

      org.removeName(name);

      assertThat(org.getNames()).isEmpty();
      assertThat(org.hasChildChanges()).isTrue();
    }

    @Test
    @DisplayName("重复添加相同名称应忽略")
    void shouldIgnoreDuplicateName() {
      OrganizationAggregate org = createTestOrganization();
      OrganizationName name1 =
          OrganizationName.create("哈佛大学", Set.of(OrganizationNameType.LABEL), "zh");
      OrganizationName name2 =
          OrganizationName.create(
              "哈佛大学", Set.of(OrganizationNameType.ALIAS), "zh" // 相同 value+lang
              );

      org.addName(name1);
      org.addName(name2);

      assertThat(org.getNames()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("外部标识符测试")
  class ExternalIdsTest {

    @Test
    @DisplayName("应添加外部标识符")
    void shouldAddExternalId() {
      OrganizationAggregate org = createTestOrganization();
      ExternalId extId = ExternalId.create(ExternalIdType.ISNI, "0000 0001 2157 6568");

      org.addExternalId(extId);

      assertThat(org.getExternalIds()).hasSize(1);
      assertThat(org.isDirty()).isTrue();
    }

    @Test
    @DisplayName("相同类型的外部标识符应替换")
    void shouldReplaceExternalIdOfSameType() {
      OrganizationAggregate org = createTestOrganization();
      ExternalId extId1 = ExternalId.create(ExternalIdType.ISNI, "old-value");
      ExternalId extId2 = ExternalId.create(ExternalIdType.ISNI, "new-value");

      org.addExternalId(extId1);
      org.addExternalId(extId2);

      assertThat(org.getExternalIds()).hasSize(1);
      assertThat(org.getExternalIds().get(0).preferred()).isEqualTo("new-value");
    }

    @Test
    @DisplayName("getExternalId() 应按类型获取标识符")
    void shouldGetExternalIdByType() {
      OrganizationAggregate org = createTestOrganization();
      org.addExternalId(ExternalId.create(ExternalIdType.ISNI, "isni-value"));
      org.addExternalId(ExternalId.create(ExternalIdType.WIKIDATA, "Q219563"));

      assertThat(org.getExternalId(ExternalIdType.ISNI))
          .isPresent()
          .hasValueSatisfying(e -> assertThat(e.preferred()).isEqualTo("isni-value"));
      assertThat(org.getExternalId(ExternalIdType.GRID)).isEmpty();
    }

    @Test
    @DisplayName("应移除外部标识符")
    void shouldRemoveExternalId() {
      OrganizationAggregate org = createTestOrganization();
      org.addExternalId(ExternalId.create(ExternalIdType.ISNI, "isni-value"));
      org.addExternalId(ExternalId.create(ExternalIdType.WIKIDATA, "Q219563"));
      org.pullChildChanges(); // 清空变更记录

      boolean removed = org.removeExternalId(ExternalIdType.ISNI);

      assertThat(removed).isTrue();
      assertThat(org.getExternalIds()).hasSize(1);
      assertThat(org.getExternalId(ExternalIdType.ISNI)).isEmpty();
      assertThat(org.hasChildChanges()).isTrue();
    }

    @Test
    @DisplayName("移除不存在的外部标识符应返回 false")
    void shouldReturnFalseWhenRemovingNonExistentExternalId() {
      OrganizationAggregate org = createTestOrganization();

      boolean removed = org.removeExternalId(ExternalIdType.ISNI);

      assertThat(removed).isFalse();
      assertThat(org.hasChildChanges()).isFalse();
    }
  }

  @Nested
  @DisplayName("地理位置测试")
  class LocationsTest {

    @Test
    @DisplayName("应添加地理位置")
    void shouldAddLocation() {
      OrganizationAggregate org = createTestOrganization();
      GeoLocation location =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .cityName("Cambridge")
              .build();

      org.addLocation(location);

      assertThat(org.getLocations()).hasSize(1);
      assertThat(org.isDirty()).isTrue();
    }

    @Test
    @DisplayName("相同 GeoNames ID 的位置应忽略")
    void shouldIgnoreDuplicateLocation() {
      OrganizationAggregate org = createTestOrganization();
      GeoLocation loc1 =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();
      GeoLocation loc2 =
          GeoLocation.builder()
              .geonamesId(4931972) // 相同 GeoNames ID
              .countryCode("US")
              .countryName("USA")
              .build();

      org.addLocation(loc1);
      org.addLocation(loc2);

      assertThat(org.getLocations()).hasSize(1);
    }

    @Test
    @DisplayName("应移除地理位置")
    void shouldRemoveLocation() {
      OrganizationAggregate org = createTestOrganization();
      GeoLocation location =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();
      org.addLocation(location);
      org.pullChildChanges(); // 清空变更记录

      boolean removed = org.removeLocation(location);

      assertThat(removed).isTrue();
      assertThat(org.getLocations()).isEmpty();
      assertThat(org.hasChildChanges()).isTrue();
    }

    @Test
    @DisplayName("移除不存在的地理位置应返回 false")
    void shouldReturnFalseWhenRemovingNonExistentLocation() {
      OrganizationAggregate org = createTestOrganization();
      GeoLocation location =
          GeoLocation.builder()
              .geonamesId(4931972)
              .countryCode("US")
              .countryName("United States")
              .build();

      boolean removed = org.removeLocation(location);

      assertThat(removed).isFalse();
      assertThat(org.hasChildChanges()).isFalse();
    }
  }

  @Nested
  @DisplayName("机构关系测试")
  class RelationsTest {

    @Test
    @DisplayName("应添加机构关系")
    void shouldAddRelation() {
      OrganizationAggregate org = createTestOrganization();
      OrganizationRelation relation =
          OrganizationRelation.create(
              OrganizationRelationType.PARENT,
              RorId.of("https://ror.org/0abcdefgh"),
              "Parent University");

      org.addRelation(relation);

      assertThat(org.getRelations()).hasSize(1);
      assertThat(org.isDirty()).isTrue();
    }

    @Test
    @DisplayName("相同类型和 ROR ID 的关系应忽略")
    void shouldIgnoreDuplicateRelation() {
      OrganizationAggregate org = createTestOrganization();
      RorId relatedRorId = RorId.of("https://ror.org/0abcdefgh");
      OrganizationRelation rel1 =
          OrganizationRelation.create(OrganizationRelationType.PARENT, relatedRorId, "Label 1");
      OrganizationRelation rel2 =
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, relatedRorId, "Label 2" // 相同 type+rorId
              );

      org.addRelation(rel1);
      org.addRelation(rel2);

      assertThat(org.getRelations()).hasSize(1);
    }

    @Test
    @DisplayName("getParents() 应返回所有父级关系")
    void shouldGetParents() {
      OrganizationAggregate org = createTestOrganization();
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, RorId.of("https://ror.org/0parent01"), "Parent 1"));
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.CHILD, RorId.of("https://ror.org/0child001"), "Child 1"));

      assertThat(org.getParents()).hasSize(1);
      assertThat(org.getParents().get(0).relatedLabel()).isEqualTo("Parent 1");
    }

    @Test
    @DisplayName("getChildren() 应返回所有子级关系")
    void shouldGetChildren() {
      OrganizationAggregate org = createTestOrganization();
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, RorId.of("https://ror.org/0parent01"), "Parent 1"));
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.CHILD, RorId.of("https://ror.org/0child001"), "Child 1"));

      assertThat(org.getChildren()).hasSize(1);
      assertThat(org.getChildren().get(0).relatedLabel()).isEqualTo("Child 1");
    }

    @Test
    @DisplayName("应移除机构关系")
    void shouldRemoveRelation() {
      OrganizationAggregate org = createTestOrganization();
      OrganizationRelation relation =
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, RorId.of("https://ror.org/0parent01"), "Parent 1");
      org.addRelation(relation);
      org.pullChildChanges(); // 清空变更记录

      boolean removed = org.removeRelation(relation);

      assertThat(removed).isTrue();
      assertThat(org.getRelations()).isEmpty();
      assertThat(org.hasChildChanges()).isTrue();
    }

    @Test
    @DisplayName("移除不存在的机构关系应返回 false")
    void shouldReturnFalseWhenRemovingNonExistentRelation() {
      OrganizationAggregate org = createTestOrganization();
      OrganizationRelation relation =
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, RorId.of("https://ror.org/0parent01"), "Parent 1");

      boolean removed = org.removeRelation(relation);

      assertThat(removed).isFalse();
      assertThat(org.hasChildChanges()).isFalse();
    }

    @Test
    @DisplayName("getRelatedOrganizations() 应返回所有 RELATED 类型关系")
    void shouldGetRelatedOrganizations() {
      OrganizationAggregate org = createTestOrganization();
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, RorId.of("https://ror.org/0parent01"), "Parent 1"));
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.RELATED,
              RorId.of("https://ror.org/0related1"),
              "Related 1"));

      assertThat(org.getRelatedOrganizations()).hasSize(1);
      assertThat(org.getRelatedOrganizations().get(0).relatedLabel()).isEqualTo("Related 1");
    }

    @Test
    @DisplayName("getSuccessors() 应返回所有后继机构")
    void shouldGetSuccessors() {
      OrganizationAggregate org = createTestOrganization();
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.SUCCESSOR,
              RorId.of("https://ror.org/0succes01"),
              "Successor 1"));
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.PREDECESSOR,
              RorId.of("https://ror.org/0predec01"),
              "Predecessor 1"));

      assertThat(org.getSuccessors()).hasSize(1);
      assertThat(org.getSuccessors().get(0).relatedLabel()).isEqualTo("Successor 1");
    }

    @Test
    @DisplayName("getPredecessors() 应返回所有前任机构")
    void shouldGetPredecessors() {
      OrganizationAggregate org = createTestOrganization();
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.SUCCESSOR,
              RorId.of("https://ror.org/0succes01"),
              "Successor 1"));
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.PREDECESSOR,
              RorId.of("https://ror.org/0predec01"),
              "Predecessor 1"));

      assertThat(org.getPredecessors()).hasSize(1);
      assertThat(org.getPredecessors().get(0).relatedLabel()).isEqualTo("Predecessor 1");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("isActive() 应正确判断活跃状态")
    void shouldCheckIsActive() {
      OrganizationAggregate active =
          OrganizationAggregate.fromRor(
              RorId.of("https://ror.org/03vek6s52"), "Org", OrganizationStatus.ACTIVE);
      OrganizationAggregate inactive =
          OrganizationAggregate.fromRor(
              RorId.of("https://ror.org/0abcdefgh"), "Org", OrganizationStatus.INACTIVE);

      assertThat(active.isActive()).isTrue();
      assertThat(inactive.isActive()).isFalse();
    }

    @Test
    @DisplayName("isEducation() 应正确判断教育机构")
    void shouldCheckIsEducation() {
      OrganizationAggregate org = createTestOrganization();
      org.addType(OrganizationType.EDUCATION);

      assertThat(org.isEducation()).isTrue();
    }

    @Test
    @DisplayName("hasParent() 应正确判断是否有父级")
    void shouldCheckHasParent() {
      OrganizationAggregate org = createTestOrganization();
      assertThat(org.hasParent()).isFalse();

      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.PARENT, RorId.of("https://ror.org/0parent01"), "Parent"));
      assertThat(org.hasParent()).isTrue();
    }

    @Test
    @DisplayName("hasChildren() 应正确判断是否有子级")
    void shouldCheckHasChildren() {
      OrganizationAggregate org = createTestOrganization();
      assertThat(org.hasChildren()).isFalse();

      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.CHILD, RorId.of("https://ror.org/0child001"), "Child"));
      assertThat(org.hasChildren()).isTrue();
    }

    @Test
    @DisplayName("isInactive() 应正确判断非活跃状态")
    void shouldCheckIsInactive() {
      OrganizationAggregate inactive =
          OrganizationAggregate.fromRor(
              RorId.of("https://ror.org/0abcdefgh"), "Org", OrganizationStatus.INACTIVE);
      OrganizationAggregate active =
          OrganizationAggregate.fromRor(
              RorId.of("https://ror.org/03vek6s52"), "Org", OrganizationStatus.ACTIVE);

      assertThat(inactive.isInactive()).isTrue();
      assertThat(active.isInactive()).isFalse();
    }

    @Test
    @DisplayName("isWithdrawn() 应正确判断撤销状态")
    void shouldCheckIsWithdrawn() {
      OrganizationAggregate withdrawn =
          OrganizationAggregate.fromRor(
              RorId.of("https://ror.org/0abcdefgh"), "Org", OrganizationStatus.WITHDRAWN);
      OrganizationAggregate active =
          OrganizationAggregate.fromRor(
              RorId.of("https://ror.org/03vek6s52"), "Org", OrganizationStatus.ACTIVE);

      assertThat(withdrawn.isWithdrawn()).isTrue();
      assertThat(active.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("isCompany() 应正确判断企业类型")
    void shouldCheckIsCompany() {
      OrganizationAggregate org = createTestOrganization();
      assertThat(org.isCompany()).isFalse();

      org.addType(OrganizationType.COMPANY);
      assertThat(org.isCompany()).isTrue();
    }

    @Test
    @DisplayName("isHealthcare() 应正确判断医疗机构类型")
    void shouldCheckIsHealthcare() {
      OrganizationAggregate org = createTestOrganization();
      assertThat(org.isHealthcare()).isFalse();

      org.addType(OrganizationType.HEALTHCARE);
      assertThat(org.isHealthcare()).isTrue();
    }

    @Test
    @DisplayName("isFunder() 应正确判断资助机构类型")
    void shouldCheckIsFunder() {
      OrganizationAggregate org = createTestOrganization();
      assertThat(org.isFunder()).isFalse();

      org.addType(OrganizationType.FUNDER);
      assertThat(org.isFunder()).isTrue();
    }
  }

  @Nested
  @DisplayName("批量操作测试")
  class BatchOperationsTest {

    @Test
    @DisplayName("withNames() 应替换所有名称并跟踪变更")
    void shouldReplaceAllNamesWithTracking() {
      OrganizationAggregate org = createTestOrganization();
      OrganizationName oldName =
          OrganizationName.create("旧名称", Set.of(OrganizationNameType.LABEL), "zh");
      org.addName(oldName);
      org.pullChildChanges(); // 清空变更记录

      OrganizationName newName1 =
          OrganizationName.create("新名称1", Set.of(OrganizationNameType.LABEL), "zh");
      OrganizationName newName2 =
          OrganizationName.create("新名称2", Set.of(OrganizationNameType.ALIAS), "en");

      org.withNames(List.of(newName1, newName2));

      assertThat(org.getNames()).hasSize(2);
      assertThat(org.hasChildChanges()).isTrue();
      // 应该有 1 个删除 + 2 个添加 = 3 个变更
      assertThat(org.pullChildChanges()).hasSize(3);
    }

    @Test
    @DisplayName("withNames(null) 应清空所有名称")
    void shouldClearAllNamesWhenNull() {
      OrganizationAggregate org = createTestOrganization();
      org.addName(OrganizationName.create("名称", Set.of(OrganizationNameType.LABEL), "zh"));
      org.pullChildChanges();

      org.withNames(null);

      assertThat(org.getNames()).isEmpty();
      assertThat(org.hasChildChanges()).isTrue();
    }

    @Test
    @DisplayName("withExternalIds() 应替换所有外部标识符并跟踪变更")
    void shouldReplaceAllExternalIdsWithTracking() {
      OrganizationAggregate org = createTestOrganization();
      org.addExternalId(ExternalId.create(ExternalIdType.ISNI, "old-isni"));
      org.pullChildChanges();

      ExternalId newExtId = ExternalId.create(ExternalIdType.WIKIDATA, "Q123");
      org.withExternalIds(List.of(newExtId));

      assertThat(org.getExternalIds()).hasSize(1);
      assertThat(org.getExternalId(ExternalIdType.WIKIDATA)).isPresent();
      assertThat(org.hasChildChanges()).isTrue();
    }

    @Test
    @DisplayName("withLocations() 应替换所有地理位置并跟踪变更")
    void shouldReplaceAllLocationsWithTracking() {
      OrganizationAggregate org = createTestOrganization();
      org.addLocation(
          GeoLocation.builder().geonamesId(1).countryCode("CN").countryName("China").build());
      org.pullChildChanges();

      GeoLocation newLocation =
          GeoLocation.builder()
              .geonamesId(2)
              .countryCode("US")
              .countryName("United States")
              .build();
      org.withLocations(List.of(newLocation));

      assertThat(org.getLocations()).hasSize(1);
      assertThat(org.getLocations().get(0).countryCode()).isEqualTo("US");
      assertThat(org.hasChildChanges()).isTrue();
    }

    @Test
    @DisplayName("withRelations() 应替换所有机构关系并跟踪变更")
    void shouldReplaceAllRelationsWithTracking() {
      OrganizationAggregate org = createTestOrganization();
      org.addRelation(
          OrganizationRelation.create(
              OrganizationRelationType.PARENT,
              RorId.of("https://ror.org/0oldpare1"),
              "Old Parent"));
      org.pullChildChanges();

      OrganizationRelation newRelation =
          OrganizationRelation.create(
              OrganizationRelationType.CHILD, RorId.of("https://ror.org/0newchil1"), "New Child");
      org.withRelations(List.of(newRelation));

      assertThat(org.getRelations()).hasSize(1);
      assertThat(org.getChildren()).hasSize(1);
      assertThat(org.getParents()).isEmpty();
      assertThat(org.hasChildChanges()).isTrue();
    }
  }

  // ========== 辅助方法 ==========

  private OrganizationAggregate createTestOrganization() {
    return OrganizationAggregate.fromRor(
        RorId.of("https://ror.org/03vek6s52"), "Harvard University", OrganizationStatus.ACTIVE);
  }
}
