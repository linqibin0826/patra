# Mac mini 连接问题排查

记录 MacBook（应用进程）↔ Mac mini（基础设施容器）跨网络访问时反复出现的**连接 / 地址 / 路由类问题**。其它部署操作、服务 URL、凭据见 `../docker/README.md`；架构大图景见 `../CLAUDE.md`。

## 为什么这类问题反复出现

本机（MacBook）同时存在多张虚拟网卡，且组合多变：

| 网卡 | 典型地址 | 来源 |
|---|---|---|
| `en0` | `192.168.1.x` | 物理 LAN |
| `utun*`（tailscale） | `100.x.x.x` (CGNAT) | tailscale 隧道，本机对 Mac mini 的正确路径 |
| `utun*`（Shadowrocket TUN） | `198.18.0.1` / `192.17.194.4` 等 | 代理 fake-ip / TUN 网段 |

任何"让进程或组件自动探测本机 IP / 自动选路由"的逻辑，都可能挑中错误的代理网卡，导致对端（Mac mini）路由不到。**通用原则：凡是要对外注册自身地址的组件，一律显式指定 tailscale IP，不要依赖自动探测。** 本机 tailscale IP 见 `env.zsh` 的 `$TAILSCALE_IP`（`tailscale ip -4 | head -1`）。

---

## 已知问题

### 1. XXL-Job 执行器注册到错误的代理网卡 IP

- **症状**：xxl-job-admin 手动触发任务 → `code:500`，`xxl-job remoting error(Read timed out), for url : http://192.17.194.4:6301/run`。执行器地址列表里是一个 admin 路由不到的地址（如 `192.17.194.4`）。
- **根因**：未注入 `XXL_JOB_EXECUTOR_IP` 时，XXL-Job 自动探测本机 IP，挑中了 Shadowrocket TUN 网卡（如 `utun9` 的 `192.17.194.4`）。admin 容器在 Mac mini，需主动回调本机执行器，但路由不到该地址 → 超时。
- **解决**：显式注入本机 tailscale IP。已在 `env.zsh` 配置（复用 `$TAILSCALE_IP`）：
  ```sh
  export XXL_JOB_EXECUTOR_IP="$TAILSCALE_IP"
  ```
  对应应用配置 `xxl.job.executor.ip: ${XXL_JOB_EXECUTOR_IP:}`（catalog/ingest 各 boot 的 `application.yml`）。
- **验证**：重启服务后，xxl-job-admin「执行器管理」注册地址应为 `http://100.x.x.x:<port>/`（catalog 执行器端口 6301）；手动触发返回 `Success`。
- **坑**：macOS GUI（Dock/Spotlight）启动的 IntelliJ **不继承** `env.zsh` 的环境变量。需从终端 `bootRun`，或在 IDEA 运行配置的 Environment variables 里手填。
- **注意**：设对 IP 后若仍 `Connect timed out`，是 admin 容器自身到不了 tailnet，见 §6。

### 2. Nacos gRPC 跨 tailscale 卡 STARTING（MTU 1280）——当前拓扑已不复现

