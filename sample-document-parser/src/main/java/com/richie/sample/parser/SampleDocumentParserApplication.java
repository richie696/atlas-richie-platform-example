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

package com.richie.sample.parser;

import com.richie.component.parser.DocumentReader;
import com.richie.sample.parser.fixture.SampleDocumentFixtures;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * atlas-richie-component-document-parser 端到端示例工程入口。
 * <p>
 * 启动时:
 * <ol>
 *   <li>在 {@code target/sample-docs/} 下生成 6 种格式的测试文件 (PDF/DOCX/XLSX/PPTX/TXT/MD)</li>
 *   <li>启动 Spring Boot Web 容器 (默认 8080)</li>
 * </ol>
 * 访问 {@code http://localhost:8080/api/parse/...} 体验流式解析。
 */
@SpringBootApplication
public class SampleDocumentParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleDocumentParserApplication.class, args);
    }

    @Bean
    ApplicationRunner generateFixtures(DocumentReader reader) {
        return args -> SampleDocumentFixtures.generateAll(reader);
    }
}
