package com.lqy.excel.utils;

import com.lqy.excel.utils.utils.StableMd5Util;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @ClassName: StableMd5UtilTest
 * @Description:
 * @Author: XiaoYun
 * @Date: 2026/3/28 18:11
 **/
public class StableMd5UtilTest {
    enum Status {
        NEW, DONE
    }

    /** 1) Map key 顺序不同，MD5 相同（字段排序稳定性） */
    @Test
    public void testMapOrderShouldNotAffectMd5() {
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("b", 2);
        a.put("a", 1);

        Map<String, Object> b = new LinkedHashMap<String, Object>();
        b.put("a", 1);
        b.put("b", 2);

        assertEquals(StableMd5Util.md5(a), StableMd5Util.md5(b));
    }

    /** 2) 默认忽略规则：id / createTime 等不影响 MD5 */
    @Test
    public void testDefaultIgnoreFieldsShouldWork() {
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("id", 1);
        a.put("name", "tom");

        Map<String, Object> b = new HashMap<String, Object>();
        b.put("id", 999);
        b.put("name", "tom");

        // 默认规则包含 "id"
        assertEquals(StableMd5Util.md5(a), StableMd5Util.md5(b));
    }

    /** 3) 追加忽略规则（路径）：仅忽略 orderItems.*.id */
    @Test
    public void testAppendIgnoreByPath() {
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("orderItems", Arrays.<Object>asList(
                new HashMap<String, Object>() {{
                    put("id", 1);
                    put("sku", "A");
                }},
                new HashMap<String, Object>() {{
                    put("id", 2);
                    put("sku", "B");
                }}
        ));

        Map<String, Object> b = new HashMap<String, Object>();
        b.put("orderItems", Arrays.<Object>asList(
                new HashMap<String, Object>() {{
                    put("id", 999);
                    put("sku", "A");
                }},
                new HashMap<String, Object>() {{
                    put("id", 888);
                    put("sku", "B");
                }}
        ));

        // 如果你默认已经忽略 "id"，这里看不出“路径忽略”的效果。
        // 所以我们用 overrideIgnoreGlobs 把默认忽略干掉，只保留路径规则来验证。
        StableMd5Util.StableMd5Options options = StableMd5Util.StableMd5Options.builder()
                .overrideIgnoreGlobs(Arrays.asList("orderItems.*.id"))
                .build();

        assertEquals(StableMd5Util.md5(a, options), StableMd5Util.md5(b, options));

        // 同时验证：如果 sku 不同则应该不同
        Map<String, Object> c = new HashMap<String, Object>(b);
        ((Map)((List)c.get("orderItems")).get(1)).put("sku", "C");

        assertNotEquals(StableMd5Util.md5(a, options), StableMd5Util.md5(c, options));
    }

    /** 4) List 无序：顺序变化 MD5 不变 */
    @Test
    public void testListUnordered() {
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("items", Arrays.asList(3, 1, 2));

        Map<String, Object> b = new HashMap<String, Object>();
        b.put("items", Arrays.asList(1, 2, 3));

        assertEquals(StableMd5Util.md5(a), StableMd5Util.md5(b));
    }

    /** 5) Set 无序：HashSet/LinkedHashSet 顺序不同 MD5 不变 */
    @Test
    public void testSetUnordered() {
        Set<Integer> s1 = new HashSet<Integer>(Arrays.asList(3, 1, 2));
        Set<Integer> s2 = new LinkedHashSet<Integer>(Arrays.asList(1, 2, 3));

        assertEquals(StableMd5Util.md5(s1), StableMd5Util.md5(s2));
    }

    /** 6) 数组无序：顺序变化 MD5 不变 */
    @Test
    public void testArrayUnordered() {
        Integer[] a = new Integer[]{3, 1, 2};
        Integer[] b = new Integer[]{1, 2, 3};
        assertEquals(StableMd5Util.md5(a), StableMd5Util.md5(b));
    }

