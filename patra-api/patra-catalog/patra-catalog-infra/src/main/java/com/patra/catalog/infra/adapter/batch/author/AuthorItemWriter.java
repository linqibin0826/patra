package com.patra.catalog.infra.adapter.batch.author;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.domain.model.vo.author.Orcid;
import com.patra.catalog.domain.port.repository.AuthorRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/// PubMed Computed Authors 批量写入器。
///
/// **职责**：
///
/// - 将 AuthorAggregate 批量持久化
/// - **基于 ORCID 的去重与合并**：处理批次内和跨批次的 ORCID 重复
/// - 子实体数据（NameVariant、Orcid）由 Repository 统一处理
///
/// **ORCID 去重策略**：
///
/// PubMed 数据源中，同一个 ORCID 可能出现在多条记录中（消歧算法的误差）。
/// 由于 ORCID 是全球唯一的个人标识符，这些记录实际上是同一个人。
/// 本写入器实现两级去重：
///
/// **第一级：批次内合并**
/// 1. 遍历批次中的所有作者
/// 2. 对于有 ORCID 的作者，按**所有 ORCID** 聚合（不只是主要 ORCID）
/// 3. 如果发现 ORCID 重复，将后续记录的名字变体合并到第一个遇到的作者
///
/// **第二级：跨批次 UPSERT（合并而非跳过）**
/// 1. 批量查询数据库中已存在的 ORCID 对应的作者
/// 2. 如果已存在，将新记录的名字变体**合并**到已存在的作者，然后**更新**
/// 3. 如果不存在，**新增**该作者
/// 4. 确保跨批次重复时不会丢失名字变体信息
///
/// **示例**：
///
/// 批次 1（已写入）：
/// - 作者A：ORCID=0000-0002-0688-2193，name=SMITH+A，variants=[Smith,Anna,A]
///
/// 批次 2（待写入）：
/// - 作者B：ORCID=0000-0002-0688-2193，name=SMITH+B，variants=[Smith,Anne,A]
///
/// 处理结果：
/// - 作者A（更新）：variants=[Smith,Anna,A, Smith,Anne,A]  ← 合并名字变体
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorItemWriter implements ItemWriter<AuthorAggregate> {

  private final AuthorRepository authorRepository;

  @Override
  public void write(Chunk<? extends AuthorAggregate> chunk) throws Exception {
    List<? extends AuthorAggregate> items = chunk.getItems();
    if (items.isEmpty()) {
      return;
    }

    log.debug("开始处理 {} 条 Author 记录", items.size());

    // 第一级：批次内合并
    List<AuthorAggregate> mergedAuthors = mergeByOrcidWithinBatch(items);
    int mergedCount = items.size() - mergedAuthors.size();

    // 第二级：跨批次 UPSERT（合并而非跳过）
    UpsertResult upsertResult = mergeWithExistingAuthors(mergedAuthors);

    log.debug(
        "去重统计：原始={}，批次内合并={}，跨批次合并={}，新增={}",
        items.size(),
        mergedCount,
        upsertResult.updatedAuthors.size(),
        upsertResult.newAuthors.size());

    // 批量更新已存在的作者（合并了名字变体）
    if (!upsertResult.updatedAuthors.isEmpty()) {
      authorRepository.saveBatch(upsertResult.updatedAuthors);
      log.debug("更新完成：合并名字变体到 {} 个已存在的作者", upsertResult.updatedAuthors.size());
    }

    // 批量保存新作者
    if (!upsertResult.newAuthors.isEmpty()) {
      authorRepository.saveBatch(upsertResult.newAuthors);
      log.debug("写入完成：新增 {} 个作者", upsertResult.newAuthors.size());
    }
  }

  /// 第一级去重：批次内基于 ORCID 合并重复记录。
  ///
  /// **合并规则**：
  ///
  /// 1. 有 ORCID 的作者：按**所有 ORCID** 聚合（不只是主要 ORCID）
  /// 2. 如果作者的任一 ORCID 与已存在作者的任一 ORCID 匹配，则合并
  /// 3. 无 ORCID 的作者：直接保留，不参与合并
  /// 4. 保持原始顺序：使用 LinkedHashMap 保证顺序稳定
  ///
  /// **多 ORCID 场景**：
  ///
  /// 一个作者可能有多个 ORCID（约 5% 的情况）。
  /// 如果只按主要 ORCID 合并，会遗漏以下情况：
  /// - 作者 A：ORCID = [X, Y]
  /// - 作者 B：ORCID = [Y]
  /// 两者应该合并（共享 ORCID Y），但按主要 ORCID 无法匹配。
  ///
  /// @param items 原始作者列表
  /// @return 合并后的作者列表
  private List<AuthorAggregate> mergeByOrcidWithinBatch(List<? extends AuthorAggregate> items) {
    // Key: 任意 ORCID → 作者（同一作者的所有 ORCID 都指向它）
    Map<String, AuthorAggregate> orcidToAuthor = new LinkedHashMap<>();

    // 存储无 ORCID 的作者
    List<AuthorAggregate> authorsWithoutOrcid = new ArrayList<>();

    // 已处理的作者集合（避免重复添加）
    Set<AuthorAggregate> processedAuthors = new java.util.LinkedHashSet<>();

    for (AuthorAggregate author : items) {
      List<Orcid> orcids = author.getOrcids();

      if (orcids.isEmpty()) {
        // 无 ORCID 的作者直接保留
        authorsWithoutOrcid.add(author);
        continue;
      }

      // 查找是否有任一 ORCID 已存在
      AuthorAggregate existingAuthor = null;
      String matchedOrcid = null;
      for (Orcid orcid : orcids) {
        if (orcidToAuthor.containsKey(orcid.value())) {
          existingAuthor = orcidToAuthor.get(orcid.value());
          matchedOrcid = orcid.value();
          break;
        }
      }

      if (existingAuthor != null) {
        // 找到匹配，合并名字变体
        existingAuthor.mergeNameVariantsFrom(author);

        log.debug(
            "批次内合并：ORCID={}，保留={}，合并={}",
            matchedOrcid,
            existingAuthor.getNormalizedKey(),
            author.getNormalizedKey());

        // 将当前作者的所有 ORCID 也指向已存在的作者
        for (Orcid orcid : orcids) {
          orcidToAuthor.put(orcid.value(), existingAuthor);
        }
      } else {
        // 首次遇到，记录该作者的所有 ORCID
        for (Orcid orcid : orcids) {
          orcidToAuthor.put(orcid.value(), author);
        }
        processedAuthors.add(author);
      }
    }

    // 合并结果：先添加有 ORCID 的作者，再添加无 ORCID 的作者
    List<AuthorAggregate> result = new ArrayList<>(processedAuthors);
    result.addAll(authorsWithoutOrcid);

    return result;
  }

  /// 第二级去重：跨批次 UPSERT（合并而非跳过）。
  ///
  /// **处理流程**：
  ///
  /// 1. 收集批次内所有作者的**所有 ORCID**
  /// 2. 一次批量查询获取数据库中已存在的作者（及其所有 ORCID）
  /// 3. 对于已存在的作者：**合并名字变体** → 加入更新列表
  /// 4. 对于不存在的作者：加入新增列表
  /// 5. 无 ORCID 的作者直接加入新增列表
  ///
  /// **多 ORCID 场景**：
  ///
  /// 使用 `findAuthorsByAnyOrcid` 返回 ORCID → 作者 映射，
  /// 确保作者 A 的所有 ORCID [X, Y] 都指向同一个作者对象。
  ///
  /// **性能优化**：
  ///
  /// 使用批量 IN 查询替代 N 次单条查询，将 O(N) 次数据库查询优化为 O(1) 次。
  ///
  /// @param authors 待处理的作者列表
  /// @return UPSERT 结果（包含需要更新的和需要新增的作者）
  private UpsertResult mergeWithExistingAuthors(List<AuthorAggregate> authors) {
    // 1. 收集批次内所有作者的所有 ORCID
    Set<String> orcidsInBatch =
        authors.stream()
            .flatMap(author -> author.getOrcids().stream())
            .map(Orcid::value)
            .collect(Collectors.toSet());

    if (orcidsInBatch.isEmpty()) {
      // 批次内无 ORCID，全部作为新增
      return new UpsertResult(List.of(), authors);
    }

    // 2. 一次批量查询获取已存在的作者（ORCID → 作者 映射）
    Map<String, AuthorAggregate> orcidToExistingAuthor =
        authorRepository.findAuthorsByAnyOrcid(orcidsInBatch);

    if (orcidToExistingAuthor.isEmpty()) {
      // 数据库中无匹配 ORCID，全部作为新增
      return new UpsertResult(List.of(), authors);
    }

    // 3. 分类处理：已存在的合并，不存在的新增
    // 使用 Set 跟踪已处理的已存在作者（避免重复更新）
    Set<AuthorAggregate> updatedAuthorsSet = new LinkedHashSet<>();
    List<AuthorAggregate> newAuthors = new ArrayList<>();

    for (AuthorAggregate author : authors) {
      List<Orcid> orcids = author.getOrcids();

      if (orcids.isEmpty()) {
        // 无 ORCID 的作者直接新增
        newAuthors.add(author);
        continue;
      }

      // 查找是否有任一 ORCID 匹配到已存在的作者
      AuthorAggregate existingAuthor = null;
      String matchedOrcid = null;
      for (Orcid orcid : orcids) {
        if (orcidToExistingAuthor.containsKey(orcid.value())) {
          existingAuthor = orcidToExistingAuthor.get(orcid.value());
          matchedOrcid = orcid.value();
          break;
        }
      }

      if (existingAuthor != null) {
        // 已存在：合并名字变体
        existingAuthor.mergeNameVariantsFrom(author);
        updatedAuthorsSet.add(existingAuthor);

        log.debug(
            "跨批次合并：ORCID={}，已存在作者={}，合并来源={}",
            matchedOrcid,
            existingAuthor.getNormalizedKey(),
            author.getNormalizedKey());
      } else {
        // 不存在：新增
        newAuthors.add(author);
      }
    }

    return new UpsertResult(new ArrayList<>(updatedAuthorsSet), newAuthors);
  }

  /// UPSERT 操作结果。
  ///
  /// @param updatedAuthors 需要更新的作者（已合并名字变体）
  /// @param newAuthors 需要新增的作者
  private record UpsertResult(
      List<AuthorAggregate> updatedAuthors, List<AuthorAggregate> newAuthors) {}
}
