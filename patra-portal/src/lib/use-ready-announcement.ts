import { useEffect, useRef, useState } from "react";
import { RESEND_MESSAGES } from "@/lib/auth/messages";

/// 重发倒计时的读屏播报：每秒的数字不读，等待结束时读一次「可以重新发送了」。放进 `role="status"` 的 `sr-only` 节点
export function useReadyAnnouncement(waiting: boolean): string {
  const [announcement, setAnnouncement] = useState("");
  const wasWaiting = useRef(waiting);
  useEffect(() => {
    if (wasWaiting.current && !waiting) {
      setAnnouncement(RESEND_MESSAGES.ready);
    }
    if (waiting) {
      setAnnouncement("");
    }
    wasWaiting.current = waiting;
  }, [waiting]);
  return announcement;
}
