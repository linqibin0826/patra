-- 建一条会话。
-- KEYS[1] 用户的会话索引（HASH：session_id → 令牌哈希）  KEYS[2] 新会话的键（HASH）
-- ARGV[1] 会话键前缀（拼其他会话的键用）  ARGV[2] 新会话的令牌哈希  ARGV[3] 会话 ID
-- ARGV[4] 用户 ID  ARGV[5] 账号类型  ARGV[6] 客户端类型  ARGV[7] 设备标识（空串表示没有）
-- ARGV[8] 当前时间（epoch 毫秒）  ARGV[9] 不活跃过期毫秒  ARGV[10] 绝对过期时间（epoch 毫秒）
-- ARGV[11] 每用户会话上限
-- 返回：被挤掉的会话 ID 列表
local index = KEYS[1]
local sessionKey = KEYS[2]
local now = tonumber(ARGV[8])
local idle = tonumber(ARGV[9])
local expiresAt = tonumber(ARGV[10])
local evicted = {}

-- 写新会话
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

-- 写索引，TTL 不小于新会话的绝对有效期
redis.call('HSET', index, ARGV[3], ARGV[2])
local absoluteTtl = expiresAt - now
if redis.call('PTTL', index) < absoluteTtl then
  redis.call('PEXPIRE', index, absoluteTtl)
end
return evicted
