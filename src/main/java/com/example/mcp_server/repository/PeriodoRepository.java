package com.example.mcp_server.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Numeros de um periodo, com as mesmas regras do GET /dashboard da API: pedidos contam por orders.created_at,
 * receita pelas comandas fechadas em tab.closed_at, dias no fuso de Sao Paulo.
 */
@Repository
public class PeriodoRepository {
	public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	public record Ranked(String nome, long quantidade, BigDecimal total) {
	}

	public record Dia(LocalDate dia, long pedidos, BigDecimal receita) {
	}

	public record Dados(Map<String, Long> pedidosPorStatus, BigDecimal receita, long comandasFechadas,
			BigDecimal adicionais, long comandasAbertas, BigDecimal valorEmAberto,
			List<Ranked> produtos, List<Ranked> categorias, List<Dia> porDia) {
	}

	private final JdbcTemplate jdbc;

	public PeriodoRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Dados buscar(LocalDate from, LocalDate to) {
		Timestamp start = Timestamp.from(from.atStartOfDay(ZONE).toInstant());
		Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant());

		Map<String, Long> porStatus = new LinkedHashMap<>();
		jdbc.query("select status, count(*) as amount from orders where created_at >= ? and created_at < ? "
				+ "group by status order by status",
				rs -> { porStatus.put(rs.getString("status"), rs.getLong("amount")); }, start, end);

		Map<String, Object> receita = jdbc.queryForMap("""
				select coalesce(sum(total_value), 0) as total, count(*) as closed_tabs
				from tab where closed = true and closed_at >= ? and closed_at < ?
				""", start, end);
		Map<String, Object> abertas = jdbc.queryForMap("""
				select count(*) as open_tabs, coalesce(sum(total_value), 0) as open_value
				from tab where closed = false
				""");
		BigDecimal adicionais = jdbc.queryForObject("""
				select coalesce(sum(coalesce(ie.unit_price_snapshot, e.price) * oi.quantity), 0)
				from item_extra ie
				join order_item oi on oi.id = ie.order_item_id
				join orders o on o.id = oi.order_id
				join tab t on t.id = o.tab_id
				join extra e on e.id = ie.extra_id
				where t.closed = true and t.closed_at >= ? and t.closed_at < ?
				  and o.status <> 'CANCELLED' and oi.status <> 'CANCELLED'
				""", BigDecimal.class, start, end);

		List<Ranked> produtos = ranking("p.id, p.name", "", start, end);
		List<Ranked> categorias = ranking("c.id, c.name", "join category c on c.id = p.category_id", start, end);

		Map<LocalDate, Long> pedidosDia = new HashMap<>();
		jdbc.query("""
				select date(timezone(?, created_at)) as day, count(*) as amount
				from orders where created_at >= ? and created_at < ? group by day
				""", rs -> { pedidosDia.put(rs.getDate("day").toLocalDate(), rs.getLong("amount")); },
				ZONE.getId(), start, end);
		Map<LocalDate, BigDecimal> receitaDia = new HashMap<>();
		jdbc.query("""
				select date(timezone(?, closed_at)) as day, sum(total_value) as amount
				from tab where closed = true and closed_at >= ? and closed_at < ? group by day
				""", rs -> { receitaDia.put(rs.getDate("day").toLocalDate(), rs.getBigDecimal("amount")); },
				ZONE.getId(), start, end);
		List<Dia> porDia = new ArrayList<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			porDia.add(new Dia(d, pedidosDia.getOrDefault(d, 0L), receitaDia.getOrDefault(d, BigDecimal.ZERO)));
		}

		return new Dados(porStatus, (BigDecimal) receita.get("total"), ((Number) receita.get("closed_tabs")).longValue(),
				adicionais, ((Number) abertas.get("open_tabs")).longValue(), (BigDecimal) abertas.get("open_value"),
				produtos, categorias, porDia);
	}

	private List<Ranked> ranking(String grupo, String join, Timestamp start, Timestamp end) {
		String nome = grupo.substring(grupo.indexOf(',') + 1).trim();
		return jdbc.query("select " + nome + " as nome, sum(oi.quantity) as quantity, "
				+ "sum(oi.unit_price_snapshot * oi.quantity) as total "
				+ "from order_item oi join orders o on o.id = oi.order_id join product p on p.id = oi.product_id "
				+ join + " where o.created_at >= ? and o.created_at < ? "
				+ "and o.status <> 'CANCELLED' and oi.status <> 'CANCELLED' "
				+ "group by " + grupo + " order by quantity desc, " + nome + " asc limit 5",
				(rs, row) -> new Ranked(rs.getString("nome"), rs.getLong("quantity"), rs.getBigDecimal("total")),
				start, end);
	}
}
