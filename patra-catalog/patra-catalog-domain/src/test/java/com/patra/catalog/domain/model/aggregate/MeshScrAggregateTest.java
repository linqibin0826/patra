package com.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.enums.ScrClass;
import com.patra.catalog.domain.model.vo.mesh.HeadingMappedTo;
import com.patra.catalog.domain.model.vo.mesh.IndexingInfo;
import com.patra.catalog.domain.model.vo.mesh.MeshScrId;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.domain.model.vo.mesh.PharmacologicalAction;
import com.patra.catalog.domain.model.vo.mesh.ScrSource;
import com.patra.common.domain.ChildEntityChange;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// MeshScrAggregate 聚合根单元测试。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("MeshScrAggregate 聚合根测试")
class MeshScrAggregateTest {

  private static final MeshUI SCR_UI = MeshUI.of("C000001");
  private static final String SCR_NAME = "Calcimycin";

  private MeshScrAggregate scr;

  @BeforeEach
  void setUp() {
    scr = MeshScrAggregate.create(SCR_UI, SCR_NAME);
  }

  @Nested
  @DisplayName("创建测试")
  class CreationTests {

    @Test
    @DisplayName("应该成功创建 SCR 聚合根")
    void shouldCreateScr() {
      assertThat(scr.getUi()).isEqualTo(SCR_UI);
      assertThat(scr.getName()).isEqualTo(SCR_NAME);
      assertThat(scr.getScrClass()).isEqualTo(ScrClass.CHEMICAL); // 默认值
      assertThat(scr.isActive()).isTrue();
    }

    @Test
    @DisplayName("应该成功创建指定类别的 SCR")
    void shouldCreateScrWithClass() {
      // when
      MeshScrAggregate diseaseScr = MeshScrAggregate.create(SCR_UI, SCR_NAME, ScrClass.DISEASE);

      // then
      assertThat(diseaseScr.getScrClass()).isEqualTo(ScrClass.DISEASE);
    }

    @Test
    @DisplayName("UI 为 null 应该抛出异常")
    void shouldRejectNullUi() {
      assertThatThrownBy(() -> MeshScrAggregate.create(null, SCR_NAME))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("UI不能为空");
    }

