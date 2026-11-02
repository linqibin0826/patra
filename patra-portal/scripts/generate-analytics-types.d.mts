/// 埋点契约 schema 的默认目录（后端 api 模块的资源）
export const DEFAULT_SCHEMA_DIR: string;
/// 生成 `src/lib/analytics/generated/events.ts` 的全文（已按 Biome 格式化）
export function generateAnalyticsTypes(schemaDir?: string): Promise<string>;
