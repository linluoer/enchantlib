package com.enchantlib.mixin;

import com.enchantlib.event.EnchantLibEvents;
import com.enchantlib.event.FoodRegenEvent;
import com.enchantlib.event.FoodTickTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 LivingEntity.heal 调用,配合 FoodDataMixin 实现静默契约的自然回血压制。
 *
 * <p>在 heal HEAD 处检查 FoodDataMixin.isInFoodTick() 标志:
 * 仅当当前线程正在执行 FoodData.tick(即自然回血)时,才触发
 * {@link EnchantLibEvents#FOOD_REGEN} 事件。事件被取消则跳过 heal 调用。</p>
 *
 * <p>非自然回血(药水、金苹果、魔法伤害治疗等)的 heal 调用不受影响,
 * 因为此时 isInFoodTick() 返回 false。</p>
 *
 * <p>设计说明:原本计划用 @WrapOperation 在 FoodData.tick 内包裹 heal 调用,
 * 但 Loom 1.17 + MC 26.2 运行时无 refmap,@At.INVOKE 目标无法解析。
 * 改用"线程局部标志 + heal HEAD 注入"方案,仅用 @At("HEAD") 不依赖 INVOKE 目标解析。</p>
 *
 * @since 0.2.0
 */
@Mixin(LivingEntity.class)
public class LivingEntityHealMixin {

	/**
	 * heal HEAD:检查是否为自然回血,触发 FOOD_REGEN 事件。
	 *
	 * @param amount 治疗量
	 * @param ci     回调信息(用于取消 heal 调用)
	 */
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void enchantlib$interceptNaturalRegen(float amount, CallbackInfo ci) {
		// 仅在 FoodData.tick 执行期间触发(自然回血)
		if (!FoodTickTracker.isInFoodTick()) {
			return;
		}

		// 仅对 ServerPlayer 触发事件(自然回血只在服务端计算)
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof ServerPlayer serverPlayer)) {
			return;
		}

		// 触发 FOOD_REGEN 事件,允许附魔取消自然回血
		FoodRegenEvent event = new FoodRegenEvent(serverPlayer, amount);
		EnchantLibEvents.FOOD_REGEN.invoker().onRegen(event);
		if (event.isCancelled()) {
			ci.cancel();
		}
	}
}
