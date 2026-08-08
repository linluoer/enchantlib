package com.enchantlib.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * 链式构建附魔 JSON 定义。
 *
 * <p>用于让其他模组方便地构建附魔 JSON，注入到运行时数据包。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * EnchantmentBuilder.create("mymod:my_enchant")
 *     .description("My Enchantment")
 *     .supportedItems("#minecraft:enchantable/sharp_weapon")
 *     .weight(10)
 *     .maxLevel(5)
 *     .minCost(1, 11)
 *     .maxCost(21, 11)
 *     .anvilCost(1)
 *     .slots("mainhand")
 *     .effects(EnchantmentEffectsBuilder.create()
 *         .addDamage(1.0, 0.5))
 *     .toJson();
 * }</pre>
 *
 * @since 0.1.0
 */
public class EnchantmentBuilder {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Identifier id;
	private String descriptionKey;
	private String descriptionFallback;
	private String supportedItems;
	private String primaryItems;
	private int weight = 1;
	private int maxLevel = 1;
	private int minCostBase = 1;
	private int minCostPerLevel = 1;
	private int maxCostBase = 1;
	private int maxCostPerLevel = 1;
	private int anvilCost = 1;
	private final List<String> slots = new ArrayList<>();
	private String exclusiveSet;
	private JsonObject effects;
	/** 单附魔获取途径开关（仅对配置文件定义的附魔生效，默认 false） */
	private boolean acquisitionLoot = false;
	private boolean acquisitionTrade = false;

	private EnchantmentBuilder(Identifier id) {
		this.id = id;
		// 默认 description key: enchantment.<namespace>.<path>
		this.descriptionKey = "enchantment." + id.getNamespace() + "." + id.getPath();
	}

	/**
	 * 创建构建器。
	 *
	 * @param id 附魔 ID，格式 "modid:name"
	 * @return 构建器实例
	 */
	public static EnchantmentBuilder create(String id) {
		return create(Identifier.parse(id));
	}

	/**
	 * 创建构建器。
	 *
	 * @param id 附魔 ID
	 * @return 构建器实例
	 */
	public static EnchantmentBuilder create(Identifier id) {
		return new EnchantmentBuilder(id);
	}

	/**
	 * 设置附魔描述。fallback 文本将作为裸客户端显示的兜底名称。
	 *
	 * <p>description key 自动生成为 {@code enchantment.<namespace>.<path>}。</p>
	 *
	 * @param fallback 兜底显示文本
	 * @return this
	 */
	public EnchantmentBuilder description(String fallback) {
		this.descriptionFallback = fallback;
		return this;
	}

	/**
	 * 设置附魔描述，同时指定 translate key 和 fallback。
	 *
	 * @param key 翻译键（如 "enchantment.mymod.my_enchant"）
	 * @param fallback 兜底显示文本
	 * @return this
	 */
	public EnchantmentBuilder description(String key, String fallback) {
		this.descriptionKey = key;
		this.descriptionFallback = fallback;
		return this;
	}

	/**
	 * 设置支持物品（物品可附魔）。支持标签（"#minecraft:..."）或物品 ID（"minecraft:iron_sword"）。
	 *
	 * @param tagOrItem 标签或物品 ID
	 * @return this
	 */
	public EnchantmentBuilder supportedItems(String tagOrItem) {
		this.supportedItems = tagOrItem;
		return this;
	}

	/**
	 * 设置主要物品（附魔台会优先为此类物品附魔）。可选。
	 *
	 * @param tagOrItem 标签或物品 ID
	 * @return this
	 */
	public EnchantmentBuilder primaryItems(String tagOrItem) {
		this.primaryItems = tagOrItem;
		return this;
	}

	/**
	 * 设置附魔权重（影响附魔台出现概率）。1~15。
	 *
	 * @param weight 权重
	 * @return this
	 */
	public EnchantmentBuilder weight(int weight) {
		this.weight = weight;
		return this;
	}

	/**
	 * 设置最大附魔等级。
	 *
	 * @param maxLevel 最大等级
	 * @return this
	 */
	public EnchantmentBuilder maxLevel(int maxLevel) {
		this.maxLevel = maxLevel;
		return this;
	}

