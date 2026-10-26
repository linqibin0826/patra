"use client";

import { NotebookPen } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { FIELD_BOX, FIELD_INPUT } from "@/components/auth/FormField";
import { CharCounter } from "@/components/settings/profile/CharCounter";
import { graphemeCount } from "@/lib/grapheme";
import { saveNote } from "@/lib/interaction/library-actions";
import type { LibraryItem } from "@/lib/interaction/schema";
import { LIMITS } from "@/lib/library/messages";
import { useLoginRedirect } from "@/lib/library/use-login-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnPrimary, btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface NoteEditorProps {
  itemId: string;
  note: string | null;
  onSaved: (item: LibraryItem) => void;
  /// 一打开就是编辑态（条目菜单「编辑备注」）
  startEditing?: boolean;
  /// 编辑态点「取消」或保存成功后（条目里用来收起编辑区）
  onDone?: () => void;
}

/// 备注（简报 §3.2 第 4 条）：展示态（3 行截断 + 编辑 / + 添加备注）与编辑态（4 行起、最多 8 行，字素计数）。
/// 不乐观：保存中禁用，失败保留输入；清空后保存 = 删除备注。收藏浮层与文献库条目共用。
export function NoteEditor({
  itemId,
  note,
  onSaved,
  startEditing = false,
  onDone,
}: NoteEditorProps) {
  const [editing, setEditing] = useState(startEditing);
  const [draft, setDraft] = useState(note ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const field = useRef<HTMLTextAreaElement>(null);
  const errorId = useId();
  const redirectToLogin = useLoginRedirect();
  const tooLong = graphemeCount(draft.trim()) > LIMITS.note;

  useEffect(() => {
    if (editing) {
      field.current?.focus();
    }
  }, [editing]);

  if (!editing) {
    return note ? (
      <div className="flex flex-col gap-1.5">
        <p className="line-clamp-3 text-sm leading-5 whitespace-pre-line text-fg-2">{note}</p>
        <button
          type="button"
          className={cn(btnText, "self-start text-sm")}
          onClick={() => {
            setDraft(note);
            setEditing(true);
          }}
        >
          编辑
        </button>
      </div>
    ) : (
      <button
        type="button"
        className={cn(btnText, "inline-flex items-center gap-1.5 self-start text-sm")}
        onClick={() => {
          setDraft("");
          setEditing(true);
        }}
      >
        <NotebookPen className="size-3.5" aria-hidden="true" />+ 添加备注
      </button>
    );
  }

  async function submit() {
    if (tooLong || saving) {
      return;
    }
    setSaving(true);
    setError(null);
    const result = await orUnavailable(saveNote(itemId, draft));
    setSaving(false);
    if (result.ok) {
      onSaved(result.item);
      setEditing(false);
      onDone?.();
      return;
    }
    if (result.reason === "signed-out") {
      redirectToLogin();
      return;
    }
    setError(
      result.reason === "invalid-fields" ? "备注最多 500 字" : "备注没有保存成功，请稍后再试",
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <div className={FIELD_BOX}>
        <textarea
          ref={field}
          aria-label="备注"
          aria-describedby={error || tooLong ? errorId : undefined}
          aria-invalid={tooLong || undefined}
          rows={4}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          className={cn(FIELD_INPUT, "field-sizing-content max-h-[8lh] min-h-[4lh]")}
        />
      </div>
      <div className="flex items-center justify-between gap-3">
        <CharCounter value={draft} max={LIMITS.note} />
        <div className="flex items-center gap-3">
          <button
            type="button"
            className={cn(btnText, "text-sm")}
            onClick={() => {
              setEditing(false);
              setError(null);
              onDone?.();
            }}
          >
            取消
          </button>
          <button
            type="button"
            disabled={tooLong}
            aria-disabled={saving || undefined}
            onClick={submit}
            className={cn(btnPrimary, "px-3.5 py-1.5 text-sm")}
          >
            {saving ? "保存中…" : "保存"}
          </button>
        </div>
      </div>
      {(error || tooLong) && (
        <p id={errorId} className="text-xs text-status-danger">
          {tooLong ? "备注最多 500 字" : error}
        </p>
      )}
    </div>
  );
}
