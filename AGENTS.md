# AGENTS Guide for ExcelUtils

## Project Snapshot
- Java 8 + Spring Boot 2.6 utility project focused on file conversion and deterministic hashing.
- Two main domains: annotated model <-> Excel/CSV conversion and stable MD5 generation for nested objects.
- No controller/service layering yet; most logic lives in static utility classes.

## Architecture and Data Flow
- Entry app: `src/main/java/com/lqy/excel/utils/ExcelUtilsApplication.java` (bootstraps Spring only).
- Conversion core: `src/main/java/com/lqy/excel/utils/utils/ModelData2ExcelOrCsvUtil.java`.
- Export flow: parse `@ExcelColumn` metadata -> build ordered columns -> write XLSX (POI SXSSF) or CSV (Commons CSV).
- Import flow: detect extension -> read header row -> map header to field metadata -> converter.read(...) per cell.
- Generic file reader (`FileDataReaderUtil`) is a separate row-mapper utility; it is not used by `ModelData2ExcelOrCsvUtil`.
- Stable hash flow: `StableMd5Util.md5(...)` -> normalize recursively (map sort, unordered collections, trim strings, date normalization, ignore globs) -> JSON -> MD5.

## Key Conventions (Project-Specific)
- Only fields with `@ExcelColumn` participate in import/export (`Student.remark` is ignored by design).
- Column order and names come from annotation (`header`, `order`) in `ExcelColumn`.
- Conversion strategy is annotation-driven: `converter = GenderConverter.class` on a field (see `Student.sex`).
- Default conversion is field-type aware only when converter is `DefaultConverter` (it is created with `Field` context).
- Import/export flags differ: parser currently enforces `export=false`; `importable=false` exists on annotation but is not enforced in `parseColumns`.
- `ModelData2ExcelOrCsvUtil` uses reflection with `clazz.newInstance()` and `Field#setAccessible(true)`; keep no-arg constructors.

## Developer Workflows
- Run tests:
```bash
mvn test
```
- Quick compile without tests:
```bash
mvn -DskipTests compile
```
- Start app (if needed for static page/manual checks):
```bash
mvn spring-boot:run
```
- Current state observed on 2026-03-28: `mvn test` fails at `StableMd5UtilTest.testAppendIgnoreByPath`.

## Integration Points and Dependencies
- Excel write/read: Apache POI (`poi`, `poi-ooxml`), streaming writer via `SXSSFWorkbook(200)`.
- CSV write/read: Apache Commons CSV in conversion util; OpenCSV in `FileDataReaderUtil`.
- HTTP download integration: `exportToResponse(HttpServletResponse, ...)` sets `Content-Disposition` and writes directly to response stream.
- Stable hash serialization uses Jackson with ordered properties and Asia/Shanghai timezone in `StableMd5Util`.

## Extension Playbook for Agents
- When adding new import/export models, follow `Student` pattern: annotate each participating field and set explicit `order`.
- For domain mapping (e.g., status code <-> label), add a `Converter` implementation under `service/impl` and reference it in annotation.
- If changing hash semantics, update `StableMd5UtilTest` first; tests document expected canonicalization rules.
- If you touch import rules, verify both CSV and Excel paths because they are implemented separately in the same util.

