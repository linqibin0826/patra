import { describe, expect, it } from "vitest";
import { formatPublishedDate, publishedYear } from "@/lib/published-date";

describe("formatPublishedDate", () => {
  it.each([
    ["2019", "2019"],
    ["2019-03", "2019 年 3 月"],
    ["2019-03-15", "2019 年 3 月 15 日"],
    ["2019-12-01", "2019 年 12 月 1 日"],
    ["2019-3", null],
    ["2019-03-15T00:00", null],
    ["", null],
    [null, null],
  ])("formatPublishedDate(%s) = %s", (input, out) => {
    expect(formatPublishedDate(input)).toBe(out);
  });
});

describe("publishedYear", () => {
  it("取出版日期的年份", () => {
    expect(publishedYear({ publishedDate: "2019-03-15", year: 2018 })).toBe("2019");
  });

  it("没有出版日期时回落到 year", () => {
    expect(publishedYear({ publishedDate: null, year: 2018 })).toBe("2018");
  });

  it("都没有时为 null", () => {
    expect(publishedYear({ publishedDate: null, year: null })).toBeNull();
  });
});
