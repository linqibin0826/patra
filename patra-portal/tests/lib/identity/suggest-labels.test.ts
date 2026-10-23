import { describe, expect, it } from "vitest";
import {
  hasCjk,
  meshCategoryLabel,
  organizationDetail,
  organizationTypeLabel,
} from "@/lib/identity/suggest-labels";

describe("organizationTypeLabel", () => {
  it.each([
    [["healthcare"], "医疗"],
    [["funder", "education"], "教育"],
    [["facility"], "设施"],
    [["government"], "政府"],
    [["company"], "企业"],
    [["nonprofit"], "非营利"],
    [["funder"], null],
    [["archive", "other"], null],
    [[], null],
  ])("%j → %s", (types, label) => {
    expect(organizationTypeLabel(types)).toBe(label);
  });
});

describe("organizationDetail", () => {
  const base = {
    rorId: "04jztag35",
    displayName: "Peking Union Medical College Hospital",
    zhName: "北京协和医院",
    matchedName: null,
    cityName: "Beijing",
    countryName: "中国",
    types: [],
  };

  it("有中文名：英文名 · 城市 · 国家", () => {
    expect(organizationDetail(base)).toBe("Peking Union Medical College Hospital · Beijing · 中国");
  });

  it("没有中文名：主行已是英文名，副行只有城市 · 国家", () => {
    expect(organizationDetail({ ...base, zhName: null })).toBe("Beijing · 中国");
  });

  it("什么都没有：null", () => {
    expect(
      organizationDetail({ ...base, zhName: null, cityName: null, countryName: null }),
    ).toBeNull();
  });
});

describe("meshCategoryLabel", () => {
  it.each([
    [["C14.280.434"], "疾病"],
    [["A07", "C14"], "解剖"],
    [["I01.880"], "人类学 · 教育 · 社会"],
    [["Z01"], "地理"],
    [["X99"], null],
    [[], null],
  ])("%j → %s", (treeNumbers, label) => {
    expect(meshCategoryLabel(treeNumbers)).toBe(label);
  });
});

describe("hasCjk", () => {
  it.each([
    ["心衰", true],
    ["heart 衰竭", true],
    ["がん", true],
    ["암", true],
    ["heart failure", false],
  ])("%j → %s", (value, expected) => {
    expect(hasCjk(value)).toBe(expected);
  });
});
