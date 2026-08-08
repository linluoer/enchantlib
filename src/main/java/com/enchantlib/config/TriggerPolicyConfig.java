package com.enchantlib.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.enchantlib.EnchantLib;
import com.enchantlib.event.TriggerPolicy;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 触发策略配置加载器。
 *
 * <p>加载 {@code config/enchantlib/trigger.toml}，允许按附魔 ID 覆盖注册时的默认 {@link TriggerPolicy}。</p>
 *
 * <h2>配置文件格式</h2>
 * <pre>{@code
 * # 全局默认阈值（THRESHOLD/THRESHOLD_SCALED 模式下，未配置附魔使用此值）
 * # 范围 0.0~1.0，默认 0.0（相当于不门控，由附魔自身的 policy 决定）
 * force_threshold_min = 0.0
 *
 * # 附魔级覆盖：键为附魔 ID（modid:name），含冒号必须用引号包裹
 * ["mymod:demo"]
 * mode = "THRESHOLD"
 * threshold = 0.5
 *
 * ["mymod:demo_bow"]
 * mode = "THRESHOLD_SCALED"
 * threshold = 0.7
 * }</pre>
 *
 * <h2>mode 取值</h2>
 * <ul>
 *   <li>{@code IGNORE}：忽略充能（默认）</li>
 *   <li>{@code THRESHOLD}：阈值门控</li>
 *   <li>{@code SCALED}：按充能缩放等级</li>
 *   <li>{@code THRESHOLD_SCALED}：阈值 + 缩放</li>
 * </ul>
 *
 * <p>若配置文件不存在，自动创建默认配置（force_threshold_min=0.0，无附魔覆盖）。</p>
 *
 * @since 0.1.0
 */
public class TriggerPolicyConfig {

	private static final String CONFIG_PATH = "config/enchantlib/trigger.toml";
	private static final float DEFAULT_FORCE_THRESHOLD_MIN = 0.0f;

	private final float forceThresholdMin;
	private final Map<String, TriggerPolicy> perEnchantmentPolicies;

	public TriggerPolicyConfig(float forceThresholdMin, Map<String, TriggerPolicy> perEnchantmentPolicies) {
		this.forceThresholdMin = Math.max(0.0f, Math.min(1.0f, forceThresholdMin));
		this.perEnchantmentPolicies = perEnchantmentPolicies != null
			? new HashMap<>(perEnchantmentPolicies)
			: new HashMap<>();
	}

