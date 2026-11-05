import type { PageType } from "@/lib/analytics/types";

/// 一段路径之后可以是任何东西的页面：第一段 → 页面类型
const SECTIONS: Record<string, PageType> = {
  journals: "journal",
  library: "library",
  u: "profile",
  settings: "settings",
  account: "settings",
  notifications: "notifications",
};

/// 只有这一段的页面
const SINGLE: Record<string, PageType> = {
  history: "history",
  privacy: "privacy",
  login: "auth",
  register: "auth",
  "forgot-password": "auth",
  "reset-password": "auth",
  "verify-email": "auth",
  "confirm-email-change": "auth",
  "revert-email-change": "auth",
};

/// 恰好两段（`/段/ID`）的详情页
const DETAIL: Record<string, PageType> = {
  papers: "article",
  collections: "collection",
  posts: "post",
};

/// 路径 → 事件里的 `page_type`（spec §5.1）。尾斜杠不影响结果，认不出的是 `other`
export function pageTypeOf(pathname: string): PageType {
  const segments = pathname.split("/").filter((segment) => segment !== "");
  const [first, second] = segments;
  if (first === undefined) {
    return "home";
  }
  if (segments.length === 1) {
    if (first === "papers") {
      return "search";
    }
    return SINGLE[first] ?? SECTIONS[first] ?? "other";
  }
  if (segments.length === 2 && second !== undefined && Object.hasOwn(DETAIL, first)) {
    return DETAIL[first] ?? "other";
  }
  return Object.hasOwn(SECTIONS, first) ? (SECTIONS[first] ?? "other") : "other";
}
