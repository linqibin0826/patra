import { describe, expect, it } from "vitest";
import { buildReport, codeRefStatus, type GitPort } from "./report";

const PINNED = "a".repeat(40);
const HEAD = "f".repeat(40);
const NEWER = "c".repeat(40);
const NEWEST = "d".repeat(40);

/// 假 git：known 是本地有的提交，paths 是每个提交里有的路径，touching 是 sha..HEAD 间改过某路径的提交
function fakeGit(options: {
  known?: string[];
  paths?: Record<string, string[]>;
  touching?: string[];
}): GitPort {
  const { known = [PINNED, HEAD], paths = {}, touching = [] } = options;
  return {
    head: () => HEAD,
    hasCommit: (sha) => known.includes(sha),
    hasPath: (rev, path) => (paths[rev === "HEAD" ? HEAD : rev] ?? []).includes(path),
    commitsTouching: () => touching,
  };
}

const PATH = "patra-api/a/B.java";
const both = { [PINNED]: [PATH], [HEAD]: [PATH] };

describe("codeRefStatus", () => {
  it("pending", () => {
    expect(codeRefStatus({ path: PATH, sha: "pending" }, fakeGit({}))).toEqual({
      status: "pending",
    });
  });

  it("本地没有这个提交是 unknown-sha", () => {
    expect(codeRefStatus({ path: PATH, sha: PINNED }, fakeGit({ known: [HEAD] }))).toEqual({
      status: "unknown-sha",
    });
  });

  it("钉住的提交里没有这个路径是 not-in-commit", () => {
    const git = fakeGit({ paths: { [HEAD]: [PATH] } });
    expect(codeRefStatus({ path: PATH, sha: PINNED }, git)).toEqual({ status: "not-in-commit" });
  });

  it("HEAD 里没有了是 missing", () => {
    const git = fakeGit({ paths: { [PINNED]: [PATH] } });
    expect(codeRefStatus({ path: PATH, sha: PINNED }, git)).toEqual({ status: "missing" });
  });

  it("之后没有提交改过它是 unchanged", () => {
    expect(codeRefStatus({ path: PATH, sha: PINNED }, fakeGit({ paths: both }))).toEqual({
      status: "unchanged",
      commitsSince: 0,
    });
  });

  it("之后被改过是 changed，带改动次数与最近一次", () => {
    const git = fakeGit({ paths: both, touching: [NEWEST, NEWER] });
    expect(codeRefStatus({ path: PATH, sha: PINNED }, git)).toEqual({
      status: "changed",
      commitsSince: 2,
      lastChangedBy: NEWEST,
    });
  });
});

describe("buildReport", () => {
  const now = new Date("2026-10-31T12:00:00.000Z");
  const issue = { rule: "recap-missing", file: "content/entries/a.mdx", message: "缺复述题" };

  it("透传问题、写入时间与 HEAD，每个引用一条", () => {
    const report = buildReport({
      issues: [issue],
      codeRefs: [
        { entry: "a", file: "content/entries/a.mdx", at: `${PATH}@${PINNED}`, lines: "3-5" },
        { entry: "a", file: "content/entries/a.mdx", at: "格式不对" },
      ],
      git: fakeGit({ paths: both }),
      now,
    });
    expect(report).toEqual({
      generatedAt: "2026-10-31T12:00:00.000Z",
      head: HEAD,
      issues: [issue],
      codeRefs: [
        { entry: "a", path: PATH, sha: PINNED, lines: "3-5", status: "unchanged", commitsSince: 0 },
      ],
    });
  });
});
