import { describe, expect, it } from "vitest";
import { type DialogScope, exportScopeOf } from "@/lib/library/scope";

const base = { label: "x", name: "x", count: 1 };

describe("exportScopeOf：导出范围 → 埋点 export_completed 的 scope", () => {
  it.each([
    [{ kind: "items", items: [], label: "x", name: "x" }, "items"],
    [{ ...base, kind: "library", view: "all", sort: "added" }, "library_all"],
    [{ ...base, kind: "library", view: "unfiled", sort: "added" }, "library_unfiled"],
    [{ ...base, kind: "collection", id: "9", sort: "added" }, "collection"],
    [{ ...base, kind: "public-collection", id: "9", sort: "added" }, "public_collection"],
  ] as [DialogScope, string][])("%o → %s", (scope, expected) => {
    expect(exportScopeOf(scope)).toBe(expected);
  });
});
