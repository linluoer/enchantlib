package com.enchantlib.resources;

import com.enchantlib.EnchantLib;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

/**
 * 约定目录扫描器。
 *
 * <p>扫描所有已加载模组的 {@code assets/<modid>/enchant_sync/} 目录，
 * 收集客户端资源（主要是语言文件），为语言合并和运行时资源包构建提供数据源。</p>
 *
 * <h2>目录约定</h2>
 * <pre>{@code
 * assets/<modid>/enchant_sync/
 * ├── lang/
 * │   ├── en_us.json       # 英文翻译（附魔名称、描述）
 * │   └── zh_cn.json       # 中文翻译
 * ├── textures/            # 纹理（附魔光效、物品贴图等）
 * ├── particles/           # 粒子定义
 * ├── sounds/              # 音效文件
 * └── ...
 * }</pre>
 *
 * <h2>扫描流程</h2>
 * <ol>
 *   <li>遍历所有已加载模组（{@code FabricLoader.getInstance().getAllMods()}）</li>
 *   <li>对每个模组，查找 {@code assets/<modid>/enchant_sync/} 目录</li>
 *   <li>递归遍历目录，收集所有文件</li>
 *   <li>返回 {@code Map<Identifier, byte[]>}（资源路径 → 文件内容）</li>
 * </ol>
 *
 * <h2>Identifier 约定</h2>
 * <ul>
 *   <li>namespace = 模组 ID</li>
 *   <li>path = {@code enchant_sync/<相对路径>}（如 {@code enchant_sync/lang/en_us.json}）</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class EnchantSyncScanner {

	/** 目录约定前缀 */
	private static final String ASSETS_PREFIX = "assets/";

	/** enchant_sync 目录名 */
	private static final String ENCHANT_SYNC_DIR = "enchant_sync";

	/** 扫描结果：资源路径 → 文件内容 */
	private static Map<Identifier, byte[]> scannedResources = Collections.emptyMap();

	private EnchantSyncScanner() {
	}

	/**
	 * 执行全量扫描。
	 *
	 * <p>遍历所有已加载模组，收集 {@code assets/<modid>/enchant_sync/} 目录下的所有文件。
	 * 扫描结果存储在静态字段中，可通过 {@link #getResources()} 获取。</p>
	 *
	 * @return 扫描到的资源数量
	 */
	public static int scan() {
		Map<Identifier, byte[]> results = new HashMap<>();
		int modCount = 0;

		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String modId = mod.getMetadata().getId();
			String dirPath = ASSETS_PREFIX + modId + "/" + ENCHANT_SYNC_DIR;

			try {
				var pathOpt = mod.findPath(dirPath);
				if (pathOpt.isEmpty()) {
					continue;
				}

				Path syncDir = pathOpt.get();
				if (!Files.isDirectory(syncDir)) {
					continue;
				}

				modCount++;
				int fileCount = scanDirectory(modId, syncDir, results);
				EnchantLib.LOGGER.info("[EnchantLib] 扫描模组 {} 的 enchant_sync 目录: {} 个文件",
					modId, fileCount);
			} catch (IOException e) {
				EnchantLib.LOGGER.warn("[EnchantLib] 扫描模组 {} 的 enchant_sync 目录失败: {}",
					modId, e.getMessage());
			}
		}

		scannedResources = Collections.unmodifiableMap(results);

		EnchantLib.LOGGER.info("[EnchantLib] 目录扫描完成: 扫描了 {} 个模组，收集到 {} 个资源文件",
			modCount, results.size());

		return results.size();
	}

	/**
	 * 递归扫描目录，收集所有文件。
	 *
	 * @param modId    模组 ID（用于构建 Identifier namespace）
	 * @param syncDir  enchant_sync 目录路径
	 * @param results  结果收集 Map
	 * @return 扫描到的文件数量
	 * @throws IOException 如果遍历失败
	 */
	private static int scanDirectory(String modId, Path syncDir, Map<Identifier, byte[]> results)
		throws IOException {
		int count = 0;
		try (Stream<Path> stream = Files.walk(syncDir)) {
			var fileList = stream.filter(Files::isRegularFile).toList();
			for (Path file : fileList) {
				String relativePath = syncDir.relativize(file).toString()
					.replace('\\', '/'); // Windows 路径兼容

				String resourcePath = ENCHANT_SYNC_DIR + "/" + relativePath;
				Identifier id = Identifier.fromNamespaceAndPath(modId, resourcePath);

				byte[] content = Files.readAllBytes(file);
				results.put(id, content);
				count++;

				EnchantLib.LOGGER.debug("[EnchantLib] 发现资源: {} ({} bytes)", id, content.length);
			}
		}
		return count;
	}

	/**
	 * 获取扫描结果。
	 *
	 * @return 不可变的资源映射（Identifier → 文件内容）
	 */
	public static Map<Identifier, byte[]> getResources() {
		return scannedResources;
	}

	/**
	 * 获取扫描到的资源数量。
	 *
	 * @return 资源数量
	 */
	public static int resourceCount() {
		return scannedResources.size();
	}

	/**
	 * 获取指定 namespace 的所有资源。
	 *
	 * @param namespace 模组 ID
	 * @return 该模组的所有 enchant_sync 资源
	 */
	public static Map<Identifier, byte[]> getResources(String namespace) {
		Map<Identifier, byte[]> filtered = new HashMap<>();
		for (var entry : scannedResources.entrySet()) {
			if (entry.getKey().getNamespace().equals(namespace)) {
				filtered.put(entry.getKey(), entry.getValue());
			}
		}
		return filtered;
	}

	/**
	 * 获取所有语言文件。
	 *
	 * <p>筛选路径以 {@code enchant_sync/lang/} 开头的资源。</p>
	 *
	 * @return 语言文件映射（Identifier → 文件内容）
	 */
	public static Map<Identifier, byte[]> getLangFiles() {
		Map<Identifier, byte[]> langFiles = new HashMap<>();
		String langPrefix = ENCHANT_SYNC_DIR + "/lang/";
		for (var entry : scannedResources.entrySet()) {
			if (entry.getKey().getPath().startsWith(langPrefix)) {
				langFiles.put(entry.getKey(), entry.getValue());
			}
		}
		return langFiles;
	}
}
