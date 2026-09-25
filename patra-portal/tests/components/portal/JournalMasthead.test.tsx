import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { JournalMasthead } from "@/components/portal/journal-detail/JournalMasthead";
import type { VenueDetail } from "@/types/portal";

function makeVenue(o: Partial<VenueDetail> = {}): VenueDetail {
  return {
    id: "1",
    title: "Nature",
    abbreviatedTitle: "Nat",
    venueType: null,
    issnL: null,
    countryCode: null,
    primaryLanguage: null,
    foundedYear: 1869,
    coverObjectKey: null,
    homepageUrl: null,
    isOpenAccess: null,
    impactFactor: null,
    jcrQuartile: null,
    jcrSubject: null,
    casMajorCategory: null,
    casMajorQuartile: null,
    casIsTop: null,
    citeScore: null,
    hIndex: null,
    citedByCount: null,
    worksCount: null,
    frequency: null,
    medlineIndexed: null,
    oaType: null,
    apcUsd: null,
    isInDoaj: null,
    jcrRatings: [],
    casRatings: [],
    scopusRatings: [],
    yearlyStats: [],
    identifiers: [],
    ...o,
  };
}

describe("JournalMasthead", () => {
  it("homepageUrl 为 null → 主操作 disabled + 待采集文案", () => {
    render(<JournalMasthead venue={makeVenue({ homepageUrl: null })} />);
    expect(screen.getByRole("button", { name: /官网链接待采集/ })).toBeDisabled();
  });

  it("homepageUrl 存在 → 渲染官网外链", () => {
    render(<JournalMasthead venue={makeVenue({ homepageUrl: "https://nature.com" })} />);
    expect(screen.getByRole("link", { name: /访问期刊官网/ })).toHaveAttribute(
      "href",
      "https://nature.com",
    );
  });

  it("封面词标来自缩写", () => {
    render(<JournalMasthead venue={makeVenue({ abbreviatedTitle: "Nat" })} />);
    expect(screen.getAllByText("Nat").length).toBeGreaterThanOrEqual(1);
  });

  it("「查看该刊文献」跳 /papers?venue=id", () => {
    render(<JournalMasthead venue={makeVenue({ id: "319041872872550658" })} />);
    expect(screen.getByRole("link", { name: /查看该刊文献/ })).toHaveAttribute(
      "href",
      "/papers?venue=319041872872550658",
    );
  });
});
