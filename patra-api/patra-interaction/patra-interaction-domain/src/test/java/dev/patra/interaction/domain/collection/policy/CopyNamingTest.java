package dev.patra.interaction.domain.collection.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import java.util.Set;
import org.junit.jupiter.api.Test;

/// 复制收藏夹时的命名：重名加最小的空序号，超长时截短原名。
class CopyNamingTest {

  /// 不重名就用原名。
  @Test
  void should_keep_name_when_free() {
    assertThat(nameFor("组会", Set.of("别的"))).isEqualTo("组会");
  }

  /// 重名时取最小的空号。
  @Test
  void should_append_smallest_free_number() {
    assertThat(nameFor("组会", Set.of("组会", "组会 (2)", "组会 (4)"))).isEqualTo("组会 (3)");
  }

  /// 原名已满 100 字素时截短再加后缀。
  @Test
  void should_truncate_base_to_fit_suffix() {
    String base = "字".repeat(100);
    assertThat(nameFor(base, Set.of(base))).isEqualTo("字".repeat(96) + " (2)");
  }

  /// 测试用包装。
  ///
  /// @param source 原名
  /// @param taken 已占用的名称
  /// @return 结果名称
  private static String nameFor(String source, Set<String> taken) {
    return CopyNaming.nameFor(CollectionName.parse(source), taken).value();
  }
}
