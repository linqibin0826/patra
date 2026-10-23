-- 删掉一个用户除指定一条之外的全部会话。
-- KEYS[1] 用户的会话索引
-- ARGV[1] 会话键前缀  ARGV[2] 要保留的会话 ID
-- 返回：会话键真正删掉了的会话 ID 列表
local entries = redis.call('HGETALL', KEYS[1])
local deleted = {}
for i = 1, #entries, 2 do
  if entries[i] ~= ARGV[2] then
    if redis.call('DEL', ARGV[1] .. entries[i + 1]) == 1 then
      deleted[#deleted + 1] = entries[i]
    end
    redis.call('HDEL', KEYS[1], entries[i])
  end
end
return deleted
