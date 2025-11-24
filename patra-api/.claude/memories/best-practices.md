# 开发最佳实践

## DO 原则

✅ Domain 层保持纯 Java，无框架依赖
✅ Application 层管理事务边界（@Transactional）
✅ 使用 MapStruct 进行对象转换
✅ 通过 Port 和 Repository 接口定义依赖

## DON'T 反模式

❌ 在 Domain 层使用 Spring 注解
❌ 跨层直接调用（如 Controller 直接调用 Repository）
❌ 在 Controller 层处理业务逻辑
❌ 硬编码配置值