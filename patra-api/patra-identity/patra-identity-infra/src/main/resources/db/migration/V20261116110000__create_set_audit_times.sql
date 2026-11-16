-- 审计时间触发器函数，每张表建表后挂上它（库表规范第 4 节）
CREATE FUNCTION set_audit_times() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.created_at := now();
        NEW.updated_at := NEW.created_at;
        RETURN NEW;
    END IF;
    NEW.created_at := OLD.created_at;
    NEW.updated_at := OLD.updated_at;
    IF NEW IS DISTINCT FROM OLD THEN
        NEW.updated_at := now();
    END IF;
    RETURN NEW;
END
$$;
COMMENT ON FUNCTION set_audit_times() IS '审计时间由数据库维护：插入取当前时间；更新时行内容变了才刷新 updated_at；应用写的值一律忽略';
