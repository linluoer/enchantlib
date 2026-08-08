package com.enchantlib.mixin;

import com.enchantlib.debug.DebugLogger;
import com.enchantlib.event.BuiltInEvents;
import com.enchantlib.event.EnchantmentEventDispatcher;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * DamageHelper：伤害修改事件捕获与分发（Q1 架构修复版）。
 *
 * <p>在 {@link LivingEntity#hurtServer(ServerLevel, DamageSource, float)} 方法 HEAD 处通过
 * {@link ModifyVariable} 直接修改 {@code damage} 参数，让原调用链<b>只走一次</b>。</p>
 *
 * <h2>Q1 架构修复说明</h2>
 * <p>原实现使用"取消+重调"模式（{@code cir.setReturnValue(self.hurtServer(...))}），存在三个问题：</p>
 * <ol>
 *   <li>其他模组的 HEAD 注入会执行两次（原始调用被取消前已执行副作用）</li>
 *   <li>无敌帧/伤害合并逻辑可能被二次结算</li>
 *   <li>重入标记只防自己，防不了别人</li>
 * </ol>
 * <p>重构为 {@link ModifyVariable} 模式后：</p>
 * <ul>
 *   <li>原调用链只走一次，无重入</li>
 *   <li>其他模组的 HEAD 注入只执行一次，看到的是修改后的伤害值</li>
 *   <li>无敌帧/伤害合并只结算一次</li>
 *   <li>无需重入保护标志</li>
 * </ul>
 *
 * <h2>事件触发条件</h2>
 * <ul>
 *   <li>伤害来源（{@code source.getEntity()}）为 {@link LivingEntity}
 *       （必须有生物攻击者，环境伤害如摔落/火焰不触发）</li>
 *   <li>存在至少一个 MODIFY_DAMAGE 回调（O1 位掩码短路）</li>
 * </ul>
 *
 * <h2>参数索引</h2>
 * <p>{@code hurtServer} 是实例方法，局部变量索引：</p>
 * <ul>
 *   <li>index 0: this (LivingEntity)</li>
 *   <li>index 1: level (ServerLevel)</li>
 *   <li>index 2: source (DamageSource)</li>
 *   <li>index 3: damage (float) ← 修改目标</li>
 * </ul>
 *
 * <p>事件分发由 {@link EnchantmentEventDispatcher#dispatch} 处理，自带异常隔离，
 * 单个回调异常不影响其他回调或原版逻辑。</p>
 *
 * @since 0.1.0
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

	/**
	 * HEAD 处直接修改 damage 参数，让原调用链只走一次。
	 *
	 * <p>使用 {@link ModifyVariable} + {@code argsOnly = true} 修改方法参数，
	 * 通过 MixinExtras {@link Local} 访问其他参数（level, source）。</p>
	 *
	 * <p>流程：</p>
	 * <ol>
	 *   <li>O1 位掩码短路：若无 MODIFY_DAMAGE 回调，直接返回原值</li>
	 *   <li>检查攻击者：仅当 source.getEntity() 为 LivingEntity 时触发</li>
	 *   <li>分发 MODIFY_DAMAGE 事件，回调通过 MutableFloat 修改伤害值</li>
	 *   <li>返回修改后的伤害值，原调用链使用新值继续执行</li>
	 * </ol>
	 *
	 * @param damage 原始伤害值（index 3，被修改的参数）
	 * @param level  服务端世界（通过 @Local 捕获，index 1）
	 * @param source 伤害来源（通过 @Local 捕获，index 2）
	 * @return 修改后的伤害值（若回调未修改则返回原值）
	 */
	@ModifyVariable(
		method = "hurtServer",
		at = @At("HEAD"),
		argsOnly = true,
		index = 3
	)
	private float enchantlib$modifyDamage(
		float damage,
		@Local(argsOnly = true, ordinal = 0) ServerLevel level,
		@Local(argsOnly = true, ordinal = 0) DamageSource source) {
		// O1 位掩码短路：若无任何 MODIFY_DAMAGE 回调，跳过所有检查
		if (!EnchantmentEventDispatcher.hasCallbacks(BuiltInEvents.MODIFY_DAMAGE)) {
			return damage;
		}

		// 仅当有生物攻击者时触发（环境伤害如摔落/火焰不触发）
		Entity attackerEntity = source.getEntity();
		if (!(attackerEntity instanceof LivingEntity attacker)) {
			return damage;
		}

		@SuppressWarnings("DataFlowIssue")
		LivingEntity self = (LivingEntity) (Object) this;

		// 构建可修改的伤害值容器
		MutableFloat damageMutable = new MutableFloat(damage);

		DebugLogger.log("MODIFY_DAMAGE 入口: 攻击者={} 目标={} 原始伤害={} 来源={}",
			attacker.getName().getString(), self.getName().getString(),
			damage, source.type().msgId());

		// 分发 MODIFY_DAMAGE 事件，扫描攻击者的装备附魔
		BuiltInEvents.ModifyDamageEvent event = new BuiltInEvents.ModifyDamageEvent(
			level,
			attacker,
			self,
			source,
			damage,
			damageMutable
		);
		EnchantmentEventDispatcher.dispatch(BuiltInEvents.MODIFY_DAMAGE, event, attacker);

		float modified = damageMutable.floatValue();
		DebugLogger.log("MODIFY_DAMAGE 出口: 攻击者={} 目标={} 修改后伤害={} (变化={})",
			attacker.getName().getString(), self.getName().getString(),
			modified, modified - damage);

		// 返回修改后的伤害值（若回调未修改则等于原值）
		return modified;
	}
}
