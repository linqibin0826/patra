import type { OrganizationSuggestion } from "@/lib/portal-api/suggest";

/// ROR 类型码 → 候选右侧的类型小标（简报 §3.8）；`funder` 与其他类型不显示
const ORGANIZATION_TYPES: Record<string, string> = {
  healthcare: "医疗",
  education: "教育",
  facility: "设施",
  government: "政府",
  company: "企业",
  nonprofit: "非营利",
};

/// 机构类型小标：取第一个能显示的类型，都不能显示时为 null
export function organizationTypeLabel(types: readonly string[]): string | null {
  for (const type of types) {
    const label = ORGANIZATION_TYPES[type];
    if (label) {
      return label;
    }
  }
  return null;
}

/// 机构候选与已选卡片的副行：有中文名时先写英文名，再接城市、国家（有的才接）；都没有时为 null
export function organizationDetail(
  suggestion: Pick<OrganizationSuggestion, "displayName" | "zhName" | "cityName" | "countryName">,
): string | null {
  const parts = [
    suggestion.zhName ? suggestion.displayName : null,
    suggestion.cityName,
    suggestion.countryName,
  ].filter((part): part is string => Boolean(part));
  return parts.length > 0 ? parts.join(" · ") : null;
}

/// MeSH 树号首字母 → 大类中文名（简报 §3.9）
const MESH_CATEGORIES: Record<string, string> = {
  A: "解剖",
  B: "生物",
  C: "疾病",
  D: "化学品和药物",
  E: "技术与设备",
  F: "精神病学与心理学",
  G: "现象与过程",
  H: "学科与职业",
  I: "人类学 · 教育 · 社会",
  J: "技术 · 工业 · 农业",
  K: "人文",
  L: "信息科学",
  M: "人群",
  N: "卫生保健",
  V: "出版特征",
  Z: "地理",
};

/// 主题词的大类：看第一个（主）树号的首字母；没有树号或不认识时为 null
export function meshCategoryLabel(treeNumbers: readonly string[]): string | null {
  return MESH_CATEGORIES[treeNumbers[0]?.charAt(0) ?? ""] ?? null;
}

/// 有没有中日韩文字：关注领域只支持英文主题词，含这些字时不请求（release spec 边界 J）
export function hasCjk(value: string): boolean {
  return /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Hangul}]/u.test(value);
}
