import { Laptop, type LucideIcon, MonitorSmartphone, Smartphone, Tablet } from "lucide-react";
import { StatusPill } from "@/components/ui/status-pill";
import { SESSIONS_MESSAGES } from "@/lib/auth/messages";
import { formatDateTime, formatRelative } from "@/lib/format-time";
import type { SessionItem } from "@/lib/identity/session-list";
import { btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 按「端」选图标（简报 §3.11）
const ICONS: Record<SessionItem["deviceClass"], LucideIcon> = {
  DESKTOP: Laptop,
  MOBILE: Smartphone,
  TABLET: Tablet,
  UNKNOWN: MonitorSmartphone,
};

interface SessionRowProps {
  session: SessionItem;
  /// 计算「最后活跃」用的当前时间
  now: number;
  /// 正在退出这一行
  busy: boolean;
  /// 退出成功、正在淡出
  leaving: boolean;
  /// 本机：退出登录；其他：踢掉
  onAction: () => void;
}

/// 设备名：空或全是空白时是「未知设备」
const deviceName = (session: SessionItem) =>
  session.deviceName?.trim() || SESSIONS_MESSAGES.unknownDevice;

/// 第二行：内网 / 属地 / 属地未知（空或全是空白也算未知），IP 有才接「 · IP」
function whereText(session: SessionItem): string {
  const region = session.privateNetwork
    ? SESSIONS_MESSAGES.privateNetwork
    : session.ipRegion?.trim() || SESSIONS_MESSAGES.unknownRegion;
  return session.ipAddress ? `${region} · ${session.ipAddress}` : region;
}

/// 登录设备的一行：端图标、设备名（本机带胶囊）、属地与 IP、登录时间与最后活跃；右侧本机「退出登录」、
/// 其他「退出」（危险色文字按钮，读屏名称带设备名）。
export function SessionRow({ session, now, busy, leaving, onAction }: SessionRowProps) {
  const Icon = ICONS[session.deviceClass];
  const name = deviceName(session);
  return (
    <li
      className={cn(
        "grid grid-cols-[40px_minmax(0,1fr)_auto] items-start gap-3.5 py-4 not-first:border-t not-first:border-border-subtle motion-safe:transition-opacity motion-safe:duration-(--dur-normal) max-[880px]:grid-cols-[36px_minmax(0,1fr)]",
        leaving && "opacity-40",
      )}
    >
      <span
        aria-hidden="true"
        className="inline-flex size-10 items-center justify-center rounded-lg bg-bg-sunken text-fg-2 max-[880px]:size-9"
      >
        <Icon className="size-4.5" strokeWidth={1.75} />
      </span>
      <div className="min-w-0">
        <div className="flex items-center gap-2 text-base leading-5 font-medium text-fg-1">
          <span className="truncate">{name}</span>
          {session.current && <StatusPill tone="accent">{SESSIONS_MESSAGES.here}</StatusPill>}
        </div>
        <p className="mt-0.75 text-sm leading-5 text-fg-3">{whereText(session)}</p>
        <p className="text-sm leading-5 text-fg-3">
          登录于 {formatDateTime(session.signedInAt)} ·{" "}
          {session.current
            ? SESSIONS_MESSAGES.online
            : `最后活跃 ${formatRelative(session.lastActiveAt, now)}`}
        </p>
      </div>
      <div className="max-[880px]:col-start-2">
        {session.current ? (
          <button type="button" onClick={onAction} className={cn(btnText, "min-h-8 text-sm")}>
            {SESSIONS_MESSAGES.logout}
          </button>
        ) : (
          <button
            type="button"
            onClick={onAction}
            aria-label={`${SESSIONS_MESSAGES.revoke} ${name}`}
            aria-disabled={busy || leaving || undefined}
            className="min-h-8 text-sm font-medium text-status-danger transition-colors aria-disabled:cursor-default aria-disabled:text-fg-3"
          >
            {busy ? SESSIONS_MESSAGES.revoking : SESSIONS_MESSAGES.revoke}
          </button>
        )}
      </div>
    </li>
  );
}