- **症状**：应用启动卡在 Nacos 注册，nacos-client 永远收不到 gRPC SETTINGS ACK。
- **当时的根因**：tailscale wireguard MTU=1280，Nacos gRPC 的 HTTP/2 SETTINGS frame 经 Docker bridge(1500)→OrbStack→tailscale 时超过 1280 被静默丢弃（tailscale PMTU discovery 未实现），曾靠 MacBook 上的 ssh tunnel launchd agent 绕过。
- **现状**：2026-09-23 在当前拓扑（mini 改用官方 tailscale GUI、节点重建后）实测 MacBook 直连 `$PATRA_INFRA_HOST:9848` 的 HTTP/2 握手、服务注册与心跳均正常，大响应/大请求体跨 tailscale 双向也无丢包，tunnel 已拆除。应用 yml 的 `NACOS_HOST` 缺省跟随 `PATRA_INFRA_HOST`。
- **若复发**：对比 HTTP 与 gRPC 端口，区分是不是 MTU——
  ```sh
  # HTTP 端口：正常回 JSON
  curl -s -m 8 "http://$PATRA_INFRA_HOST:8848/nacos/v1/ns/instance/list?serviceName=patra-gateway&username=nacos&password=nacos"
  # gRPC 端口：纯 h2 请求回 415 即说明 HTTP/2 握手通了
  curl -s -m 8 -o /dev/null -w '%{http_code}\n' --http2-prior-knowledge http://$PATRA_INFRA_HOST:9848/
  ```
  8848 正常而 9848 握手超时才指向 MTU 问题（注：`ping -D -s 1400` 在本机 tailscale 网卡 MTU 1280 下必然 `Message too long`，正常时也如此，不能作判据）。临时解法是 `ssh -N -L 8848:127.0.0.1:8848 -L 9848:127.0.0.1:9848 linqibin@linqibins-mac-mini` 并以 `NACOS_HOST=127.0.0.1` 启动应用。

### 3. RocketMQ 客户端连不上 broker（BROKER_IP1 注册错误）

- **症状**：NameServer 拿到 broker 地址后客户端解析/连接失败。
- **根因**：broker 对外注册地址由 `docker/.env` 的 `BROKER_IP1` 决定，注册成客户端不可达的字符串即失败。
- **解决**：`BROKER_IP1=linqibins-mac-mini`（MagicDNS 短名，家内/离家自动解析）；回退用 FQDN `linqibins-mac-mini.taild06182.ts.net`。详见 `../docker/README.md` §故障排查。

### 4. JDK HttpClient 把 tailscale 100.x 流量误走代理

- **症状**：`http_proxy=127.0.0.1:7890`（Shadowrocket）开启时，Java 进程访问 tailscale 内网 `100.x` 失败（如服务注册）。
- **根因**：JDK 默认 ProxySelector 把所有出向 HTTP 走代理，包括应走 tailscale 直连的 `100.x`。
- **解决**：`env.zsh` 已设 `JAVA_TOOL_OPTIONS` 的 `*.nonProxyHosts=localhost|127.*|100.*`，对所有 Java 进程生效。

### 5. tailscale 路由被 Shadowrocket 拨断重连抢占

- **症状**：Shadowrocket 重连后，去 `100.x` 的流量漂移到代理 TUN，tailnet peer 不可达。
- **根因**：utun 重建的 link-change 空窗里，`100.64/10` 流量 fallback 被新 TUN 抢成 `/32` 克隆主机路由，比 tailscale 的 `/10` 更具体。
- **解决**：`scripts/tailscale-route-guard.sh`（root LaunchDaemon）每 5s 巡检，检测漂移即清克隆路由并 `tailscale down/up` 重协商。安装见 `scripts/install-tailscale-route-guard.sh`。

### 6. 容器主动外连 tailnet（如 xxl-job-admin 回调 MacBook 执行器）

- **症状**：容器内服务连 `100.x`（MacBook 的 tailscale IP）超时。如 §1 设对执行器 IP 后，xxl-job-admin 触发仍报 `Connect timed out, for url : http://100.x:6301/run`。
- **根因**：两机在同一 LAN 却被路由器 AP 隔离，唯一互通路径是 tailscale；但容器命名空间没有 macOS 宿主的 tailscale 接口，OrbStack 的 `network_mode: host` 也只是 Linux VM 网络，同样到不了 `100.x`。
- **解决**：`patra-net` 上跑**一个**共享 tailscale 网关容器（`docker-compose.tailnet.yaml`）：`tailscale-gw` 自身加入 tailnet + `ip_forward` + 对 `100.64.0.0/10` MASQUERADE；业务容器用路由注入边车（共享其网络命名空间）加 `100.64.0.0/10 via tailscale-gw`。任意容器由此可达任意 tailnet 机器的任意端口，新增容器复用同一网关、无需改它。
- **前置**：tailscale auth key 放 `docker/.env.secret`（gitignore，不进公开仓库，模板见 `.env.secret.example`）。镜像走 ghcr.io（docker.io 在国内常 502）。
- **验证**：`docker exec patra-tailscale-gw tailscale ip -4` 出现 `100.x`；业务容器命名空间内 `nc -zv <MacBook tailscale IP> <port>` 由 `timed out` 变 `succeeded`（或 `refused`=通了只是没人监听）。
- **现状（2026-09-23 实测）**：mini 改用官方 tailscale GUI 后，**未挂路由边车的普通应用容器**（`patra-gateway`、`patra-catalog`）也能直接访问 MacBook 的 `100.x` 与 mini 自身的 tailscale IP——容器内无 `100.64/10` 路由，流量由 OrbStack 交给 mini 的 macOS 宿主发出。本节的网关 + 边车方案是否仍必要待评估，评估前保持原样。
- **排查注意**：经 OrbStack 宿主转发时，目标端口无人监听表现为 curl `Empty reply`（exit 52）而非 `Connection refused`；判断连通性应在目标端临时起监听（如 `python3 -m http.server <port> --bind <tailscale IP>`）再探测。

