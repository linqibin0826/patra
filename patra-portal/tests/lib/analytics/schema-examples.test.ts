// @vitest-environment node
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { EXAMPLES_DIR, validateAgainst } from "@tests/support/analytics-schema";
import { describe, expect, it } from "vitest";

/// 门户用到的三种形态：浏览器批次、浏览器单条事件、采集信封
const SHAPES = ["browser-batch", "browser-event", "collect-envelope"] as const;

function examples(kind: "valid" | "invalid") {
  return readdirSync(join(EXAMPLES_DIR, kind))
    .map((file) => ({ file, shape: SHAPES.find((s) => file.startsWith(`${s}--`)) }))
    .filter((entry): entry is { file: string; shape: (typeof SHAPES)[number] } => !!entry.shape)
    .map(({ file, shape }) => ({
      file,
      shape,
      value: JSON.parse(readFileSync(join(EXAMPLES_DIR, kind, file), "utf8")) as unknown,
    }));
}

// 门户测试里的校验器与后端对同一份 schema 的理解一致：合格样例都过、不合格样例都不过
describe("契约样例", () => {
  it.each(examples("valid"))("合格：$file", ({ shape, value }) => {
    expect(validateAgainst(shape, value)).toEqual([]);
  });

  it.each(examples("invalid"))("不合格：$file", ({ shape, value }) => {
    expect(validateAgainst(shape, value)).not.toEqual([]);
  });
});
