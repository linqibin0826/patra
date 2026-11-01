// patra-learn/src/components/annotations.tsx
// 条目页批注（画布「学习站批注」Main / MainMobile 板）：选中正文 → 工具条「有问题 / 记笔记」→ 写说明 → 保存。
// 高亮用 CSS Custom Highlight API 画，不改正文 DOM（正文里有教学组件，React 管着它们的节点）；
// 徽章摆在正文列右侧，与原文最后一行对齐；手机上不放徽章，点高亮的文字打开，写和看改成底部抽屉
"use client";

import Link from "next/link";
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import { describeQuote, locateQuote, type Quote } from "@/lib/annotations/anchor";
import type { Annotation, AnnotationKind } from "@/lib/annotations/annotations";
import { rangeAt, spanOf, textOf } from "@/lib/annotations/dom-text";
import { useAnnotations } from "@/lib/annotations/use-annotations";
import { button, cx } from "./ui";

/// 提示停留多久（毫秒），同掌握度
const TOAST_MS = 2600;
/// 正文变了（教学组件展开、切换）之后等多久再重新挂批注
const REANCHOR_MS = 150;
const KIND_LABEL: Record<AnnotationKind, string> = { question: "有问题", note: "笔记" };
const HIGHLIGHTS = ["patra-question", "patra-note", "patra-pending"] as const;

// ---------- 条目页共享：侧栏点一条，正文打开它 ----------

interface EntryContext {
  slug: string;
  request: { id: string; n: number } | null;
  open: (id: string) => void;
}

const Ctx = createContext<EntryContext | null>(null);

function useEntry() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("批注组件要放在 EntryAnnotations 里");
  return ctx;
}

export function EntryAnnotations({ slug, children }: { slug: string; children: ReactNode }) {
  const [request, setRequest] = useState<EntryContext["request"]>(null);
  const open = useCallback((id: string) => setRequest((r) => ({ id, n: (r?.n ?? 0) + 1 })), []);
  const value = useMemo(() => ({ slug, request, open }), [slug, request, open]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

// ---------- 小部件 ----------

const noop = () => () => {};

/// 手机宽度（< 640px，同 Tailwind max-sm）；服务端与不支持 matchMedia 时按桌面
function useNarrow() {
  return useSyncExternalStore(
    (onChange) => {
      if (typeof window.matchMedia !== "function") return () => {};
      const query = window.matchMedia("(max-width: 639px)");
      query.addEventListener("change", onChange);
      return () => query.removeEventListener("change", onChange);
    },
    () =>
      typeof window.matchMedia === "function" && window.matchMedia("(max-width: 639px)").matches,
    () => false,
  );
}

/// 水合完成后为 true
function useHydrated() {
  return useSyncExternalStore(
    noop,
    () => true,
    () => false,
  );
}

function monthDay(time: number) {
  const date = new Date(time);
  return `${String(date.getMonth() + 1).padStart(2, "0")}/${String(date.getDate()).padStart(2, "0")}`;
}

/// 类型标签：有问题琥珀、笔记水青，名字永远写出来
export function KindChip({ kind }: { kind: AnnotationKind }) {
  return (
    <span
      className={cx(
        "inline-flex h-[22px] items-center whitespace-nowrap rounded-chip px-2 font-semibold font-ui text-tiny",
        kind === "question" ? "bg-amber-bg text-amber" : "bg-teal-tint text-teal-deep",
      )}
    >
      {KIND_LABEL[kind]}
    </span>
  );
}

function QuestionIcon({ size = 12, strokeWidth = 2.8 }: { size?: number; strokeWidth?: number }) {
  return (
    <svg
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 2.5-3 4.5" />
      <path d="M12 19h.01" />
    </svg>
  );
}

function PencilIcon({ size = 11, strokeWidth = 2.6 }: { size?: number; strokeWidth?: number }) {
  return (
    <svg
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M4 20h4L19 9l-4-4L4 16v4z" />
    </svg>
  );
}

function CloseButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className="inline-flex size-8 cursor-pointer items-center justify-center rounded-lg text-ink-4 hover:bg-line-soft hover:text-ink"
    >
      <svg
        aria-hidden="true"
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
      >
        <path d="M6 6l12 12M18 6L6 18" />
      </svg>
    </button>
  );
}

