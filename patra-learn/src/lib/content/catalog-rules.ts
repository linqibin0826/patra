// patra-learn/src/lib/content/catalog-rules.ts
// 跨篇规则：标签、主题、前置（存在且无环）、被取代、路线（存在、不重复、难度不倒排、
// 本路线前置在前、个人环境与已被取代的条目不进路线）
import {
  type ContentCatalog,
  type ContentIssue,
  DIFFICULTY_RANK,
  type Entry,
  type Tag,
} from "./types";

/// 条目的标签都在标签表里（单篇规则，单篇校验也用）
export function checkEntryTags(entry: Entry, tags: Tag[]): ContentIssue[] {
  const known = new Set(tags.map((t) => t.slug));
  return entry.meta.tags
    .filter((tag) => !known.has(tag))
    .map((tag) => ({
      rule: "tag-unknown",
      file: entry.file,
      message: `标签 ${tag} 不在 content/tags.yaml 里：换成已有标签，或先把它加进标签表`,
    }));
}

function checkTopics(catalog: ContentCatalog): ContentIssue[] {
  const known = new Set(catalog.topics.map((t) => t.slug));
  const issues: ContentIssue[] = [];
  for (const entry of catalog.entries) {
    for (const topic of entry.meta.topics) {
      if (!known.has(topic)) {
        issues.push({
          rule: "topic-unknown",
          file: entry.file,
          message: `主题 ${topic} 不存在：先建 content/topics/${topic}.yaml`,
        });
      }
    }
    if (entry.meta.topics.length > 0 && entry.meta.sources.length === 0) {
      issues.push({
        rule: "topic-needs-version",
        file: entry.file,
        message: "挂了主题的条目要有带 version 的出处，主题页按版本排先后",
      });
    }
  }
  return issues;
}

function checkPrerequisites(
  catalog: ContentCatalog,
  bySlug: Map<string, Entry>,
  unparsed: Set<string>,
): ContentIssue[] {
  const issues: ContentIssue[] = [];
  for (const entry of catalog.entries) {
    for (const pre of entry.meta.prerequisites) {
      if (pre === entry.slug || (!bySlug.has(pre) && !unparsed.has(pre))) {
        issues.push({
          rule: "prerequisite-missing",
          file: entry.file,
          message:
            pre === entry.slug
              ? "前置不能是自己"
              : `前置 ${pre} 不存在：改成已有条目的 slug，或先写那一篇`,
        });
      }
    }
  }
  return [...issues, ...findCycles(bySlug)];
}

/// DFS 三色标记找环；同一个环（不论从哪个节点进入）只报一次
function findCycles(bySlug: Map<string, Entry>): ContentIssue[] {
  const state = new Map<string, "visiting" | "done">();
  const stack: string[] = [];
  const seen = new Set<string>();
  const issues: ContentIssue[] = [];

  const visit = (slug: string) => {
    state.set(slug, "visiting");
    stack.push(slug);
    for (const next of bySlug.get(slug)?.meta.prerequisites ?? []) {
      if (next === slug || !bySlug.has(next)) continue;
      if (state.get(next) === "visiting") {
        const cycle = stack.slice(stack.indexOf(next));
        const start = cycle.indexOf([...cycle].sort()[0] ?? next);
        const rotated = [...cycle.slice(start), ...cycle.slice(0, start)];
        const key = rotated.join(">");
        if (!seen.has(key)) {
          seen.add(key);
          const first = rotated[0] ?? next;
          issues.push({
            rule: "prerequisite-cycle",
            file: bySlug.get(first)?.file ?? "",
            message: `前置成环：${[...rotated, first].join(" → ")}，拆掉其中一条前置`,
          });
        }
      } else if (!state.has(next)) {
        visit(next);
      }
    }
    stack.pop();
    state.set(slug, "done");
  };

  for (const slug of [...bySlug.keys()].sort()) {
    if (!state.has(slug)) visit(slug);
  }
  return issues;
}

function checkSuperseded(
  catalog: ContentCatalog,
  bySlug: Map<string, Entry>,
  unparsed: Set<string>,
): ContentIssue[] {
  return catalog.entries
    .filter((e) => e.meta.supersededBy !== undefined)
    .filter((e) => {
      const target = e.meta.supersededBy ?? "";
      return target === e.slug || (!bySlug.has(target) && !unparsed.has(target));
    })
    .map((e) => ({
      rule: "superseded-missing",
      file: e.file,
      message: `supersededBy 指向的 ${e.meta.supersededBy} 不存在或是自己`,
    }));
}

function checkRoutes(
  catalog: ContentCatalog,
  bySlug: Map<string, Entry>,
  unparsed: Set<string>,
): ContentIssue[] {
  const issues: ContentIssue[] = [];
  const orders = new Map<number, string>();
  for (const route of [...catalog.routes].sort((a, b) => a.slug.localeCompare(b.slug))) {
    const report = (rule: string, message: string) =>
      issues.push({ rule, file: route.file, message });
    const sameOrder = orders.get(route.order);
    if (sameOrder) report("route-order-duplicate", `order ${route.order} 和路线 ${sameOrder} 重复`);
    else orders.set(route.order, route.slug);

    const position = new Map<string, number>();
    let previous: Entry | undefined;
    route.entries.forEach((slug, index) => {
      if (position.has(slug)) {
        report("route-entry-duplicate", `${slug} 在这条路线里出现了不止一次`);
        return;
      }
      position.set(slug, index);
      const entry = bySlug.get(slug);
      if (!entry) {
        // 文件在但解析失败：那篇自己已报 frontmatter-invalid，这里不误导成「不存在」
        if (unparsed.has(slug)) return;
        report("route-entry-missing", `${slug} 不存在：先写这一篇，或从路线里去掉`);
        return;
      }
      if (entry.meta.personalEnv) {
        report("route-personal-entry", `${slug} 是个人环境条目，不能进路线（只在标签页可见）`);
      }
      if (entry.meta.supersededBy) {
        report(
          "route-superseded-entry",
          `${slug} 已被 ${entry.meta.supersededBy} 取代，路线里换成取代它的那篇`,
        );
      }
      if (
        previous &&
        DIFFICULTY_RANK[entry.meta.difficulty] < DIFFICULTY_RANK[previous.meta.difficulty]
      ) {
        report(
          "route-difficulty-descending",
          `难度倒排：${previous.slug}（${previous.meta.difficulty}）之后是 ${slug}（${entry.meta.difficulty}）`,
        );
      }
      previous = entry;
    });

    for (const slug of route.entries) {
      const entry = bySlug.get(slug);
      const at = position.get(slug) ?? 0;
      for (const pre of entry?.meta.prerequisites ?? []) {
        const preAt = position.get(pre);
        if (preAt !== undefined && preAt > at) {
          report("route-prerequisite-after", `${slug} 的前置 ${pre} 排在它后面，调换顺序`);
        }
      }
    }
  }
  return issues;
}

/// 全站规则（标签检查除外，那是单篇规则）。unparsed 是文件存在但解析失败的条目 slug，
/// 引用它们不报「不存在」（它们自己已报解析问题）
export function checkCatalog(
  catalog: ContentCatalog,
  options: { unparsed?: string[] } = {},
): ContentIssue[] {
  const bySlug = new Map(catalog.entries.map((e) => [e.slug, e]));
  const unparsed = new Set(options.unparsed ?? []);
  return [
    ...checkTopics(catalog),
    ...checkPrerequisites(catalog, bySlug, unparsed),
    ...checkSuperseded(catalog, bySlug, unparsed),
    ...checkRoutes(catalog, bySlug, unparsed),
  ];
}
