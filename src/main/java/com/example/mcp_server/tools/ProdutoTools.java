package com.example.mcp_server.tools;

import java.util.List;
import java.util.Map;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp_server.repository.ProdutoRepository;

@Component
public class ProdutoTools {
	private final ProdutoRepository produtos;

	public ProdutoTools(ProdutoRepository produtos) {
		this.produtos = produtos;
	}

	@McpTool (name = "listar_produtos", description = "Lista produtos disponíveis do cardápio, com categoria, preço e extras.")
	public List<Map<String, Object>> listarProdutos() {
		return ToolExecutionLogger.logResult("listar_produtos", "{}", produtos::listarDisponiveis);
	}

	@McpTool (name = "buscar_produto", description = "Busca produtos pelo nome, incluindo preço, disponibilidade, categoria e extras.")
	public List<Map<String, Object>> buscarProduto(
			@McpToolParam(description = "Parte do nome do produto a buscar") String nome) {
		if (nome == null || nome.isBlank()) {
			throw new IllegalArgumentException("Informe um nome ou trecho do nome do produto.");
		}
		return ToolExecutionLogger.logResult("buscar_produto", Map.of("nome", nome), () -> produtos.buscarPorNome(nome));
	}
}
