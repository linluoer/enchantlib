package com.enchantlib.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 构建附魔效果（effects）JSON。
 *
 * <p>提供常见附魔效果的便捷方法，同时支持通用 effect 添加。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * EnchantmentEffectsBuilder.create()
 *     .addDamage(1.0, 0.5)                    // 简单线性伤害
 *     .addDamage(2.5, 2.5, smiteCondition)    // 带条件的伤害（如亡灵杀手）
 *     .addKnockback(1.0, 1.0)                 // 击退
 * }</pre>
 *
 * @since 0.1.0
 */
public class EnchantmentEffectsBuilder {

	private final Map<String, JsonArray> effects = new LinkedHashMap<>();

	private EnchantmentEffectsBuilder() {
	}

	/**
	 * 创建构建器。
	 *
	 * @return 构建器实例
	 */
	public static EnchantmentEffectsBuilder create() {
		return new EnchantmentEffectsBuilder();
	}

	/**
	 * 添加线性伤害效果（{@code minecraft:damage}）。
	 *
	 * <p>等价于 sharpness 的伤害效果：每级增加 {@code perLevelAboveFirst} 点伤害。</p>
	 *
	 * @param base 1 级时的基础伤害
	 * @param perLevelAboveFirst 每级递增量
	 * @return this
	 */
	public EnchantmentEffectsBuilder addDamage(double base, double perLevelAboveFirst) {
		return addDamage(base, perLevelAboveFirst, null);
	}

	/**
	 * 添加带条件的线性伤害效果（{@code minecraft:damage}）。
	 *
	 * <p>条件示例（亡灵杀手）：{@code {"condition":"minecraft:entity_properties","entity":"this","predicate":{"minecraft:entity_type":"#minecraft:sensitive_to_smite"}}}</p>
	 *
	 * @param base 1 级时的基础伤害
	 * @param perLevelAboveFirst 每级递增量
	 * @param requirements 触发条件 JSON，null 表示无条件
	 * @return this
	 */
	public EnchantmentEffectsBuilder addDamage(double base, double perLevelAboveFirst, JsonObject requirements) {
		JsonObject value = linearValue(base, perLevelAboveFirst);
		JsonObject effect = new JsonObject();
		effect.addProperty("type", "minecraft:add");
		effect.add("value", value);
		return addValueEffectEntry("minecraft:damage", effect, requirements);
	}

	/**
	 * 添加线性击退效果（{@code minecraft:knockback}）。
	 *
	 * @param base 1 级时的基础击退
	 * @param perLevelAboveFirst 每级递增量
	 * @return this
	 */
	public EnchantmentEffectsBuilder addKnockback(double base, double perLevelAboveFirst) {
		JsonObject value = linearValue(base, perLevelAboveFirst);
		JsonObject effect = new JsonObject();
		effect.addProperty("type", "minecraft:add");
		effect.add("value", value);
		return addValueEffectEntry("minecraft:knockback", effect, null);
	}

	/**
	 * 添加伤害保护效果（{@code minecraft:damage_protection}）。
	 *
	 * @param base 1 级时的基础保护值
	 * @param perLevelAboveFirst 每级递增量
	 * @return this
	 */
	public EnchantmentEffectsBuilder addDamageProtection(double base, double perLevelAboveFirst) {
		return addDamageProtection(base, perLevelAboveFirst, null);
	}

	/**
	 * 添加带条件的伤害保护效果（{@code minecraft:damage_protection}）。
	 *
	 * @param base 1 级时的基础保护值
	 * @param perLevelAboveFirst 每级递增量
	 * @param requirements 触发条件 JSON，null 表示无条件
	 * @return this
	 */
	public EnchantmentEffectsBuilder addDamageProtection(double base, double perLevelAboveFirst, JsonObject requirements) {
		JsonObject value = linearValue(base, perLevelAboveFirst);
		JsonObject effect = new JsonObject();
		effect.addProperty("type", "minecraft:add");
		effect.add("value", value);
		return addValueEffectEntry("minecraft:damage_protection", effect, requirements);
	}

