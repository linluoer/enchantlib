package com.enchantlib.mixin;

import com.enchantlib.resources.RuntimeClientPackContent;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 将 EnchantLib 运行时客户端资源包注入客户端资源包仓库。
 *
 * <p>在 Minecraft 构造器创建 {@link PackRepository} 时，向
 * {@code RepositorySource[]} 数组追加 EnchantLib 的资源来源，
 * 提供 required=true 的内存资源包（{@code enchantlib:client_sync}），
 * 将所有模组 {@code enchant_sync} 目录下的资源映射为标准客户端资源。</p>
 *
 * <p>该包始终自动启用（无需玩家操作），且位于最低优先级
 * （Position.BOTTOM），玩家手动启用的外部资源包仍可覆盖其内容。</p>
 *
 * <p>仅物理客户端加载（mixins.json 的 client 段），专用服务端不受影响。
 * 资源包仓库创建晚于 main entrypoint，此时 EnchantSyncScanner 已完成扫描。</p>
 *
 * @since 1.1.0
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

	@ModifyArg(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V"
		),
		index = 0
	)
	private RepositorySource[] enchantlib$injectClientPackSource(RepositorySource[] originals) {
		RepositorySource[] extended = new RepositorySource[originals.length + 1];
		System.arraycopy(originals, 0, extended, 0, originals.length);
		extended[originals.length] = MinecraftMixin::enchantlib$loadClientPack;
		return extended;
	}

	private static void enchantlib$loadClientPack(java.util.function.Consumer<Pack> result) {
		try {
			Pack pack = RuntimeClientPackContent.createPack();
			if (pack != null) {
				result.accept(pack);
			}
		} catch (Throwable t) {
			com.enchantlib.EnchantLib.LOGGER.error("[EnchantLib] 客户端资源包注入失败: {}", t.getMessage(), t);
		}
	}
}
