import { readdirSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import Ajv2020 from "ajv/dist/2020";

/// 埋点契约所在目录（后端的 api 模块资源，门户只读）
export const ANALYTICS_DIR = resolve(
  __dirname,
  "../../../patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics",
);
export const SCHEMA_DIR = join(ANALYTICS_DIR, "schema");
export const EXAMPLES_DIR = join(ANALYTICS_DIR, "examples");

const ID_PREFIX = "https://patra.linqibin.dev/schemas/analytics/";

function loadSchemas(): Ajv2020 {
  // `x-patra-version` 是注解；schema 不依赖 format，关掉 strict 免得对未知关键字报错
  const ajv = new Ajv2020({ strict: false, allErrors: true });
  ajv.addKeyword("x-patra-version");
  for (const dir of [SCHEMA_DIR, join(SCHEMA_DIR, "events")]) {
    for (const file of readdirSync(dir).filter((name) => name.endsWith(".schema.json"))) {
      ajv.addSchema(JSON.parse(readFileSync(join(dir, file), "utf8")));
    }
  }
  return ajv;
}

const ajv = loadSchemas();

/// 按契约里的一个 schema 校验，返回错误列表（空即通过）。`schema` 是相对 `schema/` 的名字，
/// 如 `collect-envelope`、`events/search_performed`
export function validateAgainst(schema: string, value: unknown): string[] {
  const validate = ajv.getSchema(`${ID_PREFIX}${schema}.schema.json`);
  if (!validate) {
    throw new Error(`没有这个 schema：${schema}`);
  }
  return validate(value)
    ? []
    : (validate.errors ?? []).map((error) => `${error.instancePath} ${error.message}`);
}
