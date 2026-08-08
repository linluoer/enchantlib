package com.enchantlib.mixin;

import com.enchantlib.api.EntityCategory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mob 目标选择拦截:同阵营生物不攻击被标记为同分类的玩家。
 *
 * <p>当 mob 尝试 {@code setTarget} 时,若目标是玩家且该玩家被
 * {@link EntityCategory} 标记为与 mob 同阵营的分类,则取消目标变更。</p>
 *
 * <h2>阵营判定</h2>
 * <ul>
 *   <li>玩家被标记为 {@link EntityCategory.Category#UNDEAD},且 mob 本身属于亡灵
 *       (查 {@code EntityTypeTags.UNDEAD})→ 不攻击</li>
 *   <li>节肢动物/灾厄村民/水生生物同理</li>
 * </ul>
 *
 * <h2>数据源</h2>
 * <p>mob 本身的分类查询使用原版 {@code EntityTypeTags} 标签,
 * 自动支持其他模组通过标签注册的同类生物。</p>
 *
 * @since 0.1.0
 */
@Mixin(Mob.class)
public abstract class MobMixin {

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void enchantlib$filterTarget(LivingEntity target, CallbackInfo ci) {
		if (target == null) {
			return;
		}
		if (!(target instanceof ServerPlayer player)) {
			return;
		}

		Mob mob = (Mob) (Object) this;

		// 同阵营判定:玩家被标记的分类与 mob 本身的分类一致则不攻击
		// 遍历所有分类,玩家被标记的任一分类若 mob 也属于,则取消目标
		for (EntityCategory.Category category : EntityCategory.Category.values()) {
			if (EntityCategory.has(player, category) && mob.is(category.tag())) {
				ci.cancel();
				return;
			}
		}
	}
}
