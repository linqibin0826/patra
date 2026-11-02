package dev.patra.analytics.simulator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.analytics.simulator.support.SmallArticlePoolFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArticlePoolTest {

  @TempDir Path temp;

  static Path smallPool() {
    return SmallArticlePoolFixture.path();
  }

  @Test
  void loads_pool_and_groups_topics_by_venue() {
    ArticlePool pool = ArticlePool.load(smallPool(), 200_000);

    assertThat(pool.size()).isEqualTo(3_000);
    assertThat(pool.topicCount()).isEqualTo(61);
    assertThat(pool.idAt(0)).isEqualTo(1_004_714L);
  }

  @Test
  void deduplicates_ids_and_caps_size() throws IOException {
    Path file = temp.resolve("dup.csv");
    Files.writeString(file, "5,1\n5,1\n6,2\n7,0\n");

    assertThat(ArticlePool.load(file, 200_000).size()).isEqualTo(3);
    assertThat(ArticlePool.load(smallPool(), 100).size()).isEqualTo(100);
  }

  @Test
  void rejects_empty_file() throws IOException {
    Path file = temp.resolve("empty.csv");
    Files.writeString(file, "");

    assertThatThrownBy(() -> ArticlePool.load(file, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("文献池为空");
  }

  @Test
  void rejects_bad_line_with_line_number() throws IOException {
    Path file = temp.resolve("bad.csv");
    Files.writeString(file, "5,1\n6,2\nabc,1\n");

    assertThatThrownBy(() -> ArticlePool.load(file, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("第 3 行");
  }

  @Test
  void rejects_non_positive_id() throws IOException {
    Path file = temp.resolve("zero.csv");
    Files.writeString(file, "0,1\n");

    assertThatThrownBy(() -> ArticlePool.load(file, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("第 1 行");
  }

  @Test
  void in_topic_sampling_stays_in_topic() {
    ArticlePool pool = ArticlePool.load(smallPool(), 200_000);
    SplittableRandom random = new SplittableRandom(3);
    int topic = pool.topicOf(0);

    for (int i = 0; i < 10_000; i++) {
      assertThat(pool.topicOf(pool.sampleInTopic(topic, random))).isEqualTo(topic);
    }
  }

  @Test
  void global_sampling_prefers_top_ranks() {
    ArticlePool pool = ArticlePool.load(smallPool(), 200_000);
    SplittableRandom random = new SplittableRandom(5);
    int first = 0;
    int last = 0;
    for (int i = 0; i < 100_000; i++) {
      int index = pool.sampleGlobal(random);
      if (index == 0) {
        first++;
      } else if (index == pool.size() - 1) {
        last++;
      }
    }

    assertThat(first).isGreaterThan(last * 100);
  }

  @Test
  void linear_walk_wraps_around() {
    ArticlePool pool = ArticlePool.load(smallPool(), 200_000);

    assertThat(pool.linear(2)).isEqualTo(2);
    assertThat(pool.linear(3_001)).isEqualTo(1);
  }
}
