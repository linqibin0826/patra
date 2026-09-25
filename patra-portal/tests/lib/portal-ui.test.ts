import { describe, expect, it } from "vitest";
import { sourceDotClass } from "@/lib/portal-ui";

describe("sourceDotClass", () => {
  it("已知来源给设计系统的来源色", () => {
    expect(sourceDotClass("PubMed")).toBe("bg-source-pubmed");
    expect(sourceDotClass("Europe PMC")).toBe("bg-source-epmc");
    expect(sourceDotClass("Crossref")).toBe("bg-source-crossref");
  });

  it("未知来源回退 source-other", () => {
    expect(sourceDotClass("OpenAlex")).toBe("bg-source-other");
  });
});
