package com.enchantlib.debug;

import com.enchantlib.EnchantLib;
import org.slf4j.Logger;

/**
 * EnchantLib 调试日志器。
 *
 * <p>统一的调试日志输出口，受全局调试开关控制。开启后，在关键路径输出详细日志，
 * 便于开发者和管理员排查问题。</p>
 *
 * <h2>开关控制</h2>
 * <ul>
 *   <li>配置文件：{@code config/enchantlib/acquisition.toml} 中的 {@code debug_enabled} 字段</li>
 *   <li>运行时切换：{@code /enchantlib debug toggle} 指令（不持久化，重启后回到配置值）</li>
 *   <li>重载配置：{@code /enchantlib reload} 会从配置文件重新读取 {@code debug_enabled}</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <pre>{@code
 * DebugLogger.log("附魔注册完成: {} 个附魔, {} 个互斥组", enchantCount, groupCount);
 * DebugLogger.log("事件分发: {} -> 回调数 {}", eventId, callbackCount);
 * }</pre>
 *
 * @since 0.1.0
 */
public final class DebugLogger {

	private static final Logger LOGGER = EnchantLib.LOGGER;

	/** 运行时调试开关（受配置文件和 /enchantlib debug toggle 控制） */
	private static volatile boolean enabled = false;

	private DebugLogger() {
		// 工具类，禁止实例化
	}

	/**
	 * 设置调试开关状态（由 EnchantLib.onInitialize 和 /enchantlib debug toggle 调用）。
	 *
	 * @param enabled true 开启调试日志输出
	 */
	public static void setEnabled(boolean enabled) {
		DebugLogger.enabled = enabled;
	}

	/**
	 * 查询调试开关状态。
	 *
	 * @return true 若调试模式已开启
	 */
	public static boolean isEnabled() {
		return enabled;
	}

	/**
	 * 输出调试日志（带 SLF4J 占位符格式）。
	 *
	 * <p>仅当调试开关开启时输出，否则为空操作。</p>
	 *
	 * @param format SLF4J 格式字符串（使用 {} 占位符）
	 * @param args   占位符参数
	 */
	public static void log(String format, Object... args) {
		if (enabled) {
			LOGGER.info("[EnchantLib-Debug] " + format, args);
		}
	}

	/**
	 * 输出调试日志（纯文本）。
	 *
	 * @param message 日志消息
	 */
	public static void log(String message) {
		if (enabled) {
			LOGGER.info("[EnchantLib-Debug] {}", message);
		}
	}
}