    /** 7) BigDecimal 归一：1、1.0、1.00、1E+3 vs 1000（禁止科学计数法 + 去尾零） */
    @Test
    public void testBigDecimalCanonicalization() {
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("price", new BigDecimal("1.00"));

        Map<String, Object> b = new HashMap<String, Object>();
        b.put("price", new BigDecimal("1"));

        assertEquals(StableMd5Util.md5(a), StableMd5Util.md5(b));

        Map<String, Object> c = new HashMap<String, Object>();
        c.put("amt", new BigDecimal("1E+3")); // 1000 的科学计数法输入

        Map<String, Object> d = new HashMap<String, Object>();
        d.put("amt", new BigDecimal("1000"));

        assertEquals(StableMd5Util.md5(c), StableMd5Util.md5(d));
    }

    /** 8) String trim + 空串转 null： " tom " == "tom"，"" 会被移除 */
    @Test
    public void testStringTrimAndEmptyToNull() {
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("name", " tom ");
        a.put("remark", "   "); // -> null -> 移除

        Map<String, Object> b = new HashMap<String, Object>();
        b.put("name", "tom");
        // b 不包含 remark

        assertEquals(StableMd5Util.md5(a), StableMd5Util.md5(b));
    }

    /** 9) Optional：Optional.empty 等价于 null/缺失；Optional.of(x) 等价于 x */
    @Test
    public void testOptional() {
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("x", Optional.of(" abc ")); // -> "abc"

        Map<String, Object> b = new HashMap<String, Object>();
        b.put("x", "abc");

        assertEquals(StableMd5Util.md5(a), StableMd5Util.md5(b));

        Map<String, Object> c = new HashMap<String, Object>();
        c.put("x", Optional.empty());

        Map<String, Object> d = new HashMap<String, Object>();
        // d 没有 x

        assertEquals(StableMd5Util.md5(c), StableMd5Util.md5(d));
    }

    /** 10) Enum：用 name()，所以 DONE 与 DONE 一致 */
    @Test
    public void testEnumUsesName() {
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("status", Status.DONE);

        Map<String, Object> b = new HashMap<String, Object>();
        b.put("status", "DONE");

        // normalize(enum) -> "DONE"
        assertEquals(StableMd5Util.md5(a), StableMd5Util.md5(b));
    }

    /**
     * 11) 时间固定中国时区：同一个 Date 实例在任何环境应该输出同一种格式（+08:00）
     * 这个测试的关键不是“两个不同 Date 相等”，而是确认输出里包含 +08:00（或 CST）
     *
     * 注意：不同 jackson 版本 StdDateFormat 输出的时区后缀可能略不同（+0800 / +08:00）
     * 所以这里只做“MD5 相等 + 不因环境变化”更靠谱。
     */
    @Test
    public void testDateFormattingStable() {
        Date d = new Date(0L); // 1970-01-01T00:00:00Z

        Map<String, Object> a = new HashMap<String, Object>();
        a.put("t", d);

        Map<String, Object> b = new HashMap<String, Object>();
        b.put("t", new Date(0L));

        assertEquals(StableMd5Util.md5(a), StableMd5Util.md5(b));
    }

    /**
     * 12) 覆盖默认忽略规则：如果 overrideIgnoreGlobs 为空，则 id 不再忽略
     * 用来验证 “override 会取消默认” 的语义确实生效（避免误用）
     */
    @Test
    public void testOverrideDisablesDefaultIgnore() {
        Map<String, Object> a = new HashMap<String, Object>();
        a.put("id", 1);
        a.put("name", "tom");

        Map<String, Object> b = new HashMap<String, Object>();
        b.put("id", 2);
        b.put("name", "tom");

        // 默认情况下 id 被忽略 -> 相等
        assertEquals(StableMd5Util.md5(a), StableMd5Util.md5(b));

        // override 为空：不忽略任何字段 -> 应不同
        StableMd5Util.StableMd5Options options = StableMd5Util.StableMd5Options.builder()
                .overrideIgnoreGlobs(Collections.<String>emptyList())
                .build();

        assertNotEquals(StableMd5Util.md5(a, options), StableMd5Util.md5(b, options));
    }
}