export function Toast({ text }: { text: string }) {
  return (
    <div
      role="status"
      className="-translate-x-1/2 fixed bottom-7 left-1/2 z-50 whitespace-nowrap rounded-full bg-ink px-4.5 py-2.5 font-ui text-sm text-white shadow-lift"
    >
      {text}
    </div>
  );
}

// ---------- 正文：选中、高亮、徽章、浮层 ----------

type Mode =
  | null
  | { type: "bar"; range: Range; quote: Quote }
  | {
      type: "compose";
      range: Range;
      quote: Quote;
      kind: AnnotationKind;
      draft: string;
      editId?: string;
    }
  | { type: "view"; id: string };

/// 选区最后一行的矩形（徽章、浮层都贴着它）；jsdom 没有布局时是空矩形
function lastRect(range: Range) {
  if (typeof range.getClientRects !== "function") return null;
  const rects = range.getClientRects();
  return rects.length > 0 ? rects[rects.length - 1] : range.getBoundingClientRect();
}

/// 去掉选区首尾的空白
function trimmed(text: string, start: number, end: number) {
  let s = start;
  let e = end;
  while (s < e && /\s/.test(text.charAt(s))) s++;
  while (e > s && /\s/.test(text.charAt(e - 1))) e--;
  return { start: s, end: e };
}

function setHighlight(name: (typeof HIGHLIGHTS)[number], ranges: Range[]) {
  if (typeof CSS === "undefined" || !("highlights" in CSS) || typeof Highlight === "undefined")
    return;
  if (ranges.length === 0) CSS.highlights.delete(name);
  else CSS.highlights.set(name, new Highlight(...ranges));
}

