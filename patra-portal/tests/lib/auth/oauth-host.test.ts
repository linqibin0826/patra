// @vitest-environment node
import { afterEach, describe, expect, it, vi } from "vitest";
import { isOAuthHost } from "@/lib/auth/oauth-host";

afterEach(() => vi.unstubAllEnvs());

describe("isOAuthHost", () => {
  it("默认只认 patra.linqibin.dev，不分大小写", () => {
    expect(isOAuthHost(new Headers({ host: "patra.linqibin.dev" }))).toBe(true);
    expect(isOAuthHost(new Headers({ host: "PATRA.linqibin.dev" }))).toBe(true);
  });

  it.each(["100.103.73.27:4000", "linqibins-mac-mini:4000", "localhost:4000", "evil.test"])(
    "默认不认 %s",
    (host) => {
      expect(isOAuthHost(new Headers({ host }))).toBe(false);
    },
  );

  it("先看 X-Forwarded-Host", () => {
    expect(
      isOAuthHost(
        new Headers({ host: "localhost:4000", "x-forwarded-host": "patra.linqibin.dev" }),
      ),
    ).toBe(true);
  });

  it("PATRA_OAUTH_HOST 覆盖默认值", () => {
    vi.stubEnv("PATRA_OAUTH_HOST", "localhost:4000");
    expect(isOAuthHost(new Headers({ host: "localhost:4000" }))).toBe(true);
    expect(isOAuthHost(new Headers({ host: "patra.linqibin.dev" }))).toBe(false);
  });

  it("没有 Host：false", () => {
    expect(isOAuthHost(new Headers())).toBe(false);
  });
});
