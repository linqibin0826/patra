// patra-learn/src/lib/content/types.ts
// 内容模型的类型：条目、路线、主题、标签，以及校验问题。格式见工程 spec
// docs/patra/specs/2026-10-31-learn-content-model-design.md

export const ENTRY_TYPES = ["concept", "troubleshooting", "decision", "pitfall"] as const;
export type EntryType = (typeof ENTRY_TYPES)[number];

/// 难度按数组顺序由低到高
export const DIFFICULTIES = ["intro", "basic", "advanced", "practice"] as const;
export type Difficulty = (typeof DIFFICULTIES)[number];

export const DIFFICULTY_RANK: Record<Difficulty, number> = {
  intro: 0,
  basic: 1,
  advanced: 2,
  practice: 3,
};

export interface Source {
  version: string;
  issue?: string;
  pr?: number;
}

export interface EntryMeta {
  title: string;
  summary: string;
  type: EntryType;
  difficulty: Difficulty;
  tags: string[];
  prerequisites: string[];
  topics: string[];
  sources: Source[];
  personalEnv: boolean;
  supersededBy?: string;
}

export interface Entry {
  slug: string;
  /// 相对 patra-learn 的路径
  file: string;
  meta: EntryMeta;
  /// 去掉 frontmatter 后的正文
  body: string;
}

export interface Route {
  slug: string;
  file: string;
  title: string;
  summary: string;
  order: number;
  entries: string[];
}

export interface Topic {
  slug: string;
  file: string;
  title: string;
  summary: string;
}

export interface Tag {
  slug: string;
  label: string;
}

export interface ContentCatalog {
  entries: Entry[];
  routes: Route[];
  topics: Topic[];
  tags: Tag[];
}

/// 一个内容文件的原文；file 相对 patra-learn，如 content/entries/outbox.mdx
export interface RawFile {
  file: string;
  text: string;
}

export interface RawContent {
  entries: RawFile[];
  routes: RawFile[];
  topics: RawFile[];
  tags: RawFile | null;
}

export interface ContentIssue {
  /// 规则 id，见工程 spec 第 6 节，稳定不改名
  rule: string;
  file: string;
  /// 中文说明，说清怎么修
  message: string;
}
