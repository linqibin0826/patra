"use client";

import { useUnreadStream } from "@/lib/notifications/stream";

/// 未读数实时推送的挂载点（PAP-149 spec 第 5.3 节）：放在根布局里常驻，跨板块跳转不断线（TopNav 是各页面自己挂的，
/// 挂在那里每次跳转都会断开重连）。不渲染任何东西
export function NotificationStream() {
  useUnreadStream();
  return null;
}
