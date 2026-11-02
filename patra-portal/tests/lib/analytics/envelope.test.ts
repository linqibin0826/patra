// @vitest-environment node
import { FACTS } from "@tests/fixtures/analytics";
import { validateAgainst } from "@tests/support/analytics-schema";
import { describe, expect, it } from "vitest";
import { buildServerEnvelope, toCollectEnvelope } from "@/lib/analytics/server/envelope";
import type { ServerEventInput } from "@/lib/analytics/types";

const NOW = new Date("2026-11-02T08:30:12.345Z");
const PAGE = { path: "/papers/42", type: "article" } as const;

const EVENTS: ServerEventInput[] = [
  { name: "article_viewed", properties: { article_id: "42" } },
  {
    name: "search_performed",
    properties: {
      search_id: "0193e1a3-2222-7a22-8b33-445566778899",
      mode: "keyword",
      query: "sepsis",
      filters: {
        author: "Marik",
        year_from: 2017,
        year_to: 2024,
        type: ["Randomized Controlled Trial"],
        evidence: ["RANDOMIZED_CONTROLLED_TRIAL"],
        lang: ["eng"],
        venue: ["7"],
        oa: true,
      },
      sort: "latest",
      page: 2,
      result_count: 0,
    },
  },
  { name: "pdf_downloaded", properties: { article_id: "42" } },
  {
    name: "export_completed",
    properties: { format: "ris", scope: "library_all", article_count: 3 },
  },
  { name: "article_collected", properties: { article_id: "42" } },
];

describe("buildServerEnvelope", () => {
  it.each(EVENTS)("$name 的信封符合 collect-envelope", (event) => {
    const envelope = buildServerEnvelope(event, FACTS, null, PAGE, NOW);
    expect(validateAgainst("collect-envelope", envelope)).toEqual([]);
    expect(envelope).toMatchObject({
      event_name: event.name,
      source: "server",
      created_at: "2026-11-02T08:30:12.345Z",
      sent_at: "2026-11-02T08:30:12.345Z",
      device_id: FACTS.deviceId,
      session_id: FACTS.sessionId,
      user_id: null,
      page_path: "/papers/42",
      page_type: "article",
      is_synthetic: false,
      client_ip: "203.0.113.7",
    });
  });

  it("登录时带用户 ID", () => {
    expect(buildServerEnvelope(EVENTS[0] as ServerEventInput, FACTS, "77", PAGE, NOW).user_id).toBe(
      "77",
    );
  });

  it("取不到 IP 时填 0.0.0.0，取不到 UA 时填空串（契约要求必填）", () => {
    const envelope = buildServerEnvelope(
      EVENTS[0] as ServerEventInput,
      { ...FACTS, client: { ip: null, userAgent: null } },
      null,
      PAGE,
      NOW,
    );
    expect(envelope.client_ip).toBe("0.0.0.0");
    expect(envelope.user_agent).toBe("");
    expect(validateAgainst("collect-envelope", envelope)).toEqual([]);
  });

  it("UA 超过 1024 个字符截断，路径超过 512 截断", () => {
    const envelope = buildServerEnvelope(
      EVENTS[0] as ServerEventInput,
      { ...FACTS, client: { ip: "203.0.113.7", userAgent: "x".repeat(2000) } },
      null,
      { path: `/${"a".repeat(600)}`, type: "other" },
      NOW,
    );
    expect(envelope.user_agent).toHaveLength(1024);
    expect(envelope.page_path).toHaveLength(512);
    expect(validateAgainst("collect-envelope", envelope)).toEqual([]);
  });

  it("外部来源写主机名", () => {
    const envelope = buildServerEnvelope(
      EVENTS[0] as ServerEventInput,
      { ...FACTS, referrerHost: "www.google.com" },
      null,
      PAGE,
      NOW,
    );
    expect(envelope.referrer_host).toBe("www.google.com");
  });
});

describe("toCollectEnvelope：浏览器事件按白名单取字段，身份全由服务端填", () => {
  const RAW = {
    event_id: "0193e1a2-7c4b-7d2e-9f10-000000000006",
    event_name: "page_viewed",
    schema_version: "1-0-0",
    created_at: "2026-11-02T08:30:10.120Z",
    page_path: "/",
    page_type: "home",
    properties: {},
  };

  it("合格的浏览器事件转出来符合 collect-envelope", () => {
    const envelope = toCollectEnvelope(RAW, "2026-11-02T08:30:12.345Z", FACTS, "77");
    expect(validateAgainst("collect-envelope", envelope)).toEqual([]);
    expect(envelope).toMatchObject({ source: "client", user_id: "77", referrer_host: null });
  });

  it("浏览器带来的身份字段一律丢掉，换成会话与 Cookie 里的值", () => {
    const envelope = toCollectEnvelope(
      {
        ...RAW,
        user_id: "1",
        device_id: "forged",
        session_id: "forged",
        is_synthetic: true,
        referrer_host: "evil.example",
        client_ip: "1.2.3.4",
        source: "server",
      },
      "2026-11-02T08:30:12.345Z",
      FACTS,
      null,
    );
    expect(envelope).toMatchObject({
      user_id: null,
      device_id: FACTS.deviceId,
      session_id: FACTS.sessionId,
      is_synthetic: false,
      referrer_host: null,
      client_ip: "203.0.113.7",
      source: "client",
    });
    expect(Object.keys(envelope).sort()).toEqual(
      [
        "event_id",
        "event_name",
        "schema_version",
        "source",
        "created_at",
        "sent_at",
        "device_id",
        "session_id",
        "user_id",
        "page_path",
        "page_type",
        "referrer_host",
        "is_synthetic",
        "properties",
        "client_ip",
        "user_agent",
      ].sort(),
    );
  });
});
