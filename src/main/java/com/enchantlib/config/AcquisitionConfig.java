package com.enchantlib.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.enchantlib.EnchantLib;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 获取途径与资源分发全局开关配置。
 *
 * <p>加载 {@code config/enchantlib/acquisition.toml}，控制战利品注入、村民交易
 * 和资源分发是否全局启用。全局开关关闭时，对应的子系统被完全禁用。</p>
 *
 * <h2>配置文件格式</h2>
 * <pre>{@code
 * # 全局获取途径开关
 * # 关闭后，所有战利品注入规则都不会被注册
 * loot_injection_enabled = true
 *
 * # 关闭后，所有村民交易都不会被注入
 * villager_trade_enabled = true
 *
 * # 资源分发总开关
 * # 关闭后，跳过目录扫描、语言合并、资源包构建、HTTP 服务器和推送
 * resource_distribution_enabled = true
 *
 * # HTTP 服务器端口（仅 resource_distribution_enabled=true 时生效）
 * http_server_port = 8765
 *
 * # HTTP 服务器对外完整网址（域名或公网 IP，可含端口；走 80/反代则不带端口。端口写在这里，不写 http_server_port）
 * # 留空则自动探测本机 IP（仅局域网可用，公网玩家无法访问）
 * # 示例：http_server_host = "play.example.com" 或 "203.0.113.5" 或 "play.example.com:8080"
 * http_server_host = ""
 *
 * # 调试模式开关
 * # 开启后，EnchantLib 在关键路径输出详细调试日志（附魔注册、事件分发、战利品注入等）
 * # 也可通过 /enchantlib debug toggle 运行时切换（不持久化，重启后回到配置值）
 * debug_enabled = false
 *
 * # ENTITY_TICK 事件触发间隔（tick）
 * # 1 = 每 tick 触发（高精度，CPU 开销大）
 * # 20 = 每秒触发一次（默认，推荐）
 * # 使用实体 ID 相位偏移避免全服同一 tick 集中触发
 * entity_tick_interval = 20
 * }</pre>
 *
 * <p>若配置文件不存在，自动创建默认配置（所有开关默认开启，调试模式默认关闭）。</p>
 *
 * @since 0.1.0
 */
public class AcquisitionConfig {

	private static final String CONFIG_PATH = "config/enchantlib/acquisition.toml";

	private static final boolean DEFAULT_LOOT_INJECTION_ENABLED = true;
	private static final boolean DEFAULT_VILLAGER_TRADE_ENABLED = true;
	private static final boolean DEFAULT_RESOURCE_DISTRIBUTION_ENABLED = true;
	private static final int DEFAULT_HTTP_SERVER_PORT = 8765;
	private static final String DEFAULT_HTTP_SERVER_HOST = "";
	private static final boolean DEFAULT_DEBUG_ENABLED = false;
	private static final int DEFAULT_ENTITY_TICK_INTERVAL = 20;

	private boolean lootInjectionEnabled;
	private boolean villagerTradeEnabled;
	private boolean resourceDistributionEnabled;
	private int httpServerPort;
	private String httpServerHost;
	private boolean debugEnabled;
	private int entityTickInterval;

	private AcquisitionConfig(boolean lootInjectionEnabled, boolean villagerTradeEnabled,
							 boolean resourceDistributionEnabled, int httpServerPort,
							 String httpServerHost, boolean debugEnabled, int entityTickInterval) {
		this.lootInjectionEnabled = lootInjectionEnabled;
		this.villagerTradeEnabled = villagerTradeEnabled;
		this.resourceDistributionEnabled = resourceDistributionEnabled;
		this.httpServerPort = Math.max(1, Math.min(65535, httpServerPort));
		this.httpServerHost = httpServerHost != null ? httpServerHost : "";
		this.debugEnabled = debugEnabled;
		this.entityTickInterval = Math.max(1, entityTickInterval);
	}

