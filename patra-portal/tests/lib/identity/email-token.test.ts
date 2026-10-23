import { describe, expect, it } from "vitest";
import { readFragmentToken } from "@/lib/identity/email-token";

const TOKEN = "A".repeat(20) + "b_-".repeat(7) + "z".repeat(2);

describe("readFragmentToken", () => {
  it("合法的 43 位令牌原样返回", () => {
    expect(TOKEN).toHaveLength(43);
    expect(readFragmentToken(`#token=${TOKEN}`)).toBe(TOKEN);
  });

  it("rejects_token_with_trailing_character", () => {
    expect(readFragmentToken(`#token=${TOKEN}x`)).toBeNull();
    expect(readFragmentToken(`#token=${TOKEN.slice(1)}`)).toBeNull();
    expect(readFragmentToken(`#token=${TOKEN.slice(1)}.`)).toBeNull();
  });

  it("ignores_other_fragment_params", () => {
    expect(readFragmentToken(`#x=1&token=${TOKEN}`)).toBe(TOKEN);
    expect(readFragmentToken(`#TOKEN=${TOKEN}`)).toBeNull();
  });

  it.each(["", "#", "#token=", "#foo=bar"])("%j：null", (hash) => {
    expect(readFragmentToken(hash)).toBeNull();
  });
});
