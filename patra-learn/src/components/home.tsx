// patra-learn/src/components/home.tsx
// 首页里读本地进度的部分（画板 Main）：第一屏「今天读这一条 + 进度卡」或「从这里开始 + 难度说明」、路线卡、主题卡。
// 服务端与水合时没有进度，渲染的就是新读者的样子
"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { Level } from "@/lib/progress/progress";
import { useProgress } from "@/lib/progress/use-progress";
import type { EntryCard } from "@/lib/site/entry-cards";
import { DIFFICULTY_AUDIENCE, DIFFICULTY_LABELS, LEVEL_LABELS } from "@/lib/site/labels";
import {
  continueReading,
  masteryCounts,
  nextUnread,
  rankReview,
  reviewReason,
} from "@/lib/site/today";
import type { ReviewCandidate, RouteCard, TopicCard } from "@/lib/site/views";
import { LiveMasteryChip, ProgressBar, ReadCount } from "./progress-ui";
import {
  button,
  cardClass,
  cx,
  LevelBadge,
  MasteryDot,
  meta,
  panelLabel,
  TitleText,
  TypeBadge,
} from "./ui";

const TOAST_MS = 2400;

interface Starter {
  route: { slug: string; title: string; count: number };
  entries: { slug: string; title: string; difficulty: EntryCard["difficulty"]; minutes: number }[];
}

function ReviewCard({ order, pool }: { order: string[]; pool: ReviewCandidate[] }) {
  const { progress, setMastery } = useProgress();
  const [index, setIndex] = useState(0);
  const [revealed, setRevealed] = useState(false);
  const [saved, setSaved] = useState<Level | null>(null);
  useEffect(() => {
    if (saved === null) return;
    const t = setTimeout(() => setSaved(null), TOAST_MS);
    return () => clearTimeout(t);
  }, [saved]);
  const slug = order[index % order.length] ?? "";
  const pick = pool.find((c) => c.slug === slug);
  if (!pick) return null;
  const record = progress[slug] ?? {};
  const next = () => {
    setIndex((i) => i + 1);
    setRevealed(false);
    setSaved(null);
  };

  return (
    <section
      aria-label="今天读这一条"
      className={cx(
        cardClass(),
        "flex min-w-0 flex-[999_1_600px] flex-col gap-4 px-7.5 py-7 max-sm:px-5",
      )}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className={cx(panelLabel, "text-teal")}>今天读这一条</span>
        <span className={meta}>{reviewReason(record, Date.now())} · 约 2 分钟</span>
      </div>
      <div className="flex flex-wrap items-center gap-2.5">
        <TypeBadge type={pick.type} />
        <LevelBadge difficulty={pick.difficulty} />
        {pick.where ? <span className={meta}>{pick.where}</span> : null}
      </div>
      <h1 className="m-0 font-bold font-display text-[30px] text-ink leading-[1.35]">
        <TitleText text={pick.title} />
      </h1>
      <div className="flex flex-col gap-1 rounded-inner bg-teal-wash px-4.5 py-4">
        <span className={cx(panelLabel, "text-teal")}>先自己想一想</span>
        <span className="font-display font-semibold text-ink text-lead leading-[1.6]">
          <TitleText text={pick.review.question} />
        </span>
      </div>
      {revealed ? (
        <div className="flex flex-col gap-3.5">
          <div>
            <span className={cx(panelLabel, "text-ink-4")}>答案</span>
            <p className="mt-1 mb-0 max-w-[62ch] font-semibold text-[16.5px] text-ink leading-[1.85]">
              <TitleText text={pick.review.answer} />
            </p>
            <p className="m-0 max-w-[62ch] text-[16.5px] text-ink-2 leading-[1.85]">
              <TitleText text={pick.review.explain} />
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2 border-line-soft border-t pt-3.5">
            <span className="mr-1 font-ui text-ink-3 text-ui">现在觉得自己</span>
            {([1, 2, 3] as const).map((level) => (
              <button
                key={level}
                type="button"
                aria-pressed={record.level === level}
                onClick={() => {
                  setMastery(slug, level);
                  setSaved(level);
                }}
                className={button(record.level === level ? "primary" : "normal", "sm")}
              >
                <MasteryDot level={level} className={record.level === level ? "bg-white" : ""} />
                {LEVEL_LABELS[level]}
              </button>
            ))}
            <span className="flex-1" />
            <Link href={`/entries/${slug}`} className={button("quiet", "sm")}>
              读全文 →
            </Link>
            {order.length > 1 ? (
              <button type="button" onClick={next} className={button("normal", "sm")}>
                换一条
              </button>
            ) : null}
          </div>
          {saved !== null ? (
            <span className="inline-flex h-9 items-center gap-2 self-start rounded-full bg-ink px-3.5 font-ui text-[13.5px] text-white">
              记下了：{LEVEL_LABELS[saved]}。只存在这个浏览器里
            </span>
          ) : null}
        </div>
      ) : (
        <div className="flex flex-wrap items-center gap-2.5">
          <button type="button" onClick={() => setRevealed(true)} className={button("primary")}>
            想好了，翻开看看
          </button>
          {order.length > 1 ? (
            <button type="button" onClick={next} className={button()}>
              换一条
            </button>
          ) : null}
          <span className={cx(meta, "ml-1")}>
            {order.length > 1 ? `还有 ${order.length - 1} 条候选` : "只有这一条候选"}
          </span>
        </div>
      )}
    </section>
  );
}

