import { makeDetail } from "@tests/fixtures/experience";
import { describe, expect, it } from "vitest";
import { journalHref } from "@/lib/experience/links";

describe("journalHref：经验页上指向期刊页的链接（spec 8.1）", () => {
  it("期刊还在：期刊页的经验区", () => {
    expect(journalHref(makeDetail())).toBe("/journals/100#experiences");
  });

  it("期刊已失效或没有 key：null", () => {
    const detail = makeDetail();
    expect(journalHref({ ...detail, journal: { ...detail.journal, state: "DELETED" } })).toBeNull();
    expect(journalHref({ ...detail, journal: { ...detail.journal, key: null } })).toBeNull();
  });
});
