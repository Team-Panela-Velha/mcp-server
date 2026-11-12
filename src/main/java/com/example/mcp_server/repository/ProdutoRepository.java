package com.example.mcp_server.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProdutoRepository {
	private final JdbcTemplate jdbc;

	public ProdutoRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Map<String, Object>> listarDisponiveis() {
		return jdbc.queryForList("""
				SELECT product_id, product_name, description, price, category_name, available_extras
				FROM v_menu_products WHERE available = TRUE
				ORDER BY category_name, product_name LIMIT 100
				""");
	}

	public List<Map<String, Object>> buscarPorNome(String nome) {
		return jdbc.queryForList("""
				SELECT product_id, product_name, description, price, available, category_name, available_extras
				FROM v_menu_products WHERE product_name ILIKE ?
				ORDER BY product_name LIMIT 50
				""", "%" + nome.trim() + "%");
	}
}
