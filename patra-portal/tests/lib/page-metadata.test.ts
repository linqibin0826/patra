// @vitest-environment node
import { makePublicCollection } from "@tests/fixtures/interaction";
import { makePaperDetail } from "@tests/fixtures/paper";
import { makeVenueDetail } from "@tests/fixtures/venue";
import { describe, expect, it } from "vitest";
import { collectionMetadata, journalMetadata, paperMetadata } from "@/lib/page-metadata";
import type { Author } from "@/types/portal";

const PAPER_URL = "http://h:4010/papers/1";

function author(order: number, name: string): Author {
  return { order, first: order === 1, corresponding: false, name, affiliation: null };
}

describe("paperMetadata", () => {
  it("paper title uses short main title", () => {
    const meta = paperMetadata(makePaperDetail({ title: "Heart failure: a review." }), PAPER_URL);
    expect(meta.title).toBe("Heart failure · Patra");
    expect(meta.openGraph).toMatchObject({
      title: "Heart failure",
      type: "article",
      siteName: "Patra",
    });
  });

  it("uses structured abstract sections joined", () => {
    const meta = paperMetadata(
      makePaperDetail({
        abstractSections: [
          { label: "BACKGROUND", text: "A." },
          { label: "RESULTS", text: "B." },
        ],
        abstractPlainText: "ignored",
      }),
      PAPER_URL,
    );
    expect(meta.description).toBe("A. B.");
  });

  it("strips inline markup and collapses whitespace", () => {
    const meta = paperMetadata(
      makePaperDetail({ abstractPlainText: "<i>E. coli</i>\n\n infection" }),
      PAPER_URL,
    );
    expect(meta.description).toBe("E. coli infection");
    expect(meta.openGraph).toMatchObject({ description: "E. coli infection" });
  });

  it("truncates by grapheme with ellipsis", () => {
    const cjk = paperMetadata(makePaperDetail({ abstractPlainText: "心".repeat(201) }), PAPER_URL);
    expect(cjk.description).toBe(`${"心".repeat(160)}…`);
    const emoji = paperMetadata(
      makePaperDetail({ abstractPlainText: "👨‍⚕️".repeat(161) }),
      PAPER_URL,
    );
    expect(emoji.description).toBe(`${"👨‍⚕️".repeat(160)}…`);
    const exact = paperMetadata(
      makePaperDetail({ abstractPlainText: "心".repeat(160) }),
      PAPER_URL,
    );
    expect(exact.description).toBe("心".repeat(160));
  });

  it("falls back to journal, date and first three authors", () => {
    const meta = paperMetadata(
      makePaperDetail({
        abstractPlainText: null,
        venueName: "Lancet",
        publishedDate: "2019-03",
        authors: [author(2, "B"), author(1, "A"), author(4, "D"), author(3, "C")],
      }),
      PAPER_URL,
    );
    expect(meta.description).toBe("Lancet · 2019 年 3 月 · A, B, C 等");
  });

  it("omits description when nothing to say", () => {
    const meta = paperMetadata(
      makePaperDetail({
        abstractPlainText: null,
        venueName: null,
        publishedDate: null,
        publicationYear: null,
        authors: [],
      }),
      PAPER_URL,
    );
    expect(meta).not.toHaveProperty("description");
    expect(meta.openGraph).not.toHaveProperty("description");
  });

  it("canonical and og url are the absolute address", () => {
    const meta = paperMetadata(makePaperDetail(), PAPER_URL);
    expect(meta.alternates?.canonical).toBe(PAPER_URL);
    expect(meta.openGraph).toMatchObject({ url: PAPER_URL });
  });
});

describe("journalMetadata", () => {
  it("journal description with issn", () => {
    const url = "http://h/journals/1";
    const meta = journalMetadata(makeVenueDetail({ title: "Lancet", issnL: "0140-6736" }), url);
    expect(meta.title).toBe("Lancet · Patra");
    expect(meta.description).toBe("Lancet（ISSN 0140-6736）");
    expect(meta.alternates?.canonical).toBe(url);
    expect(meta.openGraph).toMatchObject({ title: "Lancet", type: "website", url });
    expect(journalMetadata(makeVenueDetail({ title: "Lancet" }), url).description).toBe("Lancet");
  });
});

describe("collectionMetadata", () => {
  const url = "http://h/collections/9";

  it("collection title and description", () => {
    const empty = collectionMetadata(makePublicCollection({ description: null }), url, true);
    expect(empty.title).toBe("组会 · 陈宇的收藏夹 · Patra");
    expect(empty.description).toBe("陈宇在 Patra 公开的收藏夹，共 23 篇文献。");
    expect(empty.alternates?.canonical).toBe(url);
    expect(empty.openGraph).toMatchObject({ title: "组会 · 陈宇的收藏夹", type: "website", url });

    const long = collectionMetadata(
      makePublicCollection({ description: "文".repeat(130) }),
      url,
      true,
    );
    expect(long.description).toBe(`${"文".repeat(120)}…`);

    const orphan = collectionMetadata(
      makePublicCollection({ description: null, owner: null }),
      url,
      true,
    );
    expect(orphan.title).toBe("组会 · 收藏夹 · Patra");
    expect(orphan.description).toBe("Patra 上公开的收藏夹，共 23 篇文献。");
  });

  it("collection noindex follows owner", () => {
    expect(collectionMetadata(makePublicCollection(), url, false).robots).toEqual({
      index: false,
    });
    expect(collectionMetadata(makePublicCollection(), url, true)).not.toHaveProperty("robots");
  });
});