function ProgressCard({ total, routes }: { total: number; routes: RouteCard[] }) {
  const { progress, clear } = useProgress();
  const [confirming, setConfirming] = useState(false);
  const slugs = Object.keys(progress);
  const counts = masteryCounts(slugs, progress);
  const cont = continueReading(routes, progress);
  return (
    <section
      aria-label="这个浏览器里的进度"
      className="box-border flex flex-[1_1_300px] flex-col gap-4 rounded-card border border-moss-line bg-moss px-6.5 pt-6.5 pb-5.5"
    >
      <span className={cx(panelLabel, "text-ink-3")}>这个浏览器里的进度</span>
      <div className="flex items-baseline gap-2">
        <span className="font-display font-semibold text-[54px] text-ink tabular-nums leading-none">
          {counts.read}
        </span>
        <span className="font-ui text-ink-3 text-ui">/ {total} 篇读过</span>
      </div>
      <span className="flex h-2.5 overflow-hidden rounded-full bg-surface">
        {([3, 2, 1] as const).map((level) => (
          <span
            key={level}
            className={level === 3 ? "bg-teal" : level === 2 ? "bg-teal-soft" : "bg-teal-pale"}
            style={{ width: `${total ? (counts[level] / total) * 100 : 0}%` }}
          />
        ))}
      </span>
      <div className="flex flex-col gap-1.5 font-ui text-sm">
        {([3, 2, 1] as const).map((level) => (
          <div key={level} className="flex justify-between">
            <span className="inline-flex items-center gap-2">
              <MasteryDot level={level} />
              {LEVEL_LABELS[level]}
            </span>
            <span className="tabular-nums">{counts[level]}</span>
          </div>
        ))}
      </div>
      <div className="mt-auto flex flex-col gap-1 border-moss-line border-t pt-3.5">
        {cont ? (
          <>
            <span className={meta}>
              接着读 · {cont.route} 第 {cont.position} 篇
            </span>
            <Link href={`/entries/${cont.slug}`} className="font-bold font-display text-base">
              <TitleText text={cont.title} /> →
            </Link>
          </>
        ) : null}
        {confirming ? (
          <span className="flex flex-wrap items-center gap-2 pt-1">
            <span className={meta}>掌握度和阅读记录都会删掉</span>
            <button
              type="button"
              onClick={() => {
                clear();
                setConfirming(false);
              }}
              className={button("normal", "sm")}
            >
              确定清空
            </button>
            <button
              type="button"
              onClick={() => setConfirming(false)}
              className={button("quiet", "sm")}
            >
              算了
            </button>
          </span>
        ) : (
          <button
            type="button"
            onClick={() => setConfirming(true)}
            className={cx(button("quiet", "sm"), "-ml-3 self-start text-ink-4")}
          >
            清空这个浏览器里的记录
          </button>
        )}
      </div>
    </section>
  );
}

