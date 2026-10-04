import { describe, expect, it } from "vitest";
import { countryLabel, countryName } from "@/lib/country-name";

describe("countryName / countryLabel", () => {
  it("ISO 3166 两位码 → 简体中文名（大小写不敏感）", () => {
    expect(countryName("DE")).toBe("德国");
    expect(countryName("us")).toBe("美国");
  });

  it("非两位码或未知码 → null", () => {
    expect(countryName("XX")).toBeNull();
    expect(countryName("USA")).toBeNull();
    expect(countryName("")).toBeNull();
  });

  it("label：「中文名 · 代码」，未知时原样返回", () => {
    expect(countryLabel("DE")).toBe("德国 · DE");
    expect(countryLabel("XX")).toBe("XX");
  });
});
