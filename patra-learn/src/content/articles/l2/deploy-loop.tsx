// patra-learn/src/content/articles/l2/deploy-loop.tsx —— 2 号线第 3 站：部署闭环
import { ArticleSection } from "@/components/article-section";
import { CodeBlock } from "@/components/code-block";
import { InlineCode } from "@/components/inline-code";
import { Term } from "@/components/term";

export default function DeployLoopArticle() {
  return (
    <>
      <ArticleSection title="最后一公里，交给一个带单测的脚本">
        <p>
          "换上新镜像"这句话，展开是一串必须按顺序做对的事：镜像在不在、格式对不对、先起谁后起谁、起来之后活没活、活了是不是真的新版本。这段逻辑没有散落在{" "}
          <Term>workflow</Term> 的 yml 里，而是收进一个专门的脚本{" "}
          <InlineCode>patra-infra/cd/deploy.sh</InlineCode>——后端的 cd.yml 和前端的 portal-cd.yml
          共用它，而且它自己带一套单元测试（<InlineCode>deploy.test.sh</InlineCode>
          ）：部署逻辑是这套体系里最不能出错的代码，所以享受和业务代码同级的测试待遇。
        </p>
        <p>调用方式只有两个参数——要部署的镜像 tag（commit sha），和要部署哪些服务：</p>
        <CodeBlock command={'bash patra-infra/cd/deploy.sh "$TAG" "$SERVICES"'} />
      </ArticleSection>

      <ArticleSection title="第 0 步：先验票——入参校验">
        <p>
          正式干活之前，脚本先检查手里的单子合不合法：服务清单必须是非空的字符串数组，且
          <strong className="text-ink">每个名字都必须在花名册里</strong>
          。有任何一个不认识的名字，直接 <InlineCode>exit 2</InlineCode>{" "}
          拒绝执行——一个字都不部署。这条看似多余的检查是被一次真实翻车逼出来的（打错服务名却报"部署成功"），下下站的事故档案里细讲。
        </p>
        <p>
          花名册指的是同目录的 <InlineCode>services.json</InlineCode>——整条 CD
          的单一事实源（SSOT）。每个服务一个条目：叫什么名、gradle
          任务是哪个、端口多少、镜像叫什么、健康检查探哪个地址。cd.yml 的构建步骤和 deploy.sh
          都只按名字来这里查，从不自己枚举。于是"加一个新服务"变成纯配置活：加一个条目（再补上
          compose 里的 service 块），<strong className="text-ink">workflow 逻辑一行不用改</strong>。
        </p>
      </ArticleSection>

      <ArticleSection title="镜像就位，顺手验一次格式">
        <p>
          每个服务开工前先确认镜像在本机 daemon
          里。正常上线时它必然在——上一站刚在这台机器上打的。镜像不在本地只有一种正经场景：回滚旧版本、而本机缓存恰好被清了。这时才去{" "}
          <Term>GHCR</Term> 备份网盘回源拉取，网络不给力就指数退避重试：等 30 秒、60 秒、120
          秒各试一轮，都失败才认输。
        </p>
        <p>
          镜像到手，再断言一次架构必须是
          arm64——上一站说过打包已搬回本机，正常产物不可能错；这道断言防的是
          <strong className="text-ink">从 GHCR 拉回来的历史归档</strong>
          ：架构改造之前的旧镜像有 amd64 的，回滚时若不检查就会把三个月事故的主角重新请回来。
        </p>
      </ArticleSection>

      <ArticleSection title="按依赖顺序换新">
        <p>
          换新不是乱序齐上，服务清单 <InlineCode>patra-infra/cd/services.json</InlineCode>{" "}
          的条目顺序就是换新顺序：
          <InlineCode>registry identity gateway catalog interaction portal learn</InlineCode>
          。identity 排在 gateway 前面，因为网关每个请求都要找 identity 查会话；interaction 排在
          catalog 后面，因为它运行时要调 catalog——跟开饭先上米饭一个道理。脚本按这个顺序过一遍，
          <strong className="text-ink">只处理本次名单里点到名的服务</strong>
          ，没改的服务连碰都不碰。轮到谁，就用带版本号的环境变量（如{" "}
          <InlineCode>CATALOG_IMAGE_TAG</InlineCode>）执行 <Term>docker compose</Term> 的{" "}
          <InlineCode>up -d</InlineCode>，把旧容器换成新镜像。
        </p>
        <figure className="flex flex-col gap-2">
          <div className="overflow-x-auto rounded-xl border border-line bg-surface p-4">
            <svg
              viewBox="0 0 720 330"
              role="img"
              aria-label="deploy.sh 流水：入参校验、镜像就位、arm64 断言、按依赖顺序 up、健康检查与对版验证，全过才记 last-good；失败走自动回滚"
              className="min-w-[640px]"
            >
              <title>deploy.sh 步骤流水</title>
              {/* 第一行 三步 */}
              <rect
                x="20"
                y="30"
                width="200"
                height="72"
                rx="12"
                fill="#ffffff"
                stroke="#e3e5df"
                strokeWidth="1.5"
              />
              <text
                x="120"
                y="58"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                🎫 入参校验
              </text>
              <text x="120" y="80" textAnchor="middle" fontSize="11.5" fill="#565d66">
                名字不在花名册 → exit 2
              </text>
              <rect
                x="260"
                y="30"
                width="200"
                height="72"
                rx="12"
                fill="#ffffff"
                stroke="#e3e5df"
                strokeWidth="1.5"
              />
              <text
                x="360"
                y="58"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                📦 镜像就位
              </text>
              <text x="360" y="80" textAnchor="middle" fontSize="11.5" fill="#565d66">
                本地优先，缺了才回源 GHCR
              </text>
              <rect
                x="500"
                y="30"
                width="200"
                height="72"
                rx="12"
                fill="#ffffff"
                stroke="#e3e5df"
                strokeWidth="1.5"
              />
              <text
                x="600"
                y="58"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                🔬 arm64 断言
              </text>
              <text x="600" y="80" textAnchor="middle" fontSize="11.5" fill="#565d66">
                历史 amd64 归档直接拒收
              </text>
              <line x1="220" y1="66" x2="252" y2="66" stroke="#d95b32" strokeWidth="2" />
              <polygon points="251,61 260,66 251,71" fill="#d95b32" />
              <line x1="460" y1="66" x2="492" y2="66" stroke="#d95b32" strokeWidth="2" />
              <polygon points="491,61 500,66 491,71" fill="#d95b32" />
              {/* 折返箭头 */}
              <line x1="600" y1="102" x2="600" y2="128" stroke="#d95b32" strokeWidth="2" />
              <line x1="600" y1="128" x2="120" y2="128" stroke="#d95b32" strokeWidth="2" />
              <line x1="120" y1="128" x2="120" y2="152" stroke="#d95b32" strokeWidth="2" />
              <polygon points="115,151 120,160 125,151" fill="#d95b32" />
              {/* 第二行 三步 */}
              <rect
                x="20"
                y="160"
                width="200"
                height="72"
                rx="12"
                fill="#ffffff"
                stroke="#d95b32"
                strokeWidth="1.8"
              />
              <text
                x="120"
                y="188"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                🔄 按序换新
              </text>
              <text x="120" y="210" textAnchor="middle" fontSize="11.5" fill="#565d66">
                compose up -d，被依赖的先上
              </text>
              <rect
                x="260"
                y="160"
                width="200"
                height="72"
                rx="12"
                fill="#ffffff"
                stroke="#e3e5df"
                strokeWidth="1.5"
              />
              <text
                x="360"
                y="188"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                🩺 健康检查 + 对版
              </text>
              <text x="360" y="210" textAnchor="middle" fontSize="11.5" fill="#565d66">
                活着吗？跑的真是新版吗？
              </text>
              <rect
                x="500"
                y="160"
                width="200"
                height="72"
                rx="12"
                fill="#f1f2ee"
                stroke="#d95b32"
                strokeWidth="1.8"
              />
              <text
                x="600"
                y="188"
                textAnchor="middle"
                fontSize="12.5"
                fontWeight="700"
                fill="#22262c"
              >
                📒 记 last-good
              </text>
              <text x="600" y="210" textAnchor="middle" fontSize="11.5" fill="#565d66">
                小本本记下"这版好用"
              </text>
              <line x1="220" y1="196" x2="252" y2="196" stroke="#d95b32" strokeWidth="2" />
              <polygon points="251,191 260,196 251,201" fill="#d95b32" />
              <line x1="460" y1="196" x2="492" y2="196" stroke="#d95b32" strokeWidth="2" />
              <polygon points="491,191 500,196 491,201" fill="#d95b32" />
              <text
                x="480"
                y="184"
                textAnchor="middle"
                fontSize="11.5"
                fill="#8b929b"
                stroke="#fff"
                strokeWidth="3"
                style={{ paintOrder: "stroke" }}
              >
                全过 ✓
              </text>
              {/* 失败分支 */}
              <line
                x1="360"
                y1="232"
                x2="360"
                y2="266"
                stroke="#8b929b"
                strokeWidth="1.8"
                strokeDasharray="5 4"
              />
              <polygon points="355,265 360,274 365,265" fill="#8b929b" />
              <rect
                x="240"
                y="274"
                width="240"
                height="42"
                rx="10"
                fill="#f1f2ee"
                stroke="#e3e5df"
                strokeWidth="1.5"
              />
              <text x="360" y="300" textAnchor="middle" fontSize="11.5" fill="#565d66">
                失败 ✗ → 自动回滚（下下站细讲）
              </text>
              {/* 循环标注 */}
              <text x="672" y="262" textAnchor="end" fontSize="11.5" fill="#8b929b">
                ↺ 名单里每个服务各走一遍
              </text>
            </svg>
          </div>
          <figcaption className="text-xs text-fog">
            deploy.sh
            的一轮闭环。前三步是资格审查，后三步才动真格；名单里每个服务按依赖顺序各走一遍这条流水线，谁失败都不影响别人走完。
          </figcaption>
        </figure>
      </ArticleSection>

      <ArticleSection title="小本本：last-good 是服务级的">
        <p>
          流水线尾端那本"小本本"值得单独说。每个服务通过健康检查和对版验证后，脚本立刻把当前 tag
          写进 mini 上的 <InlineCode>~/.patra/cd/last-good-&lt;服务名&gt;</InlineCode>
          ——文件内容就是一个 commit sha，意思是"这个服务上一次确认好用的版本"。
        </p>
        <p>
          注意两个细节。一是<strong className="text-ink">每个服务一份账</strong>
          ，不是整批一份：catalog 的 last-good 和 identity 的各记各的，回滚时互不牵连。二是
          <strong className="text-ink">通过一个记一个</strong>
          ，不等整批结束：就算这批里最后一个服务翻了车，前面成功的几个也已经把自己的新版本记上账了。这本账就是下下站"自动回滚"的全部底气——出事的时候，机器人翻的就是它。
        </p>
        <p>
          收尾前脚本还会顺手 <InlineCode>docker image prune</InlineCode>{" "}
          清理无主镜像，别让几百次部署把磁盘塞满。不过先别急着谈收尾——流水线中间那格"健康检查"，两个字背后全是坑。下一站专门讲：怎么才算"活着"，以及
          localhost 怎么背叛过这套系统。
        </p>
      </ArticleSection>
    </>
  );
}
