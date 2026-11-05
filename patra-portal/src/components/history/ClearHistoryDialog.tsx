"use client";

import { useRef, useState } from "react";
import { FormAlert } from "@/components/auth/FormAlert";
import { Dialog } from "@/components/ui/dialog";
import { clearHistory } from "@/lib/analytics/history-actions";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnDanger, btnSecondary } from "@/lib/portal-ui";

interface ClearHistoryDialogProps {
  open: boolean;
  /// 阅读记录是否开着：关着时说明换中间一句
  recordingEnabled: boolean;
  /// 所在页面的路径，会话过期时登录后回到这里
  page: string;
  onOpenChange: (open: boolean) => void;
  onCleared: () => void;
  /// 清空成功关闭后焦点去哪（触发按钮这时已不可用）；不传或返回 null 时回到触发按钮
  finalFocus?: () => HTMLElement | null;
}

/// 清空阅读历史的确认（简报 v0.18 §3.7 第 7 条）：阅读历史页与设置卡片共用。焦点默认在「取消」（危险操作不落在确认上）；
/// 清空中两个按钮都不可点、不能关；失败时对话框留着并提示
export function ClearHistoryDialog({
  open,
  recordingEnabled,
  page,
  onOpenChange,
  onCleared,
  finalFocus,
}: ClearHistoryDialogProps) {
  const leave = useCommonFailureRedirect(page);
  const cancel = useRef<HTMLButtonElement>(null);
  const [clearing, setClearing] = useState(false);
  const [failed, setFailed] = useState(false);

  const close = () => {
    setFailed(false);
    onOpenChange(false);
  };
  const confirm = async () => {
    setClearing(true);
    setFailed(false);
    const result = await orUnavailable(clearHistory());
    setClearing(false);
    if (result.ok) {
      onCleared();
      close();
    } else if (result.reason === "signed-out") {
      leave("signed-out");
    } else {
      setFailed(true);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && close()}
      title="清空全部阅读历史？"
      description={`已有的阅读记录会全部删除，不能恢复。${
        recordingEnabled
          ? "之后打开的文献会继续记录。"
          : "阅读记录现在是关闭的，清空后这里会一直是空的。"
      }文献页上的浏览数是汇总后的人数，不受影响。`}
      size="sm"
      dismissible={!clearing}
      initialFocus={cancel}
      finalFocus={() => finalFocus?.() ?? true}
      alert={failed ? <FormAlert>暂时没能清空，请稍后再试。</FormAlert> : undefined}
    >
      <div className="flex justify-end gap-3">
        <button
          ref={cancel}
          type="button"
          onClick={close}
          disabled={clearing}
          className={btnSecondary}
        >
          取消
        </button>
        <button
          type="button"
          onClick={() => void confirm()}
          disabled={clearing}
          className={btnDanger}
        >
          {clearing ? "清空中…" : "清空"}
        </button>
      </div>
    </Dialog>
  );
}
