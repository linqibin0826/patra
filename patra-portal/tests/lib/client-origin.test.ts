// @vitest-environment node
import { describe, expect, it, vi } from "vitest";
import { clientOriginFromHeaders, readClientOrigin } from "@/lib/client-origin";

vi.mock("next/headers", () => ({
  headers: async () => new Headers({ "x-forwarded-for": "10.0.0.7", "user-agent": "UA/2" }),
}));

const ipOf = (value: string | null) =>
  clientOriginFromHeaders(new Headers(value === null ? {} : { "x-forwarded-for": value })).ip;

describe("clientOriginFromHeaders", () => {
  it.each([
    ["192.168.97.1", "192.168.97.1"],
    ["::1", "::1"],
    ["::ffff:10.0.0.2", "10.0.0.2"],
    ["fe80::1%eth0", "fe80::1"],
    [" 10.0.0.3 ", "10.0.0.3"],
    ["2408:8207::1", "2408:8207::1"],
  ])("把 %j 认成 %j", (raw, expected) => {
    expect(ipOf(raw)).toBe(expected);
  });

  it.each([["203.0.113.9, 10.0.0.1"], ["evil.example.com"], [""], ["10.0.0.1:443"], ["[::1]"]])(
    "%j 不是单个 IP 字面量：为 null",
    (raw) => {
      expect(ipOf(raw)).toBeNull();
    },
  );

  it("没有这个头：为 null", () => {
    expect(ipOf(null)).toBeNull();
  });

  it("User-Agent 原样保留", () => {
    const origin = clientOriginFromHeaders(
      new Headers({ "user-agent": "Mozilla/5.0 (Macintosh) Chrome/141" }),
    );
    expect(origin.userAgent).toBe("Mozilla/5.0 (Macintosh) Chrome/141");
  });

  it("没有 User-Agent：为 null", () => {
    expect(clientOriginFromHeaders(new Headers()).userAgent).toBeNull();
  });

  it("User-Agent 放不进请求头（码点超过 0xFF）：为 null", () => {
    const headers = { get: (name: string) => (name === "user-agent" ? "UA 浏览器" : null) };
    expect(clientOriginFromHeaders(headers as unknown as Headers).userAgent).toBeNull();
  });
});

describe("readClientOrigin", () => {
  it("从当前请求的请求头里取", async () => {
    await expect(readClientOrigin()).resolves.toEqual({ ip: "10.0.0.7", userAgent: "UA/2" });
  });
});
