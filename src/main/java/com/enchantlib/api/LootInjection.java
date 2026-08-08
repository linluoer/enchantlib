package com.enchantlib.api;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Item;

/**
 * 战利品注入规则（不可变）。
 *
 * <p>描述一条注入规则：将指定附魔以某种物品形式（附魔书/已附魔物品），
 * 按指定概率注入到一组战利品表中。附魔从候选列表中随机选择，
 * 等级在附魔的 minLevel~maxLevel 范围内随机生成。</p>
 *
 * <p>由 {@link LootInjectionBuilder#build()} 创建，传递给
 * {@link LootInjectionRegistrar#register(LootInjectionBuilder)} 注册。</p>
 *
 * @since 0.1.0
 * @see LootInjectionBuilder
 */
public final class LootInjection {

	/** 物品形式：附魔书 / 已附魔物品 */
	public enum Form {
		/** 附魔书形式（Items.ENCHANTED_BOOK + STORED_ENCHANTMENTS） */
		BOOK,
		/** 已附魔物品形式（指定物品 + ENCHANTMENTS） */
		ITEM
	}

	private final Set<String> targetTables;
	private final Form form;
	private final Item item;
	private final Set<String> enchantments;
	private final float chance;
	private final int weight;
	private final int quality;

	LootInjection(Set<String> targetTables, Form form, Item item,
			Set<String> enchantments, float chance, int weight, int quality) {
		this.targetTables = Collections.unmodifiableSet(targetTables);
		this.form = form;
		this.item = item;
		this.enchantments = Collections.unmodifiableSet(enchantments);
		this.chance = chance;
		this.weight = weight;
		this.quality = quality;
	}

	/**
	 * 获取目标战利品表 ID 列表。
	 *
	 * @return 战利品表 ID 列表（不可修改），如 ["minecraft:chests/simple_dungeon"]
	 */
	public Set<String> getTargetTables() {
		return this.targetTables;
	}

	/**
	 * 获取物品形式。
	 *
	 * @return 物品形式（BOOK 或 ITEM）
	 */
	public Form getForm() {
		return this.form;
	}

	/**
	 * 获取目标物品（仅当 form == ITEM 时有效）。
	 *
	 * @return 目标物品，若 form == BOOK 返回 null
	 */
	public Item getItem() {
		return this.item;
	}

	/**
	 * 获取候选附魔 ID 列表。
	 *
	 * @return 附魔 ID 列表（不可修改），如 ["mymod:fire_aspect", "mymod:ice_aspect"]
	 */
	public Set<String> getEnchantments() {
		return this.enchantments;
	}

	/**
	 * 获取注入概率。
	 *
	 * @return 概率值（0.0~1.0），1.0 表示必定注入
	 */
	public float getChance() {
		return this.chance;
	}

	/**
	 * 获取战利品条目权重。
	 *
	 * @return 权重（默认 1）
	 */
	public int getWeight() {
		return this.weight;
	}

	/**
	 * 获取战利品条目质量（影响 luck 属性下的权重）。
	 *
	 * @return 质量（默认 0）
	 */
	public int getQuality() {
		return this.quality;
	}
}
