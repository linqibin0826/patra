-- 集成测试的业务表：发布方写订单，消费方写处理效果。
CREATE TABLE events_it_order
(
    order_no TEXT NOT NULL PRIMARY KEY
);

CREATE TABLE events_it_effect
(
    event_id UUID NOT NULL PRIMARY KEY,
    order_no TEXT NOT NULL
);
