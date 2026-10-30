"use client";

import { type ReactNode, useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { CommentComposer } from "@/components/discussion/CommentComposer";
import type { CommentActions } from "@/components/discussion/CommentItem";
import { ConversationDialog } from "@/components/discussion/ConversationDialog";
import { DeleteCommentDialog } from "@/components/discussion/DeleteCommentDialog";
import { RevisionHistoryDialog } from "@/components/discussion/RevisionHistoryDialog";
import { commentFailureMessage } from "@/lib/discussion/messages";
import { fetchCommentSource } from "@/lib/discussion/queries";
import type { ViewerSeed } from "@/lib/discussion/viewer";
import type { Comment, DeleteResult, Subject } from "@/lib/interaction/discussion-schema";
import { useCopy } from "@/lib/use-copy";

interface UseCommentActionsInput {
  subject: Subject;
  viewer: ViewerSeed;
  now: number;
  shareBase: string;
  permalinkOf: (comment: Comment) => string;
  openSingle: (commentId: string) => void;
  onEdited: (comment: Comment) => void;
  onDeleted: (comment: Comment, result: DeleteResult) => void;
}

type Editing = { comment: Comment; source: string } | { comment: Comment; failed: true };

/// 列表与单楼视图共用的评论操作：查看对话、修改历史、原地编辑、删除、复制链接，以及它们的对话框
export function useCommentActions({
  subject,
  viewer,
  now,
  shareBase,
  permalinkOf,
  openSingle,
  onEdited,
  onDeleted,
}: UseCommentActionsInput): {
  handlers: Omit<CommentActions, "onReply" | "onOpenPermalink" | "onHelpfulGone">;
  editingOf: (comment: Comment) => ReactNode | undefined;
  dialogs: ReactNode;
} {
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [historyId, setHistoryId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<Comment | null>(null);
  const [editing, setEditing] = useState<Editing | null>(null);
  const { copy } = useCopy();

  const handlers = {
    onShowConversation: (comment: Comment) => setConversationId(comment.commentId),
    onShowHistory: (comment: Comment) => setHistoryId(comment.commentId),
    onDelete: (comment: Comment) => setDeleting(comment),
    onEdit: async (comment: Comment) => {
      try {
        setEditing({ comment, source: await fetchCommentSource(comment.commentId) });
      } catch {
        setEditing({ comment, failed: true });
      }
    },
    onCopyLink: async (comment: Comment) => {
      if ((await copy(`${shareBase}?comment=${comment.commentId}`, null)) === "copied") {
        toast.success("已复制链接");
      }
    },
  };

  const editingOf = (comment: Comment): ReactNode | undefined => {
    if (editing?.comment.commentId !== comment.commentId) {
      return undefined;
    }
    if ("failed" in editing) {
      return (
        <FormAlert
          action={
            <button
              type="button"
              onClick={() => setEditing(null)}
              className="text-sm font-medium text-link hover:text-link-hover"
            >
              关闭
            </button>
          }
        >
          {commentFailureMessage("unavailable", "edit")}
        </FormAlert>
      );
    }
    return (
      <CommentComposer
        subject={subject}
        mode={{ kind: "edit", comment, source: editing.source }}
        placeholder=""
        viewer={viewer}
        autoFocus
        onDone={({ comment: saved }) => {
          setEditing(null);
          onEdited(saved);
        }}
        onCancel={() => setEditing(null)}
      />
    );
  };

  const dialogs = (
    <>
      <ConversationDialog
        commentId={conversationId}
        now={now}
        permalinkOf={permalinkOf}
        onOpenSingle={openSingle}
        onClose={() => setConversationId(null)}
      />
      <RevisionHistoryDialog commentId={historyId} onClose={() => setHistoryId(null)} />
      <DeleteCommentDialog
        comment={deleting}
        onClose={() => setDeleting(null)}
        onDeleted={onDeleted}
      />
    </>
  );

  return { handlers, editingOf, dialogs };
}
