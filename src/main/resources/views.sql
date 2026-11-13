
-- ==========================================
-- VIEWS DE LEITURA PARA O MCP SERVER
-- ==========================================

CREATE OR REPLACE VIEW v_menu_products AS
SELECT
    p.id AS product_id,
    p.name AS product_name,
    p.description,
    p.price,
    p.available,
    c.id AS category_id,
    c.name AS category_name,
    c.description AS category_description,
    COALESCE(
        jsonb_agg(
            DISTINCT jsonb_build_object(
                'id', e.id,
                'name', e.name,
                'price', e.price
            )
        ) FILTER (WHERE e.id IS NOT NULL),
        '[]'::jsonb
    ) AS available_extras
FROM product p
LEFT JOIN category c ON c.id = p.category_id
LEFT JOIN product_extra pe ON pe.product_id = p.id
LEFT JOIN extra e ON e.id = pe.extra_id
GROUP BY p.id, c.id;


CREATE OR REPLACE VIEW v_order_items AS
SELECT
    oi.id AS order_item_id,
    oi.order_id,
    o.tab_id,
    rt.code AS table_code,
    oi.product_id,
    p.name AS product_name,
    oi.quantity,
    oi.unit_price_snapshot,
    oi.quantity * oi.unit_price_snapshot AS item_total,
    oi.status,
    oi.observation,
    COALESCE(
        jsonb_agg(
            DISTINCT jsonb_build_object(
                'id', e.id,
                'name', e.name,
                'price', e.price
            )
        ) FILTER (WHERE e.id IS NOT NULL),
        '[]'::jsonb
    ) AS extras
FROM order_item oi
JOIN orders o ON o.id = oi.order_id
JOIN tab t ON t.id = o.tab_id
LEFT JOIN restaurant_table rt ON rt.id = t.table_id
LEFT JOIN product p ON p.id = oi.product_id
LEFT JOIN item_extra ie ON ie.order_item_id = oi.id
LEFT JOIN extra e ON e.id = ie.extra_id
GROUP BY oi.id, o.id, t.id, rt.id, p.id;


CREATE OR REPLACE VIEW v_orders AS
SELECT
    o.id AS order_id,
    o.tab_id,
    rt.code AS table_code,
    t.closed AS tab_closed,
    COUNT(oi.id) AS item_lines,
    COALESCE(SUM(oi.quantity), 0) AS item_quantity,
    COALESCE(
        SUM(oi.quantity * oi.unit_price_snapshot),
        0
    )::numeric(12, 2) AS order_total,
    COUNT(oi.id) FILTER (
        WHERE oi.status = 'RECEIVED'
    ) AS received_lines,
    COUNT(oi.id) FILTER (
        WHERE oi.status = 'IN_PREPARATION'
    ) AS in_preparation_lines,
    COUNT(oi.id) FILTER (
        WHERE oi.status = 'READY'
    ) AS ready_lines,
    COUNT(oi.id) FILTER (
        WHERE oi.status = 'DELIVERED'
    ) AS delivered_lines
FROM orders o
JOIN tab t ON t.id = o.tab_id
LEFT JOIN restaurant_table rt ON rt.id = t.table_id
LEFT JOIN order_item oi ON oi.order_id = o.id
GROUP BY o.id, t.id, rt.id;


CREATE OR REPLACE VIEW v_tabs AS
SELECT
    t.id AS tab_id,
    rt.id AS table_id,
    rt.code AS table_code,
    t.closed,
    t.total_value,
    COUNT(DISTINCT o.id) AS order_count,
    COUNT(oi.id) AS item_lines,
    COALESCE(SUM(oi.quantity), 0) AS item_quantity
FROM tab t
LEFT JOIN restaurant_table rt ON rt.id = t.table_id
LEFT JOIN orders o ON o.tab_id = t.id
LEFT JOIN order_item oi ON oi.order_id = o.id
GROUP BY t.id, rt.id;
