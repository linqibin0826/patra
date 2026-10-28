"use client";

import { X } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { FormAlert } from "@/components/auth/FormAlert";
import { MarkdownEditor, useEditorMetrics } from "@/components/discussion/MarkdownEditor";
import { useMentionSuggest } from "@/components/discussion/MentionSuggest";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { Avatar } from "@/components/user/Avatar";
import { authPageHref } from "@/lib/auth/next-path";
import { authorView } from "@/lib/discussion/author";
import { COMMENT_LIMITS, normalizeForCount } from "@/lib/discussion/limits";
import { COMPOSER_COPY, commentFailureMessage } from "@/lib/discussion/messages";
import { useViewer, type ViewerSeed } from "@/lib/discussion/viewer";
import {
  type CommentFailure,
  editComment,
  publishComment,
} from "@/lib/interaction/discussion-actions";
import type { Comment, Subject } from "@/lib/interaction/discussion-schema";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnPrimary, btnSecondary, btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 三种用法（简报 §3.5）：发表根评论、回复（`target` 是被回复的那条，回复根评论时就是 `root`）、原地编辑
export type ComposerMode =
  | { kind: "root" }
  | { kind: "reply"; root: Comment; target: Comment }
  | { kind: "edit"; comment: Comment; source: string };

interface CommentComposerProps {
  subject: Subject;
  mode: ComposerMode;
  placeholder: string;
  viewer: ViewerSeed;
  onDone: (result: { comment: Comment }) => void;
  onCancel: () => void;
  /// 讨论区顶部（列表不为空时）：先是一行占位，点了才展开
  collapsible?: boolean;
  autoFocus?: boolean;
  /// 有没有写了内容（讨论区据此决定换回复框前要不要先确认）
  onDirtyChange?: (dirty: boolean) => void;
}

const LABELS = {
  root: { idle: "发表", busy: "发表中…" },
  reply: { idle: "回复", busy: "回复中…" },
  edit: { idle: "保存", busy: "保存中…" },
} as const;

/// 提示条里的一条失败
type Failure = { reason: CommentFailure["reason"]; message: string };

/// 被回复的人怎么称呼：`@用户名` / `@昵称` / 匿名作者 / 已注销用户
function targetName(comment: Comment): string {
  const view = authorView(comment);
  switch (view.kind) {
    case "visible":
      return `@${view.card.username}`;
    case "hidden":
      return `@${view.card.displayName}`;
    case "anonymous":
      return "匿名作者";
    default:
      return "已注销用户";
  }
}

/// 根评论的作者名（「回复 陈宇 的评论」）
function rootName(comment: Comment): string {
  const view = authorView(comment);
  return view.kind === "visible" || view.kind === "hidden"
    ? (view.card.displayName ?? "")
    : view.kind === "anonymous"
      ? "匿名作者"
      : "已注销用户";
}

