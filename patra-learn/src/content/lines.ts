// patra-learn/src/content/lines.ts
import type { Line } from "./types";

/** 换乘节点：1 号线终点 → 2 号线起点的视觉连接，不计入 13 站、无文章。 */
export const TRANSFER_NODE = {
  id: "merge-to-main",
  name: "合并进 main",
  from: "l1",
  to: "l2",
} as const;

export const LINES: Line[] = [
  {
    id: "l1",
    name: "1 号线 · 质检线",
    theme: "合并前的门禁考试（CI）",
    color: "#2e66c9",
    softColor: "#5b87d6",
    status: "open",
    stations: [
      {
        id: "write-code",
        name: "写代码",
        summary: "一切从分支开始：本地开发、提交规范与推送的最小闭环",
      },
      { id: "open-pr", name: "开 PR", summary: "PR 是考场报名：分支保护、必过检查与 squash 合并" },
      {
        id: "changed-only",
        name: "只考改过的",
        summary: "detect-changes 路由——改哪考哪，docs-only 直接放行",
      },
      {
        id: "parallel-exams",
        name: "并行考试",
        summary: "preflight、后端矩阵、portal 同时开考，required-check 汇总放行",
      },
    ],
  },
  {
    id: "l2",
    name: "2 号线 · 上线线",
    theme: "合并后的自动部署（CD）",
    color: "#d95b32",
    softColor: "#e27e5c",
    status: "open",
    stations: [
      {
        id: "runner-picks-up",
        name: "mini 领任务",
        summary: "常驻家里的 Mac mini runner：怎么领活、凭什么领",
      },
      { id: "native-build", name: "本机打包", summary: "arm64 原生构建——芯片格式事故后的根治方案" },
      {
        id: "deploy-loop",
        name: "部署闭环",
        summary: "deploy.sh：镜像就位、依赖顺序拉起、记住上一个好版本",
      },
      {
        id: "health-check",
        name: "健康检查",
        summary: "上线不算完，得验货：healthcheck 与 127.0.0.1 的讲究",
      },
      {
        id: "ship-and-rollback",
        name: "上线与回滚",
        summary: "验货失败自动退回；手动回滚两分钟一条命令",
      },
    ],
  },
  {
    id: "l3",
    name: "3 号线 · 守夜线",
    theme: "每日自动巡检",
    color: "#7a5fb8",
    softColor: "#957fc8",
    status: "open",
    stations: [
      { id: "daily-0700", name: "每天 07:00", summary: "定时器怎么定：cron、UTC 与北京时间的换算" },
      {
        id: "roll-call-and-key",
        name: "查岗与钥匙",
        summary: "查 runner 在不在岗，以及那把叫 RUNNER_ADMIN_TOKEN 的钥匙",
      },
      {
        id: "four-checks",
        name: "四项体检",
        summary: "docker、磁盘、容器健康、runner 版本——金丝雀四连检",
      },
      {
        id: "notification-philosophy",
        name: "通知哲学",
        summary: "为什么拆掉了独立通知通道：单人环境的运维克制",
      },
    ],
  },
  {
    id: "l4",
    name: "4 号线 · 架构线",
    theme: "六边形架构 / DDD / 微服务拓扑",
    color: "#1e8e7e",
    softColor: "#4aa598",
    status: "planned",
    stations: [
      { id: "hexagonal", name: "六边形架构", summary: "规划中" },
      { id: "ddd-bounded-context", name: "DDD 与限界上下文", summary: "规划中" },
      { id: "microservice-topology", name: "微服务拓扑", summary: "规划中" },
    ],
  },
  {
    id: "l5",
    name: "5 号线 · 数据线",
    theme: "采集管道 / 存储",
    color: "#b0498c",
    softColor: "#c06ea3",
    status: "planned",
    stations: [
      { id: "import-pipeline", name: "采集管道", summary: "规划中" },
      { id: "storage-and-index", name: "存储与索引", summary: "规划中" },
    ],
  },
];
