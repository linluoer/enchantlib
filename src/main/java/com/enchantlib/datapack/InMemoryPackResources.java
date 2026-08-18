package com.enchantlib.datapack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * 内存数据包资源实现，从内存中的 Map 提供资源内容。
 *
 * <p>用于在运行时注入附魔定义等数据包内容（SERVER_DATA）或
 * enchant_sync 客户端资源（CLIENT_RESOURCES），无需写入磁盘文件。</p>
 *
 * @since 0.1.0
 */
public class InMemoryPackResources implements PackResources {
	private final PackLocationInfo location;
	private final PackMetadataSection metadata;
	private final Map<Identifier, byte[]> resources;
	private final Set<String> namespaces;

	public InMemoryPackResources(PackLocationInfo location, PackMetadataSection metadata, Map<Identifier, byte[]> resources) {
		this.location = location;
		this.metadata = metadata;
		this.resources = Map.copyOf(resources);
		this.namespaces = resources.keySet().stream()
			.map(Identifier::getNamespace)
			.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
		return null;
	}

	@Override
	public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
		byte[] content = this.resources.get(location);
		return content != null ? () -> new ByteArrayInputStream(content) : null;
	}

	@Override
	public void listResources(PackType type, String namespace, String directory, PackResources.ResourceOutput output) {
		String prefix = directory.endsWith("/") ? directory : directory + "/";
		for (Map.Entry<Identifier, byte[]> entry : this.resources.entrySet()) {
			Identifier id = entry.getKey();
			if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
				output.accept(id, () -> new ByteArrayInputStream(entry.getValue()));
			}
		}
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		return this.namespaces;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) throws IOException {
		if (type == PackMetadataSection.SERVER_TYPE
			|| type == PackMetadataSection.CLIENT_TYPE
			|| type == PackMetadataSection.FALLBACK_TYPE) {
			return (T) this.metadata;
		}
		return null;
	}

	@Override
	public PackLocationInfo location() {
		return this.location;
	}

	@Override
	public void close() {
	}
}
