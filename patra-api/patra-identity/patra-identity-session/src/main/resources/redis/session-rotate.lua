-- 换令牌保留会话 ID：再认证、改密码用。
-- KEYS[1] 用户的会话索引  KEYS[2] 新会话的键
-- ARGV[1] 会话键前缀  ARGV[2] 会话 ID  ARGV[3] 新令牌哈希  ARGV[4] 当前时间（epoch 毫秒）
-- 返回：新会话的全部字段（HGETALL 的扁平数组）；会话不在或已过绝对期返回空表并清掉索引条目
local index = KEYS[1]
local newKey = KEYS[2]
local prefix = ARGV[1]
local sid = ARGV[2]
local now = tonumber(ARGV[4])

local hash = redis.call('HGET', index, sid)
if not hash then
  return {}
end
local oldKey = prefix .. hash
local fields = redis.call('HGETALL', oldKey)
if #fields == 0 then
  redis.call('HDEL', index, sid)
  return {}
end
local session = {}
for i = 1, #fields, 2 do
  session[fields[i]] = fields[i + 1]
end
local expiresAt = tonumber(session['expires_at'])
if now >= expiresAt then
  redis.call('DEL', oldKey)
  redis.call('HDEL', index, sid)
  return {}
end

session['auth_at'] = ARGV[4]
session['last_active_at'] = ARGV[4]
local flat = {}
for name, value in pairs(session) do
  flat[#flat + 1] = name
  flat[#flat + 1] = value
end
redis.call('HSET', newKey, unpack(flat))
redis.call('PEXPIRE', newKey, math.min(tonumber(session['idle_timeout_ms']), expiresAt - now))
redis.call('DEL', oldKey)
redis.call('HSET', index, sid, ARGV[3])
return flat
