"use client";

import { ArrowUpRight, MailWarning } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { RadioRow } from "@/components/settings/RadioRow";
import { type SaveState, SaveStatus } from "@/components/settings/SaveStatus";
import { SettingsCard } from "@/components/settings/SettingsCard";
import type { CurrentUser } from "@/lib/auth/types";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { type SavePrivacyResult, savePrivacy } from "@/lib/identity/profile-actions";
import type { PrivacySettings } from "@/lib/identity/profile-schema";
import { SWITCH_INPUT } from "@/lib/portal-ui";

const PAGE = "/settings/privacy";
/// 「已保存」显示 2 秒后淡出
const SAVED_MS = 2_000;

/// 三组设置
type Group = "profile" | "professional" | "indexing";

const PROFILE_OPTIONS = [
  { value: "PUBLIC", title: "公开", description: "任何人都能看到，包括没有登录的访客" },
  { value: "SIGNED_IN", title: "仅登录用户", description: "只有登录了 Patra 的人能看到" },
  { value: "SELF", title: "仅自己", description: "别人打开你的主页会看到「没有找到这个用户」" },
] as const;

const PROFESSIONAL_OPTIONS = [
  { value: "PUBLIC", title: "公开", description: "跟随主页可见性展示身份、职称、机构和科室" },
  { value: "SELF", title: "仅自己", description: "只在你自己看主页时显示" },
] as const;

const UNAVAILABLE: SavePrivacyResult = { ok: false, reason: "unavailable" };

interface PrivacyFormProps {
  initial: PrivacySettings;
  user: CurrentUser;
}