### 7. MacBook 本地服务经 Nacos 调不到 mini 容器服务

- **症状**：本地起的服务（如 catalog）调 `lb://patra-registry` 超时；Nacos 里 mini 容器实例的地址是 `192.168.97.x`。
- **根因**：容器用 `patra-net` 内网 IP 注册 Nacos，该网段只存在于 mini 的 Docker 里，MacBook 路由表没有它（落进 `192.168.0.0/16 → Wi-Fi` 的泛匹配）。
- **解决（子网路由，2026-09-24 落地）**：`tailscale-gw` 以 `TS_ROUTES=192.168.97.0/24` 把 `patra-net` 网段发布到 tailnet，MacBook 装上更具体的 `192.168.97.0/24 → tailscale` 路由后即可直达容器 IP。容器侧不改注册地址，mini 上容器间调用仍走 Docker 内网，不依赖 tailscale 在线。三个前提缺一不可：
  1. 网段固定：`scripts/compose-all.sh` 以 `--subnet 192.168.97.0/24` 创建 `patra-net`（与 `TS_ROUTES` 必须一致）。
  2. 管理后台批准：login.tailscale.com → Machines → `patra-docker-gw` → Edit route settings 勾选该网段（一次性，随网关登录态保留；网关登录态丢失重新入网后需再批准）。
  3. MacBook 接受路由：tailscale 设置里「Use Tailscale subnets」开启（`tailscale debug prefs` 中 `RouteAll: true`）。
- **与 Shadowrocket 共存**：该路由为非 scoped（`netstat -rn` 标志 `UCS`，不含 `I`），`/24` 比 Shadowrocket 的 `128.0/1` 与 Wi-Fi 的 `192.168/16` 都具体，最长前缀匹配下走 tailscale，不受 §5 那类抢占影响。
- **已知限制**：若所连 Wi-Fi 本身就是 `192.168.97.0/24`，会与子网路由冲突；本地服务注册后，mini 的 gateway 会在容器实例与本地实例间轮询。
- **验证**：
  ```sh
  route -n get 192.168.97.10 | grep interface     # 应为 tailscale 的 utun
  curl -s -o /dev/null -w '%{http_code}\n' http://192.168.97.10:6300/actuator/health   # 容器 IP 以 Nacos 实际注册为准
  ```

---

## 通用排查命令

```sh
# 本机当前各网卡 inet（确认 tailscale IP、识别代理网卡）
ifconfig | awk '/^[a-z]/{iface=$1} /inet /{print iface, $2}'

# 本机 tailscale IP
tailscale ip -4

# 从 Mac mini 回探本机某端口是否可达（验证 admin→executor 这类回调链路）
ssh linqibin@linqibins-mac-mini 'nc -zv <本机 tailscale IP> <port>'

# 验证 MagicDNS 解析与 tailscale 状态
ping -c 1 linqibins-mac-mini
tailscale status | grep linqibins-mac-mini
```
