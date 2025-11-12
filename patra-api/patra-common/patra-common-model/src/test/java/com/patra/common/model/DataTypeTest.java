package com.patra.common.model;

import com.patra.common.model.CanonicalLiterature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * DataType 枚举测试
 *
 * <p>测试策略：
 * <ul>
 *   <li>枚举基本属性测试 - 验证所有字段非空且正确</li>
 *   <li>查找方法测试 - 验证通过code和class查找的正确性</li>
 *   <li>分类方法测试 - 验证类型分类逻辑</li>
 *   <li>分组方法测试 - 验证类型分组功能</li>
 *   <li>边界情况测试 - null、空字符串、大小写等</li>
 * </ul>
 *
 * @since 2.0.0
 */
@DisplayName("DataType 枚举测试")
class DataTypeTest {

    // ========== 枚举基本属性测试 ==========

    @Nested
    @DisplayName("枚举基本属性")
    class EnumBasicPropertiesTest {

        @Test
        @DisplayName("所有枚举常量的code字段不为空")
        void allEnumConstantsHaveNonNullCode() {
            for (DataType dataType : DataType.values()) {
                assertThat(dataType.getCode())
                    .as("DataType.%s 的 code 不应为 null", dataType)
                    .isNotNull()
                    .isNotBlank();
            }
        }

        @Test
        @DisplayName("所有枚举常量的description字段不为空")
        void allEnumConstantsHaveNonNullDescription() {
            for (DataType dataType : DataType.values()) {
                assertThat(dataType.getDescription())
                    .as("DataType.%s 的 description 不应为 null", dataType)
                    .isNotNull()
                    .isNotBlank();
            }
        }

        @Test
        @DisplayName("所有枚举常量的dataClass字段不为空")
        void allEnumConstantsHaveNonNullDataClass() {
            for (DataType dataType : DataType.values()) {
                assertThat(dataType.getDataClass())
                    .as("DataType.%s 的 dataClass 不应为 null", dataType)
                    .isNotNull();
            }
        }

        @Test
        @DisplayName("LITERATURE的属性值正确")
        void literatureHasCorrectProperties() {
            DataType literature = DataType.LITERATURE;
            assertThat(literature.getCode()).isEqualTo("literature");
            assertThat(literature.getDescription()).isEqualTo("文献数据");
            assertThat(literature.getDataClass()).isEqualTo(CanonicalLiterature.class);
        }

        @Test
        @DisplayName("所有枚举常量的code唯一")
        void allEnumConstantsHaveUniqueCode() {
            long uniqueCodeCount = java.util.Arrays.stream(DataType.values())
                .map(DataType::getCode)
                .distinct()
                .count();

            assertThat(uniqueCodeCount)
                .as("所有 DataType 的 code 应该唯一")
                .isEqualTo(DataType.values().length);
        }

        @Test
        @DisplayName("所有枚举常量的dataClass唯一")
        void allEnumConstantsHaveUniqueDataClass() {
            long uniqueClassCount = java.util.Arrays.stream(DataType.values())
                .map(DataType::getDataClass)
                .distinct()
                .count();

            assertThat(uniqueClassCount)
                .as("所有 DataType 的 dataClass 应该唯一")
                .isEqualTo(DataType.values().length);
        }
    }

    // ========== 查找方法测试 ==========

    @Nested
    @DisplayName("查找方法")
    class LookupMethodsTest {

        @Nested
        @DisplayName("fromCode(String)")
        class FromCodeTest {

            @Test
            @DisplayName("通过有效code查找成功")
            void shouldFindByValidCode() {
                DataType result = DataType.fromCode("literature");
                assertThat(result).isEqualTo(DataType.LITERATURE);
            }

            @Test
            @DisplayName("code不区分大小写")
            void shouldBeCaseInsensitive() {
                assertThat(DataType.fromCode("LITERATURE")).isEqualTo(DataType.LITERATURE);
                assertThat(DataType.fromCode("Literature")).isEqualTo(DataType.LITERATURE);
                assertThat(DataType.fromCode("literature")).isEqualTo(DataType.LITERATURE);
            }

