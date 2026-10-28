"use client";

import { useRef, useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { Dialog } from "@/components/ui/dialog";
import { commentFailureMessage } from "@/lib/discussion/messages";
import { deleteComment } from "@/lib/interaction/discussion-actions";
import type { Comment, DeleteResult } from "@/lib/interaction/discussion-schema";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnDanger, btnSecondary } from "@/lib/portal-ui";

interface DeleteCommentDialogProps {
  /// 要删的那条，关闭时为 null
  comment: Comment | null;
  onClose: () => void;
  onDeleted: (comment: Comment, result: DeleteResult) => void;
}

/// 删除后焦点去哪（简报 §6）：讨论区里它后面一条评论的时间链接；没有就是讨论区本身
function nextFocusAfter(comment: Comment): HTMLElement | null {
  const items = [...document.querySelectorAll<HTMLElement>('[id^="comment-"]')];
  const index = items.findIndex((item) => item.id === `comment-${comment.commentId}`);
  for (const item of items.slice(index + 1)) {
    const link = item.querySelector<HTMLElement>('a[href*="comment="]');
    if (link) {
      return link;
    }
  }
  return document.getElementById("discussion");
}

/// 删除确认（简报 §3.9）：有回复时说明会留下占位；不乐观，删完才改列表、toast「评论已删除」。评论已经不在
/// （别的标签页删掉了）按已删除处理
export function DeleteCommentDialog({ comment, onClose, onDeleted }: DeleteCommentDialogProps) {
  const [deleting, setDeleting] = useState(false);
  const [failed, setFailed] = useState(false);
  const focusAfter = useRef<HTMLElement | null>(null);
  const close = () => {
    setFailed(false);
    onClose();
  };
  const confirm = async () => {
    if (!comment || deleting) {
      return;
    }
    setDeleting(true);
    setFailed(false);
    const result = await orUnavailable(deleteComment(comment.commentId));
    setDeleting(false);
    const deleted: DeleteResult | null = result.ok
      ? { placeholder: result.placeholder, removedIds: result.removedIds }
      : result.reason === "comment-gone"
        ? { placeholder: false, removedIds: [comment.commentId] }
        : null;
    if (!deleted) {
      setFailed(true);
      return;
    }
    focusAfter.current = nextFocusAfter(comment);
    toast.success("评论已删除");
    close();
    onDeleted(comment, deleted);
  };
  return (
    <Dialog
      open={comment !== null}
      onOpenChange={(open) => !open && close()}
      title="删除这条评论？"
      description={
        comment?.hasReplies
          ? "这条评论下已经有回复。删除后会留下「该评论已删除」，回复照常保留。"
          : "删除后不能恢复。"
      }
      size="sm"
      dismissible={!deleting}
      finalFocus={() => {
        const target = focusAfter.current;
        focusAfter.current = null;
        return target ?? true;
      }}
      alert={
        failed ? <FormAlert>{commentFailureMessage("unavailable", "delete")}</FormAlert> : undefined
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
          {deleting ? "删除中…" : "删除评论"}
        </button>
      </div>
    </Dialog>
  );
}
