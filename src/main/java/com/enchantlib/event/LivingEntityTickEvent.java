package com.enchantlib.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * 任意 LivingEntity tick 事件（全局事件，不基于装备附魔扫描）。
 *
 * <p>与 {@link BuiltInEvents.EntityTickEvent} 不同，此事件对所有加载的 LivingEntity 触发，
 * 不要求实体身上有附魔物品。适用于需要在目标实体（通常是怪物）上持续触发效果的附魔，
 * 例如「焚心」类持续伤害附魔——玩家攻击时在目标身上施加标记，此事件每 tick 检查标记并造成伤害。</p>
 *
 * <h2>注册方式</h2>
 * <p>此事件为<b>全局事件</b>，不通过 {@code EnchantmentEventRegistrar} 注册（因为它不绑定到具体附魔物品），
 * 而是通过 {@link EnchantLibEvents#LIVING_ENTITY_TICK} 注册：</p>
 * <pre>{@code
 * EnchantLibEvents.LIVING_ENTITY_TICK.register(event -> {
 *     LivingEntity entity = event.entity();
 *     // 检查 EntityCounter 标记，造成伤害等
 * });
 * }</pre>
 *
 * <h2>性能注意</h2>
 * <p>此事件每 tick 对所有加载的 LivingEntity 触发。仅当至少有一个回调注册时才会启用遍历。
 * 回调应保持轻量，建议用 {@link com.enchantlib.api.EntityCounter} 等机制快速过滤无关实体。</p>
 *
 * @param level     服务端世界
 * @param entity    触发 tick 的实体
 * @param tickCount 服务端 tick 计数
 * @since 0.2.0
 */
public record LivingEntityTickEvent(
	ServerLevel level,
	LivingEntity entity,
	int tickCount
) implements EnchantmentEvent {
	@Override
	public LivingEntity getEntity() {
		return entity;
	}

	@Override
	public ServerLevel getLevel() {
		return level;
	}
}
