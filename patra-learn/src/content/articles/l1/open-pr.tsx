// patra-learn/src/content/articles/l1/open-pr.tsx —— 1 号线第 2 站：开 PR
import { ArticleSection } from "@/components/article-section";
import { CodeBlock } from "@/components/code-block";
import { InlineCode } from "@/components/inline-code";
import { Term } from "@/components/term";

export default function OpenPrArticle() {
  return (
    <>
      <ArticleSection title="PR 是考场报名表">
        <p>
          分支推上去之后，代码想进 main 只有一条路：开一个 Pull Request。把 PR
          理解成考场报名表最贴切——它声明"我想把这条分支合进
          main"，然后整套自动考试立刻围绕它转起来：CI 开考、AI 评审员进场、
          <Term>分支保护</Term>的闸机开始盯着每一项结果。
        </p>
        <p>
          开发还没完工时，PR 可以先挂成
          draft（草稿）状态——考试照样跑，但明确告诉所有人"还没准备好合并"。写完了再转正式：
        </p>
        <CodeBlock command="gh pr create --draft" />
      </ArticleSection>

      <ArticleSection title="闸机的两把锁">
        <p>
          PR 页面那颗 Merge 按钮不是你想点就能点的。<Term>分支保护</Term>
          给它加了两把锁，全开了按钮才亮：
        </p>
        <ul className="flex list-disc flex-col gap-1.5 pl-5">
          <li>
            <strong className="text-ink">required-check 必须绿</strong>——
            <Term>GitHub Actions</Term> 里跑的一堆检查，分支保护只认{" "}
            <InlineCode>required-check</InlineCode> 这一盏总闸灯（它汇总了所有科目的成绩，第 4
            站细讲）。任何一科挂了，灯就是红的。
          </li>
          <li>
            <strong className="text-ink">review 对话必须全部 resolve</strong>
            ——评审留下的每条意见都要有下文：要么改掉，要么回复清楚为什么不改，然后把对话标记为已解决。有一条挂着没处理，就合不了。
          </li>
        </ul>
        <p>
          这两把锁加起来就是一句话：
          <strong className="text-ink">坏代码进不了 main 是制度，不是自觉</strong>。
        </p>
        <figure className="flex flex-col gap-2">
          <div className="overflow-x-auto rounded-xl border border-line bg-surface p-4">
            <svg
              viewBox="0 0 760 185"
              role="img"
              aria-label="PR 生命周期：开 PR、检查开考、评审意见、全部 resolve、squash 合并"
              className="min-w-[680px]"
            >
              <title>PR 生命周期：开 PR → 检查开考 → 评审意见 → 全部 resolve → squash 合并</title>
              {/* 轨道 */}
              <line
                x1="70"
                y1="88"
                x2="690"
                y2="88"
                stroke="#2e66c9"
                strokeWidth="6"
                strokeLinecap="round"
              />
              {/* 站点 */}
              <circle cx="70" cy="88" r="9" fill="#ffffff" stroke="#22262c" strokeWidth="3" />
              <circle cx="225" cy="88" r="9" fill="#ffffff" stroke="#22262c" strokeWidth="3" />
              <circle cx="380" cy="88" r="9" fill="#ffffff" stroke="#22262c" strokeWidth="3" />
              <circle cx="535" cy="88" r="9" fill="#ffffff" stroke="#22262c" strokeWidth="3" />
              <circle cx="690" cy="88" r="12" fill="#ffffff" stroke="#2e66c9" strokeWidth="4" />
              {/* 站名 */}
              <text
                x="70"
                y="60"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                开 PR
              </text>
              <text
                x="225"
                y="60"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                检查开考
              </text>
              <text
                x="380"
                y="60"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                评审意见
              </text>
              <text
                x="535"
                y="60"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                全部 resolve
              </text>
              <text
                x="690"
                y="60"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#2e66c9"
              >
                squash 合并
              </text>
              {/* 副标 */}
              <text x="70" y="122" textAnchor="middle" fontSize="11.5" fill="#8b929b">
                考场报名
              </text>
              <text x="225" y="122" textAnchor="middle" fontSize="11.5" fill="#8b929b">
                required-check
              </text>
              <text x="225" y="140" textAnchor="middle" fontSize="11.5" fill="#8b929b">
                必须全绿
              </text>
              <text x="380" y="122" textAnchor="middle" fontSize="11.5" fill="#8b929b">
                CodeRabbit + Codex
              </text>
              <text x="380" y="140" textAnchor="middle" fontSize="11.5" fill="#8b929b">
                逐行挑毛病
              </text>
              <text x="535" y="122" textAnchor="middle" fontSize="11.5" fill="#8b929b">
                修掉或说明理由
              </text>
              <text x="690" y="126" textAnchor="middle" fontSize="11.5" fill="#565d66">
                装订成一条
              </text>
              <text x="690" y="144" textAnchor="middle" fontSize="11.5" fill="#565d66">
                干净提交进 main
              </text>
            </svg>
          </div>
          <figcaption className="text-xs text-fog">
            一个 PR 的完整旅程。改了代码就得重新考（CI
            每次推送都重跑），评审则只评一次；终点站只有一个：squash 合并进 main。
          </figcaption>
        </figure>
      </ArticleSection>

      <ArticleSection title="AI 评审员：CodeRabbit + Codex">
        <p>
          这个仓库是单人项目，没有同事帮你看代码——但评审环节没有省掉，替补上场的是两位 AI 评审员。PR
          完工转 ready 时 Codex 自动开评；再在评论区喊一声{" "}
          <InlineCode>@coderabbitai review</InlineCode>
          ，CodeRabbit 也逐行挑毛病，还顺带跑一批安全与 lint
          扫描。可疑的空指针、漏掉的边界条件、不一致的命名，都会以行级评论的形式贴出来。
        </p>
        <p>
          每个 PR 只评一次，不复评：CodeRabbit 给公开小仓库的免费额度只有每小时 1
          次，改完再评几乎必然被限流。修得对不对，交给自己验证和 CI 重跑来把关。
        </p>
        <p>
          对每条意见的处理规则很硬：要么修掉（附上修复的提交号），要么明确回复不修的理由——不允许已读不回。别小看它们：这套
          CI/CD 体系自己改造时的 PR 里，它们就真抓出过 bug。
        </p>
      </ArticleSection>

      <ArticleSection title="squash merge：把草稿装订成一页交卷">
        <p>
          最后说合并方式。这个仓库用 <Term>squash merge</Term>：不管你在 PR
          里提交了多少次小修小补——"修 typo""再改一版""格式化"——合并时全部压成 main 上
          <strong className="text-ink">一条干净提交</strong>。你的草稿过程留在 PR
          页面里可以随时回看，但 main 的历史永远是一行一件事。
        </p>
        <p>
          这不只是洁癖，它直接支撑了 2 号线的回滚能力：上线用的 <Term>Docker 镜像</Term>以 commit
          sha 当版本号，main 历史干净意味着"回滚到某一版" =
          "按提交号精确点名某次合并"，不存在"这个提交只包含半个功能"的尴尬。
        </p>
        <p>
          点下 Merge 的那一刻，你在 1
          号线的旅程就结束了。但在这之前，考试到底是怎么考的？下一站拆开看：为什么改一行文档不用全员重考。
        </p>
      </ArticleSection>
    </>
  );
}