	/**
	 * 添加攻击后点燃效果（{@code minecraft:post_attack} + {@code minecraft:ignite}）。
	 *
	 * <p>等价于 fire_aspect 的效果。</p>
	 *
	 * @param baseDuration 1 级时的基础点燃时长（秒）
	 * @param perLevelAboveFirst 每级递增时长
	 * @return this
	 */
	public EnchantmentEffectsBuilder addPostAttackIgnite(double baseDuration, double perLevelAboveFirst) {
		JsonObject duration = linearValue(baseDuration, perLevelAboveFirst);
		JsonObject effect = new JsonObject();
		effect.addProperty("type", "minecraft:ignite");
		effect.add("duration", duration);

		// 默认条件：直接伤害（非弹射物）
		JsonObject requirements = new JsonObject();
		requirements.addProperty("condition", "minecraft:damage_source_properties");
		JsonObject predicate = new JsonObject();
		predicate.addProperty("is_direct", true);
		requirements.add("predicate", predicate);

		return addPostAttackEntry("victim", "attacker", effect, requirements);
	}

	/**
	 * 通用：添加值类型效果条目（{@code effect} + 可选 {@code requirements}）。
	 *
	 * <p>适用于 {@code minecraft:damage}、{@code minecraft:knockback}、{@code minecraft:damage_protection}、
	 * {@code minecraft:armor_effectiveness} 等值类型效果。</p>
	 *
	 * @param effectType 效果类型（如 "minecraft:damage"）
	 * @param effect 效果 JSON（含 type 和 value）
	 * @param requirements 触发条件 JSON，null 表示无条件
	 * @return this
	 */
	public EnchantmentEffectsBuilder addValueEffectEntry(String effectType, JsonObject effect, JsonObject requirements) {
		JsonObject entry = new JsonObject();
		entry.add("effect", effect);
		if (requirements != null) {
			entry.add("requirements", requirements);
		}
		addEntry(effectType, entry);
		return this;
	}

	/**
	 * 通用：添加 post_attack 效果条目。
	 *
	 * <p>post_attack 条目包含 {@code affected}、{@code enchanted}、{@code effect}、{@code requirements} 字段。</p>
	 *
	 * @param affected 受影响方（"victim" 或 "attacker"）
	 * @param enchanted 附魔持有方（"victim" 或 "attacker"）
	 * @param effect 效果 JSON
	 * @param requirements 触发条件 JSON，null 表示无条件
	 * @return this
	 */
	public EnchantmentEffectsBuilder addPostAttackEntry(String affected, String enchanted, JsonObject effect, JsonObject requirements) {
		JsonObject entry = new JsonObject();
		entry.addProperty("affected", affected);
		entry.addProperty("enchanted", enchanted);
		entry.add("effect", effect);
		if (requirements != null) {
			entry.add("requirements", requirements);
		}
		addEntry("minecraft:post_attack", entry);
		return this;
	}

	/**
	 * 通用：添加任意效果条目到指定效果类型列表。
	 *
	 * <p>高级方法，允许完全自定义 effect entry 结构。</p>
	 *
	 * @param effectType 效果类型（如 "minecraft:damage"）
	 * @param entry 效果条目 JSON
	 * @return this
	 */
	public EnchantmentEffectsBuilder addEntry(String effectType, JsonObject entry) {
		this.effects.computeIfAbsent(effectType, k -> new JsonArray()).add(entry);
		return this;
	}

	/**
	 * 构建为 JSON 对象。
	 *
	 * @return effects JSON
	 */
	public JsonObject build() {
		JsonObject result = new JsonObject();
		for (Map.Entry<String, JsonArray> e : this.effects.entrySet()) {
			result.add(e.getKey(), e.getValue());
		}
		return result;
	}

	/**
	 * 创建线性值 JSON（{@code minecraft:linear}）。
	 *
	 * @param base 基础值
	 * @param perLevelAboveFirst 每级递增
	 * @return 值 JSON 对象
	 */
	private static JsonObject linearValue(double base, double perLevelAboveFirst) {
		JsonObject value = new JsonObject();
		value.addProperty("type", "minecraft:linear");
		value.addProperty("base", base);
		value.addProperty("per_level_above_first", perLevelAboveFirst);
		return value;
	}
}
