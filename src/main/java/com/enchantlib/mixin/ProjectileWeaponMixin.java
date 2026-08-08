package com.enchantlib.mixin;

import com.enchantlib.event.BuiltInEvents;
import com.enchantlib.event.DrawStrengthHolder;
import com.enchantlib.event.EnchantmentEventDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 弹射物轨道：弹射物命中实体事件捕获与分发（F3 覆盖范围扩展）。
 *
 * <p>在 {@link Projectile#onHit(HitResult)} 方法中、
 * {@link Projectile#onHitEntity(EntityHitResult)} 调用<b>之后</b>（{@link At.Shift#AFTER}）注入，
 * 覆盖<b>所有弹射物</b>（箭、三叉戟、雪球、药水、末影珍珠、烟花火箭等）。</p>
 *
 * <h2>注入点选择</h2>
 * <p>不直接注入 {@code onHitEntity RETURN}，原因有二：</p>
 * <ul>
 *   <li><b>箭类时机错误</b>：{@link AbstractArrow#onHitEntity(EntityHitResult)} 在
 *       调 {@code super.onHitEntity}（基类空方法）后才执行伤害逻辑。直接注入基类
 *       {@code onHitEntity RETURN} 会在 {@code super} 调用返回时立刻触发，伤害尚未结算。</li>
 *   <li><b>三叉戟漏触发</b>：{@link ThrownTrident#onHitEntity(EntityHitResult)} 重写后
 *       <b>不调用</b> {@code super.onHitEntity}，基类 RETURN 注入完全不会触发。</li>
 * </ul>
 * <p>改注入 {@code onHit(HitResult)} 中的 {@code INVOKE onHitEntity AFTER}：
 * 所有弹射物命中实体都经过 {@link Projectile#onHit(HitResult)} 分发到
 * {@code onHitEntity}，子类完整执行后控制权回到 {@code onHit}，此时注入触发，
 * 时机正确且覆盖三叉戟。</p>
 *
 * <h2>覆盖范围</h2>
 * <ul>
 *   <li><b>箭类（AbstractArrow 子类）</b>：Arrow、SpectralArrow、ThrownTrident
 *       —— 通过 {@link AbstractArrow#getWeaponItem()} 获取武器（{@code @Nullable}，已防御），
 *       {@link AbstractArrow#isCritArrow()} 获取暴击箭标志</li>
 *   <li><b>非箭类（Projectile 其他子类）</b>：Snowball、ThrownEnderpearl、
 *       ThrownExperienceBottle、AbstractThrownPotion、FireworkRocketEntity、ShulkerBullet 等
 *       —— 武器取发射者主手物品，isCritArrow 固定为 false</li>
 * </ul>
 *
 * <h2>drawStrength 支持</h2>
 * <p>通过 {@link DrawStrengthHolder} 接口获取拉弓程度：
 * 弓（BowItem）通过 {@code BowItemMixin} 在发射时设置 drawStrength，
 * 非弓类弹射物默认 1.0f。{@link BuiltInEvents.ProjectileHitEvent} 实现
 * {@link com.enchantlib.event.ChargeableEvent}，让 {@link com.enchantlib.event.TriggerPolicy}
 * 可基于拉弓程度门控 PROJECTILE_HIT 事件。</p>
 *
 * <h2>事件触发条件</h2>
 * <ul>
 *   <li>弹射物所在世界为 {@link ServerLevel}（仅服务端触发）</li>
 *   <li>发射者（{@code getOwner()}）为 {@link LivingEntity}</li>
 *   <li>命中目标为 {@link LivingEntity}</li>
 *   <li>存在至少一个 PROJECTILE_HIT 回调（O1 位掩码短路）</li>
 * </ul>
 *
 * <p>事件分发由 {@link EnchantmentEventDispatcher#dispatch} 处理，自带异常隔离，
 * 单个回调异常不影响其他回调或原版逻辑。</p>
 *
 * @since 0.1.0
 */
@Mixin(Projectile.class)
public abstract class ProjectileWeaponMixin {

	/**
	 * 在 {@link Projectile#onHit(HitResult)} 调用 {@code onHitEntity} 之后触发 PROJECTILE_HIT 事件。
	 *
	 * <p>用 {@link At.Shift#AFTER} 确保 {@code onHitEntity}（含子类完整重写）已执行完毕，
	 * 此时伤害已结算，时机正确。{@code onHit} 仅在命中实体时才调 {@code onHitEntity}，
	 * 因此此处 {@code hitResult} 必为 {@link EntityHitResult}。</p>
	 */
	@Inject(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/Projectile;onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
			shift = At.Shift.AFTER))
	private void enchantlib$triggerProjectileHit(HitResult hitResult, CallbackInfo ci) {
		// O1 位掩码短路：若无任何 PROJECTILE_HIT 回调，跳过所有检查
		if (!EnchantmentEventDispatcher.hasCallbacks(BuiltInEvents.PROJECTILE_HIT)) {
			return;
		}

		@SuppressWarnings("DataFlowIssue")
		Projectile self = (Projectile) (Object) this;

		// 仅服务端触发
		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		// 获取发射者，必须为 LivingEntity
		Entity owner = self.getOwner();
		if (!(owner instanceof LivingEntity attacker)) {
			return;
		}

		// onHit 中调用 onHitEntity 时 hitResult 必为 EntityHitResult（onHit 已做类型分支）
		EntityHitResult entityHitResult = (EntityHitResult) hitResult;
		Entity target = entityHitResult.getEntity();
		if (!(target instanceof LivingEntity livingTarget)) {
			return;
		}

		// 获取武器快照和暴击箭标志：箭类用 getWeaponItem/isCritArrow，非箭类用主手物品/false
		ItemStack weapon;
		boolean isCritArrow;
		if (self instanceof AbstractArrow arrow) {
			// AbstractArrow.getWeaponItem() 标注 @Nullable（箭从发射器射出时无武器快照）
			ItemStack weaponItem = arrow.getWeaponItem();
			weapon = weaponItem != null ? weaponItem : ItemStack.EMPTY;
			isCritArrow = arrow.isCritArrow();
		} else {
			// 非箭类弹射物：武器取发射者主手物品（可能为空）
			weapon = attacker.getMainHandItem();
			isCritArrow = false;
		}

		// 获取 drawStrength（拉弓程度）：弓通过 BowItemMixin 设置，非弓类默认 1.0f
		float drawStrength = 1.0f;
		if (self instanceof DrawStrengthHolder holder) {
			drawStrength = holder.enchantlib$getDrawStrength();
		}

		// 构建并分发 PROJECTILE_HIT 事件
		BuiltInEvents.ProjectileHitEvent event = new BuiltInEvents.ProjectileHitEvent(
			serverLevel,
			attacker,
			livingTarget,
			weapon,
			isCritArrow,
			drawStrength
		);
		EnchantmentEventDispatcher.dispatch(BuiltInEvents.PROJECTILE_HIT, event, attacker);
	}
}
