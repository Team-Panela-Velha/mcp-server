package com.example.mcp_server.tools;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp_server.repository.ComandaRepository;

@Component
public class ComandaTools {
	private final ComandaRepository comandas;

	public ComandaTools(ComandaRepository comandas) {
		this.comandas = comandas;
	}

	@Tool(name = "listar_comandas_abertas", description = "Lista comandas abertas com mesa, valor registrado, quantidade de pedidos e itens.")
	public List<Map<String, Object>> listarComandasAbertas() {
		return comandas.listarAbertas();
	}

	@Tool(name = "consultar_comanda", description = "Consulta uma comanda pelo ID, incluindo seu resumo e os itens dos pedidos associados.")
	public Map<String, Object> consultarComanda(
			@ToolParam(description = "ID numérico da comanda") Long comandaId) {
		if (comandaId == null || comandaId <= 0) {
			throw new IllegalArgumentException("O ID da comanda deve ser positivo.");
		}
		List<Map<String, Object>> resultados = comandas.buscarPorId(comandaId);
		if (resultados.isEmpty()) {
			return Map.of("encontrada", false, "comanda_id", comandaId);
		}
		return Map.of(
				"encontrada", true,
				"comanda", resultados.getFirst(),
				"itens", comandas.listarItens(comandaId));
	}

	@Tool(name = "resumo_operacional", description = "Retorna quantidade total de pedidos, comandas abertas e fechadas, itens por status e receita registrada nas comandas fechadas. A DDL não possui datas para filtrar por período.")
	public Map<String, Object> resumoOperacional() {
		return Map.of(
				"periodo", "todo o histórico disponível",
				"observacao", "A DDL não possui datas nos pedidos ou nas comandas; não é possível filtrar a receita por período.",
				"indicadores", comandas.resumoOperacional());
	}
}
