package com.enchantlib.resources;

import com.enchantlib.EnchantLib;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * 语言文件合并器。
 *
 * <p>将所有模组在 {@code enchant_sync/lang/} 目录下提供的语言文件按语言代码合并，
 * 生成统一的翻译表，供运行时资源包构建使用。</p>
 *
 * <h2>合并规则</h2>
 * <ol>
 *   <li>从 {@link EnchantSyncScanner#getLangFiles()} 获取所有语言文件</li>
 *   <li>按文件名（去掉扩展名）作为语言代码分组（如 {@code en_us.json} → {@code en_us}）</li>
 *   <li>同语言代码的所有文件合并为一个翻译表（key → value）</li>
 *   <li>若不同模组提供相同 key，后处理的覆盖先处理的（按模组加载顺序）</li>
 * </ol>
 *
 * <h2>输出格式</h2>
 * <pre>{@code
 * Map<语言代码, Map<翻译键, 翻译值>>
 * 例: {
 *   "en_us" -> {"enchantment.mymod.demo" -> "Demo Enchantment", ...},
 *   "zh_cn" -> {"enchantment.mymod.demo" -> "示例附魔", ...}
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public final class LanguageMerger {

	/** 语言文件目录前缀 */
	private static final String LANG_PREFIX = "enchant_sync/lang/";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** 合并结果：语言代码 → 翻译表 */
	private static Map<String, Map<String, String>> mergedLanguages = Collections.emptyMap();

	private LanguageMerger() {
	}

	/**
	 * 执行语言文件合并。
	 *
	 * <p>从扫描器获取所有语言文件，按语言代码分组并合并为统一翻译表。
	 * 合并结果存储在静态字段中，可通过 {@link #getMergedLanguages()} 获取。</p>
	 *
	 * @return 合并后的语言代码数量
	 */
	public static int merge() {
		Map<Identifier, byte[]> langFiles = EnchantSyncScanner.getLangFiles();
		Map<String, Map<String, String>> results = new LinkedHashMap<>();

		int successCount = 0;
		int failCount = 0;

		for (var entry : langFiles.entrySet()) {
			Identifier id = entry.getKey();
			byte[] content = entry.getValue();

			String langCode = extractLangCode(id.getPath());
			if (langCode == null) {
				EnchantLib.LOGGER.warn("[EnchantLib] 无法从路径提取语言代码: {}", id);
				failCount++;
				continue;
			}

			try {
				String json = new String(content, StandardCharsets.UTF_8);
				JsonElement element = JsonParser.parseString(json);
				if (!element.isJsonObject()) {
					EnchantLib.LOGGER.warn("[EnchantLib] 语言文件根节点不是对象: {}", id);
					failCount++;
					continue;
				}

				JsonObject obj = element.getAsJsonObject();
				Map<String, String> translations = results.computeIfAbsent(
					langCode, k -> new LinkedHashMap<>());

				int added = 0;
				for (var e : obj.entrySet()) {
					if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
						translations.put(e.getKey(), e.getValue().getAsString());
						added++;
					} else {
						EnchantLib.LOGGER.warn("[EnchantLib] 语言文件 {} 中的键 {} 的值不是字符串，已跳过",
							id, e.getKey());
					}
				}

				EnchantLib.LOGGER.debug("[EnchantLib] 合并语言文件 {}: 语言={}, 新增 {} 个键",
					id, langCode, added);
				successCount++;
			} catch (JsonSyntaxException e) {
				EnchantLib.LOGGER.warn("[EnchantLib] 解析语言文件 {} 失败: {}", id, e.getMessage());
				failCount++;
			}
		}

		// 转换为不可变结果
		Map<String, Map<String, String>> immutable = new LinkedHashMap<>();
		for (var entry : results.entrySet()) {
			immutable.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
		}
		mergedLanguages = Collections.unmodifiableMap(immutable);

		EnchantLib.LOGGER.info("[EnchantLib] 语言合并完成: {} 个语言文件处理成功, {} 个失败, {} 种语言, {} 个总键数",
			successCount, failCount, mergedLanguages.size(), totalKeyCount());

		return mergedLanguages.size();
	}

	/**
	 * 从资源路径提取语言代码。
	 *
	 * <p>路径格式：{@code enchant_sync/lang/<lang_code>.json}</p>
	 *
	 * @param path 资源路径
	 * @return 语言代码（如 {@code en_us}），若格式不匹配返回 null
	 */
	private static String extractLangCode(String path) {
		if (!path.startsWith(LANG_PREFIX) || !path.endsWith(".json")) {
			return null;
		}
		String sub = path.substring(LANG_PREFIX.length(), path.length() - ".json".length());
		// 排除子目录（仅处理直接位于 lang/ 下的文件）
		if (sub.contains("/")) {
			return null;
		}
		return sub;
	}

	/**
	 * 获取合并后的所有语言。
	 *
	 * @return 不可变映射（语言代码 → 翻译表）
	 */
	public static Map<String, Map<String, String>> getMergedLanguages() {
		return mergedLanguages;
	}

	/**
	 * 获取指定语言代码的翻译表。
	 *
	 * @param langCode 语言代码（如 {@code en_us}）
	 * @return 翻译表，若不存在返回空表
	 */
	public static Map<String, String> getTranslations(String langCode) {
		return mergedLanguages.getOrDefault(langCode, Collections.emptyMap());
	}

	/**
	 * 获取合并后的语言代码数量。
	 *
	 * @return 语言代码数量
	 */
	public static int languageCount() {
		return mergedLanguages.size();
	}

	/**
	 * 计算所有语言的键总数。
	 *
	 * @return 键总数
	 */
	private static int totalKeyCount() {
		int count = 0;
		for (var entry : mergedLanguages.entrySet()) {
			count += entry.getValue().size();
		}
		return count;
	}

	/**
	 * 将合并后的翻译表序列化为 JSON 字符串。
	 *
	 * @param langCode 语言代码
	 * @return JSON 字符串，若语言不存在返回空对象 "{}"
	 */
	public static String toJson(String langCode) {
		Map<String, String> translations = mergedLanguages.get(langCode);
		if (translations == null || translations.isEmpty()) {
			return "{}";
		}
		JsonObject obj = new JsonObject();
		for (var entry : translations.entrySet()) {
			obj.addProperty(entry.getKey(), entry.getValue());
		}
		return GSON.toJson(obj);
	}
}