function StartHere({ starter, routeCount }: { starter: Starter | undefined; routeCount: number }) {
  const [first] = starter?.entries ?? [];
  return (
    <>
      <section
        aria-label="从这里开始"
        className={cx(
          cardClass(),
          "flex min-w-0 flex-[999_1_600px] flex-col gap-4.5 px-8 pt-8 pb-7 max-sm:px-5",
        )}
      >
        <span className={cx(panelLabel, "text-teal")}>第一次来？从这里开始</span>
        <h1 className="m-0 font-bold font-display text-[34px] text-ink leading-[1.3] max-sm:text-[28px]">
          借一个真实的项目，
          <br />从 HTTP 请求学到消息不丢
        </h1>
        <p className="m-0 max-w-[58ch] text-ink-3">
          Patra
          是一个医学文献平台。这里每一篇都讲它真实的代码和真实踩过的坑，标着难度，写明先读哪几篇。没写过后端也能从第一篇读起。
        </p>
        {starter ? (
          <div className="flex flex-col gap-3 rounded-card border border-line bg-surface-soft px-5 py-4.5">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <span className="font-bold font-display text-h3 text-ink">
                入门路线 · {starter.route.title}
              </span>
              <span className={meta}>{starter.route.count} 篇</span>
            </div>
            <ol className="m-0 flex list-none flex-col p-0">
              {starter.entries.map((e, i) => (
                <li key={e.slug} className="grid grid-cols-[18px_minmax(0,1fr)] gap-x-3">
                  <span className="flex flex-col items-center">
                    <i
                      className={cx(
                        "box-border block flex-none rounded-full",
                        i === 0
                          ? "mt-[5px] size-4 border-4 border-teal-mark bg-surface shadow-[0_0_0_4px_var(--color-teal-tint)]"
                          : "mt-[7px] size-3 border-2 border-ink-5 bg-surface",
                      )}
                    />
                    {i < starter.entries.length - 1 ? (
                      <b className="mt-[3px] min-h-2.5 w-0.5 flex-1 bg-line" />
                    ) : null}
                  </span>
                  <span className="flex flex-wrap items-center gap-2.5 pb-3">
                    <Link
                      href={`/entries/${e.slug}`}
                      className={i === 0 ? "font-semibold text-ink" : "text-ink-2"}
                    >
                      <TitleText text={e.title} />
                    </Link>
                    <LevelBadge difficulty={e.difficulty} />
                    <span className={meta}>{e.minutes} 分钟</span>
                  </span>
                </li>
              ))}
            </ol>
          </div>
        ) : null}
        <div className="flex flex-wrap gap-2.5">
          {first ? (
            <Link href={`/entries/${first.slug}`} className={button("primary")}>
              从第一篇读起
            </Link>
          ) : null}
          <a href="#routes" className={button()}>
            看看全部 {routeCount} 条路线
          </a>
        </div>
      </section>
      <section
        aria-label="每篇都标着难度"
        className="box-border flex flex-[1_1_300px] flex-col gap-3.5 rounded-card border border-moss-line bg-moss p-6.5"
      >
        <span className={cx(panelLabel, "text-ink-3")}>每篇都标着难度</span>
        <div className="flex flex-col gap-3">
          {(Object.keys(DIFFICULTY_LABELS) as (keyof typeof DIFFICULTY_LABELS)[]).map((d) => (
            <div key={d} className="flex flex-col gap-0.5">
              <LevelBadge difficulty={d} />
              <span className="font-ui text-ink-3 text-ui">{DIFFICULTY_AUDIENCE[d]}</span>
            </div>
          ))}
        </div>
        <div className={cx(meta, "mt-auto border-moss-line border-t pt-3.5")}>
          读完标一下掌握度，下次来首页会挑一条让你回顾。不用登录，只记在这个浏览器里。
        </div>
      </section>
    </>
  );
}

/// 第一屏：有回顾候选时是老读者，否则是新读者（含清空本地记录后）
export function TodayHero({
  pool,
  starter,
  total,
  routes,
}: {
  pool: ReviewCandidate[];
  starter: Starter | undefined;
  total: number;
  routes: RouteCard[];
}) {
  const { progress, ready } = useProgress();
  const [order, setOrder] = useState<string[] | null>(null);
  const hasProgress = Object.keys(progress).length > 0;
  // 候选顺序在读到进度时算一次，之后标掌握度不让卡片跳走；清空后重新算
  useEffect(() => {
    if (!ready) return;
    if (!hasProgress) setOrder(null);
    else if (order === null) setOrder(rankReview(pool, progress, Date.now()).map((c) => c.slug));
  }, [ready, hasProgress, order, pool, progress]);
  const old = hasProgress && order !== null && order.length > 0;
  return (
    <div className="flex flex-wrap items-stretch gap-5">
      {old ? (
        <>
          <ReviewCard order={order} pool={pool} />
          <ProgressCard total={total} routes={routes} />
        </>
      ) : (
        <StartHere starter={starter} routeCount={routes.length} />
      )}
    </div>
  );
}

