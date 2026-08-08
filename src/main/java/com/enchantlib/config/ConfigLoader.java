package com.enchantlib.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.enchantlib.EnchantLib;
import com.enchantlib.api.EnchantmentBuilder;
import com.enchantlib.api.EnchantmentEffectsBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 配置文件加载器。
 *
 * <p>扫描 {@code config/enchantlib/enchantments/} 目录下的 TOML 文件，
 * 每个文件定义一个附魔，解析后转换为 {@link EnchantmentBuilder}。</p>
 *
 * <h2>配置文件格式示例</h2>
 * <pre>{@code
 * # 必填字段
 * id = "enchantlib:test_config"
 * description = "Test Config Enchantment"
 * supported_items = "#minecraft:enchantable/sharp_weapon"
 * weight = 10
 * max_level = 5
 * anvil_cost = 4
 *
 * [min_cost]
 * base = 1
 * per_level_above_first = 11
 *
 * [max_cost]
 * base = 21
 * per_level_above_first = 11
 *
 * slots = ["mainhand"]
 *
 * # 可选字段
 * primary_items = "#minecraft:enchantable/melee_weapon"
 * exclusive_set = "#minecraft:exclusive_set/damage"
 *
 * # 条件加载（可选）
 * # [condition]
 * # mod_loaded = "sophisticatedbackpacks"     # 仅当该 mod 加载时才加载此附魔
 * # mod_not_loaded = "jei"                    # 仅当该 mod 未加载时才加载此附魔
 *
 * # 获取途径开关（可选）
 * # [acquisition]
 * # loot = true     # 自动注册战利品注入（注入到 simple_dungeon、abandoned_mineshaft 等箱子）
 * # trade = true    # 自动加入 #minecraft:tradeable 标签（图书管理员概率出售）
 *
 * # 效果（可选，便捷格式）
 * # [[effects.<effect_key>]]：effect_key 用下划线替换冒号
 * # 自动生成 minecraft:add + minecraft:linear 结构
 * [[effects.minecraft_damage]]
 * base = 1.0
 * per_level_above_first = 0.5
 * }</pre>
 *
 * @since 0.1.0
 */
public class ConfigLoader {

	/** 配置目录：config/enchantlib/enchantments/ */
	private static final String CONFIG_DIR = "config/enchantlib/enchantments";

	/**
	 * 加载所有配置文件，转换为附魔构建器列表。
	 *
	 * @return 配置定义的附魔构建器列表；无配置时返回空列表
	 */
	public static List<EnchantmentBuilder> loadAll() {
		List<EnchantmentBuilder> builders = new ArrayList<>();
		Path dir = Path.of(CONFIG_DIR);

		if (!Files.isDirectory(dir)) {
			EnchantLib.LOGGER.info("[EnchantLib] 配置目录不存在，跳过配置加载: {}", dir.toAbsolutePath());
			return builders;
		}

		List<Path> tomlFiles = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.toml")) {
			stream.forEach(tomlFiles::add);
		} catch (IOException e) {
			EnchantLib.LOGGER.error("[EnchantLib] 扫描配置目录失败: {}", e.getMessage(), e);
			return builders;
		}

		if (tomlFiles.isEmpty()) {
			EnchantLib.LOGGER.info("[EnchantLib] 配置目录无 TOML 文件: {}", dir.toAbsolutePath());
			return builders;
		}

		EnchantLib.LOGGER.info("[EnchantLib] 发现 {} 个配置文件，开始加载", tomlFiles.size());

		TomlParser parser = new TomlParser();
		int success = 0;
		int failed = 0;

