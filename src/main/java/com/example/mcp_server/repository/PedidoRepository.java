package com.example.mcp_server.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PedidoRepository {
	private final JdbcTemplate jdbc;

	public PedidoRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Map<String, Object>> listarPorStatus(String status) {
		return jdbc.queryForList("""
				SELECT order_item_id, order_id, tab_id, table_code, product_name, quantity,
			       item_total, status, observation
			FROM v_order_items WHERE status = ?
			ORDER BY order_id, order_item_id LIMIT 100
			""", status);
	}

	public List<Map<String, Object>> buscarPorId(long pedidoId) {
		return jdbc.queryForList("""
				SELECT order_item_id, order_id, tab_id, table_code, product_name, quantity,
			       unit_price_snapshot, item_total, status, observation, extras
			FROM v_order_items WHERE order_id = ?
			ORDER BY order_item_id
			""", pedidoId);
	}
}
