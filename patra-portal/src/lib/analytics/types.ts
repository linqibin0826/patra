import type {
  ClientEventProperties,
  PageType,
  ServerEventProperties,
} from "@/lib/analytics/generated/events";

export type {
  ClientEventProperties,
  ListType,
  PageType,
  ServerEventProperties,
} from "@/lib/analytics/generated/events";

/// 门户服务端发的事件名
export type ServerEventName = keyof ServerEventProperties;
/// 浏览器发的事件名
export type ClientEventName = keyof ClientEventProperties;

/// 浏览器批次里的一条事件（`browser-event`）：没有任何身份字段，由门户服务端补
export type ClientEvent = {
  [K in ClientEventName]: {
    event_id: string;
    event_name: K;
    schema_version: string;
    created_at: string;
    page_path: string;
    page_type: PageType;
    properties: ClientEventProperties[K];
  };
}[ClientEventName];

/// 门户服务端要发的一个事件：事件名与它的属性
export type ServerEventInput = {
  [K in ServerEventName]: { name: K; properties: ServerEventProperties[K] };
}[ServerEventName];

/// 采集信封（`collect-envelope`）：门户发给采集端点的单条事件，字段名按契约用 snake_case
export interface CollectEnvelope {
  event_id: string;
  event_name: ServerEventName | ClientEventName;
  schema_version: string;
  source: "server" | "client";
  created_at: string;
  sent_at: string;
  device_id: string;
  session_id: string;
  user_id: string | null;
  page_path: string | null;
  page_type: PageType;
  referrer_host: string | null;
  is_synthetic: false;
  properties: unknown;
  client_ip: string;
  user_agent: string;
}
