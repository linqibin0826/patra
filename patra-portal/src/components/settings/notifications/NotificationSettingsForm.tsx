"use client";

import { Bell, Lock } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { RadioRow } from "@/components/settings/RadioRow";
import { type SaveState, SaveStatus } from "@/components/settings/SaveStatus";
import { SettingsCard } from "@/components/settings/SettingsCard";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import {
  CATEGORY_COPY,
  DEFAULT_MODE,
  SETTINGS_MESSAGES as M,
  MODE_OPTIONS,
} from "@/lib/notification/messages";
import { type SaveModeResult, saveNotificationMode } from "@/lib/notification/preference-actions";
import type {
  NotificationMode,
  NotificationPreferences,
  PreferenceCategory,
} from "@/lib/notification/schema";

const PAGE = "/settings/notifications";
/// 「已保存」显示 2 秒后淡出（同隐私页）
const SAVED_MS = 2_000;

/// 偏好里的一类：`PreferenceCategory` 与响应字段的对应
type Key = keyof NotificationPreferences;
const KEYS: readonly { key: Key; category: PreferenceCategory }[] = [
  { key: "reply", category: "REPLY" },
  { key: "mention", category: "MENTION" },
];
const otherOf = (key: Key): Key => (key === "reply" ? "mention" : "reply");

const UNAVAILABLE: SaveModeResult = { ok: false, reason: "unavailable" };

interface NotificationSettingsFormProps {
  initial: NotificationPreferences;
  /// 收件地址（`/auth/me` 的邮箱，不另取）
  email: string;
}

/// 设置 › 通知（简报 §3.5，spec 第 8 节）：「回复我的」「@ 我的」各三档，改了立即保存这一类，卡片标题右侧显示
/// 保存中 / 已保存 / 失败，不出 toast。Next 本来就把 Server Action 逐个派发，所以两类排一条队、按点的顺序发；
/// 一类连点时界面停在最后一次点的值，这一类最后一次的结果回来才收尾，失败回到服务端最后确认的值。响应带回两类
/// 当前的档位：另一类没有排队或在途的改动时一并采用（比如它刚在邮件里被退订）。
export function NotificationSettingsForm({ initial, email }: NotificationSettingsFormProps) {
  const leave = useCommonFailureRedirect(PAGE);
  const [shown, setShown] = useState(initial);
  const [status, setStatus] = useState<Partial<Record<Key, SaveState>>>({});
  const confirmed = useRef(initial);
  const queue = useRef<Promise<void>>(Promise.resolve());
  /// 请求序号，以及每类最近一次改动的序号、排队或在途的请求数
  const sequence = useRef(0);
  const latest = useRef<Partial<Record<Key, number>>>({});
  const pending = useRef<Record<Key, number>>({ reply: 0, mention: 0 });
  const timers = useRef<Partial<Record<Key, ReturnType<typeof setTimeout>>>>({});

  useEffect(() => {
    const scheduled = timers.current;
    return () => Object.values(scheduled).forEach(clearTimeout);
  }, []);

  function mark(key: Key, next: SaveState | undefined) {
    clearTimeout(timers.current[key]);
    setStatus((previous) => ({ ...previous, [key]: next }));
    if (next === "saved") {
      timers.current[key] = setTimeout(() => mark(key, undefined), SAVED_MS);
    }
  }

  function show(key: Key, mode: NotificationMode) {
    setShown((previous) => ({ ...previous, [key]: mode }));
  }

  /// 一次请求回来了
  function settle(result: SaveModeResult, key: Key, ticket: number) {
    pending.current[key] -= 1;
    const isLatest = ticket === latest.current[key];
    if (result.ok) {
      confirmed.current = { ...confirmed.current, [key]: result.preferences[key] };
      const other = otherOf(key);
      if (pending.current[other] === 0) {
        confirmed.current = { ...confirmed.current, [other]: result.preferences[other] };
        show(other, result.preferences[other]);
      }
      if (isLatest) {
        show(key, result.preferences[key]);
        mark(key, "saved");
      }
      return;
    }
    if (result.reason === "signed-out") {
      leave("signed-out");
      return;
    }
    if (isLatest) {
      show(key, confirmed.current[key]);
      mark(key, "failed");
    }
  }

  function change(key: Key, category: PreferenceCategory, mode: NotificationMode) {
    show(key, mode);
    mark(key, "saving");
    sequence.current += 1;
    const ticket = sequence.current;
    latest.current = { ...latest.current, [key]: ticket };
    pending.current[key] += 1;
    queue.current = queue.current
      .then(async () => {
        const result = await saveNotificationMode(category, mode).catch(() => UNAVAILABLE);
        settle(result, key, ticket);
      })
      .catch(() => {});
  }

  return (
    <div className="flex flex-col gap-8 max-[880px]:gap-6">
      <p className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1 text-sm leading-5 text-fg-3">
        <span>{M.sendTo}</span>
        <span className="font-mono text-fg-1 [overflow-wrap:anywhere]">{email}</span>
        <Link
          href="/settings/account#email"
          className="font-medium text-link transition-colors hover:text-link-hover"
        >
          {M.changeEmail}
        </Link>
      </p>

      {KEYS.map(({ key, category }) => {
        const copy = CATEGORY_COPY[category];
        return (
          <SettingsCard
            key={key}
            title={copy.title}
            description={copy.description}
            aside={<SaveStatus status={status[key]} />}
          >
            <div
              role="radiogroup"
              aria-label={copy.groupLabel}
              className="-mt-2 flex flex-col divide-y divide-border-subtle"
            >
              {MODE_OPTIONS.map((option) => (
                <RadioRow
                  key={option.mode}
                  name={key}
                  title={option.title}
                  description={option.description}
                  badge={option.mode === DEFAULT_MODE ? M.defaultBadge : undefined}
                  checked={shown[key] === option.mode}
                  onSelect={() => change(key, category, option.mode)}
                  className="py-3"
                />
              ))}
            </div>
          </SettingsCard>
        );
      })}

      <SettingsCard title={M.helpfulTitle} description={M.helpfulDescription}>
        <div className="-mt-1 flex items-start gap-3">
          <span className="inline-flex h-6 shrink-0 items-center gap-1.5 rounded-full bg-paper-200 px-2.5 text-xs font-medium whitespace-nowrap text-fg-2">
            <Bell className="size-3.25" strokeWidth={2} aria-hidden="true" />
            {M.helpfulPill}
          </span>
          <span className="pt-0.5 text-sm leading-5 text-fg-3">{M.helpfulNote}</span>
        </div>
      </SettingsCard>

      <div className="flex items-start gap-3 rounded-lg border border-border-subtle bg-paper-100 px-4.5 py-4">
        <Lock className="mt-px size-4.5 shrink-0 text-fg-3" strokeWidth={1.75} aria-hidden="true" />
        <div>
          <p className="text-base leading-5 font-medium text-fg-1">{M.securityTitle}</p>
          <p className="mt-1 text-sm leading-5 text-fg-3">{M.securityNote}</p>
        </div>
      </div>
    </div>
  );
}