/// 评论编辑器（简报 §3.5、§3.12、§4.2）：资格、计数、提交、按原因码分开的错误、放弃确认、会话失效确认。
/// 不乐观：等服务端返回才清空、回调
export function CommentComposer({
  subject,
  mode,
  placeholder,
  viewer: seed,
  onDone,
  onCancel,
  collapsible = false,
  autoFocus = false,
  onDirtyChange,
}: CommentComposerProps) {
  const viewer = useViewer(seed);
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams().toString();
  const here = search ? `${pathname}?${search}` : pathname;
  const [value, setValue] = useState(mode.kind === "edit" ? mode.source : "");
  const [expanded, setExpanded] = useState(!collapsible);
  const [openedByClick, setOpenedByClick] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [confirm, setConfirm] = useState<"discard" | "signed-out" | null>(null);
  const [toFloor, setToFloor] = useState(false);
  const [targetGone, setTargetGone] = useState(false);
  const busy = useRef(false);
  const textarea = useRef<HTMLTextAreaElement | null>(null);
  const mention = useMentionSuggest({ subject, value, onChange: setValue, textareaRef: textarea });
  const metrics = useEditorMetrics(value, COMMENT_LIMITS, true);
  const dirty = normalizeForCount(value) !== "";

  useEffect(() => onDirtyChange?.(dirty), [dirty, onDirtyChange]);

  if (viewer.status === "anonymous") {
    return (
      <div className="flex flex-wrap items-center gap-x-4 gap-y-3 rounded-xl border border-border-default bg-paper-50 px-4.5 py-4 text-sm text-fg-2">
        <Link href={authPageHref("/login", here)} className={btnPrimary}>
          登录后参与讨论
        </Link>
        <span>
          还没有账号？
          <Link
            href={authPageHref("/register", here)}
            className="font-medium text-link hover:text-link-hover"
          >
            注册
          </Link>
        </span>
      </div>
    );
  }

  const unverified = !viewer.emailVerified;
  const action = mode.kind === "edit" ? "edit" : "publish";
  const labels = LABELS[mode.kind];

  if (!expanded) {
    return (
      <div className="grid grid-cols-[32px_minmax(0,1fr)] items-center gap-3">
        <Avatar identity={viewer.avatar} size={32} />
        <button
          type="button"
          onClick={() => {
            setExpanded(true);
            setOpenedByClick(true);
          }}
          className="h-12 w-full truncate rounded-xl border border-border-default bg-paper-50 px-4 text-left text-md text-fg-3 hover:border-border-hover"
        >
          {placeholder}
        </button>
      </div>
    );
  }

  const close = () => {
    setValue("");
    setFailure(null);
    setConfirm(null);
    if (collapsible) {
      setExpanded(false);
    }
    onCancel();
  };

  const submit = async () => {
    if (busy.current || unverified || !dirty || metrics.blocked) {
      return;
    }
    if (mode.kind === "edit" && normalizeForCount(value) === normalizeForCount(mode.source)) {
      close();
      return;
    }
    busy.current = true;
    setSubmitting(true);
    setFailure(null);
    const parentId =
      mode.kind === "reply"
        ? toFloor || targetGone
          ? mode.root.commentId
          : mode.target.commentId
        : null;
    const result = await orUnavailable(
      mode.kind === "edit"
        ? editComment(mode.comment.commentId, value)
        : publishComment(subject, { body: value, parentId }),
    );
    busy.current = false;
    setSubmitting(false);
    if (result.ok) {
      setValue("");
      if (collapsible) {
        setExpanded(false);
      }
      onDone({ comment: result.comment });
      return;
    }
    if (result.reason === "signed-out") {
      if (dirty) {
        setConfirm("signed-out");
      } else {
        router.push(authPageHref("/login", here));
      }
      return;
    }
    if (result.reason === "reply-target-gone") {
      setTargetGone(true);
    }
    setFailure({ reason: result.reason, message: commentFailureMessage(result.reason, action) });
  };

  const replyLine =
    mode.kind === "reply" ? (
      <div className="mb-2 flex items-center gap-2 text-xs text-fg-3">
        {targetGone || mode.root.deleted ? (
          <span className="font-medium text-fg-1">回复这一楼</span>
        ) : toFloor || mode.target.commentId === mode.root.commentId ? (
          <span>
            回复 <b className="font-medium text-fg-1">{rootName(mode.root)}</b> 的评论
          </span>
        ) : (
          <>
            <span>
              回复 <b className="font-medium text-fg-1">{targetName(mode.target)}</b>
            </span>
            <button
              type="button"
              aria-label="改为回复这一楼"
              onClick={() => setToFloor(true)}
              className="inline-flex size-5 items-center justify-center rounded-full bg-bg-sunken text-fg-3 hover:text-fg-1"
            >
              <X className="size-3" aria-hidden="true" />
            </button>
          </>
        )}
      </div>
    ) : null;

  return (
    <div className="flex flex-col">
      {replyLine}
      {unverified && (
        <div className="mb-2.5">
          <FormAlert tone="warning" action={<ResendVerificationButton label="重新发送验证邮件" />}>
            验证邮箱后才能发表评论。验证邮件已发到 {viewer.email}。
          </FormAlert>
        </div>
      )}
      <MarkdownEditor
        value={value}
        onChange={setValue}
        label="评论内容"
        placeholder={placeholder}
        limits={COMMENT_LIMITS}
        limitLabel="评论"
        mentions={{ subject }}
        textareaRef={textarea}
        disabled={unverified}
        readOnly={submitting}
        autoFocus={autoFocus || openedByClick}
        onSubmitShortcut={() => void submit()}
        {...mention.editorProps}
      />
      {failure && (
        <div className="mt-2.5">
          <FormAlert
            action={
              failure.reason === "email-unverified" ? (
                <ResendVerificationButton label="重新发送验证邮件" />
              ) : undefined
            }
          >
            {failure.message}
          </FormAlert>
        </div>
      )}
      <div className="mt-2.5 flex flex-wrap items-center justify-end gap-x-3 gap-y-2.5">
        {confirm === "discard" && (
          <span className="mr-auto inline-flex items-center gap-2.5 text-sm text-fg-1">
            {mode.kind === "reply" ? COMPOSER_COPY.discardReply : COMPOSER_COPY.discard}
            <button type="button" onClick={close} className={btnText}>
              放弃
            </button>
            <button type="button" onClick={() => setConfirm(null)} className={btnText}>
              继续写
            </button>
          </span>
        )}
        {confirm === "signed-out" && (
          <span className="mr-auto inline-flex flex-wrap items-center gap-2.5 text-sm text-fg-1">
            {COMPOSER_COPY.sessionExpired}
            <Link href={authPageHref("/login", here)} className={btnText}>
              去登录
            </Link>
            <button type="button" onClick={() => setConfirm(null)} className={btnText}>
              留在这里
            </button>
          </span>
        )}
        <button
          type="button"
          disabled={submitting}
          onClick={() => (dirty ? setConfirm("discard") : close())}
          className={cn(btnSecondary, "border-transparent bg-transparent")}
        >
          取消
        </button>
        <button
          type="button"
          title="⌘ Enter / Ctrl Enter"
          disabled={submitting || unverified || !dirty || metrics.blocked}
          onClick={() => void submit()}
          className={btnPrimary}
        >
          {submitting ? labels.busy : labels.idle}
        </button>
      </div>
    </div>
  );
}
