package com.example.mcp_server.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ComandaRepository {
	private final JdbcTemplate jdbc;

	public ComandaRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Map<String, Object>> listarAbertas() {
		return jdbc.queryForList("""
				SELECT tab_id, table_code, total_value, order_count, item_lines, item_quantity
				FROM v_tabs WHERE closed = FALSE
				ORDER BY table_code, tab_id LIMIT 100
				""");
	}

	public List<Map<String, Object>> buscarPorId(long comandaId) {
		return jdbc.queryForList("""
				SELECT tab_id, table_code, closed, total_value, order_count, item_lines, item_quantity
				FROM v_tabs WHERE tab_id = ?
				""", comandaId);
	}

	public List<Map<String, Object>> listarItens(long comandaId) {
		return jdbc.queryForList("""
				SELECT order_item_id, order_id, product_name, quantity, unit_price_snapshot,
			       item_total, status, observation, extras
			FROM v_order_items WHERE tab_id = ?
			ORDER BY order_id, order_item_id LIMIT 200
			""", comandaId);
	}

	public Map<String, Object> resumoOperacional() {
		return jdbc.queryForMap("""
				SELECT
				    (SELECT COUNT(*) FROM v_orders) AS total_pedidos,
				    (SELECT COUNT(*) FROM v_tabs WHERE closed = FALSE) AS comandas_abertas,
				    (SELECT COUNT(*) FROM v_tabs WHERE closed = TRUE) AS comandas_fechadas,
				    (SELECT COALESCE(SUM(total_value), 0) FROM v_tabs WHERE closed = TRUE) AS receita_comandas_fechadas,
				    (SELECT COALESCE(SUM(item_quantity), 0) FROM v_orders) AS quantidade_itens,
				    (SELECT COALESCE(SUM(received_lines), 0) FROM v_orders) AS itens_recebidos,
				    (SELECT COALESCE(SUM(in_preparation_lines), 0) FROM v_orders) AS itens_em_preparacao,
				    (SELECT COALESCE(SUM(ready_lines), 0) FROM v_orders) AS itens_prontos,
				    (SELECT COALESCE(SUM(delivered_lines), 0) FROM v_orders) AS itens_entregues
				""");
	}
}
