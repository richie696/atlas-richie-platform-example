/*
 * Copyright (c) 2026 Richie (https://www.richie696.cn)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.richie.sample.parser.controller;

import com.richie.component.parser.model.ParsedImage;
import com.richie.component.parser.model.ParsedSection;
import com.richie.component.parser.model.ReadEvent;
import com.richie.component.parser.model.ReadResult;
import com.richie.sample.parser.service.DocumentParserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文档解析 controller — 8 个端点:
 * <ul>
 *   <li>GET  /api/parse/pdf|word|excel|ppt|txt|md — 6 个固定类型 (JSON, ReadResult)</li>
 *   <li>GET  /api/parse/file/{name} — 同步解析任意 fixture (JSON, ReadResult)</li>
 *   <li>GET  /api/parse/segments/{name} — 仅文本段 (JSON, List&lt;ParsedSection&gt;)</li>
 *   <li>GET  /api/parse/images/{name} — 仅图片资源 (JSON, List&lt;ParsedImage&gt;)</li>
 *   <li>GET  /api/parse/uri?uri=... — 解析 URL (JSON, ReadResult)</li>
 *   <li>POST /api/parse/upload — multipart 上传 (JSON, ReadResult)</li>
 *   <li>GET  /api/parse/stream/{name} — 单文件 SSE (text/event-stream, ReadEvent)</li>
 *   <li>GET  /api/parse/all — 批量 SSE (text/event-stream, ReadEvent)</li>
 *   <li>GET  /api/parse/all-once — 批量一次性 JSON</li>
 * </ul>
 * 全部基于 model 包的公开类型 (ReadResult / ReadEvent / ParsedSection / ParsedImage) — 业务方不接触组件内部类型。
 */
@RestController
@RequestMapping("/api/parse")
public class DocumentParserController {

    private static final Path FIXTURES_DIR = Paths.get("target/sample-docs");

    private final DocumentParserService service;

    public DocumentParserController(DocumentParserService service) {
        this.service = service;
    }

    // ============ 6 single-type endpoints (JSON) ============

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> parsePdf() {
        return service.parseFull(FIXTURES_DIR.resolve("sample.pdf").toFile());
    }

