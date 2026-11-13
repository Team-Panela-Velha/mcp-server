package com.example.mcp_server.tools;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp_server.repository.PedidoRepository;

@Component
public class PedidoTools {
	private static final Set<String> STATUS_VALIDOS =
			Set.of("RECEIVED", "IN_PREPARATION", "READY", "DELIVERED");

	private final PedidoRepository pedidos;

	public PedidoTools(PedidoRepository pedidos) {
		this.pedidos = pedidos;
	}

	@McpTool(name = "listar_pedidos_por_status", description = "Lista itens de pedidos por status: RECEIVED, IN_PREPARATION, READY ou DELIVERED.")
	public List<Map<String, Object>> listarPedidosPorStatus(
			@McpToolParam(description = "Status do item: RECEIVED, IN_PREPARATION, READY ou DELIVERED", required = true) String status) {
		String statusNormalizado = status == null ? "" : status.trim().toUpperCase();
		if (!STATUS_VALIDOS.contains(statusNormalizado)) {
			throw new IllegalArgumentException("Status inválido. Use: RECEIVED, IN_PREPARATION, READY ou DELIVERED.");
		}
		return ToolExecutionLogger.logResult("listar_pedidos_por_status", Map.of("status", statusNormalizado),
				() -> pedidos.listarPorStatus(statusNormalizado));
	}

	@McpTool(name = "consultar_pedido", description = "Consulta os itens e valores de um pedido pelo ID.")
	public List<Map<String, Object>> consultarPedido(
			@McpToolParam(description = "ID numérico do pedido", required = true) Long pedidoId) {
		if (pedidoId == null || pedidoId <= 0) {
			throw new IllegalArgumentException("O ID do pedido deve ser positivo.");
		}
		return ToolExecutionLogger.logResult("consultar_pedido", Map.of("pedidoId", pedidoId),
				() -> pedidos.buscarPorId(pedidoId));
	}
}
