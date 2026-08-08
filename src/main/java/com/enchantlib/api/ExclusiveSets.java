package com.enchantlib.api;

import java.util.Map;
import java.util.Set;

/**
 * 原版互斥组常量与附魔列表。
 *
 * <p>提供 MC 26.2 所有原版互斥组（{@code exclusive_set}）的标签引用字符串和成员附魔列表，
 * 可直接传入 {@link EnchantmentBuilder#exclusiveSet(String)}。</p>
 *
 * <h2>原版互斥组</h2>
 * <table>
 * <tr><th>常量</th><th>标签引用</th><th>成员附魔</th></tr>
 * <tr><td>{@link #DAMAGE}</td><td>#minecraft:exclusive_set/damage</td><td>sharpness, smite, bane_of_arthropods, impaling, density, breach</td></tr>
 * <tr><td>{@link #ARMOR}</td><td>#minecraft:exclusive_set/armor</td><td>protection, blast_protection, fire_protection, projectile_protection</td></tr>
 * <tr><td>{@link #BOOTS}</td><td>#minecraft:exclusive_set/boots</td><td>frost_walker, depth_strider</td></tr>
 * <tr><td>{@link #BOW}</td><td>#minecraft:exclusive_set/bow</td><td>infinity, mending</td></tr>
 * <tr><td>{@link #CROSSBOW}</td><td>#minecraft:exclusive_set/crossbow</td><td>multishot, piercing</td></tr>
 * <tr><td>{@link #MINING}</td><td>#minecraft:exclusive_set/mining</td><td>fortune, silk_touch</td></tr>
 * <tr><td>{@link #TRIDENT}</td><td>#minecraft:exclusive_set/riptide</td><td>loyalty, channeling</td></tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 让自定义附魔与原版 sharpness/smite 等互斥
 * EnchantmentBuilder.create("mymod:my_damage")
 *     .exclusiveSet(ExclusiveSets.DAMAGE)
 *     ...
 *
 * // 让自定义附魔与原版 fortune/silk_touch 互斥
 * EnchantmentBuilder.create("mymod:my_mining")
 *     .exclusiveSet(ExclusiveSets.MINING)
 *     ...
 * }</pre>
 *
 * <p>原版互斥组定义在 {@code data/minecraft/tags/enchantment/exclusive_set/} 目录下，
 * 由 MC 原生数据包提供，无需 EnchantLib 额外注入。</p>
 *
 * @since 0.1.0
 */
public final class ExclusiveSets {

	private ExclusiveSets() {
	}

	/**
	 * 伤害类附魔互斥组。
	 * <p>成员：sharpness, smite, bane_of_arthropods, impaling, density, breach</p>
	 */
	public static final String DAMAGE = "#minecraft:exclusive_set/damage";

	/**
	 * 保护类附魔互斥组。
	 * <p>成员：protection, blast_protection, fire_protection, projectile_protection</p>
	 */
	public static final String ARMOR = "#minecraft:exclusive_set/armor";

	/**
	 * 靴子类附魔互斥组。
	 * <p>成员：frost_walker, depth_strider</p>
	 */
	public static final String BOOTS = "#minecraft:exclusive_set/boots";

	/**
	 * 弓类附魔互斥组。
	 * <p>成员：infinity, mending</p>
	 */
	public static final String BOW = "#minecraft:exclusive_set/bow";

	/**
	 * 弩类附魔互斥组。
	 * <p>成员：multishot, piercing</p>
	 */
	public static final String CROSSBOW = "#minecraft:exclusive_set/crossbow";

	/**
	 * 挖掘类附魔互斥组。
	 * <p>成员：fortune, silk_touch</p>
	 */
	public static final String MINING = "#minecraft:exclusive_set/mining";

	/**
	 * 三叉戟类附魔互斥组（标签名为 riptide，但实际成员是 loyalty 和 channeling）。
	 * <p>成员：loyalty, channeling</p>
	 */
	public static final String TRIDENT = "#minecraft:exclusive_set/riptide";

	/**
	 * 伤害类附魔互斥组的成员附魔 ID 列表（不含命名空间前缀的简写形式）。
	 * <p>完整 ID：minecraft:sharpness, minecraft:smite, minecraft:bane_of_arthropods,
	 * minecraft:impaling, minecraft:density, minecraft:breach</p>
	 */
	public static final Set<String> DAMAGE_ENCHANTMENTS = Set.of(
		"minecraft:sharpness",
		"minecraft:smite",
		"minecraft:bane_of_arthropods",
		"minecraft:impaling",
		"minecraft:density",
		"minecraft:breach"
	);

	/**
	 * 保护类附魔互斥组的成员附魔 ID 列表。
	 */
	public static final Set<String> ARMOR_ENCHANTMENTS = Set.of(
		"minecraft:protection",
		"minecraft:blast_protection",
		"minecraft:fire_protection",
		"minecraft:projectile_protection"
	);

	/**
	 * 靴子类附魔互斥组的成员附魔 ID 列表。
	 */
	public static final Set<String> BOOTS_ENCHANTMENTS = Set.of(
		"minecraft:frost_walker",
		"minecraft:depth_strider"
	);

	/**
	 * 弓类附魔互斥组的成员附魔 ID 列表。
	 */
	public static final Set<String> BOW_ENCHANTMENTS = Set.of(
		"minecraft:infinity",
		"minecraft:mending"
	);

	/**
	 * 弩类附魔互斥组的成员附魔 ID 列表。
	 */
	public static final Set<String> CROSSBOW_ENCHANTMENTS = Set.of(
		"minecraft:multishot",
		"minecraft:piercing"
	);

	/**
	 * 挖掘类附魔互斥组的成员附魔 ID 列表。
	 */
	public static final Set<String> MINING_ENCHANTMENTS = Set.of(
		"minecraft:fortune",
		"minecraft:silk_touch"
	);

	/**
	 * 三叉戟类附魔互斥组的成员附魔 ID 列表。
	 */
	public static final Set<String> TRIDENT_ENCHANTMENTS = Set.of(
		"minecraft:loyalty",
		"minecraft:channeling"
	);

	/**
	 * 原版互斥组标签引用 → 成员附魔 ID 列表的映射。
	 * <p>用于校验器判断一个附魔是否已属于某个原版互斥组。</p>
	 */
	public static final Map<String, Set<String>> VANILLA_GROUPS = Map.of(
		DAMAGE, DAMAGE_ENCHANTMENTS,
		ARMOR, ARMOR_ENCHANTMENTS,
		BOOTS, BOOTS_ENCHANTMENTS,
		BOW, BOW_ENCHANTMENTS,
		CROSSBOW, CROSSBOW_ENCHANTMENTS,
		MINING, MINING_ENCHANTMENTS,
		TRIDENT, TRIDENT_ENCHANTMENTS
	);

	/**
	 * 判断标签引用是否是原版互斥组。
	 *
	 * @param tagRef 标签引用字符串（如 "#minecraft:exclusive_set/damage"）
	 * @return true 如果是原版互斥组
	 */
	public static boolean isVanillaGroup(String tagRef) {
		return VANILLA_GROUPS.containsKey(tagRef);
	}

	/**
	 * 获取原版互斥组的成员附魔 ID 列表。
	 *
	 * @param tagRef 标签引用字符串（如 "#minecraft:exclusive_set/damage"）
	 * @return 成员附魔 ID 列表，若非原版互斥组返回 null
	 */
	public static Set<String> getVanillaGroupEnchantments(String tagRef) {
		return VANILLA_GROUPS.get(tagRef);
	}
}
