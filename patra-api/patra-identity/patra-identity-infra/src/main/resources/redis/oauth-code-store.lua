-- 写一个第三方首次登录的验证码，覆盖旧的：旧码作废，错误次数重新算。
-- KEYS[1] 验证码的键（HASH）
-- ARGV[1] 用途  ARGV[2] 用户名，绑定用途为空串  ARGV[3] 邮箱  ARGV[4] 验证码哈希  ARGV[5] 过期时间（epoch 毫秒）
-- ARGV[6] 最早可以重发的时间（epoch 毫秒）  ARGV[7] 有效期毫秒
redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1], 'purpose', ARGV[1], 'username', ARGV[2], 'email', ARGV[3], 'code_hash', ARGV[4],
  'attempts', '0', 'expires_at', ARGV[5], 'next_resend_at', ARGV[6])
redis.call('PEXPIRE', KEYS[1], ARGV[7])
return 1
