package com.enchantlib.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 村民交易注册器。
 *
 * <p>负责收集 entrypoint 注册的两类内容：</p>
 * <ul>
 *   <li>{@link TradeableEnchantmentsBuilder}：声明附魔可被原版图书管理员自动出售（注入 #minecraft:tradeable tag）</li>
 *   <li>{@link VillagerTradeBuilder}：注册完全自定义的村民交易（指定职业/层级/价格/物品）</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * public class MyModEnchantments implements EnchantmentEntrypoint {
 *     @Override
 *     public void onRegisterVillagerTrades(VillagerTradeRegistrar registrar) {
 *         // 让自定义附魔可被图书管理员出售
 *         registrar.registerTradeableEnchantments(
 *             TradeableEnchantmentsBuilder.create()
 *                 .addEnchantments("mymod:fire_aspect", "mymod:ice_aspect")
 *         );
 *
 *         // 注册自定义交易：武器匠 Level 3 出售附魔钻石剑
 *         registrar.registerTrade(VillagerTradeBuilder
 *             .create("mymod:weaponsmith/3/sharpness_sword")
 *             .profession(VillagerTrades.WEAPONSMITH)
 *             .level(VillagerTrades.LEVEL_3)
 *             .asItem(Items.DIAMOND_SWORD)
 *             .withEnchantments("mymod:sharpness_plus")
 *             .emeralds(20));
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public final class VillagerTradeRegistrar {

	private final Set<String> tradeableEnchantments = new LinkedHashSet<>();
	private final List<VillagerTradeInjection> trades = new ArrayList<>();

	public VillagerTradeRegistrar() {
	}

	/**
	 * 注册可交易附魔（让原版图书管理员自动出售）。
	 *
	 * @param builder 可交易附魔构建器
	 * @return this
	 */
	public VillagerTradeRegistrar registerTradeableEnchantments(TradeableEnchantmentsBuilder builder) {
		this.tradeableEnchantments.addAll(builder.build());
		return this;
	}

	/**
	 * 直接添加可交易附魔 ID 集合（跳过构建器，内部使用）。
	 *
	 * @param enchantmentIds 附魔 ID 集合
	 * @return this
	 */
	public VillagerTradeRegistrar addTradeableEnchantments(Set<String> enchantmentIds) {
		this.tradeableEnchantments.addAll(enchantmentIds);
		return this;
	}

	/**
	 * 注册自定义村民交易。
	 *
	 * @param builder 交易构建器（自动调用 {@link VillagerTradeBuilder#build()}）
	 * @return this
	 */
	public VillagerTradeRegistrar registerTrade(VillagerTradeBuilder builder) {
		VillagerTradeInjection injection = builder.build();
		// 检测重复 trade ID
		for (VillagerTradeInjection existing : trades) {
			if (existing.getTradeId().equals(injection.getTradeId())) {
				throw new IllegalStateException("重复的村民交易 ID: " + injection.getTradeId());
			}
		}
		this.trades.add(injection);
		return this;
	}

	/**
	 * 直接注册已构建的村民交易。
	 *
	 * @param injection 已构建的交易规则
	 * @return this
	 */
	public VillagerTradeRegistrar registerTrade(VillagerTradeInjection injection) {
		for (VillagerTradeInjection existing : trades) {
			if (existing.getTradeId().equals(injection.getTradeId())) {
				throw new IllegalStateException("重复的村民交易 ID: " + injection.getTradeId());
			}
		}
		this.trades.add(injection);
		return this;
	}

	/**
	 * 获取可交易附魔 ID 集合（不可变）。
	 *
	 * @return 附魔 ID 集合
	 */
	public Set<String> getTradeableEnchantments() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(tradeableEnchantments));
	}

	/**
	 * 获取已注册的村民交易列表（不可变）。
	 *
	 * @return 交易列表
	 */
	public List<VillagerTradeInjection> getTrades() {
		return Collections.unmodifiableList(new ArrayList<>(trades));
	}

	/**
	 * 获取可交易附魔数量。
	 *
	 * @return 数量
	 */
	public int tradeableEnchantmentsCount() {
		return tradeableEnchantments.size();
	}

	/**
	 * 获取自定义交易数量。
	 *
	 * @return 数量
	 */
	public int tradesCount() {
		return trades.size();
	}

	/**
	 * 判断注册器是否为空（无 tradeable 附魔也无自定义交易）。
	 *
	 * @return true 表示空
	 */
	public boolean isEmpty() {
		return tradeableEnchantments.isEmpty() && trades.isEmpty();
	}
}
