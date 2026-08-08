package com.enchantlib.mixin;

import com.enchantlib.EnchantLib;
import com.enchantlib.datapack.RuntimeDatapackContent;
import java.util.function.Consumer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 将 EnchantLib 运行时内存数据包注入服务端数据包列表。
 *
 * <p>在 BuiltInPackSource.loadPacks 返回时追加内存包，
 * 仅对 SERVER_DATA 类型生效，确保不影响客户端资源加载。</p>
 *
 * @since 0.1.0
 */
@Mixin(BuiltInPackSource.class)
public abstract class BuiltInPackSourceMixin {

	@Shadow
	@Final
	private PackType packType;

	@Inject(method = "loadPacks", at = @At("RETURN"))
	private void enchantlib$addRuntimePack(Consumer<Pack> result, CallbackInfo ci) {
		if (this.packType == PackType.SERVER_DATA) {
			Pack pack = RuntimeDatapackContent.createPack();
			if (pack != null) {
				result.accept(pack);
				EnchantLib.LOGGER.info("[EnchantLib] 运行时内存数据包已注入: {}", pack.getId());
			}
		}
	}
}
