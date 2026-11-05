package dev.patra.analytics.simulator.truth;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/// 真值文件 `truth.csv.gz`：表头 `day,article_id,viewers,fulltext_readers`，一天一天追加写。
public final class TruthFile {

  private static final String HEADER = "day,article_id,viewers,fulltext_readers";

  private TruthFile() {}

  /// 新建文件，准备追加。
  ///
  /// @param file 路径
  /// @return 追加器
  public static Appender open(Path file) {
    try {
      BufferedWriter writer =
          new BufferedWriter(
              new OutputStreamWriter(
                  new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8));
      writer.write(HEADER);
      writer.newLine();
      return new Appender(writer);
    } catch (IOException e) {
      throw new UncheckedIOException("建真值文件失败：" + file, e);
    }
  }

  /// 一天一天读（文件按日期升序写出），每读完一天回调一次。
  ///
  /// @param file 路径
  /// @param consumer 日期与当天的行
  public static void forEachDay(Path file, BiConsumer<LocalDate, List<TruthRow>> consumer) {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
      reader.readLine();
      LocalDate current = null;
      List<TruthRow> rows = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        TruthRow row = parse(line);
        if (current != null && !row.day().equals(current)) {
          consumer.accept(current, rows);
          rows = new ArrayList<>();
        }
        current = row.day();
        rows.add(row);
      }
      if (current != null) {
        consumer.accept(current, rows);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("读真值文件失败：" + file, e);
    }
  }

  /// 读全部真值行。
  ///
  /// @param file 路径
  /// @return 行
  public static List<TruthRow> read(Path file) {
    List<TruthRow> rows = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
      reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) {
        rows.add(parse(line));
      }
      return rows;
    } catch (IOException e) {
      throw new UncheckedIOException("读真值文件失败：" + file, e);
    }
  }

  /// 解析一行。
  ///
  /// @param line `day,article_id,viewers,fulltext_readers`
  /// @return 行
  private static TruthRow parse(String line) {
    String[] parts = line.split(",");
    return new TruthRow(
        LocalDate.parse(parts[0]),
        Long.parseLong(parts[1]),
        Integer.parseInt(parts[2]),
        Integer.parseInt(parts[3]));
  }

  /// 往真值文件里追加行。
  public static final class Appender implements AutoCloseable {

    private final BufferedWriter writer;

    private Appender(BufferedWriter writer) {
      this.writer = writer;
    }

    /// 追加。
    ///
    /// @param rows 行
    public void append(List<TruthRow> rows) {
      try {
        for (TruthRow row : rows) {
          writer.write(
              row.day()
                  + ","
                  + row.articleId()
                  + ","
                  + row.viewers()
                  + ","
                  + row.fulltextReaders());
          writer.newLine();
        }
      } catch (IOException e) {
        throw new UncheckedIOException("写真值文件失败", e);
      }
    }

    /// 写完并关闭。
    @Override
    public void close() {
      try {
        writer.close();
      } catch (IOException e) {
        throw new UncheckedIOException("关真值文件失败", e);
      }
    }
  }
}