/// 隐私设置（简报 §3.12）：每次改动立即保存（三项一起 PUT），控件所在卡片旁显示保存中 / 已保存 / 失败，
/// 不出 toast。快速连续改动时按顺序串行提交；界面停在最后一次点的值，全部回来后若最后一次失败，回到服务端
/// 最后确认的值。
export function PrivacyForm({ initial, user }: PrivacyFormProps) {
  const leave = useCommonFailureRedirect(PAGE);
  const [shown, setShown] = useState(initial);
  const [status, setStatus] = useState<Partial<Record<Group, SaveState>>>({});
  const confirmed = useRef(initial);
  const queue = useRef<Promise<void>>(Promise.resolve());
  /// 最近一次发出的请求序号，以及每组最近一次改动的请求序号
  const latest = useRef(0);
  const groupLatest = useRef<Partial<Record<Group, number>>>({});
  const statusRef = useRef<Partial<Record<Group, SaveState>>>({});
  const timers = useRef<Partial<Record<Group, ReturnType<typeof setTimeout>>>>({});

  useEffect(() => {
    const pending = timers.current;
    return () => Object.values(pending).forEach(clearTimeout);
  }, []);

  function mark(group: Group, next: SaveState | undefined) {
    clearTimeout(timers.current[group]);
    statusRef.current = { ...statusRef.current, [group]: next };
    setStatus(statusRef.current);
    if (next === "saved") {
      timers.current[group] = setTimeout(() => mark(group, undefined), SAVED_MS);
    }
  }

  /// 一次请求回来了。每次 PUT 都是三项整份，所以一次成功也确认了此前各组的改动：那些还在「保存中」或
  /// 「失败」的组一并标成已保存。失败时如果后面还有请求在途（它们带着这一组的值），先保持「保存中」，
  /// 等后面的结果；没有了才显示失败、回到服务端最后确认的值。
  function settle(result: SavePrivacyResult, group: Group, sequence: number) {
    if (result.ok) {
      confirmed.current = result.settings;
      if (sequence === latest.current) {
        setShown(result.settings);
      }
      for (const [other, otherSequence] of Object.entries(groupLatest.current) as [
        Group,
        number,
      ][]) {
        const settled =
          statusRef.current[other] === "saving" || statusRef.current[other] === "failed";
        if (otherSequence <= sequence && (other === group || settled)) {
          mark(other, "saved");
        }
      }
      return;
    }
    if (result.reason === "pending-deletion" || result.reason === "signed-out") {
      leave(result.reason);
      return;
    }
    if (sequence === latest.current) {
      setShown(confirmed.current);
      for (const [other, otherSequence] of Object.entries(groupLatest.current) as [
        Group,
        number,
      ][]) {
        if (statusRef.current[other] === "saving" && otherSequence <= sequence) {
          mark(other, "failed");
        }
      }
    }
  }

  function change(group: Group, next: PrivacySettings) {
    setShown(next);
    mark(group, "saving");
    latest.current += 1;
    const sequence = latest.current;
    groupLatest.current = { ...groupLatest.current, [group]: sequence };
    queue.current = queue.current
      .then(async () => {
        const result = await savePrivacy(next).catch(() => UNAVAILABLE);
        settle(result, group, sequence);
      })
      .catch(() => {});
  }

  return (
    <div className="flex flex-col gap-8 max-[880px]:gap-6">
      {!user.emailVerified && (
        <div className="flex items-start gap-2.5 rounded-lg bg-status-warning-bg px-3.5 py-3 text-base leading-5 text-fg-1">
          <MailWarning
            className="mt-0.5 size-4 shrink-0 text-status-warning-ink"
            aria-hidden="true"
          />
          <span>你的邮箱还没验证：验证之前，不论下面怎么设置，主页都只有你能看到。</span>
        </div>
      )}

      <SettingsCard title="主页可见性" aside={<SaveStatus status={status.profile} />}>
        <div role="radiogroup" aria-label="主页可见性" className="-mt-2 flex flex-col">
          {PROFILE_OPTIONS.map((option) => (
            <RadioRow
              key={option.value}
              name="profileVisibility"
              title={option.title}
              description={option.description}
              checked={shown.profileVisibility === option.value}
              onSelect={() => change("profile", { ...shown, profileVisibility: option.value })}
            />
          ))}
        </div>
      </SettingsCard>

      <SettingsCard title="专业背景可见性" aside={<SaveStatus status={status.professional} />}>
        <div role="radiogroup" aria-label="专业背景可见性" className="-mt-2 flex flex-col">
          {PROFESSIONAL_OPTIONS.map((option) => (
            <RadioRow
              key={option.value}
              name="professionalVisibility"
              title={option.title}
              description={option.description}
              checked={shown.professionalVisibility === option.value}
              onSelect={() =>
                change("professional", { ...shown, professionalVisibility: option.value })
              }
            />
          ))}
        </div>
      </SettingsCard>

      <SettingsCard title="搜索引擎" aside={<SaveStatus status={status.indexing} />}>
        <label className="-mt-2 flex cursor-pointer items-start gap-3 py-2">
          <input
            type="checkbox"
            role="switch"
            aria-checked={shown.searchIndexing}
            checked={shown.searchIndexing}
            onChange={(event) =>
              change("indexing", { ...shown, searchIndexing: event.target.checked })
            }
            className={`${SWITCH_INPUT} mt-0.5`}
          />
          <div className="flex flex-col gap-0.5">
            <div className="text-base leading-5 font-medium text-fg-1">允许搜索引擎收录主页</div>
            <div className="text-sm leading-5 text-fg-3">
              开启后，搜索引擎可以收录你的主页。只有主页可见性为「公开」时才会被收录。
            </div>
            {shown.profileVisibility !== "PUBLIC" && (
              <div className="text-sm leading-5 text-amber-700">
                当前主页不是公开的，开启也不会被收录
              </div>
            )}
          </div>
        </label>
      </SettingsCard>

      <Link
        href={`/u/${user.username}`}
        className="inline-flex items-center gap-1 self-start text-sm font-medium text-link transition-colors hover:text-link-hover"
      >
        查看我的主页
        <ArrowUpRight className="size-3.5" strokeWidth={1.75} aria-hidden="true" />
      </Link>
    </div>
  );
}
