-- 按令牌哈希读会话，距上次写入满一个续期间隔才续期。
-- KEYS[1] 会话键
-- ARGV[1] 当前时间（epoch 毫秒）  ARGV[2] 续期间隔毫秒
-- 返回：会话的全部字段（HGETALL 的扁平数组）；没有会话或已过绝对期返回空表（Lettuce 把 false 解成 [null]）
local fields = redis.call('HGETALL', KEYS[1])
if #fields == 0 then
  return {}
end
local session = {}
for i = 1, #fields, 2 do
  session[fields[i]] = fields[i + 1]
end
local now = tonumber(ARGV[1])
local expiresAt = tonumber(session['expires_at'])
if now >= expiresAt then
  redis.call('DEL', KEYS[1])
  return {}
end
if now - tonumber(session['last_active_at']) >= tonumber(ARGV[2]) then
  redis.call('HSET', KEYS[1], 'last_active_at', ARGV[1])
  redis.call('PEXPIRE', KEYS[1], math.min(tonumber(session['idle_timeout_ms']), expiresAt - now))
  for i = 1, #fields, 2 do
    if fields[i] == 'last_active_at' then
      fields[i + 1] = ARGV[1]
    end
  end
end
return fields
