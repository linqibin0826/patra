// patra-learn/src/lib/content/testing/factory.ts
// 测试用的内容工厂：构造合法默认值，用例只写跟自己有关的字段
import type { ContentCatalog, Entry, EntryMeta, Route, Topic } from "../types";

export function entry(slug: string, meta: Partial<EntryMeta> = {}): Entry {
  return {
    slug,
    file: `content/entries/${slug}.mdx`,
    meta: {
      title: slug,
      summary: `${slug} 的简介`,
      type: "concept",
      difficulty: "basic",
      tags: ["backend"],
      prerequisites: [],
      topics: [],
      sources: [],
      personalEnv: false,
      ...meta,
    },
    body: "",
  };
}

export function route(slug: string, entries: string[], order = 1): Route {
  return {
    slug,
    file: `content/routes/${slug}.yaml`,
    title: slug,
    summary: "简介",
    order,
    entries,
  };
}

export function topic(slug: string): Topic {
  return { slug, file: `content/topics/${slug}.yaml`, title: slug, summary: "简介" };
}

export function catalog(parts: Partial<ContentCatalog> = {}): ContentCatalog {
  return {
    entries: [],
    routes: [],
    topics: [],
    tags: [
      { slug: "backend", label: "后端" },
      { slug: "network", label: "网络" },
    ],
    ...parts,
  };
}
