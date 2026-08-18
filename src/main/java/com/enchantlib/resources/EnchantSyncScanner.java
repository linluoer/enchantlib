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
 * <p>扫描所有已加载模组中<b>任意命名空间</b>下的 {@code assets/<namespace>/enchant_sync/} 目录，
 * 收集客户端资源（主要是语言文件），为语言合并和运行时资源包构建提供数据源。</p>
 *
 * <h2>目录约定</h2>
 * <pre>{@code
 * assets/<任意命名空间>/enchant_sync/
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
 *   <li>列出模组 assets 目录下的所有子目录（每个子目录即一个命名空间，不限于模组 ID）</li>
 *   <li>查找每个命名空间下的 {@code enchant_sync/} 目录</li>
 *   <li>递归遍历目录，收集所有文件</li>
 *   <li>返回 {@code Map<Identifier, byte[]>}（资源路径 → 文件内容）</li>
 * </ol>
 *
 * <h2>Identifier 约定</h2>
 * <ul>
 *   <li>namespace = assets 下的子目录名（任意命名空间，如模组 ID 或 {@code minecraft}）</li>
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
	 * <p>遍历所有已加载模组，列出其 assets 目录下的所有命名空间子目录，
	 * 收集每个命名空间下 {@code enchant_sync/} 目录内的所有文件。
	 * 扫描结果存储在静态字段中，可通过 {@link #getResources()} 获取。</p>
	 *
	 * @return 扫描到的资源数量
	 */
	public static int scan() {
		Map<Identifier, byte[]> results = new HashMap<>();
		int modCount = 0;
		int namespaceCount = 0;

		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String modId = mod.getMetadata().getId();

			try {
				var assetsOpt = mod.findPath(ASSETS_PREFIX);
				if (assetsOpt.isEmpty() || !Files.isDirectory(assetsOpt.get())) {
					continue;
				}

				Path assetsDir = assetsOpt.get();
				boolean modHasSync = false;

				// 列出 assets 下所有子目录（每个子目录即一个命名空间，不限于模组 ID）
				try (Stream<Path> nsDirs = Files.list(assetsDir)) {
					for (Path nsDir : nsDirs.filter(Files::isDirectory).toList()) {
						Path syncDir = nsDir.resolve(ENCHANT_SYNC_DIR);
						if (!Files.isDirectory(syncDir)) {
							continue;
						}

						String namespace = nsDir.getFileName().toString();
						int fileCount = scanDirectory(namespace, syncDir, results);
						if (fileCount > 0) {
							modHasSync = true;
							namespaceCount++;
							EnchantLib.LOGGER.info("[EnchantLib] 扫描模组 {} 的命名空间 {}: {} 个文件",
								modId, namespace, fileCount);
						}
					}
				}

				if (modHasSync) {
					modCount++;
				}
			} catch (IOException e) {
				EnchantLib.LOGGER.warn("[EnchantLib] 扫描模组 {} 的 enchant_sync 目录失败: {}",
					modId, e.getMessage());
			}
		}

		scannedResources = Collections.unmodifiableMap(results);

		EnchantLib.LOGGER.info("[EnchantLib] 目录扫描完成: 扫描了 {} 个模组的 {} 个命名空间，收集到 {} 个资源文件",
			modCount, namespaceCount, results.size());

		return results.size();
	}

	/**
	 * 递归扫描目录，收集所有文件。
	 *
	 * @param namespace 命名空间（assets 下的子目录名，用于构建 Identifier）
	 * @param syncDir   enchant_sync 目录路径
	 * @param results   结果收集 Map
	 * @return 扫描到的文件数量
	 * @throws IOException 如果遍历失败
	 */
	private static int scanDirectory(String namespace, Path syncDir, Map<Identifier, byte[]> results)
		throws IOException {
		int count = 0;
		try (Stream<Path> stream = Files.walk(syncDir)) {
			var fileList = stream.filter(Files::isRegularFile).toList();
			for (Path file : fileList) {
				String relativePath = syncDir.relativize(file).toString()
					.replace('\\', '/'); // Windows 路径兼容

				String resourcePath = ENCHANT_SYNC_DIR + "/" + relativePath;
				Identifier id = Identifier.fromNamespaceAndPath(namespace, resourcePath);

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
	 * @param namespace 命名空间（assets 下的子目录名）
	 * @return 该命名空间下的所有 enchant_sync 资源
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
