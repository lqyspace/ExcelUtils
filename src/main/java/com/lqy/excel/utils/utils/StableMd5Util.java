package com.lqy.excel.utils.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import org.apache.commons.codec.digest.DigestUtils;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @ClassName: Md5Util
 * @Description:
 * @Author: XiaoYun
 * @Date: 2026/3/27 19:34
 **/
public class StableMd5Util {
    private static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 默认全局忽略规则（可被 options 覆盖或追加）
     */
    private static final List<String> DEFAULT_IGNORE_GLOBS = Collections.unmodifiableList(Arrays.asList(
            "id",
            "**.createTime",
            "**.updateTime",
            "deleted",
            "isDeleted",
            "version"
    ));

    static {
        MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        MAPPER.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MAPPER.enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN);

        // Java 8 兼容：TimeZone.getTimeZone(String) 传入 ID
        StdDateFormat df = new StdDateFormat().withTimeZone(TimeZone.getTimeZone(ZONE_CN.getId()));
        MAPPER.setDateFormat(df);
    }

    // ========== 对外入口 ==========

    /** 使用默认忽略规则 */
    public static String md5(Object obj) {
        return md5(obj, StableMd5Options.defaults());
    }

    /**
     * 追加忽略规则：在默认规则基础上追加 ignoreGlobs
     * （如果你更希望“覆盖默认”，用 md5(obj, StableMd5Options.builder().overrideIgnoreGlobs(...))）
     */
    public static String md5(Object obj, List<String> ignoreGlobs) {
        StableMd5Options options = StableMd5Options.builder()
                .appendIgnoreGlobs(ignoreGlobs)
                .build();
        return md5(obj, options);
    }

    /** 最通用入口 */
    public static String md5(Object obj, StableMd5Options options) {
        try {
            if (options == null) {
                throw new NullPointerException("options");
            }
            List<GlobRule> compiledRules = compileIgnoreRules(options);
            Object normalized = normalize(obj, "", compiledRules);
            String json = MAPPER.writeValueAsString(normalized);
            return DigestUtils.md5Hex(json);
        } catch (Exception e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }

    // ========== normalize 实现 ==========

    private static Object normalize(Object obj, String path, List<GlobRule> ignoreRules) {
        if (obj == null) return null;

        // Optional (Java 8)
        if (obj instanceof Optional) {
            Optional opt = (Optional) obj;
            if (!opt.isPresent()) {
                return null;
            }
            return normalize(opt.get(), path, ignoreRules);
        }

        // String：trim + 空串转 null
        if (obj instanceof String) {
            String t = ((String) obj).trim();
            return t.isEmpty() ? null : t;
        }

        // BigDecimal：统一 plain string（禁止科学计数法）
        if (obj instanceof BigDecimal) {
            BigDecimal v = ((BigDecimal) obj).stripTrailingZeros();
            return v.toPlainString();
        }

        // Date：Asia/Shanghai + StdDateFormat（线程安全）
        if (obj instanceof Date) {
            return MAPPER.getDateFormat().format((Date) obj);
        }

        // Enum：用 name()（避免 toString 被重写导致不稳定）
        if (obj instanceof Enum) {
            return ((Enum) obj).name();
        }

        // 数组：按无序集合处理
        if (obj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(obj);
            List<Object> list = new ArrayList<Object>(len);
            String elementPath = path.isEmpty() ? "*" : path + ".*";
            for (int i = 0; i < len; i++) {
                Object elem = java.lang.reflect.Array.get(obj, i);
                Object n = normalize(elem, elementPath, ignoreRules);
                if (n != null) list.add(n);
            }
            return normalizeUnorderedCollection(list);
        }

        // Collection：List/Set 等统一按无序集合处理
        if (obj instanceof Collection) {
            Collection col = (Collection) obj;
            List<Object> list = new ArrayList<Object>(col.size());
            String elementPath = path.isEmpty() ? "*" : path + ".*";
            for (Object o : col) {
                Object n = normalize(o, elementPath, ignoreRules);
                if (n != null) {
                    list.add(n);
                }
            }
            return normalizeUnorderedCollection(list);
        }

        // Map：key 排序 + 忽略字段（支持通配符 + 仅路径）
        if (obj instanceof Map) {
            Map map = (Map) obj;
            TreeMap<String, Object> sorted = new TreeMap<String, Object>();
            for (Object o : map.entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                String key = String.valueOf(entry.getKey());
                String currentPath = path.isEmpty() ? key : path + "." + key;
                if (shouldIgnore(key, currentPath, ignoreRules)) {
                    continue;
                }
                Object value = normalize(entry.getValue(), currentPath, ignoreRules);
                if (value != null) {
                    sorted.put(key, value);
                }
            }
            return sorted;
        }

        // Number/Boolean/Character：直接返回（除 BigDecimal 已处理）
        if (isPrimitive(obj)) {
            return obj;
        }

        // POJO -> Map -> normalize(Map)
        Map<String, Object> map = MAPPER.convertValue(obj, Map.class);
        return normalize(map, path, ignoreRules);
    }

    /**
     * 将集合视为无序：对每个元素生成“规范 JSON key”，排序后返回元素本身（规范结构）。
     */
    private static List<Object> normalizeUnorderedCollection(List<Object> normalizedElements) {
        List<SortableElement> tmp = new ArrayList<SortableElement>(normalizedElements.size());
        for (Object e : normalizedElements) {
            tmp.add(new SortableElement(toCanonicalJson(e), e));
        }

        Collections.sort(tmp, new Comparator<SortableElement>() {
            @Override
            public int compare(SortableElement o1, SortableElement o2) {
                return o1.sortKey.compareTo(o2.sortKey);
            }
        });

        List<Object> out = new ArrayList<Object>(tmp.size());
        for (SortableElement se : tmp) {
            out.add(se.value);
        }
        return out;
    }

    private static String toCanonicalJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("生成规范JSON失败", e);
        }
    }

    private static boolean shouldIgnore(String key, String path, List<GlobRule> ignoreRules) {
        for (GlobRule rule : ignoreRules) {
            if (rule.matches(key) || rule.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrimitive(Object obj) {
        return obj instanceof Number ||
                obj instanceof Boolean ||
                obj instanceof Character;
    }

    // ========== options & ignore rules ==========

    /**
     * 编译最终忽略规则：
     * - overrideIgnoreGlobs 非空：完全覆盖默认
     * - 否则：默认 + appendIgnoreGlobs
     */
    private static List<GlobRule> compileIgnoreRules(StableMd5Options options) {
        List<String> globs;
        if (options.overrideIgnoreGlobs != null) {
            globs = options.overrideIgnoreGlobs;
        } else {
            globs = new ArrayList<String>(DEFAULT_IGNORE_GLOBS);
            if (options.appendIgnoreGlobs != null && !options.appendIgnoreGlobs.isEmpty()) {
                globs.addAll(options.appendIgnoreGlobs);
            }
        }
        // 去重但保持顺序
        LinkedHashSet<String> dedup = new LinkedHashSet<String>(globs);
        List<GlobRule> rules = new ArrayList<GlobRule>(dedup.size());
        for (String g : dedup) {
            rules.add(GlobRule.of(g));
        }
        return rules;
    }

    public static final class StableMd5Options {
        private final List<String> appendIgnoreGlobs;   // 追加（默认 + 追加）
        private final List<String> overrideIgnoreGlobs; // 覆盖（只用覆盖这份）

        private StableMd5Options(List<String> appendIgnoreGlobs, List<String> overrideIgnoreGlobs) {
            this.appendIgnoreGlobs = appendIgnoreGlobs;
            this.overrideIgnoreGlobs = overrideIgnoreGlobs;
        }

        public static StableMd5Options defaults() {
            return builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private List<String> appendIgnoreGlobs;
            private List<String> overrideIgnoreGlobs;

            /**
             * 在默认忽略规则基础上追加
             */
            public Builder appendIgnoreGlobs(List<String> globs) {
                this.appendIgnoreGlobs = (globs == null ? null : new ArrayList<String>(globs));
                return this;
            }

            /**
             * 完全覆盖默认忽略规则（如果设置了 override，则 append 不再生效）
             */
            public Builder overrideIgnoreGlobs(List<String> globs) {
                this.overrideIgnoreGlobs = (globs == null ? null : new ArrayList<String>(globs));
                return this;
            }

            public StableMd5Options build() {
                return new StableMd5Options(appendIgnoreGlobs, overrideIgnoreGlobs);
            }
        }
    }

    private static final class GlobRule {
        private final String glob;
        private final Pattern regex;

        private GlobRule(String glob, Pattern regex) {
            this.glob = glob;
            this.regex = regex;
        }

        static GlobRule of(String glob) {
            return new GlobRule(glob, Pattern.compile(globToRegex(glob)));
        }

        boolean matches(String text) {
            return regex.matcher(text).matches();
        }

        private static String globToRegex(String glob) {
            StringBuilder sb = new StringBuilder();
            sb.append("^");
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '*') {
                    boolean isDoubleStar = (i + 1 < glob.length() && glob.charAt(i + 1) == '*');
                    if (isDoubleStar) {
                        sb.append(".*");
                        i++;
                    } else {
                        sb.append("[^.]*");
                    }
                } else if (c == '?') {
                    sb.append(".");
                } else if (c == '.') {
                    sb.append("\\.");
                } else {
                    if ("\\+()^$|{}[]".indexOf(c) >= 0) sb.append("\\");
                    sb.append(c);
                }
            }
            sb.append("$");
            return sb.toString();
        }
    }

    private static final class SortableElement {
        private final String sortKey;
        private final Object value;

        private SortableElement(String sortKey, Object value) {
            this.sortKey = sortKey;
            this.value = value;
        }
    }
}
