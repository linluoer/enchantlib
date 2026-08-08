package com.enchantlib.mixin;

import com.enchantlib.event.DrawStrengthHolder;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Projectile 基类 Mixin：为所有弹射物添加 drawStrength 持有能力（F3 drawStrength 支持）。
 *
 * <p>实现 {@link DrawStrengthHolder} 接口，通过 {@code @Unique} 字段存储拉弓程度。
 * 默认值为 1.0f（满拉弓），适用于弩（满充能）和非弓类弹射物（雪球/药水等）。</p>
 *
 * <p>弓（{@code BowItem}）通过 {@code BowItemMixin} 在 {@code shootProjectile} 时
 * 设置实际拉弓程度（{@code power / 3.0F}，因为 BowItem 传入的 power 是 {@code pow * 3.0F}）。</p>
 *
 * <p>{@code ProjectileWeaponMixin} 在触发 {@link com.enchantlib.event.BuiltInEvents.ProjectileHitEvent}
 * 时读取此字段，作为事件的 {@code drawStrength} 参数。</p>
 *
 * @since 0.1.0
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin implements DrawStrengthHolder {

	@Unique
	private float enchantlib$drawStrength = 1.0f;

	@Override
	public float enchantlib$getDrawStrength() {
		return enchantlib$drawStrength;
	}

	@Override
	public void enchantlib$setDrawStrength(float drawStrength) {
		this.enchantlib$drawStrength = drawStrength;
	}
}
