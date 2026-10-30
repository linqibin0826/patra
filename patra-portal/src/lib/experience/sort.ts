import type { ExperienceSort } from "@/lib/interaction/experience-schema";

/// 地址或请求里的 `sort` → 期刊页经验列表的排序：只认字符串 `helpful`，其余（含同名参数出现多次）是最新投稿。
/// 不 import 客户端 hook：页面（RSC）与 Route Handler 共用
export function parseExperienceSort(value: unknown): ExperienceSort {
  return value === "helpful" ? "helpful" : "latest";
}
