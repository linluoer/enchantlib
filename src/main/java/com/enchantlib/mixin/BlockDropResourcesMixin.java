package com.enchantlib.mixin;

import com.enchantlib.debug.DebugLogger;
import com.enchantlib.event.BuiltInEvents;
import com.enchantlib.event.EnchantmentEventDispatcher;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 方块掉落物修改事件捕获与分发。
 *
 * <p>在 {@link Block#dropResources} 6 参数重载（带 breaker 的玩家破坏路径）中，
 * 通过 MixinExtras {@link WrapOperation} 拦截 {@link Block#getDrops} 6 参数重载调用，
 * 让回调在 loot table 求值完成、ItemEntity 生成之前修改掉落物列表。</p>
 *
 * <h2>触发条件（按短路顺序）</h2>
 * <ol>
 *   <li><b>O1 位掩码短路</b>：若无任何 MODIFY_BLOCK_DROPS 回调，立即返回原始 drops</li>
 *   <li><b>BLOCK_DROPS 游戏规则</b>：若管理员禁用方块掉落，跳过事件（修改无意义）</li>
 *   <li><b>ServerPlayer 过滤</b>：爆炸、活塞、非玩家破坏不触发</li>
 *   <li><b>创造/旁观模式</b>：防御性检查，避免无意义事件分发</li>
 *   <li><b>空掉落物过滤</b>：自然处理"无正确工具不触发"场景</li>
 * </ol>
 *
 * <h2>兼容性</h2>
 * <p>使用 {@link WrapOperation} 而非 {@code @Redirect}，允许多个模组同时包装同一个
 * {@code getDrops} 调用，避免与 Fabric 端各类 tree feller 模组的 Mixin 冲突。
 * 其他模组的包装器会嵌套调用 {@link Operation#call(Object...)}，最终到达本包装器或原始方法。</p>
 *
 * <h2>异常隔离</h2>
 * <p>事件分发由 {@link EnchantmentEventDispatcher#dispatch} 处理，自带 Q3 异常隔离：
 * 单个回调异常不影响其他回调或原版逻辑。顶层基础设施异常由 dispatch 的顶层 try-catch 兜底。</p>
 *
 * <h2>额外经验生成</h2>
 * <p>事件分发后，若回调通过 {@link BuiltInEvents.ModifyBlockDropsEvent#addBonusXp(int)}
 * 累积了额外经验，且 BLOCK_DROPS 游戏规则启用，则通过 {@link ExperienceOrb#award}
 * 生成经验球（与原版 {@code Block.popExperience} 行为一致）。</p>
 *
 * @since 0.1.0
 */
@Mixin(Block.class)
public abstract class BlockDropResourcesMixin {

	/**
	 * 包装 {@code Block.getDrops} 调用，在掉落物列表返回后、被 {@code forEach} 消费前
	 * 拦截并分发 {@link BuiltInEvents#MODIFY_BLOCK_DROPS} 事件。
	 *
	 * <p>仅针对 {@code dropResources} 6 参数重载（带 breaker 的玩家破坏路径）中的
	 * {@code getDrops} 6 参数重载调用。其他 {@code dropResources}/{@code getDrops} 重载
	 * （无 breaker、爆炸、活塞路径）不受影响。</p>
	 *
	 * @param state       被破坏的方块状态
	 * @param level       服务端世界
	 * @param pos         方块位置
	 * @param blockEntity 方块实体（可能为 null）
	 * @param breaker     破坏者（可能为 null，仅 ServerPlayer 触发事件）
	 * @param tool        破坏工具（ItemInstance 形式，实际为 ItemStack）
	 * @param original    原始 getDrops 调用（可被其他模组的包装器嵌套）
	 * @return 可能被回调修改过的掉落物列表
	 */
	@WrapOperation(
		method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;")
	)
	private static List<ItemStack> enchantlib$modifyBlockDrops(
		BlockState state, ServerLevel level, BlockPos pos,
		@Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemInstance tool,
		Operation<List<ItemStack>> original
	) {
		// 1. 调用原始 getDrops（会先执行其他模组的包装器，最终到达原版 getDrops）
		List<ItemStack> drops = original.call(state, level, pos, blockEntity, breaker, tool);

		// 2. O1 位掩码短路：若无任何 MODIFY_BLOCK_DROPS 回调，直接返回
		if (!EnchantmentEventDispatcher.hasCallbacks(BuiltInEvents.MODIFY_BLOCK_DROPS)) {
			return drops;
		}

		// 3. BLOCK_DROPS 游戏规则检查：管理员禁用方块掉落时跳过事件（修改无意义）
		if (!level.getGameRules().get(GameRules.BLOCK_DROPS)) {
			return drops;
		}

		// 4. 仅对 ServerPlayer 触发（爆炸、活塞、非玩家破坏不触发）
		if (!(breaker instanceof ServerPlayer player)) {
			DebugLogger.log("MODIFY_BLOCK_DROPS 跳过: 非玩家破坏 方块={} 破坏者={}",
				state.getBlock().toString(), breaker == null ? "null" : breaker.getClass().getSimpleName());
			return drops;
		}

		// 5. 创造/旁观模式不触发（防御性检查，创造模式通常无掉落）
		if (player.isCreative() || player.isSpectator()) {
			DebugLogger.log("MODIFY_BLOCK_DROPS 跳过: 创造/旁观模式 玩家={}", player.getName().getString());
			return drops;
		}

		// 6. 无掉落物时不触发（自然处理"无正确工具不触发"场景）
		if (drops.isEmpty()) {
			DebugLogger.log("MODIFY_BLOCK_DROPS 跳过: 无掉落物 方块={} 玩家={}",
				state.getBlock().toString(), player.getName().getString());
			return drops;
		}

		DebugLogger.log("MODIFY_BLOCK_DROPS 入口: 玩家={} 方块={} 掉落数={} 工具={}",
			player.getName().getString(), state.getBlock().toString(),
			drops.size(), tool.getClass().getSimpleName());

		// 7. 确保掉落物列表可变（getDrops 可能返回不可变 List，回调需通过 add/remove/set 修改）
		List<ItemStack> mutableDrops = new ArrayList<>(drops);

		// 8. 构造并分发事件（O2/O5 在 scan 阶段生效：仅扫描玩家主手/副手的有回调附魔）
		MutableInt bonusXp = new MutableInt(0);
		ItemStack toolStack = tool instanceof ItemStack is ? is : ItemStack.EMPTY;
		BuiltInEvents.ModifyBlockDropsEvent event = new BuiltInEvents.ModifyBlockDropsEvent(
			level, player, pos, state, toolStack, mutableDrops, bonusXp
		);
		EnchantmentEventDispatcher.dispatch(BuiltInEvents.MODIFY_BLOCK_DROPS, event, player);

		DebugLogger.log("MODIFY_BLOCK_DROPS 出口: 玩家={} 方块={} 修改后掉落数={} 经验={}",
			player.getName().getString(), state.getBlock().toString(),
			mutableDrops.size(), bonusXp.intValue());

		// 9. 分发累积的额外经验（自动烧炼通常要补熔炼经验）
		if (bonusXp.intValue() > 0) {
			ExperienceOrb.award(level, Vec3.atCenterOf(pos), bonusXp.intValue());
		}

		return mutableDrops;
	}
}