            @Test
            @DisplayName("code不存在时抛出IllegalArgumentException")
            void shouldThrowExceptionWhenCodeNotFound() {
                assertThatThrownBy(() -> DataType.fromCode("unknown_type"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown DataType code: unknown_type");
            }

            @Test
            @DisplayName("code为null时抛出异常")
            void shouldThrowExceptionWhenCodeIsNull() {
                assertThatThrownBy(() -> DataType.fromCode(null))
                    .isInstanceOf(NullPointerException.class);
            }

            @Test
            @DisplayName("code为空字符串时抛出异常")
            void shouldThrowExceptionWhenCodeIsEmpty() {
                assertThatThrownBy(() -> DataType.fromCode(""))
                    .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        @DisplayName("findByCode(String)")
        class FindByCodeTest {

            @Test
            @DisplayName("通过有效code查找返回Optional.of")
            void shouldReturnOptionalOfWhenCodeExists() {
                Optional<DataType> result = DataType.findByCode("literature");
                assertThat(result)
                    .isPresent()
                    .contains(DataType.LITERATURE);
            }

            @Test
            @DisplayName("code不存在时返回Optional.empty")
            void shouldReturnEmptyWhenCodeNotFound() {
                Optional<DataType> result = DataType.findByCode("unknown_type");
                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("code不区分大小写")
            void shouldBeCaseInsensitive() {
                assertThat(DataType.findByCode("LITERATURE")).contains(DataType.LITERATURE);
                assertThat(DataType.findByCode("Literature")).contains(DataType.LITERATURE);
            }

            @Test
            @DisplayName("code为null时返回Optional.empty")
            void shouldReturnEmptyWhenCodeIsNull() {
                Optional<DataType> result = DataType.findByCode(null);
                assertThat(result).isEmpty();
            }
        }

        @Nested
        @DisplayName("fromClass(Class<?>)")
        class FromClassTest {

            @Test
            @DisplayName("通过有效Class查找成功")
            void shouldFindByValidClass() {
                DataType result = DataType.fromClass(CanonicalLiterature.class);
                assertThat(result).isEqualTo(DataType.LITERATURE);
            }

            @Test
            @DisplayName("Class不存在时抛出IllegalArgumentException")
            void shouldThrowExceptionWhenClassNotFound() {
                assertThatThrownBy(() -> DataType.fromClass(String.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown DataType for class: java.lang.String");
            }

            @Test
            @DisplayName("Class为null时抛出异常")
            void shouldThrowExceptionWhenClassIsNull() {
                assertThatThrownBy(() -> DataType.fromClass(null))
                    .isInstanceOf(NullPointerException.class);
            }
        }

        @Nested
        @DisplayName("findByClass(Class<?>)")
        class FindByClassTest {

            @Test
            @DisplayName("通过有效Class查找返回Optional.of")
            void shouldReturnOptionalOfWhenClassExists() {
                Optional<DataType> result = DataType.findByClass(CanonicalLiterature.class);
                assertThat(result)
                    .isPresent()
                    .contains(DataType.LITERATURE);
            }

            @Test
            @DisplayName("Class不存在时返回Optional.empty")
            void shouldReturnEmptyWhenClassNotFound() {
                Optional<DataType> result = DataType.findByClass(String.class);
                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("Class为null时返回Optional.empty")
            void shouldReturnEmptyWhenClassIsNull() {
                Optional<DataType> result = DataType.findByClass(null);
                assertThat(result).isEmpty();
            }
        }
    }

    // ========== 分类方法测试 ==========

    @Nested
    @DisplayName("分类方法")
    class ClassificationMethodsTest {

        @Nested
        @DisplayName("isRelational()")
        class IsRelationalTest {

            @Test
            @DisplayName("CITATION是关系型数据")
            void citationIsRelational() {
                assertThat(DataType.CITATION.isRelational()).isTrue();
            }

            @Test
            @DisplayName("REFERENCE是关系型数据")
            void referenceIsRelational() {
                assertThat(DataType.REFERENCE.isRelational()).isTrue();
            }

            @Test
            @DisplayName("DRUG_INTERACTION是关系型数据")
            void drugInteractionIsRelational() {
                assertThat(DataType.DRUG_INTERACTION.isRelational()).isTrue();
            }

            @Test
            @DisplayName("LITERATURE_FULLTEXT是关系型数据")
            void literatureFulltextIsRelational() {
                assertThat(DataType.LITERATURE_FULLTEXT.isRelational()).isTrue();
            }

            @Test
            @DisplayName("LITERATURE不是关系型数据")
            void literatureIsNotRelational() {
                assertThat(DataType.LITERATURE.isRelational()).isFalse();
            }

            @Test
            @DisplayName("JOURNAL不是关系型数据")
            void journalIsNotRelational() {
                assertThat(DataType.JOURNAL.isRelational()).isFalse();
            }

            @Test
            @DisplayName("DRUG不是关系型数据")
            void drugIsNotRelational() {
                assertThat(DataType.DRUG.isRelational()).isFalse();
            }
        }

        @Nested
        @DisplayName("isCoreEntity()")
        class IsCoreEntityTest {

            @Test
            @DisplayName("LITERATURE是核心实体")
            void literatureIsCoreEntity() {
                assertThat(DataType.LITERATURE.isCoreEntity()).isTrue();
            }

            @Test
            @DisplayName("JOURNAL是核心实体")
            void journalIsCoreEntity() {
                assertThat(DataType.JOURNAL.isCoreEntity()).isTrue();
            }

            @Test
            @DisplayName("DRUG是核心实体")
            void drugIsCoreEntity() {
                assertThat(DataType.DRUG.isCoreEntity()).isTrue();
            }

            @Test
            @DisplayName("AUTHOR是核心实体")
            void authorIsCoreEntity() {
                assertThat(DataType.AUTHOR.isCoreEntity()).isTrue();
            }

            @Test
            @DisplayName("CITATION不是核心实体")
            void citationIsNotCoreEntity() {
                assertThat(DataType.CITATION.isCoreEntity()).isFalse();
            }

            @Test
            @DisplayName("REFERENCE不是核心实体")
            void referenceIsNotCoreEntity() {
                assertThat(DataType.REFERENCE.isCoreEntity()).isFalse();
            }

            @Test
            @DisplayName("LITERATURE_FULLTEXT不是核心实体")
            void literatureFulltextIsNotCoreEntity() {
                assertThat(DataType.LITERATURE_FULLTEXT.isCoreEntity()).isFalse();
            }
        }

        @Nested
        @DisplayName("isAssignableFrom(Class<?>)")
        class IsAssignableFromTest {

            @Test
            @DisplayName("相同Class可赋值")
            void shouldBeAssignableFromSameClass() {
                assertThat(DataType.LITERATURE.isAssignableFrom(CanonicalLiterature.class)).isTrue();
            }

            @Test
            @DisplayName("不同Class不可赋值")
            void shouldNotBeAssignableFromDifferentClass() {
                assertThat(DataType.LITERATURE.isAssignableFrom(String.class)).isFalse();
            }

            @Test
            @DisplayName("null参数返回false")
            void shouldReturnFalseForNullClass() {
                assertThat(DataType.LITERATURE.isAssignableFrom(null)).isFalse();
            }
        }
    }

    // ========== 分组方法测试 ==========

    @Nested
    @DisplayName("分组方法")
    class GroupingMethodsTest {

        @Test
        @DisplayName("literatureTypes()返回所有文献相关类型")
        void literatureTypesShouldReturnAllLiteratureTypes() {
            Set<DataType> literatureTypes = DataType.literatureTypes();

            assertThat(literatureTypes)
                .hasSize(3)
                .contains(
                    DataType.LITERATURE,
                    DataType.LITERATURE_FULLTEXT,
                    DataType.REFERENCE
                );
        }

        @Test
        @DisplayName("journalTypes()返回所有期刊相关类型")
        void journalTypesShouldReturnAllJournalTypes() {
            Set<DataType> journalTypes = DataType.journalTypes();

            assertThat(journalTypes)
                .hasSize(2)
                .contains(
                    DataType.JOURNAL,
                    DataType.JOURNAL_METRICS
                );
        }

        @Test
        @DisplayName("drugTypes()返回所有药品相关类型")
        void drugTypesShouldReturnAllDrugTypes() {
            Set<DataType> drugTypes = DataType.drugTypes();

            assertThat(drugTypes)
                .hasSize(2)
                .contains(
                    DataType.DRUG,
                    DataType.DRUG_INTERACTION
                );
        }

        @Test
        @DisplayName("分组方法返回的Set不可变")
        void groupingMethodsShouldReturnImmutableSets() {
            Set<DataType> literatureTypes = DataType.literatureTypes();

            assertThatThrownBy(() -> literatureTypes.add(DataType.JOURNAL))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ========== 边界情况测试 ==========

    @Nested
    @DisplayName("边界情况")
    class EdgeCasesTest {

        @Test
        @DisplayName("枚举常量总数为10")
        void shouldHaveExactlyTenConstants() {
            assertThat(DataType.values()).hasSize(10);
        }

        @Test
        @DisplayName("valueOf()能正确返回枚举常量")
        void valueOfShouldWork() {
            assertThat(DataType.valueOf("LITERATURE")).isEqualTo(DataType.LITERATURE);
            assertThat(DataType.valueOf("JOURNAL")).isEqualTo(DataType.JOURNAL);
            assertThat(DataType.valueOf("DRUG")).isEqualTo(DataType.DRUG);
        }

        @Test
        @DisplayName("valueOf()对无效名称抛出IllegalArgumentException")
        void valueOfShouldThrowExceptionForInvalidName() {
            assertThatThrownBy(() -> DataType.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("toString()返回枚举常量名称")
        void toStringShouldReturnEnumName() {
            assertThat(DataType.LITERATURE.toString()).isEqualTo("LITERATURE");
        }
    }

    // ========== 预定义数据类型完整性测试 ==========

    @Nested
    @DisplayName("预定义数据类型完整性")
    class PredefinedTypesTest {

        @Test
        @DisplayName("包含所有10种预定义数据类型")
        void shouldContainAllPredefinedTypes() {
            assertThat(DataType.values())
                .extracting(DataType::getCode)
                .containsExactlyInAnyOrder(
                    "literature",
                    "literature_fulltext",
                    "journal",
                    "journal_metrics",
                    "citation",
                    "reference",
                    "drug",
                    "drug_interaction",
                    "author",
                    "affiliation"
                );
        }
    }
}
