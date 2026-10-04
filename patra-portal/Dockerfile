# patra-portal 运行镜像（Next.js 16 standalone Node server）
# 三阶段：deps（装依赖，缓存层）→ builder（next build，standalone trace）→ runner（精简运行镜像）。
# 用法：docker build -f patra-portal/Dockerfile patra-portal

# ---- deps：装依赖（锁文件不变则该层缓存命中）----
FROM node:24-alpine AS deps
WORKDIR /app
# pnpm 版本对齐 portal-ci.yml（pnpm/action-setup version '10'）；package.json 无 packageManager 字段
RUN npm install -g pnpm@10
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# ---- builder：next build（output:"standalone" → 产出 .next/standalone）----
FROM node:24-alpine AS builder
WORKDIR /app
RUN npm install -g pnpm@10
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN pnpm build

# ---- runner：精简运行镜像（非 root）----
FROM node:24-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production \
    PORT=4000 \
    HOSTNAME=0.0.0.0
# 非 root 运行（纵深防御）；alpine busybox adduser 语法
RUN addgroup -g 10001 -S nodejs \
    && adduser -u 10001 -S nextjs -G nodejs
# standalone 产物：server.js + 最小依赖。static 与 public 需手动 COPY（standalone 不自动带）
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public
USER nextjs
EXPOSE 4000
# standalone 入口；监听 0.0.0.0:4000（HOSTNAME/PORT env 控制）
CMD ["node", "server.js"]
