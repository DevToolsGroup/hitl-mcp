/*
MIT License

Copyright (c) 2026-present DevToolsGroup (https://github.com/DevToolsGroup)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package org.devtoolsgroup.hitlmcp;

import org.devtoolsgroup.hitlmcp.example.TextTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HitlMcpServerTest {

    @Test
    void rejectsToolsWithoutAnnotation() {
        assertThatThrownBy(() -> HitlMcpServer.builder().tools(new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("java.lang.Object is not annotated with @HitlMcpTools");
    }

    /** Starts the real server once, so the auto-configuration and the unreviewed-tools check run together. */
    @Test
    void startsOnRandomPortAndStops(@TempDir Path tempDir) {
        try (HitlMcpServer server = HitlMcpServer.builder()
                .tools(new TextTools())
                .port(0)
                .historyFile(tempDir.resolve("history.jsonl"))
                .start()) {
            assertThat(server.getPort()).isPositive();
        }
    }
}