		for (Path file : tomlFiles) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Config config = parser.parse(reader);
				EnchantmentBuilder builder = convertToBuilder(config, file);
				if (builder != null) {
					builders.add(builder);
					success++;
					EnchantLib.LOGGER.info("[EnchantLib] 加载配置附魔: {} (from {})",
						builder.getId(), file.getFileName());
				}
			} catch (Exception e) {
				failed++;
				EnchantLib.LOGGER.error("[EnchantLib] 解析配置文件失败 {}: {}", file.getFileName(), e.getMessage(), e);
			}
		}

		EnchantLib.LOGGER.info("[EnchantLib] 配置加载完成: 成功 {} 个, 失败 {} 个", success, failed);
		return builders;
	}

	/**
	 * 将 night-config Config 转换为 EnchantmentBuilder。
	 *
	 * @param config TOML 配置
	 * @param file   配置文件路径（用于错误信息）
	 * @return EnchantmentBuilder；若配置无效返回 null
	 */
	private static EnchantmentBuilder convertToBuilder(Config config, Path file) {
		// 必填字段
		String id = config.get("id");
		if (id == null || id.isEmpty()) {
			EnchantLib.LOGGER.error("[EnchantLib] 配置 {} 缺少必填字段 'id'", file.getFileName());
			return null;
		}

		// 条件加载检查：若条件不满足则跳过此附魔
		if (!checkConditions(config, file)) {
			return null;
		}

		String description = config.getOrElse("description", "");
		String supportedItems = config.get("supported_items");
		if (supportedItems == null || supportedItems.isEmpty()) {
			EnchantLib.LOGGER.error("[EnchantLib] 配置 {} 缺少必填字段 'supported_items'", file.getFileName());
			return null;
		}

		EnchantmentBuilder builder = EnchantmentBuilder.create(id)
			.description(description)
			.supportedItems(supportedItems);

		// 可选字段：primary_items
		String primaryItems = config.get("primary_items");
		if (primaryItems != null) {
			builder.primaryItems(primaryItems);
		}

		// 必填字段：weight, max_level, anvil_cost（Q5.2: 范围校验 + clamp）
		int weight = clampMin(config.getIntOrElse("weight", 1), 1, "weight", id, file);
		int maxLevel = clampMin(config.getIntOrElse("max_level", 1), 1, "max_level", id, file);
		int anvilCost = clampMin(config.getIntOrElse("anvil_cost", 1), 1, "anvil_cost", id, file);
		builder.weight(weight).maxLevel(maxLevel).anvilCost(anvilCost);

		// 必填字段：min_cost, max_cost（Q5.2: 范围校验 + clamp）
		Config minCost = config.get("min_cost");
		if (minCost != null) {
			int base = clampMin(minCost.getIntOrElse("base", 1), 1, "min_cost.base", id, file);
			int perLevel = clampMin(minCost.getIntOrElse("per_level_above_first", 1), 1, "min_cost.per_level_above_first", id, file);
			builder.minCost(base, perLevel);
		}

		Config maxCost = config.get("max_cost");
		if (maxCost != null) {
			int base = clampMin(maxCost.getIntOrElse("base", 1), 1, "max_cost.base", id, file);
			int perLevel = clampMin(maxCost.getIntOrElse("per_level_above_first", 1), 1, "max_cost.per_level_above_first", id, file);
			builder.maxCost(base, perLevel);
		}

		// 必填字段：slots
		List<String> slots = config.get("slots");
		if (slots != null && !slots.isEmpty()) {
			builder.slots(slots.toArray(new String[0]));
		} else {
			builder.slots("mainhand");
		}

		// 可选字段：exclusive_set
		String exclusiveSet = config.get("exclusive_set");
		if (exclusiveSet != null) {
			builder.exclusiveSet(exclusiveSet);
		}

		// 可选字段：acquisition（单附魔获取途径开关）
		Config acquisition = config.get("acquisition");
		if (acquisition != null) {
			boolean loot = acquisition.getOrElse("loot", false);
			boolean trade = acquisition.getOrElse("trade", false);
			builder.acquisition(loot, trade);
			EnchantLib.LOGGER.info("[EnchantLib] 配置附魔 {} 的获取途径: loot={}, trade={}", id, loot, trade);
		}

		// 可选字段：effects
		Config effects = config.get("effects");
		if (effects != null) {
			EnchantmentEffectsBuilder effectsBuilder = convertEffects(effects, file);
			if (effectsBuilder != null) {
				builder.effects(effectsBuilder);
			}
		}

		return builder;
	}

	/**
	 * 转换 effects 配置为 EnchantmentEffectsBuilder。
	 *
	 * <p>配置格式：{@code [[effects.<effect_key>]]}，effect_key 用下划线替换冒号。
	 * 例如 {@code minecraft_damage} 对应 {@code minecraft:damage}。</p>
	 *
	 * <p>每个 effect 条目支持字段：base、per_level_above_first。
	 * 自动生成 {@code minecraft:add + minecraft:linear} 结构。</p>
	 */
	private static EnchantmentEffectsBuilder convertEffects(Config effects, Path file) {
		EnchantmentEffectsBuilder builder = EnchantmentEffectsBuilder.create();

		for (Map.Entry<String, Object> entry : effects.valueMap().entrySet()) {
			String effectKey = entry.getKey().replace('_', ':');
			Object value = entry.getValue();

			if (!(value instanceof List<?> entries)) {
				EnchantLib.LOGGER.warn("[EnchantLib] 配置 {} 的 effects.{} 格式错误，应为数组", file.getFileName(), effectKey);
				continue;
			}

			for (Object item : entries) {
				if (!(item instanceof Config entryConfig)) {
					continue;
				}
				double base = entryConfig.getOrElse("base", 0.0);
				double perLevel = entryConfig.getOrElse("per_level_above_first", 0.0);
				addEffectByType(builder, effectKey, base, perLevel, entryConfig, file);
			}
		}

		return builder;
	}

	/**
	 * 根据 effect 类型调用对应的便捷方法。
	 */
	private static void addEffectByType(EnchantmentEffectsBuilder builder, String effectType,
		double base, double perLevel, Config entryConfig, Path file) {
		switch (effectType) {
			case "minecraft:damage" -> builder.addDamage(base, perLevel);
			case "minecraft:knockback" -> builder.addKnockback(base, perLevel);
			case "minecraft:damage_protection" -> builder.addDamageProtection(base, perLevel);
			case "minecraft:post_attack_ignite" -> builder.addPostAttackIgnite(base, perLevel);
			default -> EnchantLib.LOGGER.warn("[EnchantLib] 配置 {} 包含未支持的 effect 类型: {}", file.getFileName(), effectType);
		}
	}

	/**
	 * 将整数值 clamp 到最小值（Q5.2 范围校验）。
	 *
	 * <p>若值小于 minValue，clamp 为 minValue 并记录 WARN 日志。</p>
	 *
	 * @param value    原始值
	 * @param minValue 最小值
	 * @param fieldName 字段名（用于日志）
	 * @param enchantmentId 附魔 ID（用于日志）
	 * @param file     配置文件路径（用于日志）
	 * @return clamp 后的值
	 */
	private static int clampMin(int value, int minValue, String fieldName, String enchantmentId, Path file) {
		if (value < minValue) {
			EnchantLib.LOGGER.warn("[EnchantLib] 配置 {} 的 {}={} 小于最小值 {}，已 clamp 为 {}（附魔: {}）",
				file.getFileName(), fieldName, value, minValue, minValue, enchantmentId);
			return minValue;
		}
		return value;
	}

	/**
	 * 检查配置的加载条件。
	 *
	 * <p>支持的条件（在 {@code [condition]} 表中定义）：</p>
	 * <ul>
	 *   <li>{@code mod_loaded}：仅当指定 mod 已加载时加载此附魔</li>
	 *   <li>{@code mod_not_loaded}：仅当指定 mod 未加载时加载此附魔</li>
	 * </ul>
	 *
	 * <p>两个条件可同时存在，需同时满足。无 {@code [condition]} 表时默认加载。</p>
	 *
	 * @param config TOML 配置
	 * @param file   配置文件路径（用于日志）
	 * @return true 若条件满足（或无条件），应加载此附魔；false 若条件不满足，应跳过
	 */
	private static boolean checkConditions(Config config, Path file) {
		Config condition = config.get("condition");
		if (condition == null) {
			return true; // 无条件时默认加载
		}

		// mod_loaded 条件：仅当指定 mod 已加载时才加载
		String modLoaded = condition.get("mod_loaded");
		if (modLoaded != null && !modLoaded.isEmpty()) {
			if (!FabricLoader.getInstance().isModLoaded(modLoaded)) {
				EnchantLib.LOGGER.info("[EnchantLib] 配置 {} 因 mod_loaded 条件不满足跳过加载: {}",
					file.getFileName(), modLoaded);
				return false;
			}
		}

		// mod_not_loaded 条件：仅当指定 mod 未加载时才加载
		String modNotLoaded = condition.get("mod_not_loaded");
		if (modNotLoaded != null && !modNotLoaded.isEmpty()) {
			if (FabricLoader.getInstance().isModLoaded(modNotLoaded)) {
				EnchantLib.LOGGER.info("[EnchantLib] 配置 {} 因 mod_not_loaded 条件不满足跳过加载: {}",
					file.getFileName(), modNotLoaded);
				return false;
			}
		}

		return true;
	}
}
