import { describe, expect, it } from "vitest";
import { normalizeSuggestQuery, suggestQueryLength } from "@/lib/suggest-query";

describe("normalizeSuggestQuery", () => {
  it.each([
    ["  北京　协和 ", "北京 协和"],
    ["ＡＢ", "AB"],
    ["heart   failure", "heart failure"],
    ["", ""],
  ])("%j → %j", (raw, normalized) => {
    expect(normalizeSuggestQuery(raw)).toBe(normalized);
  });

  it("长度按码点计", () => {
    expect(suggestQueryLength("协和")).toBe(2);
    expect(suggestQueryLength("😀")).toBe(1);
  });
});
