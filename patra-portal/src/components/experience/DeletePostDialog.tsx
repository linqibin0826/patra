"use client";

import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { FormAlert } from "@/components/auth/FormAlert";
import { Dialog } from "@/components/ui/dialog";
import { authPageHref } from "@/lib/auth/next-path";
import { experienceFailureMessage } from "@/lib/experience/messages";
import { deleteExperience } from "@/lib/interaction/experience-actions";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnDanger, btnSecondary } from "@/lib/portal-ui";

/// 删除投稿经验的确认（简报 §3.16）：不乐观，删完才跳转；经验已经不在（别的标签页删掉了）按已删除处理
export function DeletePostDialog({
  postId,
  open,
  onOpenChange,
  onDeleted,
}: {
  postId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onDeleted: () => void;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [deleting, setDeleting] = useState(false);
  const [failed, setFailed] = useState(false);
  const close = () => {
    setFailed(false);
    onOpenChange(false);
  };
  const confirm = async () => {
    if (deleting) {
      return;
    }
    setDeleting(true);
    setFailed(false);
    const result = await orUnavailable(deleteExperience(postId));
    setDeleting(false);
    if (result.ok || result.reason === "post-gone") {
      onDeleted();
      return;
    }
    // 删除不会丢内容，会话失效时直接去登录（spec 4.3）
    if (result.reason === "signed-out") {
      router.push(authPageHref("/login", pathname));
      return;
    }
    setFailed(true);
  };
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => (next ? onOpenChange(true) : close())}
      title="删除这条投稿经验？"
      description="删除后，它会从期刊页的列表和统计中去掉，下面的回复也不再显示。不能恢复。"
      size="sm"
      dismissible={!deleting}
      alert={
        failed ? (
          <FormAlert>{experienceFailureMessage("unavailable", "delete", "")}</FormAlert>
        ) : undefined
      }
    >
      <div className="flex justify-end gap-3">
        <button type="button" onClick={close} disabled={deleting} className={btnSecondary}>
          取消
        </button>
        <button
          type="button"
          onClick={() => void confirm()}
          disabled={deleting}
          className={btnDanger}
        >
          {deleting ? "删除中…" : "删除投稿经验"}
        </button>
      </div>
    </Dialog>
  );
}