export function AnnotatableBody({ children }: { children: ReactNode }) {
  const { slug, request } = useEntry();
  const { annotations, add, edit, remove, applyAnchorResult } = useAnnotations();
  const hydrated = useHydrated();
  const narrow = useNarrow();
  const wrapRef = useRef<HTMLDivElement>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const [mode, setMode] = useState<Mode>(null);
  const [ranges, setRanges] = useState<ReadonlyMap<string, Range>>(new Map());
  const [textTick, setTextTick] = useState(0);
  const [, setLayoutTick] = useState(0);
  const [toast, setToast] = useState("");
  const modeRef = useRef<Mode>(null);
  modeRef.current = mode;

  const mine = useMemo(() => annotations.filter((a) => a.slug === slug), [annotations, slug]);

  // 按原文把这一篇的批注挂回正文；挂不回去的记成原文已变
  // biome-ignore lint/correctness/useExhaustiveDependencies: textTick 是正文变了的信号
  useEffect(() => {
    const root = rootRef.current;
    if (!root || !hydrated) return;
    const text = textOf(root);
    const next = new Map<string, Range>();
    for (const a of mine) {
      const span = locateQuote(text, a.quote);
      const range = span ? rangeAt(root, span.start, span.end) : null;
      if (range) next.set(a.id, range);
    }
    setRanges(next);
    applyAnchorResult(slug, new Set(next.keys()));
  }, [mine, slug, hydrated, applyAnchorResult, textTick]);

  // 正文变了（教学组件展开、切换）就重新挂；窗口大小变了就重新量徽章位置
  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const observer = new MutationObserver(() => {
      clearTimeout(timer);
      timer = setTimeout(() => setTextTick((t) => t + 1), REANCHOR_MS);
    });
    observer.observe(root, { childList: true, subtree: true, characterData: true });
    const relayout = () => setLayoutTick((t) => t + 1);
    window.addEventListener("resize", relayout);
    const resize = typeof ResizeObserver === "function" ? new ResizeObserver(relayout) : null;
    resize?.observe(root);
    return () => {
      clearTimeout(timer);
      observer.disconnect();
      resize?.disconnect();
      window.removeEventListener("resize", relayout);
    };
  }, []);

  // 高亮：有问题、笔记、正在写的那一段
  useLayoutEffect(() => {
    const byKind = (kind: AnnotationKind) =>
      mine.filter((a) => a.kind === kind && ranges.has(a.id)).map((a) => ranges.get(a.id) as Range);
    setHighlight("patra-question", byKind("question"));
    setHighlight("patra-note", byKind("note"));
    setHighlight("patra-pending", mode?.type === "compose" && !mode.editId ? [mode.range] : []);
  }, [mine, ranges, mode]);
  useEffect(
    () => () => {
      for (const name of HIGHLIGHTS) setHighlight(name, []);
    },
    [],
  );

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(""), TOAST_MS);
    return () => clearTimeout(t);
  }, [toast]);

  const openView = useCallback(
    (id: string) => {
      const range = ranges.get(id);
      if (!range) return;
      window.getSelection()?.removeAllRanges();
      setMode({ type: "view", id });
      const el = range.startContainer.parentElement;
      if (el && typeof el.scrollIntoView === "function") el.scrollIntoView({ block: "center" });
    },
    [ranges],
  );

  // 侧栏点了一条
  useEffect(() => {
    if (request) openView(request.id);
  }, [request, openView]);

  // 松开鼠标 / 手指、键盘改了选区：选中了正文里的文字就出工具条；点在高亮上就打开那条批注
  useEffect(() => {
    let pointerDown = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const evaluate = (point?: { x: number; y: number }) => {
      const root = rootRef.current;
      const current = modeRef.current;
      const selection = window.getSelection();
      if (!root || !selection) return;
      if (selection.isCollapsed || selection.rangeCount === 0) {
        if (current?.type === "bar") setMode(null);
        if (point && (!current || current.type === "view")) {
          const hit = [...rangesRef.current].find(
            ([, range]) =>
              typeof range.getClientRects === "function" &&
              [...range.getClientRects()].some(
                (r) =>
                  point.x >= r.left &&
                  point.x <= r.right &&
                  point.y >= r.top &&
                  point.y <= r.bottom,
              ),
          );
          if (hit) setMode({ type: "view", id: hit[0] });
          else if (current?.type === "view") setMode(null);
        }
        return;
      }
      if (current?.type === "compose") return;
      const span = spanOf(root, selection.getRangeAt(0));
      if (!span) return;
      const text = textOf(root);
      const { start, end } = trimmed(text, span.start, span.end);
      const range = start < end ? rangeAt(root, start, end) : null;
      if (!range) return;
      setMode({ type: "bar", range, quote: describeQuote(text, start, end) });
    };
    const inUi = (target: EventTarget | null) =>
      target instanceof Element && target.closest("[data-annotation-ui]") !== null;
    const onDown = (event: PointerEvent) => {
      if (!inUi(event.target)) pointerDown = true;
    };
    const onUp = (event: PointerEvent) => {
      pointerDown = false;
      if (inUi(event.target)) return;
      evaluate({ x: event.clientX, y: event.clientY });
    };
    const onKeyUp = (event: KeyboardEvent) => {
      if (event.shiftKey && !inUi(event.target)) evaluate();
    };
    // 手机上拖选区两头的把手不一定有指针事件，靠 selectionchange 补上
    const onSelectionChange = () => {
      clearTimeout(timer);
      timer = setTimeout(() => {
        if (!pointerDown && modeRef.current?.type !== "compose") evaluate();
      }, 250);
    };
    document.addEventListener("pointerdown", onDown);
    document.addEventListener("pointerup", onUp);
    document.addEventListener("keyup", onKeyUp);
    document.addEventListener("selectionchange", onSelectionChange);
    return () => {
      clearTimeout(timer);
      document.removeEventListener("pointerdown", onDown);
      document.removeEventListener("pointerup", onUp);
      document.removeEventListener("keyup", onKeyUp);
      document.removeEventListener("selectionchange", onSelectionChange);
    };
  }, []);
  const rangesRef = useRef(ranges);
  rangesRef.current = ranges;

  const close = () => {
    setMode(null);
    window.getSelection()?.removeAllRanges();
  };

  const startCompose = (kind: AnnotationKind) => {
    if (mode?.type !== "bar") return;
    setMode({ type: "compose", range: mode.range, quote: mode.quote, kind, draft: "" });
  };

  const save = () => {
    if (mode?.type !== "compose") return;
    const text = mode.draft.trim();
    if (mode.editId) {
      edit(mode.editId, { kind: mode.kind, text });
      setToast("已更新");
    } else {
      add({ slug, kind: mode.kind, text, quote: mode.quote });
      setToast("已记下 · 顶栏「批注」里能看到全部");
    }
    close();
  };

  // 位置都相对正文外层（relative）
  const wrapRect = wrapRef.current?.getBoundingClientRect();
  const wrapWidth = wrapRect?.width ?? 0;
  const place = (range: Range | undefined, width: number) => {
    const rect = range ? lastRect(range) : null;
    if (!rect || !wrapRect) return { top: 0, left: 0 };
    return {
      top: rect.bottom - wrapRect.top + 10,
      left: Math.max(0, Math.min(rect.left - wrapRect.left - 8, wrapWidth - width)),
    };
  };

  // 徽章贴着正文列右侧（侧栏折下去时页边只有 24px，不能再往右）；同一行有好几条时往下排
  const badges: { a: Annotation; line: number; top: number; left: number }[] = [];
  if (!narrow) {
    for (const a of mine) {
      const range = ranges.get(a.id);
      if (!range) continue;
      const rect = lastRect(range);
      const line = rect && wrapRect ? rect.top - wrapRect.top + (rect.height - 20) / 2 : 0;
      const sameLine = badges.filter((b) => Math.abs(b.line - line) < 6).length;
      badges.push({ a, line, top: line + sameLine * 22, left: wrapWidth + 2 });
    }
  }

  const viewed = mode?.type === "view" ? mine.find((a) => a.id === mode.id) : undefined;
  const sheet = narrow && (mode?.type === "compose" || viewed !== undefined);
  const cardPos =
    mode?.type === "compose" ? place(mode.range, 340) : place(viewed && ranges.get(viewed.id), 340);
  const cardClass = sheet
    ? "fixed inset-x-0 bottom-0 z-40 flex flex-col gap-3 rounded-t-[18px] bg-surface px-4 pt-2.5 pb-6 font-ui shadow-[0_-10px_28px_rgba(31,42,36,0.16)]"
    : "absolute z-30 flex w-[340px] max-w-[calc(100vw-32px)] flex-col gap-2.5 rounded-xl border border-line bg-surface p-3.5 font-ui shadow-lift";

  return (
    <div ref={wrapRef} className="relative">
      <div ref={rootRef}>{children}</div>

      {badges.map(({ a, top, left }) => (
        <button
          key={a.id}
          type="button"
          data-annotation-ui=""
          aria-label={`查看批注：${KIND_LABEL[a.kind]}`}
          onClick={() => openView(a.id)}
          style={{ top, left }}
          className={cx(
            "absolute inline-flex size-5 cursor-pointer items-center justify-center rounded-full transition-transform duration-160 ease-out after:absolute after:-inset-3 hover:scale-110",
            a.kind === "question" ? "bg-question text-amber" : "bg-teal-tint text-teal-deep",
            viewed?.id === a.id && "outline-2 outline-teal-mark outline-offset-1",
          )}
        >
          {a.kind === "question" ? <QuestionIcon /> : <PencilIcon />}
        </button>
      ))}

      {mode?.type === "bar" ? (
        <div
          role="toolbar"
          aria-label="批注选中的文字"
          data-annotation-ui=""
          onPointerDown={(event) => event.preventDefault()}
          onMouseDown={(event) => event.preventDefault()}
          style={place(mode.range, 232)}
          className="absolute z-30 flex gap-0.5 rounded-xl border border-line bg-surface p-1 font-ui shadow-lift"
        >
          <button
            type="button"
            onClick={() => startCompose("question")}
            className="inline-flex h-9 cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-lg px-3 text-ink text-sm hover:bg-line-soft"
          >
            <span className="text-amber">
              <QuestionIcon size={16} strokeWidth={2} />
            </span>
            有问题
          </button>
          <span aria-hidden="true" className="my-1.5 w-px bg-line" />
          <button
            type="button"
            onClick={() => startCompose("note")}
            className="inline-flex h-9 cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-lg px-3 text-ink text-sm hover:bg-line-soft"
          >
            <span className="text-teal">
              <PencilIcon size={16} strokeWidth={2} />
            </span>
            记笔记
          </button>
        </div>
      ) : null}

      {sheet ? (
        <button
          type="button"
          aria-label="关闭"
          data-annotation-ui=""
          onClick={close}
          className="fixed inset-0 z-39 cursor-default bg-ink/30"
        />
      ) : null}

      {mode?.type === "compose" ? (
        <Composer
          key={mode.editId ?? "new"}
          mode={mode}
          className={cardClass}
          style={sheet ? undefined : cardPos}
          sheet={sheet}
          onKind={(kind) => setMode({ ...mode, kind })}
          onDraft={(draft) => setMode({ ...mode, draft })}
          onCancel={close}
          onSave={save}
        />
      ) : null}

      {viewed ? (
        <div
          role="dialog"
          aria-label="查看批注"
          data-annotation-ui=""
          onKeyDown={(event) => {
            if (event.key === "Escape") close();
          }}
          style={sheet ? undefined : cardPos}
          className={cardClass}
        >
          {sheet ? (
            <span aria-hidden="true" className="h-1 w-9 self-center rounded-full bg-line" />
          ) : null}
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <KindChip kind={viewed.kind} />
              <span className="text-ink-4 text-meta">{monthDay(viewed.createdAt)} 记</span>
            </div>
            <CloseButton label="关闭" onClick={close} />
          </div>
          <p className="m-0 whitespace-pre-line text-[14.5px] text-ink leading-[1.65]">
            {viewed.text || "（没写说明）"}
          </p>
          <div className="flex items-center justify-between gap-2">
            <span className="text-ink-4 text-meta">
              {viewed.kind === "note"
                ? "只有你自己看得到"
                : viewed.exportedAt
                  ? `${monthDay(viewed.exportedAt)} 导出过`
                  : "还没导出给 Claude"}
            </span>
            <div className="-mr-2 flex gap-0.5">
              <button
                type="button"
                className={button("quiet", "sm")}
                onClick={() => {
                  const range = ranges.get(viewed.id);
                  if (!range) return;
                  setMode({
                    type: "compose",
                    range,
                    quote: viewed.quote,
                    kind: viewed.kind,
                    draft: viewed.text,
                    editId: viewed.id,
                  });
                }}
              >
                编辑
              </button>
              <button
                type="button"
                className={cx(button("quiet", "sm"), "text-wrong hover:bg-wrong-bg")}
                onClick={() => {
                  remove(viewed.id);
                  setMode(null);
                  setToast("已删除");
                }}
              >
                删除
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {toast ? <Toast text={toast} /> : null}
    </div>
  );
}

function Composer({
  mode,
  className,
  style,
  sheet,
  onKind,
  onDraft,
  onCancel,
  onSave,
}: {
  mode: Extract<Mode, { type: "compose" }>;
  className: string;
  style: { top: number; left: number } | undefined;
  sheet: boolean;
  onKind: (kind: AnnotationKind) => void;
  onDraft: (draft: string) => void;
  onCancel: () => void;
  onSave: () => void;
}) {
  const inputId = "annotation-draft";
  const quote =
    mode.quote.exact.length > 60 ? `${mode.quote.exact.slice(0, 60)}…` : mode.quote.exact;
  return (
    <div
      role="dialog"
      aria-label="写批注"
      data-annotation-ui=""
      onKeyDown={(event) => {
        if (event.key === "Escape") onCancel();
        if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) onSave();
      }}
      style={style}
      className={className}
    >
      {sheet ? (
        <span aria-hidden="true" className="h-1 w-9 self-center rounded-full bg-line" />
      ) : null}
      <div className="flex items-center justify-between gap-2">
        <fieldset className="m-0 flex gap-1.5 border-0 p-0">
          <legend className="sr-only">批注类型</legend>
          {(["question", "note"] as const).map((kind) => (
            <button
              key={kind}
              type="button"
              aria-pressed={mode.kind === kind}
              onClick={() => onKind(kind)}
              className={cx(
                "inline-flex h-8 cursor-pointer items-center rounded-full border px-3 text-[13.5px]",
                mode.kind !== kind && "border-line bg-surface text-ink-3",
                mode.kind === kind &&
                  kind === "question" &&
                  "border-[#d9a54a] bg-question font-semibold text-amber",
                mode.kind === kind &&
                  kind === "note" &&
                  "border-teal-mark bg-teal-tint font-semibold text-teal-deep",
              )}
            >
              {KIND_LABEL[kind]}
            </button>
          ))}
        </fieldset>
        <CloseButton label="取消" onClick={onCancel} />
      </div>
      <p className="m-0 rounded-lg bg-line-soft px-2.5 py-2 font-body text-[13.5px] text-ink-3 leading-[1.65]">
        {`“${quote}”`}
      </p>
      <label htmlFor={inputId} className="-mb-1 text-ink-4 text-meta">
        {mode.kind === "question" ? "哪里不对，或者哪里没看懂？" : "用你自己的话记下来"}
      </label>
      <textarea
        id={inputId}
        // biome-ignore lint/a11y/noAutofocus: 点了「有问题 / 记笔记」就是要开始写
        autoFocus
        value={mode.draft}
        onChange={(event) => onDraft(event.target.value)}
        placeholder={
          mode.kind === "question"
            ? "比如：这里说每个监听器一行，那 10 个监听器就是 10 行吗？"
            : "比如：表里有行 = 有监听器还没处理成功"
        }
        className={cx(
          "box-border block w-full resize-y rounded-[10px] border border-line bg-surface px-3 py-2.5 font-ui text-ink leading-[1.6] focus:border-teal-mark focus:shadow-[0_0_0_3px_var(--color-teal-wash)] focus:outline-none",
          sheet ? "min-h-[104px] text-base" : "min-h-[88px] text-[14.5px]",
        )}
      />
      <div className="flex items-center justify-between gap-2">
        <span className="text-ink-4 text-meta">只存在这个浏览器里</span>
        <div className="flex gap-1.5">
          <button type="button" className={button("normal", "sm")} onClick={onCancel}>
            取消
          </button>
          <button type="button" className={button("primary", "sm")} onClick={onSave}>
            保存
          </button>
        </div>
      </div>
    </div>
  );
}

