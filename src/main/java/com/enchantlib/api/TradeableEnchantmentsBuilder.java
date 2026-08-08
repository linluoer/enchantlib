package com.enchantlib.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 可交易附魔构建器。
 *
 * <p>将开发者声明的附魔 ID 列表注入到 {@code #minecraft:tradeable} 附魔标签中。
 * 加入此标签后，原版图书管理员会自动通过 {@code LIBRARIAN_N_EMERALD_AND_BOOK_ENCHANTED_BOOK}
 * 交易（Level 1~4）随机出售这些附魔书。</p>
 *
 * <h2>价格机制</h2>
 * <ul>
 *   <li>基础价格由附魔的 {@code min_cost} / {@code max_cost} 字段决定（原版算法）</li>
 *   <li>稀有附魔可通过加入 {@code #minecraft:double_trade_price} 标签让价格翻倍</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 让自定义附魔可被图书管理员出售
 * TradeableEnchantmentsBuilder.create()
 *     .addEnchantments("mymod:fire_aspect", "mymod:ice_aspect")
 *     .build();
 * }</pre>
 *
 * @since 0.1.0
 */
public final class TradeableEnchantmentsBuilder {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Set<String> enchantments = new LinkedHashSet<>();

	private TradeableEnchantmentsBuilder() {
	}

	/**
	 * 创建构建器实例。
	 *
	 * @return 构建器实例
	 */
	public static TradeableEnchantmentsBuilder create() {
		return new TradeableEnchantmentsBuilder();
	}

	/**
	 * 添加可交易附魔。
	 *
	 * @param enchantmentIds 附魔 ID 列表（如 "mymod:fire_aspect"）
	 * @return this
	 */
	public TradeableEnchantmentsBuilder addEnchantments(String... enchantmentIds) {
		for (String id : enchantmentIds) {
			this.enchantments.add(id);
		}
		return this;
	}

	/**
	 * 添加可交易附魔。
	 *
	 * @param enchantmentIds 附魔 ID 集合
	 * @return this
	 */
	public TradeableEnchantmentsBuilder addEnchantments(Set<String> enchantmentIds) {
		this.enchantments.addAll(enchantmentIds);
		return this;
	}

	/**
	 * 构建不可变的可交易附魔 ID 集合。
	 *
	 * @return 附魔 ID 集合
	 * @throws IllegalStateException 如果未添加任何附魔
	 */
	public Set<String> build() {
		if (this.enchantments.isEmpty()) {
			throw new IllegalStateException("可交易附魔构建器为空（请调用 addEnchantments()）");
		}
		return new LinkedHashSet<>(this.enchantments);
	}

	/**
	 * 生成 {@code data/minecraft/tags/enchantment/tradeable.json} 的字节数组。
	 *
	 * @param enchantments 附魔 ID 集合
	 * @return JSON 字节数组
	 */
	public static byte[] toTagJsonBytes(Set<String> enchantments) {
		JsonObject root = new JsonObject();
		root.addProperty("replace", false);
		JsonArray values = new JsonArray();
		for (String id : enchantments) {
			values.add(id);
		}
		root.add("values", values);
		return GSON.toJson(root).getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}
}
