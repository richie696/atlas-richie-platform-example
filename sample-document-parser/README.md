# sample-document-parser

`atlas-richie-component-document-parser` 组件的端到端示例工程。

## 快速开始

```bash
# 安装组件到本地仓库 (如果未安装)
cd ../..
mvn -pl atlas-richie-component/atlas-richie-component-document-parser install -DskipTests

# 启动 sample
cd sample-document-parser
mvn spring-boot:run
```

应用启动后,会自动在 `target/sample-docs/` 下生成 6 个测试文件,然后启动 Web 容器 (默认 8080)。

## 端点 (Endpoints)

启动后,可通过以下端点体验:

### 1. 单独解析每个文件类型 (JSON 响应)

| 端点 | 文件类型 | 底层 Parser |
|------|---------|-----------|
| `GET /api/parse/pdf` | PDF | TikaDocumentParser |
| `GET /api/parse/word` | DOCX | TikaDocumentParser |
| `GET /api/parse/excel` | XLSX | FesodDocumentParser |
| `GET /api/parse/ppt` | PPTX | TikaDocumentParser |
| `GET /api/parse/txt` | TXT | TikaDocumentParser |
| `GET /api/parse/md` | Markdown | TikaDocumentParser |

**响应示例** (GET /api/parse/pdf):
```json
{
  "title": null,
  "author": null,
  "totalSegments": 1,
  "totalImages": 0,
  "segments": [
    {"text": "Sample PDF Document", "sectionPath": "/sample.pdf", ...}
  ],
  "images": []
}
```

### 2. 一次性批量解析所有文件 (GET /api/parse/all-once)

```bash
curl http://localhost:8080/api/parse/all-once
```

返回完整 JSON,包含所有 6 个文件的解析结果。

### 3. **流式批量解析 (SSE 实时推送)** (GET /api/parse/all)

```bash
curl -N http://localhost:8080/api/parse/all
```

**Server-Sent Events 流** (text/event-stream):
```
data: {"type":"file_start","file":"sample.pdf"}

data: {"type":"segment","file":"sample.pdf","index":0,"sectionPath":"/sample.pdf","text":"Sample PDF Document"}

data: {"type":"image","file":"sample.pdf","mimeType":"image/png","size":1024,"sectionPath":"/sample.pdf"}

data: {"type":"file_done","file":"sample.pdf","totalSegments":5,"totalImages":1}

data: {"type":"file_start","file":"sample.docx"}
...
data: {"type":"all_done","totalFiles":6}
```

每个文件处理完立即推送,不需等待所有文件解析完成。适合大批量文件场景 (RAG 入库、批量文档处理等)。

## 业务调用示例 (Java)

```java
@Autowired DocumentParserService parser;

// 1. 同步解析本地 PDF
Map<String, Object> result = parser.parseFull(
    ParserSource.fileSource(new File("target/sample-docs/sample.pdf")));

// 2. 流式订阅 (Phase 8+ 核心能力)
parser.parseStream(
    ParserSource.fileSource(new File("target/sample-docs/sample.pdf")),
    event -> switch (event) {
        case ParseEvent.Streaming s -> 
            embeddingService.embed(s.segment().text());
        case ParseEvent.ImageStreaming i -> 
            ocrService.extract(i.image().data());
        case ParseEvent.Finished f -> 
            log.info("done: {} segs, {} imgs", f.totalSegments(), f.totalImages());
        case ParseEvent.Failed err -> 
            log.error("parse failed", err.error());
    });
```

## 关键设计原则

1. **不封堵调用方的路** — image-only PDF 不抛错, 改为 emit ImageStreaming 让调用方处理
2. **业务 → 抽象 → 实现** — `DocumentReader` 是唯一入口, 实现细节封装在 `internal/` 包
3. **三种输入同 API** — File / InputStream / URL 共用 `parseStream` 流式 API
4. **失败不静默** — 异常用 `ParseEvent.Failed` 显式 emit, 不吞

## 模块结构

```
com.richie.sample.parser/
├── SampleDocumentParserApplication.java     # Spring Boot 启动 + 启动时生成 fixture
├── controller/
│   └── DocumentParserController.java         # 7 个 REST 端点 (6 个单文件 + 1 个流式)
├── service/
│   └── DocumentParserService.java             # 包装 DocumentReader
└── fixture/
    └── SampleDocumentFixtures.java           # 启动时生成 6 个测试文件 (PDF/DOCX/XLSX/PPTX/TXT/MD)
```

## 相关文档

- 组件 README: `../../atlas-richie-component/atlas-richie-component-document-parser/README.md`
- 组件 README.zh: `../../atlas-richie-component/atlas-richie-component-document-parser/README.zh.md`
- 组件 application.yml 示例: `../../atlas-richie-component/atlas-richie-component-document-parser/docs/application-parser-example.yml`

## 验证

启动后,所有 6 个端点应该返回 200 + JSON,流式端点 `/api/parse/all` 持续推送 SSE 事件直到 `all_done`。
