// patra-learn/src/lib/progress/progress.ts
// 读者进度：掌握度与最近阅读时间，只存在读者浏览器的 localStorage 里（release spec 2.1、决策 B）。
// 纯函数：解析容错、更新返回新对象；读写存储都 try/catch，拿不到存储时按空进度处理。格式见工程 spec
// docs/patra/specs/2026-10-31-learn-pages-design.md 第 3 节

export const PROGRESS_KEY = "patra-learn.progress";

/// 1 刚接触、2 理解中、3 吃透了；0 表示没标过（只在更新时用，不存）
export type Level = 1 | 2 | 3;

export interface EntryProgress {
  level?: Level;
  /// 标掌握度的时间（毫秒）
  markedAt?: number;
  /// 最近一次打开条目页的时间（毫秒）
  readAt?: number;
}

export type Progress = Readonly<Record<string, EntryProgress>>;

/// localStorage 用到的那几个方法，测试里换成假的
export interface ProgressStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

function isTime(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

function isLevel(value: unknown): value is Level {
  return value === 1 || value === 2 || value === 3;
}

/// 一条记录里只留合法字段；什么都不剩时返回 undefined
function cleanRecord(value: unknown): EntryProgress | undefined {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return undefined;
  const { level, markedAt, readAt } = value as Record<string, unknown>;
  const record: EntryProgress = {
    ...(isLevel(level) ? { level } : {}),
    ...(isLevel(level) && isTime(markedAt) ? { markedAt } : {}),
    ...(isTime(readAt) ? { readAt } : {}),
  };
  return Object.keys(record).length > 0 ? record : undefined;
}

/// 存储原文 → 进度；不是 JSON 对象时是空进度，坏的那一项丢掉
export function parseProgress(raw: string | null): Progress {
  if (!raw) return {};
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return {};
  }
  if (typeof data !== "object" || data === null || Array.isArray(data)) return {};
  const out: Record<string, EntryProgress> = {};
  for (const [slug, value] of Object.entries(data)) {
    const record = cleanRecord(value);
    if (record) out[slug] = record;
  }
  return out;
}

/// 标掌握度；level 为 0 时取消标记，保留阅读时间
export function withMastery(progress: Progress, slug: string, level: Level | 0, now: number) {
  const { level: _level, markedAt: _markedAt, ...rest } = progress[slug] ?? {};
  const record: EntryProgress = level === 0 ? rest : { ...rest, level, markedAt: now };
  const { [slug]: _old, ...others } = progress;
  return Object.keys(record).length > 0 ? { ...others, [slug]: record } : others;
}

/// 打开了一篇条目
export function withVisit(progress: Progress, slug: string, now: number): Progress {
  return { ...progress, [slug]: { ...progress[slug], readAt: now } };
}

export function loadProgress(storage: ProgressStorage | undefined): Progress {
  try {
    return parseProgress(storage?.getItem(PROGRESS_KEY) ?? null);
  } catch {
    return {};
  }
}

/// 写成功返回 true；存储不可用或写失败返回 false，由调用方决定退到内存
export function saveProgress(storage: ProgressStorage | undefined, progress: Progress): boolean {
  if (!storage) return false;
  try {
    storage.setItem(PROGRESS_KEY, JSON.stringify(progress));
    return true;
  } catch {
    return false;
  }
}

export function clearProgress(storage: ProgressStorage | undefined): boolean {
  if (!storage) return false;
  try {
    storage.removeItem(PROGRESS_KEY);
    return true;
  } catch {
    return false;
  }
}
