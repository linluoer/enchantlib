package com.enchantlib.resources;

import com.enchantlib.EnchantLib;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * 内置 HTTP 服务器，提供客户端资源包下载。
 *
 * <p>在服务端启动时创建轻量级 HTTP 服务器，提供资源包下载端点。
 * 当玩家加入服务端时，EnchantLib 通过 MC 原生资源包推送机制
 * （{@code ClientboundResourcePackPushPacket}）告知客户端下载 URL。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /enchantlib-resourcepack.zip} - 下载资源包 ZIP</li>
 *   <li>{@code GET /enchantlib-resourcepack.sha1} - 获取 SHA1 哈希</li>
 * </ul>
 *
 * <h2>使用流程</h2>
 * <ol>
 *   <li>服务端启动时调用 {@link #start(int)} 启动 HTTP 服务器</li>
 *   <li>玩家加入时，EnchantLib 发送推送包（包含 URL 和 SHA1）</li>
 *   <li>客户端自动下载资源包并应用</li>
 *   <li>服务端关闭时调用 {@link #stop()} 停止 HTTP 服务器</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class ResourcePackHttpServer {

	/** 资源包下载路径 */
	private static final String RESOURCE_PACK_PATH = "/enchantlib-resourcepack.zip";

	/** SHA1 哈希路径 */
	private static final String SHA1_PATH = "/enchantlib-resourcepack.sha1";

	/** 默认 HTTP 端口 */
	private static final int DEFAULT_PORT = 8765;

	/** HTTP 服务器实例 */
	private static HttpServer server = null;

	/** 实际监听端口 */
	private static int actualPort = 0;

	/** 服务器主机地址（用于构建下载 URL） */
	private static String hostAddress = "localhost";

	private ResourcePackHttpServer() {
	}

	/**
	 * 启动 HTTP 服务器。
	 *
	 * @param port 监听端口（若 0 使用默认端口 8765）
	 * @return 实际监听端口，若启动失败返回 -1
	 */
	public static int start(int port) {
		return start(port, "");
	}

	/**
	 * 启动 HTTP 服务器。
	 *
	 * @param port 监听端口（若 0 使用默认端口 8765）
	 * @param configuredHost 对外主机地址（域名或公网 IP），空字符串则自动探测本机 IP
	 * @return 实际监听端口，若启动失败返回 -1
	 */
	public static int start(int port, String configuredHost) {
		int listenPort = port > 0 ? port : DEFAULT_PORT;

		if (configuredHost != null && !configuredHost.isEmpty()) {
			// 管理员配置了对外主机地址（域名或公网 IP）
			hostAddress = configuredHost;
			EnchantLib.LOGGER.info("[EnchantLib] 使用配置的对外主机地址: {}", hostAddress);
		} else {
			// 自动探测本机 IP
			try {
				hostAddress = resolveHostAddress();
			} catch (UnknownHostException e) {
				EnchantLib.LOGGER.warn("[EnchantLib] 无法解析主机地址，使用 localhost: {}", e.getMessage());
				hostAddress = "localhost";
			}
			EnchantLib.LOGGER.warn("[EnchantLib] http_server_host 未配置，使用自动探测的本机 IP: {}", hostAddress);
			EnchantLib.LOGGER.warn("[EnchantLib] 公网玩家可能无法访问，请在 acquisition.toml 中设置 http_server_host 为对外域名或公网 IP");
		}

		try {
			server = HttpServer.create(new InetSocketAddress("0.0.0.0", listenPort), 0);
			server.createContext(RESOURCE_PACK_PATH, new ResourcePackHandler());
			server.createContext(SHA1_PATH, new Sha1Handler());
			server.setExecutor(null);
			server.start();
			actualPort = listenPort;

			EnchantLib.LOGGER.info("[EnchantLib] HTTP 服务器已启动: http://{}:{}/",
				hostAddress, actualPort);
			EnchantLib.LOGGER.info("[EnchantLib] 资源包下载 URL: http://{}:{}{}",
				hostAddress, actualPort, RESOURCE_PACK_PATH);

			return actualPort;
		} catch (IOException e) {
			EnchantLib.LOGGER.error("[EnchantLib] HTTP 服务器启动失败（端口 {}）: {}",
				listenPort, e.getMessage());
			return -1;
		}
	}

	/**
	 * 停止 HTTP 服务器。
	 */
	public static void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
			actualPort = 0;
			EnchantLib.LOGGER.info("[EnchantLib] HTTP 服务器已停止");
		}
	}

	/**
	 * 获取资源包下载 URL。
	 *
	 * @return 完整的下载 URL，若服务器未启动返回 null
	 */
	public static String getResourcePackUrl() {
		if (server == null) {
			return null;
		}
		return "http://" + hostAddress + ":" + actualPort + RESOURCE_PACK_PATH;
	}

	/**
	 * 获取服务器是否正在运行。
	 *
	 * @return true 如果 HTTP 服务器正在运行
	 */
	public static boolean isRunning() {
		return server != null;
	}

	/**
	 * 获取实际监听端口。
	 *
	 * @return 端口号，若未启动返回 0
	 */
	public static int getPort() {
		return actualPort;
	}

	/**
	 * 解析本机 IP 地址。
	 *
	 * @return 本机 IP 地址字符串
	 * @throws UnknownHostException 如果无法解析
	 */
	private static String resolveHostAddress() throws UnknownHostException {
		// 优先使用 server.properties 中的 server-ip
		// 简化实现：使用 InetAddress 获取本机地址
		java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
		return addr.getHostAddress();
	}

	/**
	 * 资源包下载处理器。
	 */
	private static class ResourcePackHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(405, -1);
				exchange.close();
				return;
			}

			byte[] zipBytes = ClientResourcePackBuilder.getZipBytes();
			if (zipBytes == null) {
				String msg = "Resource pack not built yet";
				exchange.sendResponseHeaders(503, msg.length());
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(msg.getBytes());
				}
				exchange.close();
				return;
			}

			// 设置响应头
			exchange.getResponseHeaders().set("Content-Type", "application/zip");
			exchange.getResponseHeaders().set("Content-Length", String.valueOf(zipBytes.length));
			exchange.getResponseHeaders().set("Content-Disposition",
				"attachment; filename=\"enchantlib-resourcepack.zip\"");
			exchange.getResponseHeaders().set("Cache-Control", "no-cache");

			exchange.sendResponseHeaders(200, zipBytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(zipBytes);
			}
			exchange.close();

			EnchantLib.LOGGER.debug("[EnchantLib] 资源包已下载: {} 字节", zipBytes.length);
		}
	}

	/**
	 * SHA1 哈希处理器。
	 */
	private static class Sha1Handler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(405, -1);
				exchange.close();
				return;
			}

			String sha1 = ClientResourcePackBuilder.getSha1();
			if (sha1 == null) {
				sha1 = "";
			}

			byte[] bytes = sha1.getBytes();
			exchange.getResponseHeaders().set("Content-Type", "text/plain");
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
			exchange.close();
		}
	}
}