// ---------- 侧栏「这一篇的批注」 ----------

export function EntryNotesCard() {
  const { slug, open } = useEntry();
  const { annotations } = useAnnotations();
  const mine = annotations.filter((a) => a.slug === slug);
  return (
    <section
      aria-label="这一篇的批注"
      className="rounded-card border border-line bg-surface p-4.5 shadow-card"
    >
      <div className="mb-1.5 flex items-baseline justify-between font-bold font-display text-base text-ink">
        这一篇的批注
        <span className="font-normal font-ui text-ink-4 text-meta">{mine.length} 条</span>
      </div>
      {mine.length === 0 ? (
        <p className="m-0 font-ui text-ink-4 text-meta leading-normal">
          选中正文里的一段文字，就能记下「有问题」或「笔记」。
        </p>
      ) : (
        <div className="-mx-2.5 flex flex-col">
          {mine.map((a) =>
            a.orphaned ? (
              <div
                key={a.id}
                className="flex flex-col gap-1 px-2.5 py-2.5 font-ui text-ink-3 text-sm"
              >
                <span className="flex gap-1.5">
                  <KindChip kind={a.kind} />
                  <span className="inline-flex h-[22px] items-center rounded-chip bg-line-soft px-2 font-semibold text-ink-3 text-tiny">
                    原文已变
                  </span>
                </span>
                <span className="line-clamp-2">{a.text || "（没写说明）"}</span>
              </div>
            ) : (
              <button
                key={a.id}
                type="button"
                onClick={() => open(a.id)}
                className="flex cursor-pointer flex-col items-start gap-1 rounded-lg px-2.5 py-2.5 text-left font-ui text-ink-2 text-sm leading-normal hover:bg-line-soft"
              >
                <KindChip kind={a.kind} />
                <span className="line-clamp-2">{a.text || "（没写说明）"}</span>
              </button>
            ),
          )}
        </div>
      )}
      <div className="mt-3 border-line-soft border-t pt-3 font-ui text-sm">
        <Link href="/notes">全部批注 · 全站 {annotations.length} 条 →</Link>
      </div>
    </section>
  );
}
