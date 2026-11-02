"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Check } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ClearHistoryDialog } from "@/components/history/ClearHistoryDialog";
import { type SaveState, SaveStatus } from "@/components/settings/SaveStatus";
import { SettingsCard } from "@/components/settings/SettingsCard";
import { setReadingRecording } from "@/lib/analytics/history-actions";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnSecondary, SWITCH_INPUT } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";

const PAGE = "/settings/privacy";
/// 「已保存」「已清空」显示 2 秒后淡出
const NOTICE_MS = 2_000;

/// 设置 › 隐私的第四张卡片「阅读记录」（简报 v0.18 §3.8）：开关改了立即单独保存（阅读记录在行为数据服务，不跟上面
/// 三项一起提交），快速连点时按顺序串行提交，界面停在最后一次点的值，最后一次失败时回到服务端最后确认的值。
/// 行为数据服务取不到时只有这张卡片降级
export function ReadingRecordCard({ initial }: { initial: { enabled: boolean } | "unavailable" }) {
  const router = useRouter();
  if (initial === "unavailable") {
    return (
      <SettingsCard id="reading" title="阅读记录">
        <div className="-mt-2 flex flex-col items-start gap-3">
          <p className="text-sm leading-5 text-fg-2">
            阅读记录设置暂时打不开，请稍后再试。上面的设置不受影响。
          </p>
          <button
            type="button"
            onClick={() => router.refresh()}
            className={cn(btnSecondary, "px-3 py-1.5 text-sm")}
          >
            重试
          </button>
        </div>
      </SettingsCard>
    );
  }
  return <RecordingSwitch initial={initial.enabled} />;
}

function RecordingSwitch({ initial }: { initial: boolean }) {
  const client = useQueryClient();
  const leave = useCommonFailureRedirect(PAGE);
  const [shown, setShown] = useState(initial);
  const [status, setStatus] = useState<SaveState | "cleared" | undefined>();
  const [clearing, setClearing] = useState(false);
  const confirmed = useRef(initial);
  const latest = useRef(0);
  const queue = useRef<Promise<void>>(Promise.resolve());
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => () => clearTimeout(timer.current), []);

  function show(next: SaveState | "cleared" | undefined) {
    clearTimeout(timer.current);
    setStatus(next);
    if (next === "saved" || next === "cleared") {
      timer.current = setTimeout(() => setStatus(undefined), NOTICE_MS);
    }
  }

  function change(enabled: boolean) {
    setShown(enabled);
    show("saving");
    latest.current += 1;
    const sequence = latest.current;
    queue.current = queue.current
      .then(async () => {
        const result = await orUnavailable(setReadingRecording(enabled));
        if (result.ok) {
          confirmed.current = result.enabled;
          client.removeQueries({ queryKey: queryKeys.readingHistory });
        } else if (result.reason === "signed-out") {
          leave("signed-out");
          return;
        }
        if (sequence !== latest.current) {
          return;
        }
        setShown(confirmed.current);
        show(result.ok ? "saved" : "failed");
      })
      .catch(() => {});
  }

  return (
    <SettingsCard
      id="reading"
      title="阅读记录"
      aside={
        status === "cleared" ? (
          <span
            aria-live="polite"
            className="inline-flex items-center gap-1.5 text-sm leading-5 text-status-success-ink"
          >
            <Check className="size-3.5" strokeWidth={2} aria-hidden="true" />
            已清空
          </span>
        ) : (
          <SaveStatus status={status} />
        )
      }
    >
      <label className="-mt-2 flex cursor-pointer items-start gap-3 py-2">
        <input
          type="checkbox"
          role="switch"
          aria-checked={shown}
          checked={shown}
          onChange={(event) => change(event.target.checked)}
          className={`${SWITCH_INPUT} mt-0.5`}
        />
        <div className="flex flex-col gap-0.5">
          <div className="text-base leading-5 font-medium text-fg-1">记录我的阅读历史</div>
          <div className="text-sm leading-5 text-fg-3">
            开启后，你打开过的文献会记到「阅读历史」里，只有你自己能看到，保留 12
            个月。关闭后不再记录，已有的记录保留。
          </div>
          {!shown && (
            <div className="text-sm leading-5 text-fg-2">
              已关闭：现在打开的文献不会记录。之前的记录还在阅读历史里，需要的话可以清空。
            </div>
          )}
        </div>
      </label>
      <p className="-mt-2 text-sm leading-5 text-fg-3">
        文献页上的浏览数是汇总后的人数，不显示是谁，这个开关不影响它们。采集了什么、保留多久，见
        <Link href="/privacy" className="text-link hover:text-link-hover">
          隐私政策
        </Link>
        。
      </p>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border-subtle pt-4">
        <Link
          href="/history"
          className="inline-flex items-center gap-1 text-sm font-medium text-link hover:text-link-hover"
        >
          查看阅读历史
          <ArrowRight className="size-3.5" strokeWidth={1.75} aria-hidden="true" />
        </Link>
        <button
          type="button"
          onClick={() => setClearing(true)}
          className="text-sm font-medium text-status-danger hover:underline"
        >
          清空阅读历史
        </button>
      </div>
      <ClearHistoryDialog
        open={clearing}
        page={PAGE}
        recordingEnabled={shown}
        onOpenChange={setClearing}
        onCleared={() => {
          client.removeQueries({ queryKey: queryKeys.readingHistory });
          show("cleared");
        }}
      />
    </SettingsCard>
  );
}
