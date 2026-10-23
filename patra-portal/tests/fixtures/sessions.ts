import type { SessionItem } from "@/lib/identity/session-list";

/// 网关 `GET /me/sessions` 的一份响应体：本机 + 内网手机 + 属地未知的平板
export const SESSIONS_RESPONSE = {
  sessions: [
    {
      id: "723511223344556677",
      current: true,
      deviceName: "Chrome 141 · macOS",
      deviceClass: "DESKTOP",
      ipAddress: "101.88.12.4",
      ipRegion: "中国 上海",
      privateNetwork: false,
      signedInAt: "2026-10-20T01:12:00Z",
      lastActiveAt: "2026-10-23T07:59:30Z",
    },
    {
      id: "723511223344556678",
      current: false,
      deviceName: "Safari 18 · iOS",
      deviceClass: "MOBILE",
      ipAddress: "192.168.97.1",
      ipRegion: null,
      privateNetwork: true,
      signedInAt: "2026-10-18T13:40:00Z",
      lastActiveAt: "2026-10-23T06:00:00Z",
    },
    {
      id: "723511223344556679",
      current: false,
      deviceName: null,
      deviceClass: "TABLET",
      ipAddress: "101.88.12.9",
      ipRegion: null,
      privateNetwork: false,
      signedInAt: "2026-10-10T02:00:00Z",
      lastActiveAt: "2026-10-12T02:00:00Z",
    },
  ],
};

/// 门户解析后的列表
export const SESSIONS = SESSIONS_RESPONSE.sessions as SessionItem[];
