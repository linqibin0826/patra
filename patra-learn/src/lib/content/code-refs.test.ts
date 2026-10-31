import { describe, expect, it } from "vitest";
import { checkCodeRefs, codeRefUrl, parseCodeRefAt } from "./code-refs";
import type { CodeRefUse } from "./mdx-rules";

const SHA = "a".repeat(40);

function use(at: string, lines?: string): CodeRefUse {
  return { entry: "outbox", file: "content/entries/outbox.mdx", at, ...(lines ? { lines } : {}) };
}

describe("parseCodeRefAt", () => {
  it("拆出路径与 40 位 SHA", () => {
    expect(parseCodeRefAt(`patra-api/a/B.java@${SHA}`)).toEqual({
      path: "patra-api/a/B.java",
      sha: SHA,
    });
  });

  it("pending 表示还没钉", () => {
    expect(parseCodeRefAt("patra-api/a/B.java@pending")).toEqual({
      path: "patra-api/a/B.java",
      sha: "pending",
    });
  });

  it.each([
    ["缺 @", "patra-api/a/B.java"],
    ["短 SHA", "patra-api/a/B.java@abcdef1"],
    ["大写 SHA", `patra-api/a/B.java@${"A".repeat(40)}`],
    ["绝对路径", `/patra-api/a/B.java@${SHA}`],
    ["./ 开头", `./patra-api/a/B.java@${SHA}`],
    ["../ 开头", `../patra-api/a/B.java@${SHA}`],
    ["路径含空格", `patra api/B.java@${SHA}`],
    ["路径为空", `@${SHA}`],
  ])("%s 返回 null", (_name, at) => {
    expect(parseCodeRefAt(at)).toBeNull();
  });
});

describe("checkCodeRefs", () => {
  const exists = (path: string) => path === "patra-api/a/B.java";

  it("格式不对报 code-ref-invalid", () => {
    expect(checkCodeRefs([use("patra-api/a/B.java@abc")], exists).map((i) => i.rule)).toEqual([
      "code-ref-invalid",
    ]);
  });

  it("lines 格式不对报 code-ref-invalid", () => {
    expect(
      checkCodeRefs([use(`patra-api/a/B.java@${SHA}`, "abc")], exists).map((i) => i.rule),
    ).toEqual(["code-ref-invalid"]);
  });

  it("文件在工作树里不存在报 code-ref-missing（pending 也查）", () => {
    expect(
      checkCodeRefs([use(`gone/C.java@${SHA}`), use("gone/D.java@pending")], exists).map(
        (i) => i.rule,
      ),
    ).toEqual(["code-ref-missing", "code-ref-missing"]);
  });

  it("都对时没有问题", () => {
    expect(
      checkCodeRefs(
        [use(`patra-api/a/B.java@${SHA}`, "12-30"), use("patra-api/a/B.java@pending")],
        exists,
      ),
    ).toEqual([]);
  });
});

describe("codeRefUrl", () => {
  const ref = { path: "patra-api/a/B.java", sha: SHA };

  it("钉住的引用链到那次提交的永久地址", () => {
    expect(codeRefUrl(ref)).toBe(
      `https://github.com/linqibin0826/patra/blob/${SHA}/patra-api/a/B.java`,
    );
  });

  it("带行号范围", () => {
    expect(codeRefUrl(ref, "12-30")).toMatch(/#L12-L30$/);
  });

  it("单行", () => {
    expect(codeRefUrl(ref, "12")).toMatch(/#L12$/);
  });

  it("pending 没有链接", () => {
    expect(codeRefUrl({ ...ref, sha: "pending" })).toBeNull();
  });
});
