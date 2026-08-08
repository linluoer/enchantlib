package com.enchantlib.loot;

import com.enchantlib.EnchantLib;
import com.enchantlib.api.LootInjection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

/**
 * 战利品注入处理器。
 *
 * <p>注册 {@link LootTableEvents#MODIFY} 事件监听器，在战利品表加载时根据
 * {@link LootInjection} 规则向指定战利品表添加附魔物品条目。</p>
 *
 * <h2>注入流程</h2>
 * <ol>
 *   <li>EnchantLib 主类在 {@code onInitialize} 中收集 entrypoint 注册的注入规则</li>
 *   <li>调用 {@link #initialize(List)} 构建"战利品表 ID → 注入规则列表"映射</li>
 *   <li>注册 {@code LootTableEvents.MODIFY} 监听器</li>
 *   <li>战利品表加载时，根据表 ID 查找映射，命中则向 {@link LootTable.Builder} 添加对应 {@link LootPool}</li>
 * </ol>
 *
 * <h2>LootPool 构建</h2>
 * <p>每条注入规则构建一个独立的 LootPool：</p>
 * <ul>
 *   <li>条目：{@link Items#BOOK}（附魔书形式）或目标物品（已附魔物品形式）</li>
 *   <li>函数：{@link EnchantRandomlyFunction}（从候选附魔中随机选一个，等级在 minLevel~maxLevel 范围内随机）</li>
 *   <li>条件：{@link LootItemRandomChanceCondition}（若 chance < 1.0）</li>
 *   <li>rolls：固定 1 次</li>
 * </ul>
 *
 * <p>注：附魔书形式使用 {@link Items#BOOK} 而非 {@link Items#ENCHANTED_BOOK}，
 * 因为 {@link EnchantRandomlyFunction#enchantItem} 会自动将 BOOK 转换为 ENCHANTED_BOOK
 * 并应用附魔到 STORED_ENCHANTMENTS 组件。</p>
 *
 * @since 0.1.0
 */
public final class LootInjectionHandler {

	private static Map<String, List<LootInjection>> tableToInjections = Collections.emptyMap();
	private static boolean registered = false;

	private LootInjectionHandler() {
	}

