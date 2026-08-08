package com.enchantlib.event;

/**
 * 可充能事件标记接口。
 *
 * <p>实现此接口的事件携带"充能比例"信息（如 {@link BuiltInEvents.PostAttackEvent} 的 charge 字段），
 * 供 {@link TriggerPolicy} 进行阈值门控和等级缩放。</p>
 *
 * <p>未实现此接口的事件类型（ENTITY_TICK、POST_HURT、POST_KILL、MODIFY_DAMAGE、PROJECTILE_HIT 等）
 * 在分发时忽略 {@link TriggerPolicy}，始终触发回调。</p>
 *
 * @since 0.1.0
 */
public interface ChargeableEvent extends EnchantmentEvent {

	/**
	 * 获取充能比例。
	 *
	 * @return 充能比例（0.0~1.0，1.0 表示满充能）
	 */
	float charge();
}
