# StableMd5Util 使用指南

适用代码：`src/main/java/com/lqy/excel/utils/utils/StableMd5Util.java`

## 1. 设计目标
`StableMd5Util` 的核心目标不是“对象字节级一致”，而是“业务语义一致”。

它会先做规范化（normalize），再转 JSON，最后算 MD5：
1. 输入对象
2. 递归规范化（排序、裁剪、忽略字段等）
3. Jackson 序列化为稳定 JSON
4. `DigestUtils.md5Hex(...)`

适用场景：
- 幂等键（同一业务请求不重复处理）
- 数据变更检测（忽略审计字段）
- 快照签名（跨环境比对）

## 2. 对外 API 与语义

### 2.1 默认规则
```java
String hash = StableMd5Util.md5(obj);
```
默认忽略规则：`id`、`**.createTime`、`**.updateTime`、`deleted`、`isDeleted`、`version`。

### 2.2 追加忽略规则（默认 + 追加）
```java
String hash = StableMd5Util.md5(obj, Arrays.asList("**.traceId", "**.requestId"));
```

### 2.3 自定义 Options（最通用）
```java
StableMd5Util.StableMd5Options options = StableMd5Util.StableMd5Options.builder()
        .appendIgnoreGlobs(Arrays.asList("**.traceId"))
        .build();
String hash = StableMd5Util.md5(obj, options);
```

### 2.4 覆盖默认规则（仅使用你提供的规则）
```java
StableMd5Util.StableMd5Options options = StableMd5Util.StableMd5Options.builder()
        .overrideIgnoreGlobs(Arrays.asList("orderItems.*.id"))
        .build();
String hash = StableMd5Util.md5(obj, options);
```

## 3. 规则优先级（必须明确）
- 当 `overrideIgnoreGlobs != null`：完全覆盖默认规则。
- 否则：使用 `默认规则 + appendIgnoreGlobs`。
- 规则会去重（保留首次出现顺序）。

一个常见误区：
- `overrideIgnoreGlobs(Collections.emptyList())` 表示“不忽略任何字段”，不是“继续用默认”。

## 4. 规范化语义详解

### 4.1 Map
- key 按字典序排序。
- 字段会检查是否命中忽略规则（字段名和完整路径都参与匹配）。
- 规范化后值为 `null` 的字段会被移除。

### 4.2 Collection / 数组
- 统一按“无序集合”处理。
- 每个元素先递归规范化，再按规范 JSON 排序。
- 路径匹配支持 `*` 元素层级（例如 `orderItems.*.id`）。

### 4.3 String
- 调用 `trim()`。
- 空串（包含全空白）转为 `null`，最终会在 Map 中移除。

### 4.4 BigDecimal
- `stripTrailingZeros().toPlainString()`。
- 所以 `1`、`1.0`、`1.00` 的结果一致；`1E+3` 与 `1000` 一致。

### 4.5 Date / Enum / Optional
- `Date`：固定使用 `Asia/Shanghai` 时区格式化。
- `Enum`：使用 `name()`，避免 `toString()` 重写带来不稳定。
- `Optional.empty()`：等价于 `null`。

### 4.6 POJO
- 先通过 Jackson 转成 `Map`，再执行同一套规则。

## 5. Glob 规则写法

`StableMd5Util` 的忽略规则支持简单 glob：
- `*`：单段匹配（不跨 `.`）
- `**`：多段匹配（可跨层级）
- `?`：单字符

常用示例：
- `id`：忽略任意层级字段名为 `id` 的字段
- `**.createTime`：忽略任意层级 `createTime`
- `orderItems.*.id`：忽略集合 `orderItems` 各元素中的 `id`

## 6. 示例

### 6.1 Map 顺序变化不影响 MD5
```java
Map<String, Object> a = new HashMap<String, Object>();
a.put("b", 2);
a.put("a", 1);

Map<String, Object> b = new LinkedHashMap<String, Object>();
b.put("a", 1);
b.put("b", 2);

assert StableMd5Util.md5(a).equals(StableMd5Util.md5(b));
```

### 6.2 路径忽略：只忽略明细 id，不忽略 sku
```java
Map<String, Object> a = new HashMap<String, Object>();
a.put("orderItems", Arrays.<Object>asList(
        new HashMap<String, Object>() {{ put("id", 1); put("sku", "A"); }},
        new HashMap<String, Object>() {{ put("id", 2); put("sku", "B"); }}
));

Map<String, Object> b = new HashMap<String, Object>();
b.put("orderItems", Arrays.<Object>asList(
        new HashMap<String, Object>() {{ put("id", 999); put("sku", "A"); }},
        new HashMap<String, Object>() {{ put("id", 888); put("sku", "B"); }}
));

StableMd5Util.StableMd5Options options = StableMd5Util.StableMd5Options.builder()
        .overrideIgnoreGlobs(Arrays.asList("orderItems.*.id"))
        .build();

assert StableMd5Util.md5(a, options).equals(StableMd5Util.md5(b, options));
```

### 6.3 严格模式：不使用默认忽略
```java
StableMd5Util.StableMd5Options strict = StableMd5Util.StableMd5Options.builder()
        .overrideIgnoreGlobs(Collections.<String>emptyList())
        .build();

Map<String, Object> x = new HashMap<String, Object>();
x.put("id", 1);

Map<String, Object> y = new HashMap<String, Object>();
y.put("id", 2);

assert !StableMd5Util.md5(x, strict).equals(StableMd5Util.md5(y, strict));
```

### 6.4 POJO 示例（结合当前项目模型）
```java
Student s1 = new Student();
s1.setStudentId(1L);
s1.setStudentName(" 张三 ");
s1.setSex(1);

Student s2 = new Student();
s2.setStudentId(999L);
s2.setStudentName("张三");
s2.setSex(1);

// 默认忽略 id，且姓名会 trim，结果一致
assert StableMd5Util.md5(s1).equals(StableMd5Util.md5(s2));
```

## 7. 调试建议
- 如果两次哈希不一致，优先检查：
  1) 是否用了 `overrideIgnoreGlobs` 覆盖掉默认规则
  2) 规则是否应该写成路径（例如 `orderItems.*.id`）
  3) 业务是否真的需要保留集合顺序（当前实现默认无序）
- 测试里避免浅拷贝误导（`new HashMap<>(oldMap)` 只拷贝第一层）。

## 8. 与测试用例的对应关系
参考 `src/test/java/com/lqy/excel/utils/StableMd5UtilTest.java`：
- `testMapOrderShouldNotAffectMd5`：Map 排序稳定
- `testDefaultIgnoreFieldsShouldWork`：默认忽略生效
- `testAppendIgnoreByPath`：路径忽略 `orderItems.*.id`
- `testListUnordered` / `testSetUnordered` / `testArrayUnordered`：无序集合语义
- `testBigDecimalCanonicalization`：数值规范化
- `testStringTrimAndEmptyToNull`：字符串裁剪与空值处理
- `testOverrideDisablesDefaultIgnore`：覆盖规则会关闭默认忽略

