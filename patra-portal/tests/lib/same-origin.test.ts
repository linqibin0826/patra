// @vitest-environment node
import { describe, expect, it } from "vitest";
import { isSameOrigin } from "@/lib/same-origin";

const req = (headers: Record<string, string>) =>
  new Request("http://localhost:4000/api/me/avatar", { method: "POST", headers });

describe("isSameOrigin", () => {
  it("Origin 的主机（含端口）等于 Host：同源", () => {
    expect(isSameOrigin(req({ origin: "http://mini:4000", host: "mini:4000" }))).toBe(true);
  });

  it("有 X-Forwarded-Host 时按它比（与 Server Action 的规则一致）", () => {
    expect(
      isSameOrigin(
        req({
          origin: "https://patra.example",
          host: "portal:4000",
          "x-forwarded-host": "patra.example",
        }),
      ),
    ).toBe(true);
  });

  it.each([
    [{ host: "mini:4000" }],
    [{ origin: "http://mini:8025", host: "mini:4000" }],
    [{ origin: "null", host: "mini:4000" }],
    [{ origin: "http://evil.example", host: "mini:4000" }],
  ])("%j：不是同源", (headers) => {
    expect(isSameOrigin(req(headers))).toBe(false);
  });
});