	/**
	 * 设置最小附魔成本。base 为基础等级需求，perLevelAboveFirst 为每级递增。
	 *
	 * @param base 基础需求
	 * @param perLevelAboveFirst 每级递增
	 * @return this
	 */
	public EnchantmentBuilder minCost(int base, int perLevelAboveFirst) {
		this.minCostBase = base;
		this.minCostPerLevel = perLevelAboveFirst;
		return this;
	}

	/**
	 * 设置最大附魔成本。base 为基础等级需求，perLevelAboveFirst 为每级递增。
	 *
	 * @param base 基础需求
	 * @param perLevelAboveFirst 每级递增
	 * @return this
	 */
	public EnchantmentBuilder maxCost(int base, int perLevelAboveFirst) {
		this.maxCostBase = base;
		this.maxCostPerLevel = perLevelAboveFirst;
		return this;
	}

	/**
	 * 设置铁砧修复成本（经验等级）。
	 *
	 * @param anvilCost 铁砧成本
	 * @return this
	 */
	public EnchantmentBuilder anvilCost(int anvilCost) {
		this.anvilCost = anvilCost;
		return this;
	}

	/**
	 * 设置生效槽位。可选值：mainhand、offhand、armor、feet、legs、chest、head、body。
	 *
	 * @param slots 槽位列表
	 * @return this
	 */
	public EnchantmentBuilder slots(String... slots) {
		this.slots.clear();
		for (String s : slots) {
			this.slots.add(s);
		}
		return this;
	}

	/**
	 * 添加生效槽位。
	 *
	 * @param slot 槽位
	 * @return this
	 */
	public EnchantmentBuilder addSlot(String slot) {
		this.slots.add(slot);
		return this;
	}

	/**
	 * 设置互斥组（标签）。同一互斥组的附魔不可共存。可选。
	 *
	 * @param tag 互斥组标签（如 "#minecraft:exclusive_set/damage"）
	 * @return this
	 */
	public EnchantmentBuilder exclusiveSet(String tag) {
		this.exclusiveSet = tag;
		return this;
	}

	/**
	 * 设置附魔效果（直接传入 JSON）。
	 *
	 * <p>用于高级用户完全自定义 effects。格式示例：</p>
	 * <pre>{@code
	 * {
	 *   "minecraft:damage": [
	 *     { "effect": { "type": "minecraft:add", "value": { "type": "minecraft:linear", "base": 1.0, "per_level_above_first": 0.5 } } }
	 *   ]
	 * }
	 * }</pre>
	 *
	 * @param effects effects JSON 对象
	 * @return this
	 */
	public EnchantmentBuilder effects(JsonObject effects) {
		this.effects = effects;
		return this;
	}

	/**
	 * 设置附魔效果（通过 EffectsBuilder 构建）。
	 *
	 * @param builder effects builder
	 * @return this
	 */
	public EnchantmentBuilder effects(EnchantmentEffectsBuilder builder) {
		this.effects = builder.build();
		return this;
	}

	/**
	 * 获取附魔 ID。
	 *
	 * @return ID
	 */
	public Identifier getId() {
		return this.id;
	}

	/**
	 * 获取附魔描述兜底文本（裸客户端显示用）。
	 *
	 * @return 兜底文本，未设置时返回 null
	 */
	public String getDescriptionFallback() {
		return this.descriptionFallback;
	}

	/**
	 * 获取附魔描述翻译键。
	 *
	 * @return 翻译键（如 "enchantment.mymod.my_enchant"）
	 */
	public String getDescriptionKey() {
		return this.descriptionKey;
	}

	/**
	 * 获取支持物品标签/ID。
	 *
	 * @return 支持物品（如 "#minecraft:enchantable/sharp_weapon"）
	 */
	public String getSupportedItems() {
		return this.supportedItems;
	}

	/**
	 * 获取主要物品标签/ID。
	 *
	 * @return 主要物品，未设置时返回 null
	 */
	public String getPrimaryItems() {
		return this.primaryItems;
	}

	/**
	 * 获取附魔权重。
	 *
	 * @return 权重（1~15）
	 */
	public int getWeight() {
		return this.weight;
	}

