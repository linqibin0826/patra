import { describe, expect, it } from "vitest";
import { sourceDotClass } from "@/lib/portal-ui";

describe("sourceDotClass", () => {
  it("已知来源给对应色点", () => {
    expect(sourceDotClass("PubMed")).toBe("bg-emerald-500");
    expect(sourceDotClass("Crossref")).toBe("bg-sky-500");
  });

  it("未知来源回退弱化灰（可生效的 arbitrary value）", () => {
    expect(sourceDotClass("OpenAlex")).toBe("bg-(--fg-3)");
  });
});
