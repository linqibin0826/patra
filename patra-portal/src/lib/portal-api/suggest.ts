import "server-only";
import { z } from "zod";
import { countryName } from "@/lib/country-name";
import { gatewayFetch } from "@/lib/gateway";

/// 机构候选（catalog `/portal/organizations/suggest` 的一项）。国家码在服务端换成中文名：两端 ICU 数据
/// 版本不同时，浏览器算出来的可能不一样。
export interface OrganizationSuggestion {
  rorId: string;
  /// 英文名
  displayName: string;
  zhName: string | null;
  /// 命中的名称原文（中文名、别名、缩写……）
  matchedName: string | null;
  cityName: string | null;
  countryName: string | null;
  /// 小写的 ROR 类型码
  types: string[];
}

/// MeSH 主题词候选（catalog `/portal/mesh/suggest` 的一项）
export interface MeshSuggestion {
  ui: string;
  /// 英文主题词
  name: string;
  /// 经入口词命中时的入口词原文
  matchedTerm: string | null;
  /// 全部树号，主树号在前
  treeNumbers: string[];
}

/// 每次最多取 10 条（简报 §3.8）
const LIMIT = 10;

const organizationsSchema = z.object({
  items: z.array(
    z.object({
      rorId: z.string(),
      displayName: z.string(),
      zhName: z.string().nullable(),
      matchedName: z.string().nullable(),
      countryCode: z.string().nullable(),
      cityName: z.string().nullable(),
      types: z.array(z.string()),
    }),
  ),
});

const meshSchema = z.object({
  items: z.array(
    z.object({
      ui: z.string(),
      name: z.string(),
      matchedTerm: z.string().nullable(),
      treeNumbers: z.array(z.string()),
    }),
  ),
});

/// 请求 catalog 的建议接口：非 2xx 或形状不对时抛错。公开读，不带令牌，不转发浏览器来源（spec §4.2）。
async function suggest<T>(
  endpoint: string,
  q: string,
  schema: z.ZodType<{ items: T[] }>,
): Promise<T[]> {
  const params = new URLSearchParams({ q, limit: String(LIMIT) });
  const res = await gatewayFetch(`/patra-catalog/portal/${endpoint}/suggest?${params}`);
  if (!res.ok) {
    throw new Error(`${endpoint} 建议加载失败：${res.status}`);
  }
  return schema.parse(await res.json()).items;
}

/// 机构候选：按中文名、英文名、别名、缩写搜 ROR 机构（`q` 已归一）
export async function fetchOrganizationSuggestions(q: string): Promise<OrganizationSuggestion[]> {
  const items = await suggest("organizations", q, organizationsSchema);
  return items.map(({ countryCode, ...item }) => ({
    ...item,
    countryName: countryCode ? countryName(countryCode) : null,
  }));
}

/// MeSH 主题词候选：按主题词与入口词搜（`q` 已归一）
export function fetchMeshSuggestions(q: string): Promise<MeshSuggestion[]> {
  return suggest("mesh", q, meshSchema);
}
