package com.enchantlib.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * 附魔事件。
 *
 * <p>所有 EnchantLib 事件分发器分发的事件都实现此接口。</p>
 *
 * <p>事件对象本身描述"发生了什么"，{@link EnchantmentContext} 描述"在哪个附魔上触发"。
 * 分发器会扫描实体装备槽位的所有附魔，对每个附魔独立调用回调，传入相同的事件对象和不同的 context。</p>
 *
 * @since 0.1.0
 */
public interface EnchantmentEvent {

	/**
	 * 获取触发事件的实体（事件主体，通常是受影响或主动触发者）。
	 *
	 * @return 实体
	 */
	LivingEntity getEntity();

	/**
	 * 获取事件发生所在的服务端世界。
	 *
	 * @return 服务端世界
	 */
	ServerLevel getLevel();
}
