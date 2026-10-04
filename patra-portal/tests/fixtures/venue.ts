import type { VenueDetail } from "@/types/portal";

/** 构造测试用 VenueDetail：全部可空字段默认 null / 空数组，按需覆盖。 */
export function makeVenueDetail(overrides: Partial<VenueDetail> = {}): VenueDetail {
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
    ...overrides,
  };
}
