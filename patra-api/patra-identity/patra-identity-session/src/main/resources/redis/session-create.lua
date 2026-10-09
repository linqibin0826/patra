-- 建一条会话。
-- KEYS[1] 用户的会话索引（HASH：session_id → 令牌哈希）  KEYS[2] 新会话的键（HASH）
-- ARGV[1] 会话键前缀（拼其他会话的键用）  ARGV[2] 新会话的令牌哈希  ARGV[3] 会话 ID
-- ARGV[4] 用户 ID  ARGV[5] 账号类型  ARGV[6] 客户端类型  ARGV[7] 设备标识（空串表示没有）
-- ARGV[8] 当前时间（epoch 毫秒）  ARGV[9] 不活跃过期毫秒  ARGV[10] 绝对过期时间（epoch 毫秒）
-- ARGV[11] 每用户会话上限
-- 返回：被挤掉的会话 ID 列表
local index = KEYS[1]
local sessionKey = KEYS[2]
local prefix = ARGV[1]
local now = tonumber(ARGV[8])
local idle = tonumber(ARGV[9])
local expiresAt = tonumber(ARGV[10])
local limit = tonumber(ARGV[11])

-- 1. 清掉指向不存在会话的悬空条目，留下还活着的
local entries = redis.call('HGETALL', index)
local live = {}
local hashOf = {}
for i = 1, #entries, 2 do
  local sid = entries[i]
  local hash = entries[i + 1]
  if redis.call('EXISTS', prefix .. hash) == 1 then
    live[#live + 1] = sid
    hashOf[sid] = hash
  else
    redis.call('HDEL', index, sid)
  end
end

-- 2. 到上限就按会话 ID 从小到大挤掉多出来的。
--    雪花 ID 按时间递增，最小的最老；先比位数再比字典序，转成 double 会丢精度。
local function older(a, b)
  if #a ~= #b then
    return #a < #b
  end
  return a < b
end
table.sort(live, older)
local evicted = {}
local excess = #live - limit + 1
for i = 1, excess do
  local sid = live[i]
  redis.call('DEL', prefix .. hashOf[sid])
  redis.call('HDEL', index, sid)
  evicted[#evicted + 1] = sid
end

-- 3. 写新会话
local fields = {
  'user_id', ARGV[4], 'session_id', ARGV[3], 'account_type', ARGV[5], 'client_type', ARGV[6],
  'created_at', ARGV[8], 'last_active_at', ARGV[8], 'expires_at', ARGV[10], 'idle_timeout_ms', ARGV[9]
}
if ARGV[7] ~= '' then
  fields[#fields + 1] = 'device_id'
  fields[#fields + 1] = ARGV[7]
end
redis.call('HSET', sessionKey, unpack(fields))
redis.call('PEXPIRE', sessionKey, math.min(idle, expiresAt - now))

-- 4. 写索引，TTL 不小于新会话的绝对有效期
redis.call('HSET', index, ARGV[3], ARGV[2])
local absoluteTtl = expiresAt - now
if redis.call('PTTL', index) < absoluteTtl then
  redis.call('PEXPIRE', index, absoluteTtl)
end
return evicted
