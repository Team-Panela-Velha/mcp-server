package com.example.mcp_server.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.mcp_server.repository.PeriodoRepository;
import com.example.mcp_server.repository.PeriodoRepository.Dados;

/**
 * Vendas e pedidos por periodo, com os mesmos numeros do /dashboard da API.
 * Quem pode ver receita (so ADMIN e MANAGER) e decidido na API: o ChatService so oferece esta ferramenta
 * ao modelo para esses perfis.
 */
@Component
public class PeriodoTools {
	private static final String AJUDA = "Use datas no formato yyyy-MM-dd, ou as palavras HOJE e ONTEM.";

	private final PeriodoRepository periodos;
	private final Clock clock;

	@Autowired
	public PeriodoTools(PeriodoRepository periodos) {
		this(periodos, Clock.system(PeriodoRepository.ZONE));
	}

	PeriodoTools(PeriodoRepository periodos, Clock clock) {
		this.periodos = periodos;
		this.clock = clock;
	}

	// nome em snake_case como as demais: em camelCase o modelo misturava os estilos e inventava nomes
	@McpTool(name = "resumo_por_periodo", description = "Vendas e pedidos de um periodo (hoje, ontem, semana, mes ou datas). "
			+ "Pedidos contam pelo dia em que foram criados; receita e o total das comandas fechadas no periodo. "
			+ "totalVendido dos produtos e a soma de todas as unidades, nao o preco de uma unidade. "
			+ "Datas no horario de Sao Paulo. Use sempre que a pergunta citar um periodo. "
			+ "Aceita datas yyyy-MM-dd ou as palavras HOJE, ONTEM, SEMANA (ultimos 7 dias) e MES (do dia 1 ate hoje).")
	public Object resumoPorPeriodo(
			@McpToolParam(description = "Data inicial: yyyy-MM-dd, HOJE, ONTEM, SEMANA ou MES", required = true) String de,
			@McpToolParam(description = "Data final: yyyy-MM-dd, HOJE ou ONTEM. Omita para um dia so, SEMANA ou MES.", required = false) String ate) {
		return ToolExecutionLogger.logResult("resumo_por_periodo", Map.of("de", String.valueOf(de), "ate", String.valueOf(ate)),
				() -> calcular(de, ate));
	}

	private Object calcular(String de, String ate) {
		LocalDate today = LocalDate.now(clock.withZone(PeriodoRepository.ZONE));
		String key = de == null ? "" : de.trim();
		LocalDate from;
		LocalDate to;
		if (key.equalsIgnoreCase("SEMANA")) {
			from = today.minusDays(6);
			to = today;
		} else if (key.equalsIgnoreCase("MES")) {
			from = today.withDayOfMonth(1);
			to = today;
		} else {
			try {
				from = parse(de, today);
				to = ate == null || ate.isBlank() ? from : parse(ate, today);
			} catch (DateTimeParseException e) {
				return erro("Data invalida. " + AJUDA);
			}
		}
		if (from.isAfter(to)) {
			// o modelo pequeno as vezes manda de=HOJE, ate=ONTEM: inverte em vez de falhar
			LocalDate swap = from;
			from = to;
			to = swap;
		}
		if (from.plusDays(366).isBefore(to)) {
			return erro("O periodo deve ter de 1 a 367 dias. Para um dia so use de=HOJE e omita ate.");
		}
		return resumo(from, to, periodos.buscar(from, to));
	}

	/**
	 * Resultado enviado ao modelo. Os nomes dizem exatamente o que cada numero e: com o JSON cru do dashboard
	 * ("baseRevenue") o modelo lia o total de um produto como preco unitario ("R$ 77,80 cada").
	 */
	public record ResumoPeriodo(String periodo, long pedidosCriadosNoPeriodo, Map<String, Long> pedidosPorStatus,
			String receitaTotal, long comandasFechadasNoPeriodo, String ticketMedioPorComanda,
			String receitaDeAdicionais, long comandasAbertasAgora, String valorEmAberto,
			List<Item> produtosMaisVendidos, List<Item> categoriasMaisVendidas, List<Dia> porDia) {
		/** totalVendido e a soma de TODAS as unidades vendidas, nao o preco de uma unidade. */
		public record Item(String nome, long unidadesVendidas, String totalVendido) {
		}

		public record Dia(String dia, long pedidos, String receita) {
		}
	}

	/** Valores ja em reais no padrao brasileiro: o modelo pequeno errava a formatacao ("R$ 42,00,00"). */
	private static String reais(BigDecimal value) {
		return String.format(Locale.of("pt", "BR"), "R$ %,.2f", value);
	}

	private static ResumoPeriodo resumo(LocalDate from, LocalDate to, Dados d) {
		boolean umDia = from.equals(to);
		long total = d.pedidosPorStatus().values().stream().mapToLong(Long::longValue).sum();
		BigDecimal ticket = d.comandasFechadas() == 0 ? BigDecimal.ZERO
				: d.receita().divide(BigDecimal.valueOf(d.comandasFechadas()), 2, RoundingMode.HALF_UP);
		List<ResumoPeriodo.Dia> porDia = umDia ? List.of()
				: d.porDia().stream().map(x -> new ResumoPeriodo.Dia(x.dia().toString(), x.pedidos(), reais(x.receita()))).toList();
		return new ResumoPeriodo(umDia ? from.toString() : from + " a " + to, total, d.pedidosPorStatus(),
				reais(d.receita()), d.comandasFechadas(), reais(ticket), reais(d.adicionais()),
				d.comandasAbertas(), reais(d.valorEmAberto()), itens(d.produtos()), itens(d.categorias()), porDia);
	}

	private static List<ResumoPeriodo.Item> itens(List<PeriodoRepository.Ranked> items) {
		return items.stream().map(i -> new ResumoPeriodo.Item(i.nome(), i.quantidade(), reais(i.total()))).toList();
	}

	private static LocalDate parse(String value, LocalDate today) {
		String v = value == null ? "" : value.trim();
		if (v.isEmpty() || v.equalsIgnoreCase("HOJE")) return today;
		if (v.equalsIgnoreCase("ONTEM")) return today.minusDays(1);
		return LocalDate.parse(v);
	}

	private static Map<String, String> erro(String message) {
		return Map.of("erro", message);
	}
}
