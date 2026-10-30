import { describe, expect, it } from "vitest";
import { parseExperienceSort } from "@/lib/experience/sort";

describe("parseExperienceSort", () => {
  it.each([
    ["helpful", "helpful"],
    ["latest", "latest"],
    ["hot", "latest"],
    [null, "latest"],
    [["helpful"], "latest"],
  ])("%o → %s", (value, sort) => {
    expect(parseExperienceSort(value)).toBe(sort);
  });
});
