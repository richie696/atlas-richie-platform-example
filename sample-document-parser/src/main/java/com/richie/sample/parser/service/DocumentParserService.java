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

package com.richie.sample.parser.service;

import com.richie.component.parser.DocumentReader;
import com.richie.component.parser.exception.DocumentParseException;
import com.richie.component.parser.model.ParsedImage;
import com.richie.component.parser.model.ParsedSection;
import com.richie.component.parser.model.ReadEvent;
import com.richie.component.parser.model.ReadListener;
import com.richie.component.parser.model.ReadResult;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档解析服务 — 业务侧入口层。
 * <p>
 * 完全基于 model 包的公开类型 ({@link ReadResult} / {@link ReadEvent} / {@link ReadListener} /
 * {@link ParsedSection} / {@link ParsedImage}), 不暴露 component 内部 ParserSource / ParseEvent 等类型。
 * <p>
 * 提供 3 类核心能力:
 * <ol>
 *   <li>{@link #read(File)} / {@link #read(InputStream, String)} / {@link #read(URI)}
 *       — 同步解析, 返回 {@link ReadResult}</li>
 *   <li>{@link #readStreaming(File, ReadListener)} / {@link #readStreaming(InputStream, String, ReadListener)}
 *       — 流式订阅 {@link ReadEvent}</li>
 *   <li>{@link #parseFull(File)} — 包装 ReadResult 为 JSON 兼容 Map, 适配旧 REST 响应契约</li>
 * </ol>
 */
@Service
public class DocumentParserService {

    private final DocumentReader reader;

    public DocumentParserService(DocumentReader reader) {
        this.reader = reader;
    }

    // ============ Sync reads (return ReadResult) ============

    public ReadResult read(File file) {
        return reader.read(file);
    }

    public ReadResult read(InputStream in, String nameHint) {
        return reader.read(in, nameHint);
    }

    public ReadResult read(URI uri) {
        try {
            return reader.read(uri.toURL());
        } catch (Exception e) {
            throw new DocumentParseException("Invalid URL: " + uri, e);
        }
    }

    // ============ Streaming reads (callback ReadListener<ReadEvent>) ============

    public void readStreaming(File file, ReadListener listener) {
        reader.readStreaming(file, listener);
    }

    public void readStreaming(InputStream in, String nameHint, ReadListener listener) {
        reader.readStreaming(in, nameHint, listener);
    }

    // ============ JSON-friendly adapter ============

    /**
     * 把 {@link ReadResult} 包装为 JSON 兼容的 {@code Map} (供 REST 响应使用)。
     * 业务方对响应结构兼容老 {@code parseFull(ParserSource)}: 含 file / totalSegments /
     * totalImages / sections / images。
     */
    public Map<String, Object> parseFull(File file) {
        ReadResult result = reader.read(file);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", result.title());
        map.put("author", result.author());
        map.put("totalSegments", result.sectionCount());
        map.put("totalImages", result.imageCount());
        map.put("sections", result.sections());
        map.put("images", result.images());
        map.put("metadata", result.metadata());
        return map;
    }

    /** 仅文本段 (直接返回 {@link ReadResult#sections()})。 */
    public List<ParsedSection> sections(File file) {
        return reader.read(file).sections();
    }

    /** 仅图片资源 (直接返回 {@link ReadResult#images()})。 */
    public List<ParsedImage> images(File file) {
        return reader.read(file).images();
    }
}