	/**
	 * 加载触发策略配置。
	 *
	 * <p>若配置文件不存在，自动创建默认配置文件并返回默认值。</p>
	 *
	 * @return 配置实例
	 */
	public static TriggerPolicyConfig load() {
		Path path = Path.of(CONFIG_PATH);

		if (!Files.exists(path)) {
			EnchantLib.LOGGER.info("[EnchantLib] 触发策略配置不存在，创建默认配置: {}", path.toAbsolutePath());
			TriggerPolicyConfig defaultConfig = new TriggerPolicyConfig(DEFAULT_FORCE_THRESHOLD_MIN, new HashMap<>());
			defaultConfig.save(path);
			return defaultConfig;
		}

		TomlParser parser = new TomlParser();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Config config = parser.parse(reader);
			// TOML 浮点数解析为 Double，需用 Number.floatValue() 转换
			double globalThresholdDouble = config.getOrElse("force_threshold_min", (double) DEFAULT_FORCE_THRESHOLD_MIN);
			float globalThreshold = clampThreshold(globalThresholdDouble);
			Map<String, TriggerPolicy> policies = new HashMap<>();

			// 遍历顶层键，识别附魔级覆盖段（modid:name 格式的子表）
			for (Config.Entry entry : config.entrySet()) {
				String key = entry.getKey();
				if ("force_threshold_min".equals(key)) {
					continue;
				}
				Object value = entry.getValue();
				if (value instanceof Config subConfig) {
					TriggerPolicy policy = parsePolicy(key, subConfig, globalThreshold);
					if (policy != null) {
						policies.put(key, policy);
					}
				}
			}

			EnchantLib.LOGGER.info("[EnchantLib] 触发策略配置加载完成: force_threshold_min={}, {} 个附魔覆盖",
				String.format("%.2f", globalThreshold), policies.size());
			return new TriggerPolicyConfig(globalThreshold, policies);
		} catch (Exception e) {
			// Q5.1: 捕获所有异常（含 ParsingException），绝不崩服，回退默认值
			EnchantLib.LOGGER.error("[EnchantLib] 读取触发策略配置失败，使用默认值: {}", e.getMessage(), e);
			return new TriggerPolicyConfig(DEFAULT_FORCE_THRESHOLD_MIN, new HashMap<>());
		}
	}

	/**
	 * 从配置子表解析触发策略。
	 *
	 * @param enchantmentId     附魔 ID（用于日志）
	 * @param subConfig         子配置表
	 * @param globalThreshold   全局默认阈值（附魔未指定 threshold 时使用）
	 * @return 触发策略，解析失败返回 null
	 */
	private static TriggerPolicy parsePolicy(String enchantmentId, Config subConfig, float globalThreshold) {
		String modeStr = subConfig.getOrElse("mode", "IGNORE");
		// TOML 浮点数解析为 Double，需用 Number.floatValue() 转换
		double thresholdDouble = subConfig.getOrElse("threshold", (double) globalThreshold);
		// Q5.2: 阈值范围校验，clamp 到 [0.0, 1.0]
		boolean outOfRange = thresholdDouble < 0.0 || thresholdDouble > 1.0;
		float threshold = clampThreshold(thresholdDouble);
		if (outOfRange) {
			EnchantLib.LOGGER.warn("[EnchantLib] 触发策略配置 {} 的 threshold={} 超出范围 [0.0, 1.0]，已 clamp 为 {}",
				enchantmentId, String.format("%.2f", thresholdDouble), String.format("%.2f", threshold));
		}
		try {
			TriggerPolicy.Mode mode = TriggerPolicy.Mode.valueOf(modeStr.toUpperCase());
			TriggerPolicy policy = new TriggerPolicy(mode, threshold);
			EnchantLib.LOGGER.debug("[EnchantLib] 触发策略覆盖: {} → {}", enchantmentId, policy);
			return policy;
		} catch (IllegalArgumentException e) {
			EnchantLib.LOGGER.warn("[EnchantLib] 触发策略配置 {} 的 mode 非法: {}，跳过", enchantmentId, modeStr);
			return null;
		}
	}

	/**
	 * 将阈值 clamp 到 [0.0, 1.0] 范围（Q5.2 范围校验）。
	 *
	 * @param value 原始值
	 * @return clamp 后的值
	 */
	private static float clampThreshold(double value) {
		return (float) Math.max(0.0, Math.min(1.0, value));
	}

	/**
	 * 保存配置到文件。
	 */
	private void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			Config config = Config.inMemory();
			config.set("force_threshold_min", forceThresholdMin);

			for (Map.Entry<String, TriggerPolicy> entry : perEnchantmentPolicies.entrySet()) {
				Config sub = Config.inMemory();
				sub.set("mode", entry.getValue().mode().name());
				sub.set("threshold", entry.getValue().threshold());
				config.set(entry.getKey(), sub);
			}

			TomlWriter writer = new TomlWriter();
			try (Writer fileWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				writer.write(config, fileWriter);
			}
		} catch (IOException e) {
			EnchantLib.LOGGER.error("[EnchantLib] 创建默认触发策略配置失败: {}", e.getMessage(), e);
		}
	}

	/**
	 * 获取全局默认阈值。
	 *
	 * @return 全局阈值（0.0~1.0）
	 */
	public float getForceThresholdMin() {
		return forceThresholdMin;
	}

	/**
	 * 查询指定附魔的策略覆盖。
	 *
	 * @param enchantmentId 附魔 ID（modid:name）
	 * @return 策略覆盖，若无返回 null
	 */
	public TriggerPolicy getOverride(String enchantmentId) {
		return perEnchantmentPolicies.get(enchantmentId);
	}

	/**
	 * 获取所有附魔级策略覆盖（不可变视图）。
	 *
	 * @return 附魔 ID → 策略 映射
	 */
	public Map<String, TriggerPolicy> getAllOverrides() {
		return Map.copyOf(perEnchantmentPolicies);
	}
}
