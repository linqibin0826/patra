import type { Metadata } from "next";
import Link from "next/link";
import { connection } from "next/server";
import type { ReactNode } from "react";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { PolicyTable } from "@/components/privacy/PolicyTable";
import { PolicyToc } from "@/components/privacy/PolicyToc";
import { sessionTrackingCookie } from "@/lib/analytics/server/identity-cookies";
import { deviceCookie, sessionCookieName } from "@/lib/auth/session-cookie";

export const metadata: Metadata = { title: "隐私政策 · Patra" };

/// 生效日期与最近更新：上线当天改成实际日期
const POLICY_DATES = { effective: "[上线当天]", updated: "[上线当天]" } as const;

/// 还没定的内容，原样显示方括号占位（合并前要填上）
function Placeholder({ children }: { children: string }) {
  return (
    <span className="rounded px-1 font-mono text-[0.9em] text-amber-700 bg-amber-50">
      {children}
    </span>
  );
}

function Code({ children }: { children: string }) {
  return (
    <code className="rounded bg-paper-200 px-1.25 py-px font-mono text-[0.88em]">{children}</code>
  );
}

const SECTIONS = [
  { id: "p-scope", toc: "适用范围", title: "适用范围" },
  { id: "p-collect", toc: "收集哪些信息", title: "我们收集哪些信息" },
  { id: "p-two", toc: "两类阅读数据", title: "两类阅读数据" },
  { id: "p-cookie", toc: "Cookie", title: "Cookie" },
  { id: "p-share", toc: "共享与委托处理", title: "共享与委托处理" },
  { id: "p-keep", toc: "保留多久", title: "保留多久" },
  { id: "p-rights", toc: "你可以做什么", title: "你可以做什么" },
  { id: "p-minor", toc: "未成年人", title: "未成年人" },
  { id: "p-contact", toc: "联系我们", title: "联系我们与政策变更" },
] as const;

type SectionId = (typeof SECTIONS)[number]["id"];