    @Test
    @DisplayName("名称为空应该抛出异常")
    void shouldRejectBlankName() {
      assertThatThrownBy(() -> MeshScrAggregate.create(SCR_UI, "   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("名称不能为空");
    }

    @Test
    @DisplayName("UI 必须以 C 开头")
    void shouldValidateUiPrefix() {
      MeshUI descriptorUi = MeshUI.of("D000001");

      assertThatThrownBy(() -> MeshScrAggregate.create(descriptorUi, SCR_NAME))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须以C开头");
    }
  }

  @Nested
  @DisplayName("HeadingMappedTo 管理测试")
  class HeadingMappedToTests {

    @Test
    @DisplayName("应该正确添加映射关系")
    void shouldAddHeadingMappedTo() {
      // given
      HeadingMappedTo mapping = HeadingMappedTo.of(MeshUI.of("D000001"));

      // when
      scr.addHeadingMappedTo(mapping);

      // then
      assertThat(scr.getHeadingMappedTos()).hasSize(1);
      assertThat(scr.getHeadingMappedTos().get(0)).isEqualTo(mapping);
    }

    @Test
    @DisplayName("应该去重相同的映射关系")
    void shouldDeduplicateMappings() {
      // given
      HeadingMappedTo mapping1 = HeadingMappedTo.of(MeshUI.of("D000001"));
      HeadingMappedTo mapping2 = HeadingMappedTo.of(MeshUI.of("D000001"));

      // when
      scr.addHeadingMappedTo(mapping1);
      scr.addHeadingMappedTo(mapping2);

      // then
      assertThat(scr.getHeadingMappedTos()).hasSize(1);
    }

    @Test
    @DisplayName("批量添加应该工作")
    void shouldAddMultipleMappings() {
      // given
      List<HeadingMappedTo> mappings =
          List.of(
              HeadingMappedTo.of(MeshUI.of("D000001")), HeadingMappedTo.of(MeshUI.of("D000002")));

      // when
      scr.addHeadingMappedTos(mappings);

      // then
      assertThat(scr.getHeadingMappedTos()).hasSize(2);
    }

    @Test
    @DisplayName("返回的列表应该是不可变的")
    void shouldReturnUnmodifiableList() {
      scr.addHeadingMappedTo(HeadingMappedTo.of(MeshUI.of("D000001")));

      assertThatThrownBy(() -> scr.getHeadingMappedTos().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("Concept 管理测试")
  class ConceptTests {

    @Test
    @DisplayName("应该正确添加概念")
    void shouldAddConcept() {
      // given
      MeshConcept concept = MeshConcept.create(MeshUI.of("M0000001"), "Test Concept", true);

      // when
      scr.addConcept(concept);

      // then
      assertThat(scr.getConcepts()).hasSize(1);
    }

    @Test
    @DisplayName("应该正确获取首选概念")
    void shouldGetPreferredConcept() {
      // given
      MeshConcept preferredConcept = MeshConcept.create(MeshUI.of("M0000001"), "Preferred", true);
      MeshConcept otherConcept = MeshConcept.create(MeshUI.of("M0000002"), "Other", false);

      // when
      scr.addConcept(preferredConcept);
      scr.addConcept(otherConcept);

      // then
      assertThat(scr.getPreferredConcept()).isPresent();
      assertThat(scr.getPreferredConcept().get().getConceptName()).isEqualTo("Preferred");
    }
  }

  @Nested
  @DisplayName("Source 管理测试")
  class SourceTests {

    @Test
    @DisplayName("应该正确添加来源")
    void shouldAddSource() {
      // given
      ScrSource source = ScrSource.of("NCI2004_11_17");

      // when
      scr.addSource(source);

      // then
      assertThat(scr.getSources()).hasSize(1);
    }

    @Test
    @DisplayName("批量添加来源应该工作")
    void shouldAddMultipleSources() {
      // when
      scr.addSources(List.of(ScrSource.of("NCI"), ScrSource.of("FDA")));

      // then
      assertThat(scr.getSources()).hasSize(2);
    }
  }

  @Nested
  @DisplayName("IndexingInfo 管理测试")
  class IndexingInfoTests {

    @Test
    @DisplayName("应该正确添加索引信息")
    void shouldAddIndexingInfo() {
      // given
      IndexingInfo info = IndexingInfo.ofDescriptor(MeshUI.of("D000001"));

      // when
      scr.addIndexingInfo(info);

      // then
      assertThat(scr.getIndexingInfos()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("PharmacologicalAction 管理测试")
  class PharmacologicalActionTests {

    @Test
    @DisplayName("应该正确添加药理作用")
    void shouldAddPharmacologicalAction() {
      // given
      PharmacologicalAction action =
          PharmacologicalAction.of(MeshUI.of("D000900"), "Anti-Bacterial Agents");

      // when
      scr.addPharmacologicalAction(action);

      // then
      assertThat(scr.getPharmacologicalActions()).hasSize(1);
      assertThat(scr.getPharmacologicalActions().get(0).descriptorUi().ui()).isEqualTo("D000900");
      assertThat(scr.getPharmacologicalActions().get(0).descriptorName())
          .isEqualTo("Anti-Bacterial Agents");
    }

    @Test
    @DisplayName("应该去重相同 UI 的药理作用")
    void shouldDeduplicateByUi() {
      // given
      PharmacologicalAction action1 =
          PharmacologicalAction.of(MeshUI.of("D000900"), "Anti-Bacterial Agents");
      PharmacologicalAction action2 =
          PharmacologicalAction.of(MeshUI.of("D000900"), "Different Name");

      // when
      scr.addPharmacologicalAction(action1);
      scr.addPharmacologicalAction(action2);

      // then - 基于 UI 去重，只保留第一个
      assertThat(scr.getPharmacologicalActions()).hasSize(1);
      assertThat(scr.getPharmacologicalActions().get(0).descriptorName())
          .isEqualTo("Anti-Bacterial Agents");
    }

    @Test
    @DisplayName("PharmacologicalAction 验证必须是 Descriptor UI")
    void shouldValidateDescriptorUi() {
      // PharmacologicalAction.of 已经验证了 UI 必须以 D 开头
      assertThatThrownBy(() -> PharmacologicalAction.of(MeshUI.of("C000001"), "Invalid"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("D开头");
    }
  }

  @Nested
  @DisplayName("状态管理测试")
  class StatusTests {

    @Test
    @DisplayName("应该正确设置 MeSH 版本")
    void shouldSetMeshVersion() {
      // when
      scr.withMeshVersion("2025");

      // then
      assertThat(scr.getMeshVersion()).isEqualTo("2025");
    }

    @Test
    @DisplayName("应该正确废弃 SCR")
    void shouldDeprecateScr() {
      // when
      scr.deprecate();

      // then
      assertThat(scr.isActive()).isFalse();
    }

    @Test
    @DisplayName("应该正确激活 SCR")
    void shouldActivateScr() {
      // given
      scr.deprecate();

      // when
      scr.activate();

      // then
      assertThat(scr.isActive()).isTrue();
    }

    @Test
    @DisplayName("应该正确设置日期")
    void shouldSetDates() {
      // given
      LocalDate created = LocalDate.of(2020, 1, 1);
      LocalDate revised = LocalDate.of(2025, 1, 1);

      // when
      scr.withDateCreated(created).withDateRevised(revised);

      // then
      assertThat(scr.getDateCreated()).isEqualTo(created);
      assertThat(scr.getDateRevised()).isEqualTo(revised);
    }
  }

  @Nested
  @DisplayName("restore 测试")
  class RestoreTests {

    @Test
    @DisplayName("应该正确从持久化状态恢复")
    void shouldRestoreFromPersistence() {
      // when
      MeshScrAggregate restored =
          MeshScrAggregate.restore(
              SCR_UI,
              SCR_NAME,
              ScrClass.DISEASE,
              "Test note",
              "High",
              null,
              LocalDate.of(2020, 1, 1),
              LocalDate.of(2025, 1, 1),
              true,
              "2025",
              null);

      // then
      assertThat(restored.getUi()).isEqualTo(SCR_UI);
      assertThat(restored.getName()).isEqualTo(SCR_NAME);
      assertThat(restored.getScrClass()).isEqualTo(ScrClass.DISEASE);
      assertThat(restored.getNote()).isEqualTo("Test note");
      assertThat(restored.getFrequency()).isEqualTo("High");
      assertThat(restored.isActive()).isTrue();
    }
  }

  @Nested
  @DisplayName("AggregateRoot 行为测试")
  class AggregateRootBehaviorTests {

    @Test
    @DisplayName("新创建的聚合根应该是瞬态的")
    void shouldBeTransientWhenNewlyCreated() {
      assertThat(scr.isTransient()).isTrue();
      assertThat(scr.getId()).isNull();
    }

    @Test
    @DisplayName("应该正确分配 ID")
    void shouldAssignId() {
      // given
      MeshScrId id = MeshScrId.of(12345L);

      // when
      scr.assignId(id);

      // then
      assertThat(scr.getId()).isEqualTo(id);
      assertThat(scr.isTransient()).isFalse();
    }

    @Test
    @DisplayName("应该正确分配版本号")
    void shouldAssignVersion() {
      // when
      scr.assignVersion(5L);

      // then
      assertThat(scr.getVersion()).isEqualTo(5L);
    }

    @Test
    @DisplayName("分配负版本号应该抛出异常")
    void shouldRejectNegativeVersion() {
      assertThatThrownBy(() -> scr.assignVersion(-1L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("版本必须 >= 0");
    }
  }

  @Nested
  @DisplayName("子实体变更追踪测试")
  class ChildEntityChangeTrackingTests {

    @Test
    @DisplayName("添加概念应该追踪变更")
    void shouldTrackConceptAddition() {
      // given
      MeshConcept concept = MeshConcept.create(MeshUI.of("M0000001"), "Test", true);

      // when
      scr.addConcept(concept);

      // then
      assertThat(scr.hasChildChanges()).isTrue();
      List<ChildEntityChange> changes = scr.pullChildChanges();
      assertThat(changes).hasSize(1);
      assertThat(changes.get(0)).isInstanceOf(ChildEntityChange.Added.class);
    }

    @Test
    @DisplayName("pullChildChanges 应该清空变更列表")
    void pullChildChangesShouldClearList() {
      // given
      MeshConcept concept = MeshConcept.create(MeshUI.of("M0000001"), "Test", true);
      scr.addConcept(concept);
      assertThat(scr.hasChildChanges()).isTrue();

      // when
      scr.pullChildChanges();

      // then
      assertThat(scr.hasChildChanges()).isFalse();
    }
  }

  @Nested
  @DisplayName("toString 测试")
  class ToStringTests {

    @Test
    @DisplayName("toString 应该包含关键信息")
    void shouldContainKeyInfo() {
      // given
      scr.assignId(MeshScrId.of(12345L));
      scr.assignVersion(3L);

      // when
      String result = scr.toString();

      // then
      assertThat(result).contains("id=12345");
      assertThat(result).contains("ui=C000001");
      assertThat(result).contains("name=Calcimycin");
      assertThat(result).contains("class=化学物质");
      assertThat(result).contains("active=true");
      assertThat(result).contains("version=3");
    }
  }
}
