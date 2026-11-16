-- 库表规范（v0.21）五条推断的验证测试用表。
-- 推断 1、3 只涉及 PostgreSQL，各自在测试里建表和删表；这里只放需要 JPA 实体映射的表。

-- 推断 2：审计时间做只读映射时，批量插入是否还生效
CREATE TABLE jpa_it_readonly_audit
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    content    TEXT        NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 推断 2 的对照：审计时间用「数据库生成、写完读回」的映射
CREATE TABLE jpa_it_generated_audit
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    content    TEXT        NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 推断 4：映射了 @SoftDelete 的实体，JPQL 批量删除会变成什么
CREATE TABLE jpa_it_soft_deleted
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    content    TEXT        NULL,
    deleted_at TIMESTAMPTZ NULL
);

-- 推断 4 的对照：deleted_at 是普通字段
CREATE TABLE jpa_it_explicit_deleted
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    content    TEXT        NULL,
    deleted_at TIMESTAMPTZ NULL
);

-- 推断 5：预先分配主键、没有 version 的实体，saveAll 走合并还是直接插入
CREATE TABLE jpa_it_preassigned
(
    id      BIGINT NOT NULL PRIMARY KEY,
    content TEXT   NULL
);

-- 推断 5 的对照：继承值对象基类（实现 Persistable）的实体
CREATE TABLE jpa_it_value_object
(
    id      BIGINT NOT NULL PRIMARY KEY,
    content TEXT   NULL
);