    @GetMapping(value = "/word", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> parseWord() {
        return service.parseFull(FIXTURES_DIR.resolve("sample.docx").toFile());
    }

    @GetMapping(value = "/excel", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> parseExcel() {
        return service.parseFull(FIXTURES_DIR.resolve("sample.xlsx").toFile());
    }

    @GetMapping(value = "/ppt", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> parsePpt() {
        return service.parseFull(FIXTURES_DIR.resolve("sample.pptx").toFile());
    }

    @GetMapping(value = "/txt", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> parseTxt() {
        return service.parseFull(FIXTURES_DIR.resolve("sample.txt").toFile());
    }

    @GetMapping(value = "/md", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> parseMd() {
        return service.parseFull(FIXTURES_DIR.resolve("sample.md").toFile());
    }

    // ============ File resolver endpoints ============

    @GetMapping(value = "/file/{name:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> readFile(@PathVariable String name) {
        ReadResult result = service.read(FIXTURES_DIR.resolve(name).toFile());
        return toJsonMap(result);
    }

    @GetMapping(value = "/segments/{name:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> readSegments(@PathVariable String name) {
        List<ParsedSection> segments = service.sections(FIXTURES_DIR.resolve(name).toFile());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("file", name);
        out.put("totalSegments", segments.size());
        out.put("segments", segments);
        return out;
    }

    @GetMapping(value = "/images/{name:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> readImages(@PathVariable String name) {
        List<ParsedImage> images = service.images(FIXTURES_DIR.resolve(name).toFile());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("file", name);
        out.put("totalImages", images.size());
        out.put("images", images);
        return out;
    }

    // ============ URI / upload endpoints ============

    @GetMapping(value = "/uri", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> readByUri(@RequestParam("uri") String uri) {
        ReadResult result = service.read(java.net.URI.create(uri));
        Map<String, Object> out = toJsonMap(result);
        out.put("uri", uri);
        return out;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> readUpload(@RequestPart("file") MultipartFile file) throws IOException {
        String nameHint = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded";
        try (InputStream in = new java.io.ByteArrayInputStream(file.getBytes())) {
            ReadResult result = service.read(in, nameHint);
            Map<String, Object> out = toJsonMap(result);
            out.put("nameHint", nameHint);
            out.put("size", file.getBytes().length);
            return out;
        }
    }

    // ============ Single-file SSE streaming ============

    @GetMapping(value = "/stream/{name:.+}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> readStreaming(@PathVariable String name) {
        java.io.File file = FIXTURES_DIR.resolve(name).toFile();
        StreamingResponseBody body = output -> {
            AtomicInteger segIndex = new AtomicInteger(0);
            try {
                service.readStreaming(file, event -> writeSse(output, event, segIndex));
            } catch (Exception e) {
                emitEvent(output, Map.of("type", "failed", "error", String.valueOf(e.getMessage())));
            }
        };
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(body);
    }

    // ============ All-files batch SSE ============

    @GetMapping(value = "/all", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> readAllStreaming() {
        List<java.io.File> files = List.of(
                FIXTURES_DIR.resolve("sample.pdf").toFile(),
                FIXTURES_DIR.resolve("sample.docx").toFile(),
                FIXTURES_DIR.resolve("sample.xlsx").toFile(),
                FIXTURES_DIR.resolve("sample.pptx").toFile(),
                FIXTURES_DIR.resolve("sample.txt").toFile(),
                FIXTURES_DIR.resolve("sample.md").toFile());
        StreamingResponseBody body = output -> {
            for (java.io.File file : files) {
                String fileName = file.getName();
                AtomicInteger segIndex = new AtomicInteger(0);
                emitEvent(output, Map.of("type", "file_start", "file", fileName));
                try {
                    service.readStreaming(file, event -> {
                        try {
                            writeSse(output, event, segIndex, fileName);
                        } catch (java.io.IOException ioe) {
                            throw new RuntimeException("SSE write failed", ioe);
                        }
                    });
                } catch (Exception e) {
                    emitEvent(output, Map.of("type", "file_failed", "file", fileName,
                            "error", String.valueOf(e.getMessage())));
                }
                emitEvent(output, Map.of("type", "file_done", "file", fileName));
            }
            emitEvent(output, Map.of("type", "all_done", "totalFiles", files.size()));
        };
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(body);
    }

    // ============ All-files batch JSON ============

    @GetMapping(value = "/all-once", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> readAllOnce() {
        List<java.io.File> files = List.of(
                FIXTURES_DIR.resolve("sample.pdf").toFile(),
                FIXTURES_DIR.resolve("sample.docx").toFile(),
                FIXTURES_DIR.resolve("sample.xlsx").toFile(),
                FIXTURES_DIR.resolve("sample.pptx").toFile(),
                FIXTURES_DIR.resolve("sample.txt").toFile(),
                FIXTURES_DIR.resolve("sample.md").toFile());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalFiles", files.size());
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (java.io.File file : files) {
            try {
                Map<String, Object> r = service.parseFull(file);
                r.put("file", file.getName());
                r.put("status", "ok");
                results.add(r);
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("file", file.getName());
                err.put("status", "error");
                err.put("error", String.valueOf(e.getMessage()));
                results.add(err);
            }
        }
        out.put("results", results);
        return out;
    }

    // ============ SSE adapter for ReadListener ============

    private void writeSse(java.io.OutputStream output, ReadEvent event, AtomicInteger segIndex) {
        try {
            writeSse(output, event, segIndex, null);
        } catch (IOException e) {
            throw new RuntimeException("SSE write failed", e);
        }
    }

    private void writeSse(java.io.OutputStream output, ReadEvent event,
                          AtomicInteger segIndex, String fileName) throws IOException {
        switch (event) {
            case ReadEvent.Section s -> {
                ParsedSection seg = s.section();
                emitEvent(output, Map.of(
                        "type", "segment",
                        "file", fileName == null ? "" : fileName,
                        "index", segIndex.getAndIncrement(),
                        "sectionPath", seg.sectionPath() == null ? "" : seg.sectionPath(),
                        "text", seg.text() == null ? "" : seg.text()));
            }
            case ReadEvent.Image i -> {
                ParsedImage image = i.image();
                emitEvent(output, Map.of(
                        "type", "image",
                        "file", fileName == null ? "" : fileName,
                        "mimeType", image.format(),
                        "size", image.size(),
                        "sectionPath", image.sectionPath() == null ? "" : image.sectionPath()));
            }
            case ReadEvent.Finished f -> {
                if (fileName == null) {
                    emitEvent(output, Map.of(
                            "type", "finished",
                            "totalSegments", f.totalSections(),
                            "totalImages", f.totalImages()));
                } else {
                    emitEvent(output, Map.of(
                            "type", "file_done",
                            "file", fileName,
                            "totalSegments", f.totalSections(),
                            "totalImages", f.totalImages()));
                }
            }
            case ReadEvent.Failed fail -> emitEvent(output, Map.of(
                    "type", fileName == null ? "failed" : "file_failed",
                    "file", fileName == null ? "" : fileName,
                    "error", fail.error().getMessage()));
        }
    }

    // ============ Helpers ============

    private Map<String, Object> toJsonMap(ReadResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", result.title());
        out.put("author", result.author());
        out.put("totalSegments", result.sectionCount());
        out.put("totalImages", result.imageCount());
        out.put("sections", result.sections());
        out.put("images", result.images());
        out.put("metadata", result.metadata());
        return out;
    }

    private void emitEvent(java.io.OutputStream out, Map<String, Object> data) throws IOException {
        out.write(("data: " + mapToJson(data) + "\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.flush();
    }

    @SuppressWarnings("unchecked")
    private String mapToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeJson((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(e.getKey())).append("\":");
                sb.append(mapToJson(e.getValue()));
                first = false;
            }
            return sb.append("}").toString();
        }
        if (value instanceof Iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) sb.append(",");
                sb.append(mapToJson(item));
                first = false;
            }
            return sb.append("]").toString();
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
