package com.enchantlib.datapack;

import com.enchantlib.EnchantLib;
import com.enchantlib.api.EnchantmentBuilder;
import com.enchantlib.api.ExclusiveGroupBuilder;
import com.enchantlib.api.TradeableEnchantmentsBuilder;
import com.enchantlib.api.VillagerTradeInjection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;

/**
 * 运行时内存数据包内容管理。
 *
 * <p>负责收集内置测试附魔与 entrypoint 注册的自定义附魔，通过
 * {@link RuntimeDatapackBuilder} 构建最终的数据包实例。</p>
 *
 * @since 0.1.0
 */
public class RuntimeDatapackContent {

	/** 内存数据包的唯一 ID */
	public static final String PACK_ID = "enchantlib:runtime";

	/** 内存数据包的显示名称 */
	private static final String PACK_NAME = "EnchantLib Runtime";

	/** 通过 entrypoint 收集的自定义附魔（由 EnchantLib 主类在 onInitialize 中设置） */
	private static List<EnchantmentBuilder> customEnchantments = new ArrayList<>();

	/** 通过 entrypoint 收集的自定义互斥组（由 EnchantLib 主类在 onInitialize 中设置） */
	private static List<ExclusiveGroupBuilder> exclusiveGroups = new ArrayList<>();

	/** 通过 entrypoint 收集的可交易附魔 ID 集合（由 EnchantLib 主类在 onInitialize 中设置） */
	private static Set<String> tradeableEnchantments = new java.util.LinkedHashSet<>();

	/** 通过 entrypoint 收集的自定义村民交易（由 EnchantLib 主类在 onInitialize 中设置） */
	private static List<VillagerTradeInjection> villagerTrades = new ArrayList<>();

	/**
	 * 设置通过 entrypoint 收集的自定义附魔。
	 *
	 * <p>由 {@link EnchantLib#onInitialize()} 调用，在数据包注入前设置。</p>
	 *
	 * @param builders 自定义附魔构建器列表
	 */
	public static void setCustomEnchantments(List<EnchantmentBuilder> builders) {
		customEnchantments = builders != null ? builders : new ArrayList<>();
	}

	/**
	 * 设置通过 entrypoint 收集的自定义互斥组。
	 *
	 * <p>由 {@link EnchantLib#onInitialize()} 调用，在数据包注入前设置。</p>
	 *
	 * @param groups 互斥组构建器列表
	 */
	public static void setExclusiveGroups(List<ExclusiveGroupBuilder> groups) {
		exclusiveGroups = groups != null ? groups : new ArrayList<>();
	}

	/**
	 * 设置通过 entrypoint 收集的可交易附魔和自定义村民交易。
	 *
	 * <p>由 {@link EnchantLib#onInitialize()} 调用，在数据包注入前设置。</p>
	 *
	 * @param tradeableEnchantments 可交易附魔 ID 集合（注入 #minecraft:tradeable tag）
	 * @param villagerTrades 自定义村民交易列表
	 */
	public static void setVillagerTrades(Set<String> tradeableEnchantments, List<VillagerTradeInjection> villagerTrades) {
		RuntimeDatapackContent.tradeableEnchantments = tradeableEnchantments != null
			? new java.util.LinkedHashSet<>(tradeableEnchantments) : new java.util.LinkedHashSet<>();
		RuntimeDatapackContent.villagerTrades = villagerTrades != null ? villagerTrades : new ArrayList<>();
	}

	/**
	 * 构建内存数据包，包含 entrypoint 注册的自定义附魔、互斥组与村民交易注入。
	 *
	 * @return Pack 实例，若元数据读取失败则返回 null
	 */
	public static Pack createPack() {
		RuntimeDatapackBuilder builder = RuntimeDatapackBuilder.create(PACK_ID, PACK_NAME);

		// 添加 entrypoint 收集的自定义附魔
		for (EnchantmentBuilder enchantBuilder : customEnchantments) {
			builder.addEnchantment(enchantBuilder);
			EnchantLib.LOGGER.info("[EnchantLib] 注入自定义附魔: {}", enchantBuilder.getId());
		}

		// 添加 entrypoint 收集的互斥组标签
		for (ExclusiveGroupBuilder groupBuilder : exclusiveGroups) {
			builder.addResource(groupBuilder.getResourceId(), groupBuilder.toBytes());
			EnchantLib.LOGGER.info("[EnchantLib] 注入互斥组标签: {} (含 {} 个附魔)",
				groupBuilder.getTagId(), groupBuilder.getEnchantments().size());
		}

		// 注入 #minecraft:tradeable 附魔标签（让原版图书管理员自动出售）
		if (!tradeableEnchantments.isEmpty()) {
			Identifier tradeableTagId = Identifier.fromNamespaceAndPath(
				"minecraft", "tags/enchantment/tradeable.json");
			builder.addResource(tradeableTagId,
				TradeableEnchantmentsBuilder.toTagJsonBytes(tradeableEnchantments));
			EnchantLib.LOGGER.info("[EnchantLib] 注入可交易附魔标签: #minecraft:tradeable (含 {} 个附魔)",
				tradeableEnchantments.size());
		}

		// 注入自定义村民交易（villager_trade/*.json + 层级 tag）
		// 合并同一职业层级的多个交易到一个 tag JSON
		Map<Identifier, List<Identifier>> tagToTrades = new LinkedHashMap<>();
		for (VillagerTradeInjection injection : villagerTrades) {
			// 注入交易定义 JSON
			builder.addResource(injection.getTradeResourceId(), injection.getTradeJsonBytes());
			EnchantLib.LOGGER.info("[EnchantLib] 注入村民交易: {} ({} level_{}, {}形式, {}个候选附魔)",
				injection.getTradeId(), injection.getProfession(), injection.getLevel(),
				injection.getForm(), injection.getEnchantments().size());

			// 收集层级 tag 归属（同一 tag 多个交易需合并）
			tagToTrades.computeIfAbsent(injection.getTagResourceId(), k -> new ArrayList<>())
				.add(injection.getTradeId());
		}

		// 注入层级 tag JSON（合并同一 tag 的多个交易）
		for (Map.Entry<Identifier, List<Identifier>> entry : tagToTrades.entrySet()) {
			builder.addResource(entry.getKey(), buildMergedTagJsonBytes(entry.getValue()));
		}

		return builder.build();
	}

	/** 构建合并多个交易 ID 的层级 tag JSON */
	private static byte[] buildMergedTagJsonBytes(List<Identifier> tradeIds) {
		com.google.gson.JsonObject root = new com.google.gson.JsonObject();
		root.addProperty("replace", false);
		com.google.gson.JsonArray values = new com.google.gson.JsonArray();
		for (Identifier id : tradeIds) {
			values.add(id.toString());
		}
		root.add("values", values);
		return new com.google.gson.GsonBuilder().setPrettyPrinting().create()
			.toJson(root).getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}
}
