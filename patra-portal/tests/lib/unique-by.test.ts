import { describe, expect, it } from "vitest";
import { uniqueBy } from "@/lib/unique-by";

describe("uniqueBy", () => {
  it("同一个键只留第一次出现的，顺序不变", () => {
    const items = [{ id: "1", v: 1 }, { id: "2" }, { id: "1", v: 2 }];
    expect(uniqueBy(items, (item) => item.id)).toEqual([{ id: "1", v: 1 }, { id: "2" }]);
  });

  it("空数组返回空数组", () => {
    expect(uniqueBy([] as { id: string }[], (item) => item.id)).toEqual([]);
  });
});
