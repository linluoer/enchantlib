package com.enchantlib.resources;

import com.enchantlib.EnchantLib;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;

/**
 * 客户端运行时资源包构建器。
 *
 * <p>将合并后的语言文件和其他客户端资源（纹理、粒子、声音等）构建为标准 Minecraft
 * 客户端资源包（ZIP 格式），供分发系统（内置 HTTP / 外部 URL）发送给客户端。</p>
 *
 * <h2>资源包结构</h2>
 * <pre>{@code
 * enchantlib-runtime.zip
 * ├── pack.mcmeta                              # 资源包元数据（pack_format + description）
 * ├── assets/minecraft/lang/en_us.json         # 合并后的英文翻译（跨模组合并）
 * ├── assets/minecraft/lang/zh_cn.json         # 合并后的中文翻译
 * ├── assets/mymod/textures/...                # 模组纹理（保留原命名空间）
 * ├── assets/mymod/particles/...               # 粒子定义
 * └── assets/mymod/sounds/...                  # 音效文件
 * }</pre>
 *
 * <h2>路径映射规则</h2>
 * <ul>
 *   <li>语言文件：{@code enchant_sync/lang/<code>.json} → 合并到 {@code assets/minecraft/lang/<code>.json}</li>
 *   <li>其他资源：{@code enchant_sync/<path>} → {@code assets/<modid>/<path>}（保留模组命名空间）</li>
 * </ul>
 *
 * <h2>语言文件路径说明</h2>
 * <p>使用 {@code assets/minecraft/lang/<langcode>.json} 路径，与原版语言文件路径一致。
 * Minecraft 客户端会自动合并所有资源包的语言文件，因此 EnchantLib 的翻译键
 * （如 {@code enchantment.mymod.demo}）会被正确加载。</p>
 *
 * @since 0.1.0
 */
public final class ClientResourcePackBuilder {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** 资源包描述文本 */
	private static final String PACK_DESCRIPTION = "EnchantLib Runtime Resources";

	/** 资源包内语言文件目录 */
	private static final String LANG_DIR = "assets/minecraft/lang/";

	/** 资源包内 assets 目录前缀 */
	private static final String ASSETS_PREFIX = "assets/";

	/** enchant_sync 路径前缀（扫描结果中的 path 以此开头） */
	private static final String ENCHANT_SYNC_PREFIX = "enchant_sync/";

	/** pack.mcmeta 路径 */
	private static final String PACK_MCMETA_PATH = "pack.mcmeta";

	/** 构建好的 ZIP 字节数组 */
	private static byte[] builtZip = null;

	/** 构建好的 SHA1 哈希 */
	private static String builtSha1 = null;

	/** 构建好的资源包大小（字节） */
	private static int builtSize = 0;

	private ClientResourcePackBuilder() {
	}

