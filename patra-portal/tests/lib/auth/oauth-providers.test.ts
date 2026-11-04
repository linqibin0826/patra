import { describe, expect, it } from "vitest";
import { isProvider, PROVIDERS, providerName } from "@/lib/auth/oauth-providers";

describe("oauth-providers", () => {
  it("固定顺序：Google、GitHub、ORCID", () => {
    expect(PROVIDERS).toEqual(["google", "github", "orcid"]);
    expect(PROVIDERS.map(providerName)).toEqual(["Google", "GitHub", "ORCID"]);
  });

  it.each(["google", "github", "orcid"])("%s 认识", (value) => {
    expect(isProvider(value)).toBe(true);
  });

  it.each(["GITHUB", "wechat", "", "__proto__", null, undefined])("%j 不认识", (value) => {
    expect(isProvider(value)).toBe(false);
  });
});
