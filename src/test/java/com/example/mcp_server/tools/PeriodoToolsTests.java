package com.example.mcp_server.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.mcp_server.repository.PeriodoRepository;
import com.example.mcp_server.repository.PeriodoRepository.Dados;
import com.example.mcp_server.repository.PeriodoRepository.Dia;
import com.example.mcp_server.repository.PeriodoRepository.Ranked;
import com.example.mcp_server.tools.PeriodoTools.ResumoPeriodo;

class PeriodoToolsTests {
	// 2040-01-02T01:30Z = 2040-01-01 22:30 em Sao Paulo: "hoje" ainda e dia 1, nao dia 2 (UTC)
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2040-01-02T01:30:00Z"), ZoneOffset.UTC);

	private PeriodoRepository repo;
	private PeriodoTools tools;

	@BeforeEach
	void setUp() {
		repo = mock(PeriodoRepository.class);
		when(repo.buscar(any(), any())).thenAnswer(inv -> sample(inv.getArgument(0), inv.getArgument(1), new BigDecimal("42.00")));
		tools = new PeriodoTools(repo, CLOCK);
	}

	private static Dados sample(LocalDate from, LocalDate to, BigDecimal receita) {
		List<Dia> daily = new ArrayList<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) daily.add(new Dia(d, 4, new BigDecimal("42.00")));
		Map<String, Long> status = new LinkedHashMap<>();
		status.put("RECEIVED", 1L);
		status.put("DELIVERED", 3L);
		return new Dados(status, receita, 1, new BigDecimal("3.00"), 2, new BigDecimal("140.30"),
				List.of(new Ranked("Poke de Salmao", 2, new BigDecimal("77.80"))),
				List.of(new Ranked("Pokes", 4, new BigDecimal("154.30"))), daily);
	}

	@Test
	void hojeEOntemUsamODiaDeSaoPauloENaoODiaUtc() {
		assertThat(tools.resumoPorPeriodo("HOJE", "HOJE")).isInstanceOf(ResumoPeriodo.class);
		verify(repo).buscar(LocalDate.parse("2040-01-01"), LocalDate.parse("2040-01-01"));

		tools.resumoPorPeriodo("ontem", "ontem");
		verify(repo).buscar(LocalDate.parse("2039-12-31"), LocalDate.parse("2039-12-31"));
	}

	@Test
	void datasExplicitasEPeriodoSemDataFinal() {
		tools.resumoPorPeriodo("2040-01-01", "2040-01-07");
		verify(repo).buscar(LocalDate.parse("2040-01-01"), LocalDate.parse("2040-01-07"));

		tools.resumoPorPeriodo("2040-01-05", null);
		verify(repo).buscar(LocalDate.parse("2040-01-05"), LocalDate.parse("2040-01-05"));
	}

	@Test
	void semDatasUsaHoje() {
		tools.resumoPorPeriodo(null, null);
		verify(repo).buscar(LocalDate.parse("2040-01-01"), LocalDate.parse("2040-01-01"));
	}

	@Test
	void dataInvalidaVoltaErroLegivelParaOModelo() {
		Object result = tools.resumoPorPeriodo("semana passada", "hoje");

		assertThat(((Map<?, ?>) result).get("erro").toString()).contains("yyyy-MM-dd");
		verifyNoInteractions(repo);
	}

	@Test
	void periodoLongoDemaisVoltaErroComDica() {
		Object result = tools.resumoPorPeriodo("2030-01-01", "2040-01-01");

		assertThat(((Map<?, ?>) result).get("erro").toString()).contains("367").contains("de=HOJE");
		verifyNoInteractions(repo);
	}

	@Test
	void periodoComDatasTrocadasEInvertidoEmVezDeFalhar() {
		assertThat(tools.resumoPorPeriodo("HOJE", "ONTEM")).isInstanceOf(ResumoPeriodo.class);
		verify(repo).buscar(LocalDate.parse("2039-12-31"), LocalDate.parse("2040-01-01"));
	}

	@Test
	void semanaSaoOsUltimosSeteDiasAteHoje() {
		tools.resumoPorPeriodo("SEMANA", null);
		verify(repo).buscar(LocalDate.parse("2039-12-26"), LocalDate.parse("2040-01-01"));
	}

	@Test
	void mesVaiDoDiaUmAteHoje() {
		PeriodoTools t = new PeriodoTools(repo, Clock.fixed(Instant.parse("2040-01-20T15:00:00Z"), ZoneOffset.UTC));
		t.resumoPorPeriodo("mes", null);
		verify(repo).buscar(LocalDate.parse("2040-01-01"), LocalDate.parse("2040-01-20"));
	}

	@Test
	void resultadoUsaNomesQueNaoPermitemConfundirTotalComPrecoUnitario() {
		ResumoPeriodo r = (ResumoPeriodo) tools.resumoPorPeriodo("HOJE", "HOJE");

		assertThat(r.periodo()).isEqualTo("2040-01-01");
		assertThat(r.pedidosCriadosNoPeriodo()).isEqualTo(4);
		assertThat(r.pedidosPorStatus()).containsEntry("RECEIVED", 1L).containsEntry("DELIVERED", 3L);
		assertThat(r.receitaTotal()).isEqualTo("R$ 42,00");
		assertThat(r.comandasFechadasNoPeriodo()).isEqualTo(1);
		assertThat(r.ticketMedioPorComanda()).isEqualTo("R$ 42,00");
		assertThat(r.receitaDeAdicionais()).isEqualTo("R$ 3,00");
		assertThat(r.comandasAbertasAgora()).isEqualTo(2);
		assertThat(r.valorEmAberto()).isEqualTo("R$ 140,30");
		assertThat(r.produtosMaisVendidos().getFirst().nome()).isEqualTo("Poke de Salmao");
		assertThat(r.produtosMaisVendidos().getFirst().unidadesVendidas()).isEqualTo(2);
		assertThat(r.produtosMaisVendidos().getFirst().totalVendido()).isEqualTo("R$ 77,80");
		assertThat(r.categoriasMaisVendidas().getFirst().nome()).isEqualTo("Pokes");
	}

	@Test
	void valoresVemProntosEmReaisNoPadraoBrasileiro() {
		LocalDate dia = LocalDate.parse("2040-01-01");
		doReturn(sample(dia, dia, new BigDecimal("1234567.5"))).when(repo).buscar(any(), any());

		ResumoPeriodo result = (ResumoPeriodo) tools.resumoPorPeriodo("HOJE", "HOJE");

		assertThat(result.receitaTotal()).isEqualTo("R$ 1.234.567,50");
		assertThat(result.ticketMedioPorComanda()).isEqualTo("R$ 1.234.567,50");
	}

	@Test
	void umDiaNaoTrazSerieDiariaEPeriodoLongoTraz() {
		assertThat(((ResumoPeriodo) tools.resumoPorPeriodo("HOJE", "HOJE")).porDia()).isEmpty();

		ResumoPeriodo semana = (ResumoPeriodo) tools.resumoPorPeriodo("SEMANA", null);
		assertThat(semana.periodo()).isEqualTo("2039-12-26 a 2040-01-01");
		assertThat(semana.porDia()).hasSize(7);
		assertThat(semana.porDia().getFirst().dia()).isEqualTo("2039-12-26");
	}
}
