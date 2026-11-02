// @vitest-environment node
import { readdirSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { SCHEMA_DIR } from "@tests/support/analytics-schema";
import { describe, expect, it } from "vitest";
import { SCHEMA_VERSIONS } from "@/lib/analytics/generated/events";
import { generateAnalyticsTypes } from "../../../scripts/generate-analytics-types.mjs";

const GENERATED = resolve(__dirname, "../../../src/lib/analytics/generated/events.ts");

describe("事件类型由 schema 生成", () => {
  it("提交的生成文件与 schema 一致（不一致时跑 pnpm gen:analytics）", async () => {
    expect(await generateAnalyticsTypes(SCHEMA_DIR)).toBe(readFileSync(GENERATED, "utf8"));
  });

  it("版本表覆盖事件目录里的全部 10 个事件，版本取自各事件 schema", () => {
    const names = readdirSync(join(SCHEMA_DIR, "events")).map((f) => f.replace(".schema.json", ""));
    expect(Object.keys(SCHEMA_VERSIONS).sort()).toEqual(names.sort());
    expect(SCHEMA_VERSIONS.article_viewed).toBe("1-0-0");
  });
});
