package com.enchantlib.event;

import java.util.EnumSet;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.commons.lang3.mutable.MutableInt;

/**
 * 内置事件类型与事件类。
 *
 * <p>定义 EnchantLib 内置的事件类型常量，模组可直接引用这些常量注册回调。</p>
 *
 * <p>仅提供 {@link #POST_ATTACK} 和 {@link #ENTITY_TICK} 两个基础事件用于骨架验证，
 * 更丰富的事件类型（近战攻击、弹射物命中、受击、击杀、方块破坏、装备 tick）已实现。</p>
 *
 * @since 0.1.0
 */
public final class BuiltInEvents {

	private BuiltInEvents() {
	}

	// O5 槽位声明：各事件类型关注的装备槽位
	// 攻击类事件（POST_ATTACK/MODIFY_DAMAGE/PROJECTILE_HIT/POST_KILL）只扫描主手 + 副手
	// 防御类事件（POST_HURT）只扫描 4 护甲 + 副手
	// ENTITY_TICK 扫描所有槽位（默认）
	private static final EnumSet<EquipmentSlot> ATTACK_SLOTS =
		EnumSet.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
	private static final EnumSet<EquipmentSlot> DEFENSE_SLOTS =
		EnumSet.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND);

	/**
	 * 攻击后事件：实体攻击其他实体后触发。
	 *
	 * <p>回调扫描 <b>攻击者</b> 的所有装备附魔。</p>
	 *
	 * <p>典型用途：吸血、击退、点燃等攻击附加效果。</p>
	 */
	public static final EnchantmentEventType<PostAttackEvent> POST_ATTACK =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "post_attack"), PostAttackEvent.class);

	/**
	 * 实体 tick 事件：服务端每 tick 对每个 LivingEntity 触发。
	 *
	 * <p>回调扫描实体的所有装备附魔。</p>
	 *
	 * <p>典型用途：周期性效果（如发光、回血），装备 tick 钩子将在此基础上增加强制间隔。</p>
	 *
	 * <p>注意：此事件每 tick 触发，回调应保持轻量，避免性能问题。</p>
	 */
	public static final EnchantmentEventType<EntityTickEvent> ENTITY_TICK =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "entity_tick"), EntityTickEvent.class);

	/**
	 * 弹射物命中事件：弹射物（箭/三叉戟）命中实体后触发。
	 *
	 * <p>回调扫描 <b>发射者</b> 的所有装备附魔。</p>
	 *
	 * <p>典型用途：弓附魔的命中附加效果（如点燃、减速、额外伤害）。</p>
	 */
	public static final EnchantmentEventType<ProjectileHitEvent> PROJECTILE_HIT =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "projectile_hit"), ProjectileHitEvent.class);

	/**
	 * 伤害修改事件：在伤害应用到目标前触发。
	 *
	 * <p>回调扫描 <b>攻击者</b> 的所有装备附魔，允许通过 {@link MutableFloat} 修改伤害值。</p>
	 *
	 * <p>典型用途：自定义伤害加成附魔（如每级 +2 伤害）、伤害倍率附魔（如 1.5x）、
	 * 条件性伤害加成（如对特定生物造成额外伤害）。</p>
	 *
	 * <p>由 Mixin {@code LivingEntityHurtMixin} 在 {@code LivingEntity.hurtServer} HEAD 处触发，
	 * 仅当 {@link DamageSource#getEntity()} 为 {@link LivingEntity} 时触发
	 * （即必须有生物攻击者，环境伤害如摔落/火焰不触发）。</p>
	 */
	public static final EnchantmentEventType<ModifyDamageEvent> MODIFY_DAMAGE =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "modify_damage"), ModifyDamageEvent.class);

	/**
	 * 受击后事件：实体被伤害后触发。
	 *
	 * <p>回调扫描 <b>目标</b> 的所有装备附魔。</p>
	 *
	 * <p>典型用途：防御类附魔（如反伤、减伤后效果、受击触发护盾）。</p>
	 *
	 * <p>由 Fabric API {@code ServerLivingEntityEvents.AFTER_DAMAGE} 触发，
	 * 在伤害完全应用后触发。所有伤害类型均触发（包括环境伤害如摔落/火焰），
	 * 允许防御类附魔对所有伤害类型生效。</p>
	 */
	public static final EnchantmentEventType<PostHurtEvent> POST_HURT =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "post_hurt"), PostHurtEvent.class);

	/**
	 * 击杀后事件：实体击杀其他实体后触发。
	 *
	 * <p>回调扫描 <b>击杀者</b> 的所有装备附魔。</p>
	 *
	 * <p>典型用途：击杀奖励附魔（如击杀回血、击杀后增益效果、击杀计数）。</p>
	 *
	 * <p>由 Fabric API {@code ServerLivingEntityEvents.AFTER_DEATH} 触发，
	 * 仅当 {@link DamageSource#getEntity()} 为 {@link LivingEntity} 时触发
	 * （即必须有生物击杀者，环境致死不触发）。</p>
	 */
	public static final EnchantmentEventType<PostKillEvent> POST_KILL =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "post_kill"), PostKillEvent.class);

	/**
	 * 修改方块掉落物事件：在 loot table 求值完成、ItemEntity 生成之前触发。
	 *
	 * <p>回调扫描 <b>玩家</b> 的主手 + 副手装备附魔，允许通过 {@link ModifyBlockDropsEvent#drops()}
	 * 修改掉落物列表（替换/添加/删除），并通过 {@link ModifyBlockDropsEvent#addBonusXp(int)} 补充熔炼经验。</p>
	 *
	 * <p>典型用途：自动烧炼附魔（粗铁→铁锭）、掉落物加倍、掉落物转换等。</p>
	 *
	 * <p>由 Mixin {@code BlockDropResourcesMixin} 在 {@code Block.dropResources} 中拦截
	 * {@code Block.getDrops} 调用触发。仅在以下条件全部满足时触发：</p>
	 * <ul>
	 *   <li>破坏者为 {@link ServerPlayer}（爆炸、活塞等非玩家破坏不触发）</li>
	 *   <li>玩家非创造/旁观模式</li>
	 *   <li>原版掉落物列表非空（自然处理"无正确工具不触发"场景）</li>
	 *   <li>存在至少一个 MODIFY_BLOCK_DROPS 回调（O1 位掩码短路）</li>
	 * </ul>
	 *
	 * <p><b>与时运/精准采集的结算顺序</b>：注入点在 loot table 求值之后，实现模组拿到的是
	 * "时运加成后"的掉落——自动烧炼场景这正是期望行为（3 个粗铁 → 3 个铁锭）。</p>
	 *
	 * <p><b>多附魔叠加顺序</b>：同一工具上多个附魔都注册了此事件时，按注册顺序链式处理各自的
	 * drops 修改，后注册者看到前者的修改结果。</p>
	 */
	public static final EnchantmentEventType<ModifyBlockDropsEvent> MODIFY_BLOCK_DROPS =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "modify_block_drops"), ModifyBlockDropsEvent.class);

	/**
	 * 方块破坏后事件（纯通知）：玩家破坏方块后触发。
	 *
	 * <p>回调扫描 <b>玩家</b> 的主手 + 副手装备附魔。基于 Fabric API
	 * {@code PlayerBlockBreakEvents.AFTER} 实现，无需 Mixin。</p>
	 *
	 * <p>典型用途：破坏方块回蓝、连锁触发、破坏计数等。</p>
	 *
	 * <p><b>注意</b>：连锁挖矿类实现请在文档中提醒——在回调里再破坏方块会重入本事件，
	 * 实现模组需自带递归标记。</p>
	 */
	public static final EnchantmentEventType<PostBlockBreakEvent> POST_BLOCK_BREAK =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "post_block_break"), PostBlockBreakEvent.class);

	/**
	 * 物品使用事件：玩家右键使用物品时触发。
	 *
	 * <p>回调扫描 <b>触发手</b>（MAINHAND 或 OFFHAND）的装备附魔。基于 Fabric API
	 * {@code UseItemCallback} 实现。</p>
	 *
	 * <p>典型用途：右键释放技能类附魔。</p>
	 *
	 * <p><b>取消语义</b>：回调可通过 {@link ItemUseEvent#setResult(InteractionResult)}
	 * 设置非 PASS 结果中断原版行为，第一个非 PASS 结果生效并停止后续分发。</p>
	 */
	public static final EnchantmentEventType<ItemUseEvent> ITEM_USE =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "item_use"), ItemUseEvent.class);

	/**
	 * 方块交互事件：玩家右键方块时触发。
	 *
	 * <p>回调扫描 <b>触发手</b>（MAINHAND 或 OFFHAND）的装备附魔。基于 Fabric API
	 * {@code UseBlockCallback} 实现。</p>
	 *
	 * <p>典型用途：右键方块转换、耕地类附魔。</p>
	 *
	 * <p><b>注意</b>：原版主副手会各触发一次，前置层面按 hand 分发，文档提醒实现模组注意。</p>
	 *
	 * <p><b>取消语义</b>：同 {@link #ITEM_USE}。</p>
	 */
	public static final EnchantmentEventType<BlockUseEvent> BLOCK_USE =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "block_use"), BlockUseEvent.class);

	/**
	 * 实体交互事件：玩家右键实体时触发。
	 *
	 * <p>回调扫描 <b>触发手</b>（MAINHAND 或 OFFHAND）的装备附魔。基于 Fabric API
	 * {@code UseEntityCallback} 实现。</p>
	 *
	 * <p>典型用途：右键实体标记、驯服增强类附魔。</p>
	 *
	 * <p><b>取消语义</b>：同 {@link #ITEM_USE}。</p>
	 */
	public static final EnchantmentEventType<EntityUseEvent> ENTITY_USE =
		EnchantmentEventType.create(Identifier.fromNamespaceAndPath("enchantlib", "entity_use"), EntityUseEvent.class);

	// O5 槽位声明：在所有事件类型创建后，设置各自的关注槽位
	static {
		POST_ATTACK.setRelevantSlots(ATTACK_SLOTS);
		MODIFY_DAMAGE.setRelevantSlots(ATTACK_SLOTS);
		PROJECTILE_HIT.setRelevantSlots(ATTACK_SLOTS);
		POST_KILL.setRelevantSlots(ATTACK_SLOTS);
		POST_HURT.setRelevantSlots(DEFENSE_SLOTS);
		MODIFY_BLOCK_DROPS.setRelevantSlots(ATTACK_SLOTS);
		POST_BLOCK_BREAK.setRelevantSlots(ATTACK_SLOTS);
		// 交互事件按触发手扫描（dispatchInteraction 用 scanSlot 指定槽位，O5 声明仅作上限参考）
		ITEM_USE.setRelevantSlots(ATTACK_SLOTS);
		BLOCK_USE.setRelevantSlots(ATTACK_SLOTS);
		ENTITY_USE.setRelevantSlots(ATTACK_SLOTS);
		// ENTITY_TICK 保持默认（所有槽位）
	}

	/**
	 * 攻击后事件。
	 *
	 * <p>由 Mixin {@code PlayerAttackMixin} 在 {@code Player.attack(Entity)} RETURN 处触发，
	 * 携带攻击充能、暴击、横扫快照。</p>
	 *
	 * @param level       服务端世界
	 * @param attacker    攻击者（事件主体，回调扫描其装备附魔）
	 * @param target      被攻击的目标
	 * @param charge      攻击充能比例 (0.0~1.0)，由 {@code getAttackStrengthScale(0.5F)} 获取
	 * @param isCritical  是否为暴击（{@code charge > 0.9 && canCriticalAttack(target)}）
	 * @param isSweep     是否为横扫攻击（{@code isSweepAttack(isFullyCharged, isCritical, isSprintingKnockback)}）
	 */
	public record PostAttackEvent(
		ServerLevel level,
		LivingEntity attacker,
		LivingEntity target,
		float charge,
		boolean isCritical,
		boolean isSweep
	) implements ChargeableEvent {
		@Override
		public LivingEntity getEntity() {
			return attacker;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}

		@Override
		public float charge() {
			return charge;
		}
	}

	/**
	 * 实体 tick 事件。
	 *
	 * @param level    服务端世界
	 * @param entity   触发 tick 的实体（回调扫描其装备附魔）
	 * @param tickCount 服务端 tick 计数（{@code server.getTickCount()}）
	 */
	public record EntityTickEvent(
		ServerLevel level,
		LivingEntity entity,
		int tickCount
	) implements EnchantmentEvent {
		@Override
		public LivingEntity getEntity() {
			return entity;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}
	}

	/**
	 * 弹射物命中事件。
	 *
	 * <p>由 Mixin {@code ProjectileWeaponMixin} 在 {@code Projectile.onHitEntity} RETURN 处触发，
	 * 覆盖所有弹射物（箭、三叉戟、雪球、药水、末影珍珠、烟花火箭等）。</p>
	 *
	 * <p>实现 {@link ChargeableEvent}，携带 {@code drawStrength}（拉弓程度），
	 * 让 {@link TriggerPolicy} 可基于拉弓程度门控 PROJECTILE_HIT 事件。
	 * 非弓类弹射物（雪球/药水等）drawStrength 默认为 1.0f。</p>
	 *
	 * @param level       服务端世界
	 * @param attacker    发射者（事件主体，回调扫描其装备附魔；通过 {@code projectile.getOwner()} 获取）
	 * @param target      命中的目标
	 * @param weapon      发射武器快照（AbstractArrow 用 {@code getWeaponItem()}，非箭类用发射者主手物品）
	 * @param isCritArrow 是否为暴击箭（拉满弓射出的箭，仅 AbstractArrow 支持，非箭类为 false）
	 * @param drawStrength 拉弓程度 (0.0~1.0)，弓通过 BowItemMixin 捕获，非弓类默认 1.0f
	 */
	public record ProjectileHitEvent(
		ServerLevel level,
		LivingEntity attacker,
		LivingEntity target,
		ItemStack weapon,
		boolean isCritArrow,
		float drawStrength
	) implements EnchantmentEvent, ChargeableEvent {
		@Override
		public LivingEntity getEntity() {
			return attacker;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}

		@Override
		public float charge() {
			return drawStrength;
		}
	}

	/**
	 * 伤害修改事件。
	 *
	 * <p>由 Mixin {@code LivingEntityHurtMixin} 在 {@code LivingEntity.hurtServer} HEAD 处触发，
	 * 在伤害应用到目标前，允许攻击者的装备附魔修改伤害值。</p>
	 *
	 * <p><b>修改伤害的方式</b>：通过 {@link #damage()} 返回的 {@link MutableFloat} 进行修改，
	 * 例如 {@code event.damage().add(2.0f)} 或 {@code event.damage().multiply(1.5f)}。</p>
	 *
	 * @param level         服务端世界
	 * @param attacker      攻击者（事件主体，回调扫描其装备附魔；通过 {@code damageSource.getEntity()} 获取）
	 * @param target        被攻击的目标
	 * @param source        伤害来源
	 * @param originalDamage 原始伤害值（不可变参考，回调前后不会改变）
	 * @param damage        可修改的伤害值（通过 {@link MutableFloat#add(float)}、
	 *                      {@link MutableFloat#multiply(float)} 等方法修改）
	 */
	public record ModifyDamageEvent(
		ServerLevel level,
		LivingEntity attacker,
		LivingEntity target,
		DamageSource source,
		float originalDamage,
		MutableFloat damage
	) implements EnchantmentEvent {
		@Override
		public LivingEntity getEntity() {
			return attacker;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}
	}

	/**
	 * 受击后事件。
	 *
	 * <p>由 Fabric API {@code ServerLivingEntityEvents.AFTER_DAMAGE} 在伤害完全应用后触发，
	 * 扫描目标（被攻击者）的装备附魔。</p>
	 *
	 * <p>所有伤害类型均触发（包括环境伤害如摔落/火焰），允许防御类附魔对所有伤害生效。
	 * 若伤害来源无生物攻击者（环境伤害），{@code attacker} 为 {@code null}。</p>
	 *
	 * @param level         服务端世界
	 * @param target        被攻击的目标（事件主体，回调扫描其装备附魔）
	 * @param attacker      攻击者（可能为 null，环境伤害时无攻击者）
	 * @param source        伤害来源
	 * @param amount        实际造成的伤害值（已扣除护甲/护盾）
	 * @param blockedDamage 被护盾格挡的伤害值
	 * @param blocked       是否被完全格挡
	 */
	public record PostHurtEvent(
		ServerLevel level,
		LivingEntity target,
		LivingEntity attacker,
		DamageSource source,
		float amount,
		float blockedDamage,
		boolean blocked
	) implements EnchantmentEvent {
		@Override
		public LivingEntity getEntity() {
			return target;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}
	}

	/**
	 * 击杀后事件。
	 *
	 * <p>由 Fabric API {@code ServerLivingEntityEvents.AFTER_DEATH} 在实体死亡后触发，
	 * 扫描击杀者的装备附魔。</p>
	 *
	 * <p>仅当 {@link DamageSource#getEntity()} 为 {@link LivingEntity} 时触发
	 * （即必须有生物击杀者，环境致死不触发）。</p>
	 *
	 * @param level   服务端世界
	 * @param killer  击杀者（事件主体，回调扫描其装备附魔；通过 {@code damageSource.getEntity()} 获取）
	 * @param victim  被击杀的实体
	 * @param source  导致死亡的伤害来源
	 */
	public record PostKillEvent(
		ServerLevel level,
		LivingEntity killer,
		LivingEntity victim,
		DamageSource source
	) implements EnchantmentEvent {
		@Override
		public LivingEntity getEntity() {
			return killer;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}
	}

	/**
	 * 修改方块掉落物事件。
	 *
	 * <p>由 Mixin {@code BlockDropResourcesMixin} 在 {@code Block.dropResources} 中拦截
	 * {@code Block.getDrops} 调用触发，允许回调修改掉落物列表和补充经验。</p>
	 *
	 * <h2>修改能力</h2>
	 * <ul>
	 *   <li>{@link #drops()}：可变 {@link List}，回调可直接 {@code add/remove/set} 修改</li>
	 *   <li>{@link #transformDrops(UnaryOperator)}：便捷方法，对所有掉落物应用转换
	 *       （如自动烧炼：{@code event.transformDrops(SmeltingLookup::smeltOrOriginal)}）</li>
	 *   <li>{@link #addBonusXp(int)}：补充熔炼经验，事件分发后由 Mixin 自动生成经验球</li>
	 * </ul>
	 *
	 * <h2>多附魔叠加顺序</h2>
	 * <p>同一工具上多个附魔都注册了此事件时，按注册顺序链式处理各自的 drops 修改，
	 * 后注册者看到前者的修改结果。回调异常会被独立捕获（Q3 异常隔离），不影响其他回调。</p>
	 *
	 * @param level      服务端世界
	 * @param player     破坏方块的玩家（事件主体，回调扫描其主手/副手装备附魔）
	 * @param pos        方块位置
	 * @param blockState 被破坏的方块状态
	 * @param tool       破坏工具（可能为 {@link ItemStack#EMPTY}）
	 * @param drops      可修改的掉落物列表（loot table 求值结果，含时运加成）
	 * @param bonusXp    可累积的额外经验（通过 {@link #addBonusXp(int)} 累加）
	 */
	public record ModifyBlockDropsEvent(
		ServerLevel level,
		ServerPlayer player,
		BlockPos pos,
		BlockState blockState,
		ItemStack tool,
		List<ItemStack> drops,
		MutableInt bonusXp
	) implements EnchantmentEvent {
		@Override
		public LivingEntity getEntity() {
			return player;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}

		/**
		 * 累加额外经验（自动烧炼通常要补熔炼经验）。
		 *
		 * @param amount 经验量（>= 0）
		 */
		public void addBonusXp(int amount) {
			if (amount > 0) {
				bonusXp.add(amount);
			}
		}

		/**
		 * 对所有掉落物应用转换。原掉落物会被转换结果替换。
		 *
		 * <p>典型用法（自动烧炼）：</p>
		 * <pre>{@code
		 * event.transformDrops(stack -> SmeltingLookup.smelt(stack.getItem())
		 *     .map(smelting -> new ItemStack(smelting, stack.getCount()))
		 *     .orElse(stack));
		 * }</pre>
		 *
		 * @param transformer 转换函数（输入原掉落物，输出新掉落物；返回 {@link ItemStack#EMPTY} 表示删除）
		 */
		public void transformDrops(UnaryOperator<ItemStack> transformer) {
			for (int i = drops.size() - 1; i >= 0; i--) {
				ItemStack transformed = transformer.apply(drops.get(i));
				if (transformed.isEmpty()) {
					drops.remove(i);
				} else {
					drops.set(i, transformed);
				}
			}
		}
	}

	/**
	 * 方块破坏后事件（纯通知）。
	 *
	 * <p>由 Fabric API {@code PlayerBlockBreakEvents.AFTER} 在方块完全破坏后触发，
	 * 扫描玩家的主手/副手装备附魔。适用于破坏方块回蓝、连锁触发、破坏计数等。</p>
	 *
	 * <p><b>注意</b>：连锁挖矿类实现需自带递归标记——在回调里再破坏方块会重入本事件。</p>
	 *
	 * @param level      服务端世界
	 * @param player     破坏方块的玩家（事件主体，回调扫描其主手/副手装备附魔）
	 * @param pos        被破坏的方块位置
	 * @param blockState 被破坏的方块状态
	 * @param tool       破坏工具（可能为 {@link ItemStack#EMPTY}）
	 */
	public record PostBlockBreakEvent(
		ServerLevel level,
		ServerPlayer player,
		BlockPos pos,
		BlockState blockState,
		ItemStack tool
	) implements EnchantmentEvent {
		@Override
		public LivingEntity getEntity() {
			return player;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}
	}

	/**
	 * 物品使用事件。
	 *
	 * <p>由 Fabric API {@code UseItemCallback} 在玩家右键使用物品时触发，
	 * 扫描触发手的装备附魔。适用于右键释放技能类附魔。</p>
	 *
	 * <p><b>取消语义</b>：回调可通过 {@link #setResult(InteractionResult)} 设置非 PASS 结果
	 * 中断原版行为，第一个非 PASS 结果生效并停止后续分发。</p>
	 *
	 * @param level     服务端世界
	 * @param player    使用物品的玩家（事件主体，回调扫描其触发手装备附魔）
	 * @param hand      触发手（MAINHAND 或 OFFHAND）
	 * @param itemStack 使用的物品（触发手中的物品）
	 * @param result    可变的交互结果（初始为 {@link InteractionResult#PASS}）
	 */
	public record ItemUseEvent(
		ServerLevel level,
		ServerPlayer player,
		InteractionHand hand,
		ItemStack itemStack,
		Mutable<InteractionResult> resultHolder
	) implements InteractionEnchantmentEvent {
		@Override
		public LivingEntity getEntity() {
			return player;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}

		@Override
		public InteractionResult result() {
			return resultHolder.getValue();
		}

		@Override
		public void setResult(InteractionResult result) {
			this.resultHolder.setValue(result);
		}
	}

	/**
	 * 方块交互事件。
	 *
	 * <p>由 Fabric API {@code UseBlockCallback} 在玩家右键方块时触发，
	 * 扫描触发手的装备附魔。适用于右键方块转换、耕地类附魔。</p>
	 *
	 * <p><b>注意</b>：原版主副手会各触发一次，前置层面按 hand 分发。</p>
	 *
	 * @param level     服务端世界
	 * @param player    右键方块的玩家（事件主体，回调扫描其触发手装备附魔）
	 * @param hand      触发手（MAINHAND 或 OFFHAND）
	 * @param hitResult 方块命中结果（含命中位置、方向等）
	 * @param result    可变的交互结果（初始为 {@link InteractionResult#PASS}）
	 */
	public record BlockUseEvent(
		ServerLevel level,
		ServerPlayer player,
		InteractionHand hand,
		BlockHitResult hitResult,
		Mutable<InteractionResult> resultHolder
	) implements InteractionEnchantmentEvent {
		@Override
		public LivingEntity getEntity() {
			return player;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}

		@Override
		public InteractionResult result() {
			return resultHolder.getValue();
		}

		@Override
		public void setResult(InteractionResult result) {
			this.resultHolder.setValue(result);
		}
	}

	/**
	 * 实体交互事件。
	 *
	 * <p>由 Fabric API {@code UseEntityCallback} 在玩家右键实体时触发，
	 * 扫描触发手的装备附魔。适用于右键实体标记、驯服增强类附魔。</p>
	 *
	 * @param level     服务端世界
	 * @param player    右键实体的玩家（事件主体，回调扫描其触发手装备附魔）
	 * @param hand      触发手（MAINHAND 或 OFFHAND）
	 * @param entity    被右键的实体
	 * @param hitResult 实体命中结果（含命中位置等）
	 * @param result    可变的交互结果（初始为 {@link InteractionResult#PASS}）
	 */
	public record EntityUseEvent(
		ServerLevel level,
		ServerPlayer player,
		InteractionHand hand,
		Entity entity,
		EntityHitResult hitResult,
		Mutable<InteractionResult> resultHolder
	) implements InteractionEnchantmentEvent {
		@Override
		public LivingEntity getEntity() {
			return player;
		}

		@Override
		public ServerLevel getLevel() {
			return level;
		}

		@Override
		public InteractionResult result() {
			return resultHolder.getValue();
		}

		@Override
		public void setResult(InteractionResult result) {
			this.resultHolder.setValue(result);
		}
	}
}
