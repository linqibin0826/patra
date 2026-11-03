// 由埋点契约（JSON Schema）生成门户的事件类型：`pnpm gen:analytics`。
// 各事件属性的接口交给 json-schema-to-typescript；事件名属于服务端还是浏览器、各自版本，从事件目录与
// 各事件 schema 的 `x-patra-version` 读。整条事件的 oneOf 不交给生成器（它拼出的交叉类型没法用），
// 判别联合在 `src/lib/analytics/types.ts` 里用这里的映射拼。结果过一遍 Biome，与提交的文件逐字可比。
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { compile } from "json-schema-to-typescript";

const HERE = dirname(fileURLToPath(import.meta.url));
const PORTAL = resolve(HERE, "..");
const OUTPUT = "src/lib/analytics/generated/events.ts";
export const DEFAULT_SCHEMA_DIR = resolve(
  PORTAL,
  "../patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics/schema",
);

const readJson = (path) => JSON.parse(readFileSync(path, "utf8"));

/// `article_viewed` → `ArticleViewed`
const typeName = (eventName) =>
  eventName
    .split("_")
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join("");

/// 事件目录里一组（server_event / client_event）的事件名，按目录顺序
const eventNames = (catalog, group) =>
  catalog.$defs[group].oneOf.map((branch) => branch.properties.event_name.const);

const union = (values) => values.map((value) => JSON.stringify(value)).join(" | ");

/// 生成 `events.ts` 的全文
export async function generateAnalyticsTypes(schemaDir = DEFAULT_SCHEMA_DIR) {
  const catalog = readJson(join(schemaDir, "event-catalog.schema.json"));
  const defs = readJson(join(schemaDir, "defs.schema.json"));
  const server = eventNames(catalog, "server_event");
  const client = eventNames(catalog, "client_event");
  const parts = [
    "// 由 scripts/generate-analytics-types.mjs 从埋点契约生成，勿手改；改了 schema 跑 `pnpm gen:analytics`",
    "",
    `export type PageType = ${union(defs.$defs.page_type.enum)};`,
    `export type ListType = ${union(defs.$defs.list_type.enum)};`,
  ];
  const versions = [];
  for (const name of [...server, ...client]) {
    const file = join(schemaDir, "events", `${name}.schema.json`);
    const schema = readJson(file);
    versions.push(`  ${name}: ${JSON.stringify(schema["x-patra-version"])},`);
    const compiled = await compile({ ...schema, title: typeName(name) }, typeName(name), {
      bannerComment: "",
      ignoreMinAndMaxItems: true,
      additionalProperties: false,
      cwd: join(schemaDir, "events"),
      format: false,
    });
    // 没有属性的事件（page_viewed）生成器给出空接口，Biome 不收，换成「不能有任何键」的对象类型
    parts.push(
      compiled.replace(
        /^export interface (\w+) \{\s*\}$/m,
        "export type $1 = Record<string, never>;",
      ),
    );
  }
  const mapping = (title, names) =>
    `export interface ${title} {\n${names.map((n) => `  ${n}: ${typeName(n)};`).join("\n")}\n}`;
  parts.push(
    "/// 门户服务端发的 5 个事件：事件名 → 属性",
    mapping("ServerEventProperties", server),
    "/// 浏览器发的 5 个事件：事件名 → 属性",
    mapping("ClientEventProperties", client),
    "/// 各事件当前的 schema 版本（`x-patra-version`）",
    `export const SCHEMA_VERSIONS = {\n${versions.join("\n")}\n} as const;`,
  );
  return execFileSync(
    join(PORTAL, "node_modules/.bin/biome"),
    ["format", `--stdin-file-path=${OUTPUT}`],
    { input: parts.join("\n"), encoding: "utf8", cwd: PORTAL },
  );
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  writeFileSync(join(PORTAL, OUTPUT), await generateAnalyticsTypes());
  console.log(`已生成 ${OUTPUT}`);
}