const SPAN = (r: RouteCard) =>
  r.from && r.to
    ? r.from === r.to
      ? DIFFICULTY_LABELS[r.from]
      : `${DIFFICULTY_LABELS[r.from]} → ${DIFFICULTY_LABELS[r.to]}`
    : "";

export function RouteCardGrid({ routes }: { routes: RouteCard[] }) {
  const { progress, ready } = useProgress();
  return (
    <div className="grid grid-cols-4 gap-4 max-lg:grid-cols-2 max-sm:grid-cols-1">
      {routes.map((r) => {
        const slugs = r.entries.map((e) => e.slug);
        const started = ready && masteryCounts(slugs, progress).read > 0;
        const nextSlug = started ? nextUnread(slugs, progress) : undefined;
        const target = r.entries.find((e) => e.slug === nextSlug) ?? r.entries[0];
        return (
          <Link
            key={r.slug}
            href={`/routes/${r.slug}`}
            className={cx(cardClass(true), "flex flex-col gap-2.5 p-5")}
          >
            <span className="font-bold font-display text-h3 text-ink leading-[1.45]">
              {r.title}
            </span>
            <span className="flex-1 font-ui text-ink-3 text-ui leading-[1.6]">{r.summary}</span>
            <span className={meta}>
              {r.count} 篇{SPAN(r) ? ` · ${SPAN(r)}` : ""}
            </span>
            {started ? (
              <span className="flex flex-col gap-1.5">
                <ProgressBar slugs={slugs} />
                <span className={meta}>
                  <ReadCount slugs={slugs} />
                </span>
              </span>
            ) : null}
            {target ? (
              <span className="border-line-soft border-t pt-2.5 font-ui text-[13.5px] text-ink-3">
                {started ? "接着读：" : "第一篇："}
                <span className="text-ink">
                  <TitleText text={target.title} />
                </span>
              </span>
            ) : null}
          </Link>
        );
      })}
    </div>
  );
}

export function TopicCardGrid({ topics }: { topics: TopicCard[] }) {
  const { progress } = useProgress();
  return (
    <div className="grid grid-cols-3 gap-4 max-lg:grid-cols-1">
      {topics.map((t) => (
        <Link
          key={t.slug}
          href={`/topics/${t.slug}`}
          className={cx(cardClass(true), "flex flex-col gap-3 px-5.5 py-5")}
        >
          <span className="flex justify-between gap-2">
            <span className="font-bold font-display text-h3 text-ink leading-[1.45]">
              {t.title}
            </span>
            <span className={cx(meta, "whitespace-nowrap")}>{t.count} 篇</span>
          </span>
          <span className="font-ui text-ink-3 text-ui leading-[1.6]">{t.summary}</span>
          <span className="relative mx-0.5 mt-1 flex h-3.5 items-center justify-between">
            <span className="absolute inset-x-0 top-1.5 h-0.5 bg-line" />
            {t.entries.map((slug) => (
              <span key={slug} className="relative inline-flex bg-surface px-[3px]">
                <MasteryDot level={progress[slug]?.level ?? 0} className="size-2.5" />
              </span>
            ))}
          </span>
          <span className={cx(meta, "flex justify-between")}>
            <span>{t.from}</span>
            <span>{t.to}</span>
          </span>
        </Link>
      ))}
    </div>
  );
}

export function RecentCards({ cards }: { cards: EntryCard[] }) {
  return (
    <div className="grid grid-cols-3 gap-3.5 max-lg:grid-cols-1">
      {cards.map((c) => (
        <Link
          key={c.slug}
          href={`/entries/${c.slug}`}
          className={cx(cardClass(true), "flex flex-col gap-2 p-4.5")}
        >
          <span className="flex flex-wrap items-center gap-2">
            <TypeBadge type={c.type} />
            <LevelBadge difficulty={c.difficulty} />
          </span>
          <span className="font-bold font-display text-[16.5px] text-ink leading-normal">
            <TitleText text={c.title} />
          </span>
          <span className="line-clamp-3 flex-1 font-ui text-[13.5px] text-ink-3 leading-[1.6]">
            {c.summary}
          </span>
          <span className={cx(meta, "flex items-center justify-between gap-2")}>
            <span>{c.latestVersion ?? ""}</span>
            <LiveMasteryChip slug={c.slug} />
          </span>
        </Link>
      ))}
    </div>
  );
}
