package com.enchantlib.mixin;

import com.enchantlib.event.DrawStrengthHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.entity.projectile.Projectile;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BowItem Mixin：捕获弓的拉弓程度（F3 drawStrength 支持）。
 *
 * <p>在 {@link BowItem#shootProjectile} RETURN 处注入，将 {@code power} 参数转换为
 * drawStrength 并存储到弹射物上。</p>
 *
 * <p>{@code BowItem.releaseUsing} 中调用 {@code this.shoot(..., pow * 3.0F, ...)}，
 * 所以 {@code shootProjectile} 的 {@code power} 参数是 {@code pow * 3.0F}。
 * drawStrength = power / 3.0F。</p>
 *
 * <p>弩（{@code CrossbowItem}）总是满充能，drawStrength 默认 1.0f 即可，
 * 不需要额外 Mixin。</p>
 *
 * @since 0.1.0
 */
@Mixin(BowItem.class)
public abstract class BowItemMixin {

	/**
	 * 在 shootProjectile RETURN 处捕获 power，转换为 drawStrength 存储到弹射物上。
	 *
	 * @param shooter         射手
	 * @param projectileEntity 弹射物实体
	 * @param index           弹射物索引（多弹射物场景）
	 * @param power           功率参数（BowItem 传入 pow * 3.0F）
	 * @param uncertainty     不确定性
	 * @param angle           角度
	 * @param targetOverride  目标覆盖（可为 null）
	 * @param ci              回调信息
	 */
	@Inject(method = "shootProjectile", at = @At("RETURN"))
	private void enchantlib$captureDrawStrength(
		LivingEntity shooter,
		Projectile projectileEntity,
		int index,
		float power,
		float uncertainty,
		float angle,
		@Nullable LivingEntity targetOverride,
		CallbackInfo ci) {
		// power = pow * 3.0F，所以 drawStrength = power / 3.0F
		// 但为防止数值越界，clamp 到 [0.0, 1.0]
		float drawStrength = Math.max(0.0f, Math.min(1.0f, power / 3.0f));
		if (projectileEntity instanceof DrawStrengthHolder holder) {
			holder.enchantlib$setDrawStrength(drawStrength);
		}
	}
}
