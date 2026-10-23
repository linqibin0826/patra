-- 按用户列出还活着的会话，不续期。顺手清掉悬空条目、缺 expires_at 的残缺会话和已过绝对期的会话。
-- KEYS[1] 用户的会话索引（HASH：session_id → 令牌哈希）
-- ARGV[1] 会话键前缀  ARGV[2] 当前时间（epoch 毫秒）
-- 返回：每条会话一个 HGETALL 扁平数组；没有会话时是空表（Lettuce 把 false 解成 [null]）
local index = KEYS[1]
local prefix = ARGV[1]
local now = tonumber(ARGV[2])
local entries = redis.call('HGETALL', index)
local result = {}
for i = 1, #entries, 2 do
  local sid = entries[i]
  local key = prefix .. entries[i + 1]
  local fields = redis.call('HGETALL', key)
  if #fields == 0 then
    redis.call('HDEL', index, sid)
  else
    local expiresAt
    for j = 1, #fields, 2 do
      if fields[j] == 'expires_at' then
        expiresAt = tonumber(fields[j + 1])
      end
    end
    if expiresAt == nil or now >= expiresAt then
      redis.call('DEL', key)
      redis.call('HDEL', index, sid)
    else
      result[#result + 1] = fields
    end
  end
end
return result
