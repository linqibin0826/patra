-- 按会话 ID 读会话，不续期：再认证守卫读 auth_at 用。
-- KEYS[1] 用户的会话索引
-- ARGV[1] 会话键前缀  ARGV[2] 会话 ID  ARGV[3] 当前时间（epoch 毫秒）
-- 返回：会话的全部字段（HGETALL 的扁平数组）；会话不在或已过绝对期返回空表并清掉索引条目
local index = KEYS[1]
local sid = ARGV[2]
local hash = redis.call('HGET', index, sid)
if not hash then
  return {}
end
local key = ARGV[1] .. hash
local fields = redis.call('HGETALL', key)
if #fields == 0 then
  redis.call('HDEL', index, sid)
  return {}
end
for i = 1, #fields, 2 do
  if fields[i] == 'expires_at' and tonumber(ARGV[3]) >= tonumber(fields[i + 1]) then
    redis.call('DEL', key)
    redis.call('HDEL', index, sid)
    return {}
  end
end
return fields
