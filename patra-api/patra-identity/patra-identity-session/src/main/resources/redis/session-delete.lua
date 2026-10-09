-- 按会话 ID 删一条会话。
-- KEYS[1] 用户的会话索引
-- ARGV[1] 会话键前缀  ARGV[2] 会话 ID
-- 返回：会话键真正删掉了返回 1，否则 0
local hash = redis.call('HGET', KEYS[1], ARGV[2])
if not hash then
  return 0
end
local deleted = redis.call('DEL', ARGV[1] .. hash)
redis.call('HDEL', KEYS[1], ARGV[2])
return deleted
