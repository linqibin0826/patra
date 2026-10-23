// @vitest-environment node
import { describe, expect, it } from "vitest";
import { isSessionRejected, parseProblem } from "@/lib/identity/problem";

const res = (status: number, headers: Record<string, string> = {}) =>
  new Response(null, { status, headers });

describe("parseProblem", () => {
  it("读出 code、traits、errors 与 retryAfterSeconds", () => {
    const problem = parseProblem(res(409), {
      code: "IDN-0409",
      traits: ["PENDING_DELETION", "CONFLICT"],
      errors: [{ field: "email", code: "ALREADY_REGISTERED", rejectedValue: null, message: "x" }],
    });
    expect(problem.status).toBe(409);
    expect(problem.code).toBe("IDN-0409");
    expect(problem.traits.has("CONFLICT")).toBe(true);
    expect(problem.traits.has("PENDING_DELETION")).toBe(true);
    expect(problem.errors).toEqual([{ field: "email", code: "ALREADY_REGISTERED" }]);
    expect(problem.retryAfterSeconds).toBeNull();
  });

  it("errors 里形状不对的项丢掉，code 不是字符串记为 null", () => {
    const problem = parseProblem(res(422), {
      errors: [
        { code: "X" },
        "bad",
        { field: "bio", code: 3 },
        { field: "links[1].url", code: "DUPLICATE" },
      ],
    });
    expect(problem.errors).toEqual([
      { field: "bio", code: null },
      { field: "links[1].url", code: "DUPLICATE" },
    ]);
  });

  it("响应体不是对象：全部取默认值", () => {
    const problem = parseProblem(res(503), undefined);
    expect(problem).toMatchObject({ status: 503, code: null, errors: [], retryAfterSeconds: null });
    expect(problem.traits.size).toBe(0);
  });

  it("retryAfterSeconds 先取响应体，再取 Retry-After 头", () => {
    expect(
      parseProblem(res(429, { "Retry-After": "30" }), { retryAfterSeconds: 45 }).retryAfterSeconds,
    ).toBe(45);
    expect(parseProblem(res(429, { "Retry-After": "30" }), {}).retryAfterSeconds).toBe(30);
    expect(
      parseProblem(res(429, { "Retry-After": "soon" }), { retryAfterSeconds: -1 })
        .retryAfterSeconds,
    ).toBeNull();
  });
});

describe("isSessionRejected", () => {
  it.each([
    [401, ["UNAUTHORIZED"], true],
    [401, ["UNAUTHORIZED", "PASSWORD_MISMATCH"], false],
    [401, [], true],
    [403, ["FORBIDDEN"], false],
  ])("%i %j → %s", (status, traits, expected) => {
    expect(isSessionRejected(parseProblem(res(status), { traits }))).toBe(expected);
  });
});
