package com.enchantlib.resources;

import com.enchantlib.EnchantLib;
import com.enchantlib.datapack.InMemoryPackResources;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

/**
 * 运行时客户端资源包内容管理。
 *
 * <p>当玩家在客户端安装了 EnchantLib 及其附属模组时（单机或连接任意服务器），
 * 将所有模组 {@code assets/<namespace>/enchant_sync/} 目录下的资源以
 * 内存资源包形式注入客户端资源系统，提供与"服务端资源包推送"等价的本地回退——
 * 确保附魔翻译、纹理等客户端内容不因缺少服务端推送而缺失。</p>
 *
 * <h2>路径映射规则</h2>
 * <pre>{@code
 * 扫描结果（enchant_sync 约定路径）        → 客户端标准资源路径
 * <ns>:enchant_sync/lang/en_us.json       → <ns>:lang/en_us.json
 * <ns>:enchant_sync/textures/xxx.png      → <ns>:textures/xxx.png
 * }</pre>
 *
 * <p>语言文件保留原命名空间（Minecraft 客户端会加载所有命名空间下的
 * {@code lang/<code>.json}），其他资源同样映射到标准位置。</p>
 *
 * <h2>注入方式</h2>
 * <p>通过 {@code MinecraftMixin} 在客户端资源包仓库初始化时追加
 * {@code RepositorySource}，提供 required=true 的内置资源包
 * （始终自动启用，无需玩家操作，外部资源包仍可覆盖其内容）。</p>
 *
 * @since 1.1.0
 */
public final class RuntimeClientPackContent {

	/** 内存客户端资源包的唯一 ID */
	public static final String PACK_ID = "enchantlib:client_sync";

	/** 内存客户端资源包的显示名称 */
	private static final String PACK_NAME = "EnchantLib Client Sync";

	private RuntimeClientPackContent() {
	}

	/**
	 * 构建客户端内存资源包。
	 *
	 * <p>从 {@link EnchantSyncScanner#getResources()} 获取扫描结果，
	 * 去掉 {@code enchant_sync/} 路径前缀后映射为标准客户端资源路径。
	 * 若无任何资源则返回 null（不注入空包）。</p>
	 *
	 * @return {@link Pack} 实例；若无资源或元数据读取失败返回 null
	 */
	public static Pack createPack() {
		Map<Identifier, byte[]> scanned = EnchantSyncScanner.getResources();
		if (scanned.isEmpty()) {
			return null;
		}

		// 路径转换：enchant_sync/<path> → <path>（保留命名空间）
		Map<Identifier, byte[]> resources = new LinkedHashMap<>();
		String prefix = "enchant_sync/";
		for (Map.Entry<Identifier, byte[]> entry : scanned.entrySet()) {
			Identifier id = entry.getKey();
			String path = id.getPath();
			if (!path.startsWith(prefix)) {
				continue;
			}
			Identifier mapped = Identifier.fromNamespaceAndPath(
				id.getNamespace(), path.substring(prefix.length()));
			resources.put(mapped, entry.getValue());
		}

		if (resources.isEmpty()) {
			return null;
		}

		PackMetadataSection metadata = new PackMetadataSection(
			Component.literal(PACK_NAME),
			SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).minorRange()
		);

		PackLocationInfo location = new PackLocationInfo(
			PACK_ID,
			Component.literal(PACK_NAME),
			PackSource.BUILT_IN,
			Optional.empty()
		);

		InMemoryPackResources packResources = new InMemoryPackResources(location, metadata, resources);

		// required=true 保证包始终被选中；Position.BOTTOM 保证最低优先级（外部包可覆盖）
		PackSelectionConfig selectionConfig = new PackSelectionConfig(true, Pack.Position.BOTTOM, false);

		Pack pack = Pack.readMetaAndCreate(location, fixedResources(packResources),
			PackType.CLIENT_RESOURCES, selectionConfig);
		if (pack != null) {
			EnchantLib.LOGGER.info("[EnchantLib] 客户端本地资源包已注入: {} ({} 个资源)",
				PACK_ID, resources.size());
		}
		return pack;
	}

	private static Pack.ResourcesSupplier fixedResources(InMemoryPackResources instance) {
		return new Pack.ResourcesSupplier() {
			@Override
			public PackResources openPrimary(PackLocationInfo location) {
				return instance;
			}

			@Override
			public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
				return instance;
			}
		};
	}
}
