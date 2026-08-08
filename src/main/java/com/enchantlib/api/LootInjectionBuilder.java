package com.enchantlib.api;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * 战利品注入规则构建器。
 *
 * <p>链式构建一条 {@link LootInjection} 规则：将指定附魔以附魔书或已附魔物品形式，
 * 按指定概率注入到一组战利品表中。</p>
 *
 * <h2>物品形式</h2>
 * <ul>
 *   <li>{@link #asBook()}（默认）：注入附魔书（Items.ENCHANTED_BOOK + STORED_ENCHANTMENTS）</li>
 *   <li>{@link #asItem(Item)}：注入已附魔的指定物品（如附魔剑、附魔镐）</li>
 * </ul>
 *
 * <h2>附魔选择机制</h2>
 * <p>从候选附魔列表中随机选择一个，等级在附魔的 minLevel~maxLevel 范围内随机生成。
 * 复用原版 {@code EnchantRandomlyFunction} 实现。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例1：向地下城箱子注入附魔书
 * LootInjectionBuilder.create()
 *     .toTables(LootTables.SIMPLE_DUNGEON, LootTables.ABANDONED_MINESHAFT)
 *     .asBook()
 *     .withEnchantments("mymod:fire_aspect", "mymod:ice_aspect")
 *     .chance(0.5F)
 *     .weight(1)
 *     .quality(0);
 *
 * // 示例2：向末地城注入附魔钻石剑
 * LootInjectionBuilder.create()
 *     .toTables(LootTables.END_CITY_TREASURE)
 *     .asItem(Items.DIAMOND_SWORD)
 *     .withEnchantments("mymod:sharpness_plus")
 *     .chance(0.3F);
 * }</pre>
 *
 * @since 0.1.0
 */
public final class LootInjectionBuilder {

	private final Set<String> targetTables = new LinkedHashSet<>();
	private LootInjection.Form form = LootInjection.Form.BOOK;
	private Item item = null;
	private final Set<String> enchantments = new LinkedHashSet<>();
	private float chance = 1.0F;
	private int weight = 1;
	private int quality = 0;

	private LootInjectionBuilder() {
	}

	/**
	 * 创建构建器实例。
	 *
	 * @return 构建器实例
	 */
	public static LootInjectionBuilder create() {
		return new LootInjectionBuilder();
	}

	/**
	 * 指定目标战利品表列表。
	 *
	 * @param lootTableIds 战利品表 ID 列表（如 "minecraft:chests/simple_dungeon"）
	 * @return this
	 */
	public LootInjectionBuilder toTables(String... lootTableIds) {
		for (String id : lootTableIds) {
			this.targetTables.add(id);
		}
		return this;
	}

	/**
	 * 指定目标战利品表列表。
	 *
	 * @param lootTableIds 战利品表 ID 列表
	 * @return this
	 */
	public LootInjectionBuilder toTables(Set<String> lootTableIds) {
		this.targetTables.addAll(lootTableIds);
		return this;
	}

	/**
	 * 设置物品形式为附魔书（默认）。
	 *
	 * <p>注入 Items.ENCHANTED_BOOK，附魔存储在 STORED_ENCHANTMENTS 组件中。</p>
	 *
	 * @return this
	 */
	public LootInjectionBuilder asBook() {
		this.form = LootInjection.Form.BOOK;
		this.item = null;
		return this;
	}

	/**
	 * 设置物品形式为已附魔物品。
	 *
	 * <p>注入指定物品，附魔存储在 ENCHANTMENTS 组件中。
	 * 物品必须支持 ENCHANTMENTS 组件（如剑、镐、盔甲等）。</p>
	 *
	 * @param item 目标物品（如 Items.DIAMOND_SWORD）
	 * @return this
	 */
	public LootInjectionBuilder asItem(Item item) {
		this.form = LootInjection.Form.ITEM;
		this.item = item;
		return this;
	}

	/**
	 * 设置物品形式为已附魔物品。
	 *
	 * @param item 目标物品（如 Items.DIAMOND_SWORD）
	 * @return this
	 * @see #asItem(Item)
	 */
	public LootInjectionBuilder asItem(net.minecraft.world.level.ItemLike item) {
		return asItem(item.asItem());
	}

	/**
	 * 添加候选附魔。
	 *
	 * @param enchantmentIds 附魔 ID 列表（如 "mymod:fire_aspect"）
	 * @return this
	 */
	public LootInjectionBuilder withEnchantments(String... enchantmentIds) {
		for (String id : enchantmentIds) {
			this.enchantments.add(id);
		}
		return this;
	}

	/**
	 * 添加候选附魔。
	 *
	 * @param enchantmentIds 附魔 ID 列表
	 * @return this
	 */
	public LootInjectionBuilder withEnchantments(Set<String> enchantmentIds) {
		this.enchantments.addAll(enchantmentIds);
		return this;
	}

	/**
	 * 设置注入概率。
	 *
	 * @param chance 概率值（0.0~1.0），1.0 表示必定注入
	 * @return this
	 */
	public LootInjectionBuilder chance(float chance) {
		if (chance < 0.0F || chance > 1.0F) {
			throw new IllegalArgumentException("概率必须在 0.0~1.0 之间: " + chance);
		}
		this.chance = chance;
		return this;
	}

	/**
	 * 设置战利品条目权重（影响在池内的选中概率）。
	 *
	 * @param weight 权重（默认 1）
	 * @return this
	 */
	public LootInjectionBuilder weight(int weight) {
		if (weight < 1) {
			throw new IllegalArgumentException("权重必须 >= 1: " + weight);
		}
		this.weight = weight;
		return this;
	}

	/**
	 * 设置战利品条目质量（影响 luck 属性下的权重加成）。
	 *
	 * @param quality 质量（默认 0）
	 * @return this
	 */
	public LootInjectionBuilder quality(int quality) {
		this.quality = quality;
		return this;
	}

	/**
	 * 构建不可变的 {@link LootInjection} 实例。
	 *
	 * @return 注入规则实例
	 * @throws IllegalStateException 如果缺少必填字段或字段值无效
	 */
	public LootInjection build() {
		validate();
		return new LootInjection(
			new LinkedHashSet<>(this.targetTables),
			this.form,
			this.form == LootInjection.Form.ITEM ? this.item : null,
			new LinkedHashSet<>(this.enchantments),
			this.chance,
			this.weight,
			this.quality
		);
	}

	private void validate() {
		if (this.targetTables.isEmpty()) {
			throw new IllegalStateException("战利品注入规则缺少目标战利品表（请调用 toTables()）");
		}
		if (this.enchantments.isEmpty()) {
			throw new IllegalStateException("战利品注入规则缺少候选附魔（请调用 withEnchantments()）");
		}
		if (this.form == LootInjection.Form.ITEM && this.item == null) {
			throw new IllegalStateException("战利品注入规则为 ITEM 形式但未指定物品（请调用 asItem()）");
		}
	}
}
