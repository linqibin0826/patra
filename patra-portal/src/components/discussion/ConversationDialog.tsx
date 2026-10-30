"use client";

import { type CommentActions, CommentItem } from "@/components/discussion/CommentItem";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import { Dialog } from "@/components/ui/dialog";
import { useConversation } from "@/lib/discussion/queries";
import type { Comment } from "@/lib/interaction/discussion-schema";

interface ConversationDialogProps {
  /// 打开时是这条回复的 ID，关闭时为 null
  commentId: string | null;
  now: number;
  permalinkOf: (comment: Comment) => string;
  onOpenSingle: (commentId: string) => void;
  onClose: () => void;
}

const NOOP = () => {};

/// 「查看对话」（简报 §3.3）：从根评论到这一条的回复链，当前这条高亮；只读，底部「只看这一楼」去单楼视图
export function ConversationDialog({
  commentId,
  now,
  permalinkOf,
  onOpenSingle,
  onClose,
}: ConversationDialogProps) {
  const query = useConversation(commentId);
  const openSingle = (id: string) => {
    onClose();
    onOpenSingle(id);
  };
  const actions: CommentActions = {
    onReply: NOOP,
    onShowConversation: NOOP,
    onShowHistory: NOOP,
    onEdit: NOOP,
    onDelete: NOOP,
    onCopyLink: NOOP,
    onHelpfulGone: NOOP,
    onOpenPermalink: (comment) => openSingle(comment.commentId),
  };
  return (
    <Dialog open={commentId !== null} onOpenChange={(open) => !open && onClose()} title="对话">
      {query.isError ? (
        <InteractionUnavailable
          compact
          title="对话暂时打不开"
          onRetry={() => void query.refetch()}
        />
      ) : query.data ? (
        <div className="flex flex-col gap-4">
          {query.data.items.map((comment) => (
            <CommentItem
              key={comment.commentId}
              comment={comment}
              level={comment.parentId ? "reply" : "root"}
              permalink={permalinkOf(comment)}
              now={now}
              actions={actions}
              current={comment.commentId === query.data.targetId}
              readOnly
            />
          ))}
        </div>
      ) : (
        <div
          aria-hidden="true"
          className="h-24 motion-safe:animate-pulse rounded-md bg-paper-200"
        />
      )}
      {commentId && (
        <button
          type="button"
          onClick={() => openSingle(commentId)}
          className="self-start text-sm font-medium text-link hover:text-link-hover"
        >
          只看这一楼 →
        </button>
      )}
    </Dialog>
  );
}