	/**
	 * 加载全局获取途径配置。
	 *
	 * <p>若配置文件不存在，自动创建默认配置文件并返回默认值。</p>
	 *
	 * @return 配置实例
	 */
	public static AcquisitionConfig load() {
		Path path = Path.of(CONFIG_PATH);

		if (!Files.exists(path)) {
			EnchantLib.LOGGER.info("[EnchantLib] 获取途径配置不存在，创建默认配置: {}", path.toAbsolutePath());
			AcquisitionConfig defaultConfig = new AcquisitionConfig(
				DEFAULT_LOOT_INJECTION_ENABLED, DEFAULT_VILLAGER_TRADE_ENABLED,
				DEFAULT_RESOURCE_DISTRIBUTION_ENABLED, DEFAULT_HTTP_SERVER_PORT,
				DEFAULT_HTTP_SERVER_HOST, DEFAULT_DEBUG_ENABLED, DEFAULT_ENTITY_TICK_INTERVAL);
			defaultConfig.save(path);
			return defaultConfig;
		}

		TomlParser parser = new TomlParser();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Config config = parser.parse(reader);
			boolean lootEnabled = config.getOrElse("loot_injection_enabled", DEFAULT_LOOT_INJECTION_ENABLED);
			boolean tradeEnabled = config.getOrElse("villager_trade_enabled", DEFAULT_VILLAGER_TRADE_ENABLED);
			boolean resourceEnabled = config.getOrElse("resource_distribution_enabled", DEFAULT_RESOURCE_DISTRIBUTION_ENABLED);
			int httpPort = config.getIntOrElse("http_server_port", DEFAULT_HTTP_SERVER_PORT);
			String httpHost = config.getOrElse("http_server_host", DEFAULT_HTTP_SERVER_HOST);
			boolean debugEnabled = config.getOrElse("debug_enabled", DEFAULT_DEBUG_ENABLED);
			int tickInterval = config.getIntOrElse("entity_tick_interval", DEFAULT_ENTITY_TICK_INTERVAL);
			EnchantLib.LOGGER.info("[EnchantLib] 获取途径配置加载完成: loot_injection={}, villager_trade={}, resource_distribution={}, http_port={}, http_host={}, debug={}, entity_tick_interval={}",
				lootEnabled, tradeEnabled, resourceEnabled, httpPort,
				httpHost.isEmpty() ? "(auto)" : httpHost, debugEnabled, tickInterval);
			return new AcquisitionConfig(lootEnabled, tradeEnabled, resourceEnabled, httpPort, httpHost, debugEnabled, tickInterval);
		} catch (Exception e) {
			// Q5.1: 捕获所有异常（含 ParsingException），绝不崩服，回退默认值
			EnchantLib.LOGGER.error("[EnchantLib] 读取获取途径配置失败，使用默认值: {}", e.getMessage(), e);
			return new AcquisitionConfig(DEFAULT_LOOT_INJECTION_ENABLED, DEFAULT_VILLAGER_TRADE_ENABLED,
				DEFAULT_RESOURCE_DISTRIBUTION_ENABLED, DEFAULT_HTTP_SERVER_PORT,
				DEFAULT_HTTP_SERVER_HOST, DEFAULT_DEBUG_ENABLED, DEFAULT_ENTITY_TICK_INTERVAL);
		}
	}

	/**
	 * 保存配置到文件。
	 *
	 * @param path 配置文件路径
	 */
	private void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			Config config = Config.inMemory();
		config.set("loot_injection_enabled", lootInjectionEnabled);
		config.set("villager_trade_enabled", villagerTradeEnabled);
		config.set("resource_distribution_enabled", resourceDistributionEnabled);
		config.set("http_server_port", httpServerPort);
		config.set("http_server_host", httpServerHost);
		config.set("debug_enabled", debugEnabled);
		config.set("entity_tick_interval", entityTickInterval);

			TomlWriter writer = new TomlWriter();
			try (Writer fileWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				writer.write(config, fileWriter);
			}
		} catch (IOException e) {
			EnchantLib.LOGGER.error("[EnchantLib] 创建默认获取途径配置失败: {}", e.getMessage(), e);
		}
	}

	/**
	 * 战利品注入是否全局启用。
	 *
	 * @return true 若启用（战利品注入规则会被注册）
	 */
	public boolean isLootInjectionEnabled() {
		return lootInjectionEnabled;
	}

	/**
	 * 村民交易是否全局启用。
	 *
	 * @return true 若启用（村民交易会被注入）
	 */
	public boolean isVillagerTradeEnabled() {
		return villagerTradeEnabled;
	}

	/**
	 * 资源分发是否全局启用。
	 *
	 * @return true 若启用（资源分发系统会被激活）
	 */
	public boolean isResourceDistributionEnabled() {
		return resourceDistributionEnabled;
	}

	/**
	 * HTTP 服务器端口。
	 *
	 * @return 端口号（默认 8765）
	 */
	public int getHttpServerPort() {
		return httpServerPort;
	}

	/**
	 * HTTP 服务器对外主机地址（域名或公网 IP）。
	 *
	 * <p>用于构建客户端可访问的资源包下载 URL。若为空字符串，则使用自动探测的本机 IP
	 * （仅局域网可用，公网玩家无法访问）。</p>
	 *
	 * @return 主机地址字符串，空字符串表示自动探测
	 */
	public String getHttpServerHost() {
		return httpServerHost;
	}

	/**
	 * 调试模式是否启用。
	 *
	 * @return true 若启用（关键路径输出详细调试日志）
	 */
	public boolean isDebugEnabled() {
		return debugEnabled;
	}

	/**
	 * 获取 ENTITY_TICK 事件触发间隔。
	 *
	 * @return 间隔（tick，>= 1，默认 20 = 每秒）
	 */
	public int getEntityTickInterval() {
		return entityTickInterval;
	}
}