/// 隐私政策（简报 v0.18 §3.9，正文照画板 Policy）：九节加「一分钟看完」。请求时渲染：Cookie 表的名字随运行时的
/// `PATRA_SESSION_COOKIE_SECURE` 变（mini 上关着，名字不带 `__Host-`），构建时预渲染会写错
export default async function PrivacyPage() {
  await connection();
  const body: Record<SectionId, ReactNode> = {
    "p-scope": (
      <p>
        本政策适用于 Patra 医学文献门户（<span>https://patra.linqibin.dev</span>
        ）上的全部页面与功能，包括检索与阅读文献、账号、讨论与投稿经验、提醒邮件。Patra 由{" "}
        <Placeholder>[运营者名称]</Placeholder> 开发和运营。
      </p>
    ),
    "p-collect": (
      <>
        <PolicyTable
          head={["信息", "内容", "用来做什么"]}
          rows={[
            [
              "账号信息",
              "邮箱、用户名、昵称；密码只存加密后的摘要，任何人都看不到原文",
              "登录、找回密码、发送账号与提醒邮件",
            ],
            [
              "个人资料",
              "头像、简介、职称、机构、科室、链接，都由你自己填写",
              "按你在「设置 › 隐私」里选的可见性展示",
            ],
            ["你发布的内容", "评论、投稿经验、公开的收藏夹", "在对应页面上展示"],
            [
              "登录记录",
              "登录时间、设备标识、浏览器与操作系统、IP 地址及据此查到的大致地区",
              "账号安全：登录设备列表、新设备登录提醒",
            ],
            [
              "浏览行为",
              "打开了哪篇文献、检索了什么、是否读了全文、下载 PDF、复制引用、导出，以及文献卡片的展示与点击；附带时间、设备标识、会话标识、浏览器类型、省级地区，登录时附带账号 ID",
              "浏览数、热读、「看过这篇的人还看了」，以及了解网站哪里好用、哪里不好用",
            ],
            ["阅读历史", "登录后打开过的文献与时间、是否读了全文", "给你自己翻看"],
          ]}
        />
        <p>
          浏览行为里不存 IP 地址：IP
          只在收到请求的那一刻用来查省级地区、识别搜索引擎等爬虫，用完即丢。
        </p>
      </>
    ),
    "p-two": (
      <>
        <p>同一次阅读，会进两套互相独立的数据：</p>
        <h3>统计用的浏览事件</h3>
        <p>
          所有访客都有。为了「同一个人反复打开只算一次」和计算有多少人回来，事件里带着设备标识，登录时还带着账号
          ID。对外只显示汇总后的人数，不显示是谁看过；你注销账号时，与你账号关联的事件会被删除。
        </p>
        <h3>你的阅读历史</h3>
        <p>
          只有登录用户有，默认开启，只有你自己能看到。关闭「阅读记录」后不再新增，已有的记录保留；可以逐条删除或一键清空。关闭、删除、清空都只影响阅读历史，不改变文献的浏览数。
        </p>
        <p>
          「看过这篇的人还看了」只根据当前这篇文献被哪些文献一起读过来计算，不根据你个人的阅读历史推荐；至少
          3 个不同的人一起读过才会出现。
        </p>
      </>
    ),
    "p-cookie": (
      <>
        <p>Patra 只使用自己域名下的 Cookie，不放第三方 Cookie：</p>
        <PolicyTable
          head={["名称", "作用", "有效期"]}
          rows={[
            [<Code key="n">{sessionCookieName()}</Code>, "保持登录状态", "到退出登录或会话过期"],
            [
              <Code key="n">{deviceCookie().name}</Code>,
              "设备标识：每个访客第一次访问时生成，用于浏览数去重与登录设备列表",
              "400 天",
            ],
            [
              <Code key="n">{sessionTrackingCookie().name}</Code>,
              "把一次访问里的浏览串起来，30 分钟没有活动就换新",
              "关闭浏览器时失效",
            ],
          ]}
        />
        <p>浏览器禁用 Cookie 后，登录等功能无法使用。</p>
      </>
    ),
    "p-share": (
      <>
        <p>
          我们不出售、不出租你的个人信息，也不和广告商共享。为了提供服务，以下处理委托给服务商：
        </p>
        <ul>
          <li>提醒邮件与账号邮件通过邮件服务商 Resend 发送，它会拿到收件邮箱与邮件内容。</li>
          <li>网站访问经 Cloudflare 的网络转发到我们的服务器。</li>
          <li>
            其余数据存放在我们自己的服务器上（<Placeholder>[服务器所在地]</Placeholder>）。
          </li>
        </ul>
        <p>法律法规要求时，我们会依法提供相应信息。</p>
      </>
    ),
    "p-keep": (
      <PolicyTable
        head={["数据", "保留期"]}
        rows={[
          ["浏览事件明细", "90 天，到期自动删除"],
          ["汇总统计（如每篇文献每天的读者数）", "长期保留；只有人数，没有设备标识和账号"],
          ["阅读历史", "12 个月，到期自动删除；也可以随时自己删除"],
          ["登录记录", "会话结束后 190 天"],
          ["账号信息、个人资料、发布的内容", "直到你删除或注销账号"],
        ]}
      />
    ),
    "p-rights": (
      <ul>
        <li>
          <b>关闭阅读记录</b>：
          <Link href="/settings/privacy#reading" className="text-link hover:text-link-hover">
            设置 › 隐私 › 阅读记录
          </Link>
          。
        </li>
        <li>
          <b>删除阅读历史</b>：在
          <Link href="/history" className="text-link hover:text-link-hover">
            阅读历史
          </Link>
          里逐条删除，或一键清空。
        </li>
        <li>
          <b>修改资料与可见性</b>：设置 › 个人资料、设置 › 隐私。
        </li>
        <li>
          <b>注销账号</b>
          ：设置 › 账号。提交后有 15
          天冷静期，期满删除账号信息、个人资料、阅读历史，以及浏览事件里与你账号关联的部分；登录记录按上面的保留期到期删除。
        </li>
        <li>
          <b>查询、更正或删除其他信息</b>：写信给我们（见第 09 节）。
        </li>
      </ul>
    ),
    "p-minor": <p>Patra 面向医学专业人员与学生。不满 14 周岁的未成年人请不要注册账号。</p>,
    "p-contact": (
      <>
        <p>
          对本政策或你的个人信息有任何问题，请写信到 <Placeholder>[联系邮箱]</Placeholder>
          ，我们会尽快回复。
        </p>
        <p>政策修改后会更新本页的版本与日期；影响你权利的重大变更，会在站内提前告知。</p>
      </>
    ),
  };

  return (
    <>
      <TopNav />
      <main>
        <div className="mx-auto grid max-w-page grid-cols-[220px_minmax(0,1fr)] items-start gap-18 px-gutter pt-14 pb-30 max-md:grid-cols-1 max-md:gap-10 max-md:pt-8">
          <PolicyToc items={SECTIONS.map(({ id, toc }) => ({ id, label: toc }))} />
          <article className="max-w-[68ch]">
            <h1 className="font-serif text-display-3 leading-heading font-medium tracking-tight text-fg-1">
              隐私政策
            </h1>
            <div className="mt-4 flex flex-wrap gap-x-4 gap-y-1 font-mono text-xs text-fg-3">
              <span>第 1 版</span>
              <span>
                生效日期 <Placeholder>{POLICY_DATES.effective}</Placeholder>
              </span>
              <span>
                最近更新 <Placeholder>{POLICY_DATES.updated}</Placeholder>
              </span>
            </div>
            <p className="mt-7 font-serif text-xl leading-[1.6] text-fg-2">
              这份政策说明 Patra
              收集哪些信息、拿来做什么、存多久，以及你可以怎样关闭和删除。我们只用自己的第一方
              Cookie，不接入任何第三方统计或广告。
            </p>
            <section
              aria-labelledby="policy-summary"
              className="mt-10 rounded-lg border border-border-subtle bg-paper-100 px-6 py-5"
            >
              <h2
                id="policy-summary"
                className="mb-3 font-mono text-2xs font-medium tracking-caps text-fg-3 uppercase"
              >
                一分钟看完
              </h2>
              <ul className="flex list-disc flex-col gap-1.5 pl-4.5 text-base leading-[1.6] text-fg-1">
                <li>
                  浏览文献时，我们记录你看了哪篇、有没有读全文，用来显示浏览数、「热读」和「看过这篇的人还看了」。
                </li>
                <li>文献页上的浏览数是汇总后的人数，不显示是谁看过。</li>
                <li>
                  登录后，你打开过的文献还会记进只有你自己能看到的「阅读历史」。可以随时关闭、逐条删除或一键清空，这些都不影响浏览数。
                </li>
                <li>行为数据里不存 IP 地址，只存到省一级的地区。</li>
                <li>不出售你的信息，不用第三方统计，不做跨站追踪。</li>
              </ul>
            </section>
            {SECTIONS.map(({ id, title }, i) => (
              <section
                key={id}
                id={id}
                aria-labelledby={`${id}-title`}
                className="mt-14 scroll-mt-22 text-md leading-[1.8] text-fg-1 [&_h3]:mt-7 [&_h3]:text-base [&_h3]:font-semibold [&_p]:mt-3.5 [&_ul]:mt-3 [&_ul]:flex [&_ul]:list-disc [&_ul]:flex-col [&_ul]:gap-2 [&_ul]:pl-5"
              >
                <h2
                  id={`${id}-title`}
                  className="flex items-baseline gap-3 font-serif text-2xl leading-[1.3] font-medium text-fg-1"
                >
                  <span className="font-mono text-xs font-medium text-accent-strong">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  {title}
                </h2>
                {body[id]}
              </section>
            ))}
          </article>
        </div>
      </main>
      <Footer />
    </>
  );
}
