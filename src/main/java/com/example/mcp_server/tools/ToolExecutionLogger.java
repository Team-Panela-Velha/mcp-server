package com.example.mcp_server.tools;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logs tool inputs and returned data so MCP calls can be traced in the server output. */
final class ToolExecutionLogger {
	private static final Logger log = LoggerFactory.getLogger("MCP_TOOL_CALLS");

	private ToolExecutionLogger() {
	}

	static <T> T logResult(String tool, Object input, Supplier<T> operation) {
		long startedAt = System.nanoTime();
		System.out.println("[MCP_TOOL_CALLS] MCP tool started: " + tool + " input=" + input);
		log.info("MCP tool started: {} input={}", tool, input);
		try {
			T result = operation.get();
			long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
			System.out.println("[MCP_TOOL_CALLS] MCP tool completed: " + tool + " elapsedMs=" + elapsedMs + " result=" + result);
			log.info("MCP tool completed: {} elapsedMs={} result={}", tool, elapsedMs, result);
			return result;
		} catch (RuntimeException exception) {
			long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
			System.out.println("[MCP_TOOL_CALLS] MCP tool failed: " + tool + " elapsedMs=" + elapsedMs + " error=" + exception.getMessage());
			log.error("MCP tool failed: {} elapsedMs={} error={}", tool, elapsedMs, exception.getMessage(), exception);
			throw exception;
		}
	}
}