	/**
	 * 初始化注入处理器。
	 *
	 * <p>构建"战利品表 ID → 注入规则列表"映射，并注册 {@code LootTableEvents.MODIFY} 监听器。
	 * 此方法应在 EnchantLib 主类的 {@code onInitialize} 中、entrypoint 收集完成后调用。</p>
	 *
	 * @param injections 所有已注册的注入规则列表
	 */
	public static synchronized void initialize(List<LootInjection> injections) {
		if (registered) {
			EnchantLib.LOGGER.warn("[EnchantLib] 战利品注入处理器已初始化，跳过重复初始化");
			return;
		}

		// 构建战利品表 ID → 注入规则列表 映射
		Map<String, List<LootInjection>> map = new HashMap<>();
		for (LootInjection injection : injections) {
			for (String tableId : injection.getTargetTables()) {
				map.computeIfAbsent(tableId, k -> new ArrayList<>()).add(injection);
			}
		}
		tableToInjections = Collections.unmodifiableMap(map);

		// 注册 LootTableEvents.MODIFY 监听器
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			String tableIdStr = key.identifier().toString();
			List<LootInjection> matching = tableToInjections.get(tableIdStr);
			if (matching == null || matching.isEmpty()) {
				return;
			}
			for (LootInjection injection : matching) {
				LootPool.Builder poolBuilder = buildPool(injection, registries);
				if (poolBuilder != null) {
					tableBuilder.withPool(poolBuilder);
					EnchantLib.LOGGER.info("[EnchantLib] 向战利品表 {} 注入附魔条目（{}形式，{}个候选附魔）",
						tableIdStr, injection.getForm(), injection.getEnchantments().size());
				}
			}
		});

		registered = true;
		int totalTables = tableToInjections.size();
		int totalInjections = injections.size();
		EnchantLib.LOGGER.info("[EnchantLib] 战利品注入处理器已注册: {} 条规则，覆盖 {} 个战利品表",
			totalInjections, totalTables);
	}

	/**
	 * 根据注入规则构建一个 {@link LootPool.Builder}。
	 *
	 * <p>若候选附魔全部无法解析（ID 不存在于注册表），返回 null 跳过此注入。</p>
	 *
	 * @param injection 注入规则
	 * @param registries 注册表访问器
	 * @return 构建好的 LootPool.Builder，若无法构建返回 null
	 */
	private static LootPool.Builder buildPool(LootInjection injection, HolderLookup.Provider registries) {
		// 解析候选附魔 ID 为 Holder<Enchantment>
		List<Holder<Enchantment>> resolvedEnchantments = resolveEnchantments(injection, registries);
		if (resolvedEnchantments.isEmpty()) {
			EnchantLib.LOGGER.warn("[EnchantLib] 战利品注入规则无可解析的候选附魔，跳过注入: {}", injection.getEnchantments());
			return null;
		}

		// 构建 HolderSet
		HolderSet<Enchantment> holderSet = HolderSet.direct(resolvedEnchantments);

		// 确定物品形式
		Item item;
		if (injection.getForm() == LootInjection.Form.BOOK) {
			item = Items.BOOK;  // EnchantRandomlyFunction 会自动转换为 ENCHANTED_BOOK
		} else {
			item = injection.getItem();
		}

		// 构建 LootPool
		LootPool.Builder poolBuilder = LootPool.lootPool()
			.setRolls(ConstantValue.exactly(1.0F))
			.add(LootItem.lootTableItem(item)
				.setWeight(injection.getWeight())
				.setQuality(injection.getQuality())
				.apply(EnchantRandomlyFunction.randomEnchantment().withOneOf(holderSet)));

		// 概率条件（chance < 1.0 时添加）
		if (injection.getChance() < 1.0F) {
			LootItemCondition.Builder chanceCondition = LootItemRandomChanceCondition.randomChance(injection.getChance());
			poolBuilder.when(chanceCondition);
		}

		return poolBuilder;
	}

	/**
	 * 解析候选附魔 ID 列表为 {@link Holder<Enchantment>} 列表。
	 *
	 * <p>无法解析的 ID 会记录警告日志并跳过。</p>
	 *
	 * @param injection 注入规则
	 * @param registries 注册表访问器
	 * @return 解析成功的 Holder 列表（可能为空）
	 */
	private static List<Holder<Enchantment>> resolveEnchantments(LootInjection injection, HolderLookup.Provider registries) {
		Optional<? extends HolderLookup.RegistryLookup<Enchantment>> registryOpt = registries.lookup(Registries.ENCHANTMENT);
		if (registryOpt.isEmpty()) {
			EnchantLib.LOGGER.error("[EnchantLib] 无法访问附魔注册表，跳过所有候选附魔解析");
			return Collections.emptyList();
		}
		HolderLookup.RegistryLookup<Enchantment> registry = registryOpt.get();

		List<Holder<Enchantment>> result = new ArrayList<>();
		for (String enchantId : injection.getEnchantments()) {
			try {
				Identifier id = Identifier.parse(enchantId);
				ResourceKey<Enchantment> resourceKey = ResourceKey.create(Registries.ENCHANTMENT, id);
				Optional<Holder.Reference<Enchantment>> holderOpt = registry.get(resourceKey);
				if (holderOpt.isPresent()) {
					result.add(holderOpt.get());
				} else {
					EnchantLib.LOGGER.warn("[EnchantLib] 战利品注入规则引用了不存在的附魔 ID: {}（将被跳过）", enchantId);
				}
			} catch (Exception e) {
				EnchantLib.LOGGER.warn("[EnchantLib] 战利品注入规则包含无效的附魔 ID: {}（{}）", enchantId, e.getMessage());
			}
		}
		return result;
	}
}
