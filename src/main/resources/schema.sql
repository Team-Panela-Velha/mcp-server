-- Views de leitura para o MCP. As views não alteram os dados de origem.

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
        jsonb_agg(DISTINCT jsonb_build_object('id', e.id, 'name', e.name, 'price', e.price))
            FILTER (WHERE e.id IS NOT NULL),
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
        jsonb_agg(DISTINCT jsonb_build_object('id', e.id, 'name', e.name, 'price', e.price))
            FILTER (WHERE e.id IS NOT NULL),
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
    COALESCE(SUM(oi.quantity * oi.unit_price_snapshot), 0)::numeric(12, 2) AS order_total,
    COUNT(oi.id) FILTER (WHERE oi.status = 'RECEIVED') AS received_lines,
    COUNT(oi.id) FILTER (WHERE oi.status = 'IN_PREPARATION') AS in_preparation_lines,
    COUNT(oi.id) FILTER (WHERE oi.status = 'READY') AS ready_lines,
    COUNT(oi.id) FILTER (WHERE oi.status = 'DELIVERED') AS delivered_lines
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


-- Dados de exemplo. Execute uma única vez em um banco que já contenha as tabelas da DDL.
-- Os códigos e nomes abaixo servem como chaves legíveis para as relações dos inserts.

INSERT INTO category (name, description) VALUES
    ('Pokes', 'Pokes preparados na hora'),
    ('Bebidas', 'Bebidas geladas');

INSERT INTO extra (name, price) VALUES
    ('Abacate extra', 4.00),
    ('Edamame extra', 3.50),
    ('Molho da casa', 1.50);

INSERT INTO product (name, description, price, available, category_id) VALUES
    ('Poke Salmão', 'Salmão, arroz, pepino e molho da casa', 32.90, TRUE,
        (SELECT id FROM category WHERE name = 'Pokes' ORDER BY id DESC LIMIT 1)),
    ('Poke Veggie', 'Tofu, edamame, pepino e cenoura', 27.90, TRUE,
        (SELECT id FROM category WHERE name = 'Pokes' ORDER BY id DESC LIMIT 1)),
    ('Chá gelado', 'Chá gelado da casa, 400 ml', 8.00, TRUE,
        (SELECT id FROM category WHERE name = 'Bebidas' ORDER BY id DESC LIMIT 1));

INSERT INTO product_extra (product_id, extra_id)
SELECT p.id, e.id
FROM product p
CROSS JOIN extra e
WHERE (p.name = 'Poke Salmão' AND e.name IN ('Abacate extra', 'Molho da casa'))
   OR (p.name = 'Poke Veggie' AND e.name IN ('Abacate extra', 'Edamame extra', 'Molho da casa'));

INSERT INTO restaurant_table (code) VALUES ('MESA-01'), ('MESA-02');

INSERT INTO tab (table_id, closed, total_value)
SELECT rt.id, FALSE, 0.00
FROM restaurant_table rt
WHERE rt.code = 'MESA-01';

INSERT INTO orders (tab_id)
SELECT t.id
FROM tab t
JOIN restaurant_table rt ON rt.id = t.table_id
WHERE rt.code = 'MESA-01' AND t.closed = FALSE;

INSERT INTO order_item (order_id, product_id, quantity, unit_price_snapshot, status, observation)
SELECT o.id, p.id, 2, 32.90, 'IN_PREPARATION', 'Pouco molho'
FROM orders o
JOIN tab t ON t.id = o.tab_id
JOIN restaurant_table rt ON rt.id = t.table_id
CROSS JOIN product p
WHERE rt.code = 'MESA-01' AND p.name = 'Poke Salmão';

INSERT INTO order_item (order_id, product_id, quantity, unit_price_snapshot, status, observation)
SELECT o.id, p.id, 1, 8.00, 'RECEIVED', NULL
FROM orders o
JOIN tab t ON t.id = o.tab_id
JOIN restaurant_table rt ON rt.id = t.table_id
CROSS JOIN product p
WHERE rt.code = 'MESA-01' AND p.name = 'Chá gelado';

INSERT INTO item_extra (order_item_id, extra_id)
SELECT oi.id, e.id
FROM order_item oi
JOIN orders o ON o.id = oi.order_id
JOIN tab t ON t.id = o.tab_id
JOIN restaurant_table rt ON rt.id = t.table_id
JOIN product p ON p.id = oi.product_id
CROSS JOIN extra e
WHERE rt.code = 'MESA-01'
  AND p.name = 'Poke Salmão'
  AND e.name = 'Abacate extra';

UPDATE tab t
SET total_value = (
    SELECT COALESCE(SUM(oi.quantity * oi.unit_price_snapshot), 0)
    FROM orders o
    JOIN order_item oi ON oi.order_id = o.id
    WHERE o.tab_id = t.id
)
FROM restaurant_table rt
WHERE rt.id = t.table_id AND rt.code = 'MESA-01' AND t.closed = FALSE;
