package com.enchantlib.mixin;

import com.enchantlib.event.FoodTickTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 标记 FoodData.tick 执行期间,用于配合 LivingEntityHealMixin 拦截自然回血。
 *
 * <p>MC 26.2 中 FoodData.tick(ServerPlayer) 在满足条件时调用 LivingEntity.heal(float)
 * 进行自然回血。由于 Loom 1.17 + MC 26.2 运行时无 refmap,@At.INVOKE 目标无法解析
 * (已实测:@WrapOperation/@At INVOKE 在运行时 "Scanned 0 target(s)"),
 * FoodDataMixin 不能用 @WrapOperation 包裹 heal 调用,@WrapMethod 也因编译期
 * MixinExtras 为 0.2.x(无 wrapmethod 包)而不可用。故只能用 @Inject HEAD/RETURN。</p>
 *
 * <p>替代方案:用 @Inject 在 tick HEAD/RETURN 设置线程局部标志
 * ({@link FoodTickTracker}),LivingEntityHealMixin 在 heal HEAD 检查标志并触发
 * {@link com.enchantlib.event.EnchantLibEvents#FOOD_REGEN} 事件,
 * 实现"仅在自然回血时触发事件,药水/金苹果等其他 heal 调用不受影响"。</p>
 *
 * <p>取消时跳过 heal 调用,但 FoodData.tick 的其他逻辑(饱食度消耗等)正常执行,
 * 满足"压制自然回血但饥饿仍正常消耗"的需求。</p>
 *
 * <p>已知限制(P2,低优先级):@At("RETURN") 仅在正常返回时触发,若 FoodData.tick
 * 抛出异常则 exit() 不执行,IN_FOOD_TICK 标志卡死为 true,直到下一次 tick 的
 * enter()/exit() 自愈。期间同线程的非自然回血 heal 可能被误触发 FOOD_REGEN。
 * FoodData.tick 在原版极少抛异常,实际影响近零。彻底修复需先解决无 refmap 问题
 * 或升级编译期 MixinExtras 至 0.3.0+ 以使用 @WrapMethod try-finally。</p>
 *
 * @since 0.2.0
 */
@Mixin(FoodData.class)
public class FoodDataMixin {

	/**
	 * tick HEAD:标记进入 FoodData.tick。
	 */
	@Inject(method = "tick", at = @At("HEAD"))
	private void enchantlib$markFoodTickStart(ServerPlayer player, CallbackInfo ci) {
		FoodTickTracker.enter();
	}

	/**
	 * tick RETURN:标记退出 FoodData.tick(仅正常返回时触发,异常时不触发,见类注释已知限制)。
	 */
	@Inject(method = "tick", at = @At("RETURN"))
	private void enchantlib$markFoodTickEnd(ServerPlayer player, CallbackInfo ci) {
		FoodTickTracker.exit();
	}
}
