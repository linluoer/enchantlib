package com.enchantlib.datapack;

import com.enchantlib.api.EnchantmentBuilder;
import java.nio.charset.StandardCharsets;
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
 * 运行时内存数据包构建器。
 *
 * <p>提供通用链式 API，支持添加附魔定义和任意资源，最终构建为 {@link Pack} 实例
 * 注入服务端数据包列表。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * RuntimeDatapackBuilder.create("mymod:runtime", "MyMod Runtime")
 *     .addEnchantment(EnchantmentBuilder.create("mymod:my_enchant")
 *         .description("My Enchantment")
 *         .supportedItems("#minecraft:enchantable/sharp_weapon")
 *         .weight(10).maxLevel(5)
 *         .minCost(1, 11).maxCost(21, 11).anvilCost(1)
 *         .slots("mainhand"))
 *     .addResource(Identifier.fromNamespaceAndPath("mymod", "tags/enchantable/custom.json"),
 *         "{\"replace\":false,\"values\":[]}")
 *     .build();
 * }</pre>
 *
 * @since 0.1.0
 */
public class RuntimeDatapackBuilder {

	private final String packId;
	private final String packName;
	private final Map<Identifier, byte[]> resources = new LinkedHashMap<>();

	/**
	 * 创建构建器。
	 *
	 * @param packId   数据包唯一 ID（如 "enchantlib:runtime"）
	 * @param packName 数据包显示名称（如 "EnchantLib Runtime"）
	 * @return 构建器实例
	 */
	public static RuntimeDatapackBuilder create(String packId, String packName) {
		return new RuntimeDatapackBuilder(packId, packName);
	}

	private RuntimeDatapackBuilder(String packId, String packName) {
		this.packId = packId;
		this.packName = packName;
	}

	/**
	 * 添加附魔定义。资源路径自动生成为 {@code <namespace>/enchantment/<path>.json}。
	 *
	 * @param builder 附魔构建器
	 * @return this
	 */
	public RuntimeDatapackBuilder addEnchantment(EnchantmentBuilder builder) {
		Identifier id = builder.getId();
		Identifier resourceId = Identifier.fromNamespaceAndPath(
			id.getNamespace(), "enchantment/" + id.getPath() + ".json");
		return addResource(resourceId, builder.toBytes());
	}

	/**
	 * 添加任意资源。
	 *
	 * @param id      资源 ID（namespace:path/to/file.json）
	 * @param content 资源内容字节数组
	 * @return this
	 */
	public RuntimeDatapackBuilder addResource(Identifier id, byte[] content) {
		byte[] previous = this.resources.put(id, content);
		if (previous != null) {
			throw new IllegalStateException("运行时数据包中存在重复资源: " + id);
		}
		return this;
	}

	/**
	 * 添加任意资源（字符串内容）。
	 *
	 * @param id      资源 ID
	 * @param content 资源内容（UTF-8 编码）
	 * @return this
	 */
	public RuntimeDatapackBuilder addResource(Identifier id, String content) {
		return addResource(id, content.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 获取已添加资源数量。
	 *
	 * @return 资源数量
	 */
	public int size() {
		return this.resources.size();
	}

	/**
	 * 判断是否无资源。
	 *
	 * @return true 表示无资源
	 */
	public boolean isEmpty() {
		return this.resources.isEmpty();
	}

	/**
	 * 构建内存数据包实例。
	 *
	 * @return {@link Pack} 实例；若元数据读取失败则返回 null
	 */
	public Pack build() {
		PackMetadataSection metadata = new PackMetadataSection(
			Component.literal(this.packName),
			SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).minorRange()
		);

		PackLocationInfo location = new PackLocationInfo(
			this.packId,
			Component.literal(this.packName),
			PackSource.BUILT_IN,
			Optional.empty()
		);

		InMemoryPackResources packResources = new InMemoryPackResources(location, metadata, this.resources);

		// required=true 保证包始终被选中；Position.BOTTOM 保证最低优先级（外部包可覆盖）
		PackSelectionConfig selectionConfig = new PackSelectionConfig(true, Pack.Position.BOTTOM, false);

		return Pack.readMetaAndCreate(location, fixedResources(packResources), PackType.SERVER_DATA, selectionConfig);
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
