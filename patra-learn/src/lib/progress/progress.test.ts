import { describe, expect, it } from "vitest";
import {
  clearProgress,
  loadProgress,
  PROGRESS_KEY,
  parseProgress,
  saveProgress,
  withMastery,
  withVisit,
} from "./progress";

/// 内存里的假存储；throwOn 让读或写抛错，模拟隐私模式、禁用存储、配额满
function fakeStorage(
  initial: Record<string, string> = {},
  throwOn: "get" | "set" | "none" = "none",
) {
  const data = new Map(Object.entries(initial));
  return {
    data,
    getItem(key: string) {
      if (throwOn === "get") throw new Error("SecurityError");
      return data.get(key) ?? null;
    },
    setItem(key: string, value: string) {
      if (throwOn === "set") throw new Error("QuotaExceededError");
      data.set(key, value);
    },
    removeItem(key: string) {
      if (throwOn === "set") throw new Error("SecurityError");
      data.delete(key);
    },
  };
}

describe("parseProgress：存储里的原文 → 进度，坏数据不抛错", () => {
  it("没有记录或不是 JSON 时是空进度", () => {
    expect(parseProgress(null)).toEqual({});
    expect(parseProgress("{oops")).toEqual({});
    expect(parseProgress("[1,2]")).toEqual({});
    expect(parseProgress('"text"')).toEqual({});
  });

  it("保留合法字段，丢掉类型不对的字段与空记录", () => {
    const raw = JSON.stringify({
      outbox: { level: 2, markedAt: 100, readAt: 200 },
      "db-index": { level: 9, readAt: "昨天" },
      broken: "x",
      visited: { readAt: 300 },
    });
    expect(parseProgress(raw)).toEqual({
      outbox: { level: 2, markedAt: 100, readAt: 200 },
      visited: { readAt: 300 },
    });
  });
});

describe("withMastery / withVisit：返回新进度，不改原对象", () => {
  it("标掌握度记下级别与时间", () => {
    const before = { outbox: { readAt: 10 } };
    const after = withMastery(before, "outbox", 2, 50);
    expect(after).toEqual({ outbox: { readAt: 10, level: 2, markedAt: 50 } });
    expect(before).toEqual({ outbox: { readAt: 10 } });
  });

  it("取消标记删掉级别与标记时间，保留阅读时间", () => {
    const after = withMastery({ outbox: { level: 3, markedAt: 5, readAt: 10 } }, "outbox", 0, 60);
    expect(after).toEqual({ outbox: { readAt: 10 } });
  });

  it("取消一篇从没打开过的条目，不留下空记录", () => {
    expect(withMastery({ outbox: { level: 1, markedAt: 5 } }, "outbox", 0, 60)).toEqual({});
  });

  it("打开条目更新最近阅读时间", () => {
    expect(withVisit({ outbox: { level: 1, markedAt: 5 } }, "outbox", 70)).toEqual({
      outbox: { level: 1, markedAt: 5, readAt: 70 },
    });
    expect(withVisit({}, "db-index", 80)).toEqual({ "db-index": { readAt: 80 } });
  });
});

describe("loadProgress / saveProgress / clearProgress：读写都 try/catch", () => {
  it("读写同一个键", () => {
    const storage = fakeStorage();
    expect(saveProgress(storage, { outbox: { level: 2, markedAt: 1 } })).toBe(true);
    expect(JSON.parse(storage.data.get(PROGRESS_KEY) ?? "")).toEqual({
      outbox: { level: 2, markedAt: 1 },
    });
    expect(loadProgress(storage)).toEqual({ outbox: { level: 2, markedAt: 1 } });
  });

  it("拿不到存储或读时抛错，按空进度处理", () => {
    expect(loadProgress(undefined)).toEqual({});
    expect(loadProgress(fakeStorage({}, "get"))).toEqual({});
  });

  it("写时抛错返回 false，不往外抛", () => {
    expect(saveProgress(fakeStorage({}, "set"), { outbox: { readAt: 1 } })).toBe(false);
    expect(saveProgress(undefined, { outbox: { readAt: 1 } })).toBe(false);
    expect(clearProgress(fakeStorage({}, "set"))).toBe(false);
  });

  it("清空删掉整个键", () => {
    const storage = fakeStorage({ [PROGRESS_KEY]: '{"outbox":{"readAt":1}}' });
    expect(clearProgress(storage)).toBe(true);
    expect(loadProgress(storage)).toEqual({});
  });
});