	/**
	 * 构建客户端资源包。
	 *
	 * <p>从 {@link LanguageMerger#getMergedLanguages()} 获取合并后的翻译表，
	 * 从 {@link EnchantSyncScanner#getResources()} 获取其他客户端资源（纹理、粒子等），
	 * 生成标准 ZIP 资源包。构建结果存储在静态字段中，可通过
	 * {@link #getZipBytes()}、{@link #getSha1()}、{@link #getSize()} 获取。</p>
	 *
	 * @return 构建的资源文件总数（语言文件 + 其他资源）
	 */
	public static int build() {
		Map<String, Map<String, String>> languages = LanguageMerger.getMergedLanguages();
		Map<Identifier, byte[]> otherResources = collectNonLangResources();

		if (languages.isEmpty() && otherResources.isEmpty()) {
			EnchantLib.LOGGER.info("[EnchantLib] 无客户端资源可构建，跳过资源包构建");
			builtZip = null;
			builtSha1 = null;
			builtSize = 0;
			return 0;
		}

		int packFormat = SharedConstants.RESOURCE_PACK_FORMAT_MAJOR;
		EnchantLib.LOGGER.info("[EnchantLib] 开始构建客户端资源包: pack_format={}, {} 种语言, {} 个其他资源",
			packFormat, languages.size(), otherResources.size());

		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (ZipOutputStream zos = new ZipOutputStream(baos)) {
				// 1. 写入 pack.mcmeta
				String mcmeta = createPackMcmeta(packFormat);
				writeEntry(zos, PACK_MCMETA_PATH, mcmeta.getBytes(StandardCharsets.UTF_8));

				// 2. 写入语言文件（合并到 assets/minecraft/lang/）
				int langCount = 0;
				for (var entry : languages.entrySet()) {
					String langCode = entry.getKey();
					String json = LanguageMerger.toJson(langCode);
					String path = LANG_DIR + langCode + ".json";
					writeEntry(zos, path, json.getBytes(StandardCharsets.UTF_8));
					langCount++;

					EnchantLib.LOGGER.debug("[EnchantLib] 资源包写入语言文件: {} ({} 字节)",
						path, json.length());
				}

				// 3. 写入其他资源（纹理、粒子、声音等，保留模组命名空间）
				int otherCount = 0;
				for (var entry : otherResources.entrySet()) {
					Identifier id = entry.getKey();
					String zipPath = ASSETS_PREFIX + id.getNamespace() + "/"
						+ id.getPath().substring(ENCHANT_SYNC_PREFIX.length());
					writeEntry(zos, zipPath, entry.getValue());
					otherCount++;

					EnchantLib.LOGGER.debug("[EnchantLib] 资源包写入资源: {} → {} ({} 字节)",
						id, zipPath, entry.getValue().length);
				}

				zos.finish();
				builtZip = baos.toByteArray();
				builtSize = builtZip.length;
				builtSha1 = computeSha1(builtZip);

				EnchantLib.LOGGER.info("[EnchantLib] 客户端资源包构建完成: {} 个语言文件, {} 个其他资源, {} 字节, sha1={}",
					langCount, otherCount, builtSize, builtSha1);

				return langCount + otherCount;
			}
		} catch (IOException e) {
			EnchantLib.LOGGER.error("[EnchantLib] 资源包构建失败: {}", e.getMessage(), e);
			builtZip = null;
			builtSha1 = null;
			builtSize = 0;
			return 0;
		}
	}

	/**
	 * 收集非语言文件的其他客户端资源。
	 *
	 * <p>从 {@link EnchantSyncScanner#getResources()} 中筛选路径不以
	 * {@code enchant_sync/lang/} 开头的资源。</p>
	 *
	 * @return 资源映射（Identifier → 文件内容）
	 */
	private static Map<Identifier, byte[]> collectNonLangResources() {
		Map<Identifier, byte[]> filtered = new LinkedHashMap<>();
		String langPrefix = ENCHANT_SYNC_PREFIX + "lang/";
		for (var entry : EnchantSyncScanner.getResources().entrySet()) {
			if (!entry.getKey().getPath().startsWith(langPrefix)) {
				filtered.put(entry.getKey(), entry.getValue());
			}
		}
		return filtered;
	}

	/**
	 * 创建 pack.mcmeta 内容。
	 *
	 * @param packFormat pack_format 值
	 * @return JSON 字符串
	 */
	private static String createPackMcmeta(int packFormat) {
		JsonObject pack = new JsonObject();
		pack.addProperty("pack_format", packFormat);
		pack.addProperty("description", PACK_DESCRIPTION);

		JsonObject root = new JsonObject();
		root.add("pack", pack);
		return GSON.toJson(root);
	}

	/**
	 * 向 ZIP 输出流写入一个条目。
	 *
	 * @param zos     ZIP 输出流
	 * @param path    条目路径
	 * @param content 条目内容
	 * @throws IOException 如果写入失败
	 */
	private static void writeEntry(ZipOutputStream zos, String path, byte[] content) throws IOException {
		ZipEntry entry = new ZipEntry(path);
		zos.putNextEntry(entry);
		zos.write(content);
		zos.closeEntry();
	}

	/**
	 * 计算字节数组的 SHA1 哈希。
	 *
	 * @param data 字节数组
	 * @return 十六进制 SHA1 字符串
	 */
	private static String computeSha1(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] hash = md.digest(data);
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			EnchantLib.LOGGER.error("[EnchantLib] SHA-1 算法不可用: {}", e.getMessage());
			return "";
		}
	}

	/**
	 * 获取构建好的资源包 ZIP 字节数组。
	 *
	 * @return ZIP 字节数组，若未构建返回 null
	 */
	public static byte[] getZipBytes() {
		return builtZip;
	}

	/**
	 * 获取构建好的资源包 SHA1 哈希。
	 *
	 * @return SHA1 十六进制字符串，若未构建返回 null
	 */
	public static String getSha1() {
		return builtSha1;
	}

	/**
	 * 获取构建好的资源包大小。
	 *
	 * @return 字节数，若未构建返回 0
	 */
	public static int getSize() {
		return builtSize;
	}

	/**
	 * 检查资源包是否已构建。
	 *
	 * @return true 如果资源包已构建且可用
	 */
	public static boolean isBuilt() {
		return builtZip != null;
	}
}
