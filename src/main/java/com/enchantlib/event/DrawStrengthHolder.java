package com.enchantlib.event;

/**
 * 弹射物拉弓程度持有者接口（F3 drawStrength 支持）。
 *
 * <p>由 {@code ProjectileMixin} 实现，通过 {@code @Unique} 字段存储拉弓程度。
 * 用于 {@link com.enchantlib.event.BuiltInEvents.ProjectileHitEvent} 携带 drawStrength，
 * 让 {@link TriggerPolicy} 可基于拉弓程度门控 PROJECTILE_HIT 事件。</p>
 *
 * <p>默认值为 1.0f（满拉弓），适用于弩（满充能）和非弓类弹射物（雪球/药水等）。
 * 弓（{@code BowItem}）通过 {@code BowItemMixin} 在发射时设置实际拉弓程度。</p>
 *
 * @since 0.1.0
 */
public interface DrawStrengthHolder {

	/**
	 * 获取拉弓程度（0.0~1.0）。
	 *
	 * @return 拉弓程度，默认 1.0f（满拉弓）
	 */
	float enchantlib$getDrawStrength();

	/**
	 * 设置拉弓程度。
	 *
	 * @param drawStrength 拉弓程度（0.0~1.0）
	 */
	void enchantlib$setDrawStrength(float drawStrength);
}
