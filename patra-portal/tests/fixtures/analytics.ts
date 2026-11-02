import type { RequestFacts } from "@/lib/analytics/server/context";

/// 一次请求的埋点上下文：匿名访客、浏览器来源齐全、站内来源
export const FACTS: RequestFacts = {
  deviceId: "D".repeat(43),
  sessionId: "0193e1a3-1111-7a22-8b33-445566778899",
  token: null,
  client: { ip: "203.0.113.7", userAgent: "Mozilla/5.0 (Macintosh) Chrome/131" },
  referrerHost: null,
  isActionRequest: false,
};