	/**
	 * 获取最大附魔等级。
	 *
	 * @return 最大等级
	 */
	public int getMaxLevel() {
		return this.maxLevel;
	}

	/**
	 * 获取铁砧修复成本。
	 *
	 * @return 铁砧成本
	 */
	public int getAnvilCost() {
		return this.anvilCost;
	}

	/**
	 * 获取生效槽位列表。
	 *
	 * @return 槽位列表（不可修改）
	 */
	public List<String> getSlots() {
		return Collections.unmodifiableList(this.slots);
	}

	/**
	 * 获取互斥组标签引用。
	 *
	 * @return 互斥组标签引用字符串（如 "#minecraft:exclusive_set/damage"），未设置时返回 null
	 */
	public String getExclusiveSet() {
		return this.exclusiveSet;
	}

	/**
	 * 设置单附魔获取途径开关。
	 *
	 * <p>仅对配置文件定义的附魔生效。开启后，EnchantLib 会自动为该附魔注册对应的获取途径：</p>
	 * <ul>
	 *   <li>{@code loot=true}：自动注册战利品注入（注入到 simple_dungeon、abandoned_mineshaft 等箱子）</li>
	 *   <li>{@code trade=true}：自动将该附魔加入 {@code #minecraft:tradeable} 标签</li>
	 * </ul>
	 * <p>注意：仅在全局开关开启时生效（参见 {@link com.enchantlib.config.AcquisitionConfig}）。</p>
	 *
	 * @param loot 是否可通过战利品获取
	 * @param trade 是否可通过村民交易获取
	 * @return this
	 */
	public EnchantmentBuilder acquisition(boolean loot, boolean trade) {
		this.acquisitionLoot = loot;
		this.acquisitionTrade = trade;
		return this;
	}

	/**
	 * 是否启用了战利品获取途径（单附魔开关）。
	 *
	 * @return true 若启用
	 */
	public boolean isAcquisitionLoot() {
		return acquisitionLoot;
	}

	/**
	 * 是否启用了村民交易获取途径（单附魔开关）。
	 *
	 * @return true 若启用
	 */
	public boolean isAcquisitionTrade() {
		return acquisitionTrade;
	}

	/**
	 * 构建 JSON 字符串。
	 *
	 * @return 附魔 JSON
	 */
	public String toJson() {
		Map<String, Object> root = new LinkedHashMap<>();

		// description：使用 translate + fallback，裸客户端显示 fallback
		Map<String, Object> desc = new LinkedHashMap<>();
		desc.put("translate", this.descriptionKey);
		desc.put("fallback", this.descriptionFallback);
		root.put("description", desc);

		// supported_items（必填）
		root.put("supported_items", this.supportedItems);

		// primary_items（可选）
		if (this.primaryItems != null) {
			root.put("primary_items", this.primaryItems);
		}

		// weight
		root.put("weight", this.weight);

		// max_level
		root.put("max_level", this.maxLevel);

		// min_cost
		Map<String, Object> minCost = new LinkedHashMap<>();
		minCost.put("base", this.minCostBase);
		minCost.put("per_level_above_first", this.minCostPerLevel);
		root.put("min_cost", minCost);

		// max_cost
		Map<String, Object> maxCost = new LinkedHashMap<>();
		maxCost.put("base", this.maxCostBase);
		maxCost.put("per_level_above_first", this.maxCostPerLevel);
		root.put("max_cost", maxCost);

		// anvil_cost
		root.put("anvil_cost", this.anvilCost);

		// slots
		root.put("slots", this.slots.isEmpty() ? List.of("mainhand") : this.slots);

		// exclusive_set（可选）
		if (this.exclusiveSet != null) {
			root.put("exclusive_set", this.exclusiveSet);
		}

		// effects（可选）：直接放入 JsonObject，Gson 会正确序列化
		if (this.effects != null) {
			root.put("effects", this.effects);
		}

		return GSON.toJson(root);
	}

	/**
	 * 构建 JSON 字节数组（供 {@link com.enchantlib.datapack.InMemoryPackResources} 使用）。
	 *
	 * @return 附魔 JSON 字节
	 */
	public byte[] toBytes() {
		return toJson().getBytes(StandardCharsets.UTF_8);
	}
}
