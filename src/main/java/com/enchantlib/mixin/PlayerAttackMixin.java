package com.enchantlib.mixin;

import com.enchantlib.EnchantLib;
import com.enchantlib.debug.DebugLogger;
import com.enchantlib.event.BuiltInEvents;
import com.enchantlib.event.EnchantmentEventDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 近战轨道：玩家攻击事件捕获与分发。
 *
 * <p>在 {@link Player#attack(Entity)} 方法中注入三个点：</p>
 * <ul>
 *   <li><b>HEAD</b>：捕获攻击快照（charge/crit/sweep），存入 {@code @Unique} 字段；
 *       同时重置 {@code wasHurt} 标记为 false</li>
 *   <li><b>INVOKE causeExtraKnockback</b>：原版 attack 方法在
 *       {@code if (wasHurt)} 块首行调用 {@link Player#causeExtraKnockback}，
 *       只有真正命中并造成伤害时才会执行到此处，借此标记 {@code wasHurt=true}</li>
 *   <li><b>RETURN</b>：读取快照与 wasHurt 标记，触发 {@link BuiltInEvents#POST_ATTACK} 事件</li>
 * </ul>
 *
 * <h2>捕获的快照</h2>
 * <ul>
 *   <li><b>charge</b>：{@link Player#getAttackStrengthScale(float)} 返回值 (0.0~1.0)
 *       <p><b>注：</b>原版 attack L950 也调用此方法。getAttackStrengthScale 是纯读取
 *       （不修改 attackStrengthTicker），重复调用仅影响性能，不影响正确性。</li>
 *   <li><b>isCritical</b>：{@code charge > 0.9 && canCriticalAttack(target)}
 *       （注意：不能直接用 {@code canCriticalAttack}，需组合判断，见 D-016）</li>
 *   <li><b>isSweep</b>：{@code isSweepAttack(isFullyCharged, isCritical, isSprintingKnockback)}
 *       （参数语义见 D-016：参数1=满充能，参数2=组合暴击判断，参数3=疾跑击退）</li>
 * </ul>
 *
 * <h2>事件触发条件</h2>
 * <ul>
 *   <li>HEAD 注入已成功捕获快照（{@code hasSnapshot=true}）</li>
 *   <li>命中标记为 true（{@code wasHurt=true}）—— 见下方"未命中防护"</li>
 *   <li>玩家所在世界为 {@link ServerLevel}（仅服务端触发）</li>
 *   <li>目标为 {@link LivingEntity}（非生物实体不触发）</li>
 * </ul>
 *
 * <h2>未命中防护</h2>
 * <p>原版 {@link Player#attack(Entity)} 有多个早返回/不触发伤害的分支：</p>
 * <ul>
 *   <li>{@code cannotAttack(entity)} 为 true → 直接返回</li>
 *   <li>{@code deflectProjectile(entity)} 为 true（如盾挡/弹射物偏转） → 跳过主体</li>
 *   <li>{@code baseDamage <= 0 && magicBoost <= 0} → 不调 hurtOrSimulate</li>
 *   <li>{@code entity.hurtOrSimulate(...)} 返回 false（伤害被盾完全挡掉等） → wasHurt=false</li>
 * </ul>
 * <p>HEAD 捕获快照后 RETURN 若无条件触发，会让 POST_ATTACK 在玩家挥空/盾挡/伤害被免疫时
 * 也误触发，导致依赖"目标已受伤"语义的附魔（如焚心标记、冰霜附着、吸血）错误生效。</p>
 *
 * <p>解决：用 {@code @Unique boolean wasHurt} 标记真实命中。在原版 {@code if (wasHurt)} 块
 * 首个方法调用 {@link Player#causeExtraKnockback} 处注入，标记 wasHurt=true。
 * 该方法<b>仅</b>在原版 wasHurt=true 分支内被调用，借此间接捕获原版语义。</p>
 *
 * <p>事件分发由 {@link EnchantmentEventDispatcher#dispatch} 处理，自带异常隔离，
 * 单个回调异常不影响其他回调或原版逻辑。</p>
 *
 * @since 0.1.0
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {

	@Shadow
	private boolean canCriticalAttack(Entity target) {
		throw new IllegalStateException("Shadow method not injected");
	}

	@Shadow
	private boolean isSweepAttack(boolean isFullyCharged, boolean isCritical, boolean isSprintingKnockback) {
		throw new IllegalStateException("Shadow method not injected");
	}

	/** 攻击快照：HEAD 捕获，RETURN 读取 */
	@Unique
	private float enchantlib$charge;

	@Unique
	private boolean enchantlib$isCritical;

	@Unique
	private boolean enchantlib$isSweep;

	@Unique
	private boolean enchantlib$hasSnapshot;

	/** 真实命中标记：HEAD 重置为 false，causeExtraKnockback 注入设为 true，RETURN 检查 */
	@Unique
	private boolean enchantlib$wasHurt;

	/**
	 * HEAD 注入：在 attack 方法开始时捕获攻击快照，并重置命中标记。
	 *
	 * <p>计算 charge/crit/sweep 三个关键参数，存入 @Unique 字段供 RETURN 注入读取。
	 * 同时将 wasHurt 重置为 false，等待 causeExtraKnockback 注入标记真实命中。</p>
	 *
	 * <p>参数语义（D-016 反编译确认）：</p>
	 * <ul>
	 *   <li>isFullyCharged = charge > 0.9F</li>
	 *   <li>isCritical = isFullyCharged && canCriticalAttack(target)（组合判断，非 canCriticalAttack 直接结果）</li>
	 *   <li>isSprintingKnockback = isSprinting() && isFullyCharged</li>
	 *   <li>isSweep = isSweepAttack(isFullyCharged, isCritical, isSprintingKnockback)</li>
	 * </ul>
	 */
	@Inject(method = "attack", at = @At("HEAD"))
	private void enchantlib$captureAttackSnapshot(Entity target, CallbackInfo ci) {
		// O1 位掩码短路：若无任何 POST_ATTACK 回调，跳过 charge/crit/sweep 计算
		if (!EnchantmentEventDispatcher.hasCallbacks(BuiltInEvents.POST_ATTACK)) {
			return;
		}

		@SuppressWarnings("DataFlowIssue")
		Player self = (Player) (Object) this;

		float charge = self.getAttackStrengthScale(0.5F);
		boolean isFullyCharged = charge > 0.9F;
		boolean isCritical = isFullyCharged && canCriticalAttack(target);
		boolean isSprintingKnockback = self.isSprinting() && isFullyCharged;
		boolean isSweep = isSweepAttack(isFullyCharged, isCritical, isSprintingKnockback);

		this.enchantlib$charge = charge;
		this.enchantlib$isCritical = isCritical;
		this.enchantlib$isSweep = isSweep;
		this.enchantlib$hasSnapshot = true;
		this.enchantlib$wasHurt = false;

		DebugLogger.log("POST_ATTACK HEAD: 玩家={} 目标={} charge={} crit={} sweep={}",
			self.getName().getString(), target.getName().getString(),
			charge, isCritical, isSweep);
	}

	/**
	 * INVOKE 注入：原版 {@code if (wasHurt)} 块首行调用 causeExtraKnockback，
	 * 仅当 hurtOrSimulate 返回 true 时才会执行到此调用，借此标记真实命中。
	 *
	 * <p>注意：{@code causeExtraKnockback} 在 attack() 方法中仅此一处调用
	 * （L981，位于 {@code if (wasHurt)} 块首行），其他重载/调用都在不同方法中，
	 * method="attack" 限定下不会误触发。</p>
	 */
	@Inject(method = "attack",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;causeExtraKnockback(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/damagesource/DamageSource;FZ)V"))
	private void enchantlib$markHurt(Entity target, CallbackInfo ci) {
		this.enchantlib$wasHurt = true;
	}

	/**
	 * RETURN 注入：在 attack 方法返回时触发 POST_ATTACK 事件。
	 *
	 * <p>仅当：</p>
	 * <ul>
	 *   <li>HEAD 已捕获快照（hasSnapshot=true）</li>
	 *   <li>真实命中标记为 true（wasHurt=true，由 causeExtraKnockback 注入设置）</li>
	 *   <li>玩家世界为 ServerLevel（服务端）</li>
	 *   <li>目标为 LivingEntity</li>
	 * </ul>
	 *
	 * <p>触发后清理快照，避免重复触发。事件分发自带异常隔离。</p>
	 */
	@Inject(method = "attack", at = @At("RETURN"))
	private void enchantlib$triggerPostAttack(Entity target, CallbackInfo ci) {
		if (!this.enchantlib$hasSnapshot) {
			return;
		}
		// 立即清理，避免重复触发
		this.enchantlib$hasSnapshot = false;

		// 未命中防护：仅在原版 wasHurt=true 时触发
		if (!this.enchantlib$wasHurt) {
			DebugLogger.log("POST_ATTACK RETURN: 跳过(未命中/伤害被免疫) 目标={}",
				target.getClass().getSimpleName());
			return;
		}

		@SuppressWarnings("DataFlowIssue")
		Player self = (Player) (Object) this;

		// 仅服务端触发
		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		// 仅对生物实体触发
		if (!(target instanceof LivingEntity livingTarget)) {
			DebugLogger.log("POST_ATTACK RETURN: 跳过(目标非生物实体) 目标={}", target.getClass().getSimpleName());
			return;
		}

		DebugLogger.log("POST_ATTACK RETURN: 分发事件 玩家={} 目标={} charge={} crit={} sweep={}",
			self.getName().getString(), livingTarget.getName().getString(),
			this.enchantlib$charge, this.enchantlib$isCritical, this.enchantlib$isSweep);

		// 构建并分发 POST_ATTACK 事件
		BuiltInEvents.PostAttackEvent event = new BuiltInEvents.PostAttackEvent(
			serverLevel,
			self,
			livingTarget,
			this.enchantlib$charge,
			this.enchantlib$isCritical,
			this.enchantlib$isSweep
		);
		EnchantmentEventDispatcher.dispatch(BuiltInEvents.POST_ATTACK, event, self);
	}
}
