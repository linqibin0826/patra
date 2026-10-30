// patra-learn/src/content/articles/l1/parallel-exams.tsx —— 1 号线第 4 站：并行考试
import { ArticleSection } from "@/components/article-section";
import { CodeBlock } from "@/components/code-block";
import { InlineCode } from "@/components/inline-code";
import { Term } from "@/components/term";

export default function ParallelExamsArticle() {
  return (
    <>
      <ArticleSection title="考卷发下去，几个考场同时开考">
        <p>
          上一站裁判算出了考卷范围，这一站正式开考。关键词是
          <strong className="text-ink">并行</strong>
          ：考试不排队，preflight、后端各单元、portal 各自领一台 GitHub 免费的云端{" "}
          <Term>runner</Term>{" "}
          同时开工。总耗时不是各科相加，而是取最慢的那一科——这是"几分钟出成绩"的另一半秘密（第一半是上一站的"只考改过的"）。
        </p>
        <figure className="flex flex-col gap-2">
          <div className="overflow-x-auto rounded-xl border border-line bg-surface p-4">
            <svg
              viewBox="0 0 720 348"
              role="img"
              aria-label="detect-changes 发卷后 preflight、backend 矩阵、portal 三个考场并行，成绩汇入 required-check 总闸灯"
              className="min-w-[640px]"
            >
              <title>并行泳道汇入 required-check 总闸灯</title>
              {/* 发卷 */}
              <rect
                x="260"
                y="16"
                width="200"
                height="44"
                rx="12"
                fill="#ffffff"
                stroke="#e3e5df"
                strokeWidth="1.5"
              />
              <text
                x="360"
                y="44"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                detect-changes 发卷
              </text>
              {/* 分发箭头 */}
              <line x1="310" y1="60" x2="140" y2="102" stroke="#8b929b" strokeWidth="1.8" />
              <polygon points="142,97 131,104 146,107" fill="#8b929b" />
              <line x1="360" y1="60" x2="360" y2="102" stroke="#8b929b" strokeWidth="1.8" />
              <polygon points="355,102 360,111 365,102" fill="#8b929b" />
              <line x1="410" y1="60" x2="580" y2="102" stroke="#8b929b" strokeWidth="1.8" />
              <polygon points="574,107 589,104 578,97" fill="#8b929b" />
              <text
                x="200"
                y="88"
                textAnchor="middle"
                fontSize="11.5"
                fill="#8b929b"
                stroke="#fff"
                strokeWidth="3"
                style={{ paintOrder: "stroke" }}
              >
                总是考
              </text>
              <text x="392" y="88" textAnchor="start" fontSize="11.5" fill="#8b929b">
                只考改过的单元
              </text>
              <text
                x="530"
                y="88"
                textAnchor="middle"
                fontSize="11.5"
                fill="#8b929b"
                stroke="#fff"
                strokeWidth="3"
                style={{ paintOrder: "stroke" }}
              >
                改了前端才考
              </text>
              {/* 三考场 */}
              <rect
                x="25"
                y="112"
                width="205"
                height="96"
                rx="12"
                fill="#ffffff"
                stroke="#2e66c9"
                strokeWidth="1.8"
              />
              <text
                x="127"
                y="140"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                preflight 体检
              </text>
              <text x="127" y="162" textAnchor="middle" fontSize="11.5" fill="#565d66">
                镜像版本三处一致
              </text>
              <text x="127" y="182" textAnchor="middle" fontSize="11.5" fill="#565d66">
                module-graph 没过期
              </text>
              <rect
                x="258"
                y="112"
                width="205"
                height="96"
                rx="12"
                fill="#ffffff"
                stroke="#2e66c9"
                strokeWidth="1.8"
              />
              <text
                x="360"
                y="140"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                backend 分科矩阵
              </text>
              <text x="360" y="162" textAnchor="middle" fontSize="11.5" fill="#565d66">
                registry · catalog · identity…
              </text>
              <text x="360" y="182" textAnchor="middle" fontSize="11.5" fill="#565d66">
                一单元一考场，同时开考
              </text>
              <rect
                x="491"
                y="112"
                width="205"
                height="96"
                rx="12"
                fill="#ffffff"
                stroke="#2e66c9"
                strokeWidth="1.8"
              />
              <text
                x="593"
                y="140"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                portal 考场
              </text>
              <text x="593" y="162" textAnchor="middle" fontSize="11.5" fill="#565d66">
                lint + 类型检查 + 单测
              </text>
              <text x="593" y="182" textAnchor="middle" fontSize="11.5" fill="#565d66">
                e2e 参考分，PR 不拦人
              </text>
              {/* 汇入箭头 */}
              <line x1="127" y1="208" x2="300" y2="270" stroke="#8b929b" strokeWidth="1.8" />
              <polygon points="294,272 309,273 298,263" fill="#8b929b" />
              <line x1="360" y1="208" x2="360" y2="266" stroke="#8b929b" strokeWidth="1.8" />
              <polygon points="355,266 360,275 365,266" fill="#8b929b" />
              <line x1="593" y1="208" x2="420" y2="270" stroke="#8b929b" strokeWidth="1.8" />
              <polygon points="422,263 411,273 426,272" fill="#8b929b" />
              <text
                x="520"
                y="246"
                textAnchor="middle"
                fontSize="11.5"
                fill="#8b929b"
                stroke="#fff"
                strokeWidth="3"
                style={{ paintOrder: "stroke" }}
              >
                覆盖率顺路汇总（仅全量时）
              </text>
              {/* 总闸灯 */}
              <rect x="210" y="278" width="300" height="48" rx="24" fill="#2e66c9" />
              <text
                x="360"
                y="308"
                textAnchor="middle"
                fontSize="13"
                fontWeight="700"
                fill="#ffffff"
              >
                required-check 总闸灯
              </text>
              <text x="118" y="308" textAnchor="end" fontSize="11.5" fill="#8b929b">
                免考科目 = 通过
              </text>
              <text x="522" y="308" textAnchor="start" fontSize="11.5" fill="#8b929b">
                全绿才亮 → 可合并
              </text>
            </svg>
          </div>
          <figcaption className="text-xs text-fog">
            三个考场并行开考，成绩最终汇入唯一的总闸灯。分支保护只盯这一盏灯，不关心里面有几科。
          </figcaption>
        </figure>
      </ArticleSection>

      <ArticleSection title="preflight：便宜、总是考的纪律检查">
        <p>
          preflight 不测你的业务代码，它管<strong className="text-ink">纪律</strong>
          ——那些便宜但漏掉会很疼的一致性检查，所以不管改了什么它都跑。目前两项：
        </p>
        <ul className="flex list-disc flex-col gap-1.5 pl-5">
          <li>
            <strong className="text-ink">容器镜像版本两处一致。</strong>集成测试用的容器{" "}
            <Term>Docker 镜像</Term>版本（PostgreSQL 和 Redis），同时写在 <Term>workflow</Term>{" "}
            的环境变量和 Java 测试基建的常量里。preflight
            逐个核对，防止"升级时改了一处忘了另一处"——版本号只有一个事实源，其他地方必须跟它对齐。
          </li>
          <li>
            <strong className="text-ink">module-graph.json 没过期。</strong>
            上一站说过判卷全靠这张模块地图。preflight
            现场重新生成一份和仓库里的比对，不一致就报错并提示你补交：
          </li>
        </ul>
        <CodeBlock command="./gradlew dumpModuleGraph" />
        <p>
          换句话说：<strong className="text-ink">地图是裁判的眼睛，preflight 保证眼睛没老花</strong>
          ——你改了模块依赖却忘了更新地图，考试第一步就会把你打回来。
        </p>
      </ArticleSection>

      <ArticleSection title="backend 矩阵：一单元一考场">
        <p>
          后端考试用矩阵（matrix）展开：裁判给出的 backend_units
          清单里有几个单元，就开几个考场，每个单元独立领一台 <Term>runner</Term>。每个考场做的事：
        </p>
        <ul className="flex list-disc flex-col gap-1.5 pl-5">
          <li>从 module-graph.json 查出这个单元该跑哪些 Gradle 任务（编译、单测、集成测试）；</li>
          <li>
            如果这科含集成测试，先把 PostgreSQL 和 Redis 的 <Term>Docker 镜像</Term>
            并行预拉下来，别让测试框架现场慢慢下载；
          </li>
          <li>跑测试，把测试报告存档 7 天备查。</li>
        </ul>
        <p>
          矩阵配置了 fail-fast: false——一个单元挂了，其他单元照常考完。这样一次失败你能看到
          <strong className="text-ink">全部</strong>挂科的科目，而不是修一科、再跑一轮才发现下一科。
        </p>
        <p>
          portal 考场则是前端的对应物：Biome lint、TypeScript 类型检查、Vitest
          单测都是硬门槛；portal 的 Playwright E2E 在 PR
          里是"参考分"（挂了不拦合并），只在夜里的全量考里才计入总分——端到端测试偶发抖动多，不让它平白卡住白天的迭代；本学习站自己的
          E2E 更简单，只在本地跑、不进 CI。
        </p>
      </ArticleSection>

      <ArticleSection title="required-check：唯一的总闸灯">
        <p>
          所有科目最后汇入一个叫 <InlineCode>required-check</InlineCode> 的收尾
          job。它自己不考任何东西，只做一件事：收齐各科成绩单，
          <strong className="text-ink">全部是"通过"或"合法免考"才亮绿灯</strong>
          。免考名单（allowed-skips）里是 backend、portal、coverage 三科——上一站讲过的 docs-only
          放行就靠它。
        </p>
        <p>
          为什么要多此一举加一层汇总，而不是让分支保护直接盯每一科？因为考的科目数
          <strong className="text-ink">每次都不一样</strong>——改 catalog 考一科，改 commons-core
          这种地基公共库按模块图波及全部六科。分支保护里的 required
          名单是写死的，没法跟着变；于是让它只认这一个固定名字的总闸。这也带来一条铁律：
          <strong className="text-ink">这个 job 的名字绝对不能改</strong>
          ，改了名，分支保护就找不到灯，任何 PR 都合不进去。
        </p>
        <p>
          最后补一块拼图：每天夜里（UTC 18 点，北京时间凌晨 2 点）这套 <Term>workflow</Term>{" "}
          还会由定时器自动全量跑一遍——所有单元重考、E2E
          转为硬门槛、覆盖率聚合上传。它兜住"选择性考试万一漏了什么"的极小概率，顺便让构建缓存保温。考试用到的少数凭据（缓存加密钥匙、覆盖率平台的令牌）都放在
          GitHub 的 <Term>secrets</Term> 保险柜里，考试要用的凭据一个都没进仓库。
        </p>
        <p>
          至此 1 号线到站。灯是绿的、意见都 resolve 了，你点下 Merge——列车驶入换乘站，2
          号线（上线线）自动发车。
        </p>
      </ArticleSection>
    </>
  );
}
