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

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HitlMcpDefaultsEnvironmentPostProcessorTest {

    private final StandardEnvironment environment = new StandardEnvironment();

    private void postProcess() {
        new HitlMcpDefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, null);
    }

    @Test
    void loadsLibraryDefaults() {
        postProcess();

        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(environment.getProperty("hitlmcp.ui.page-size", Integer.class)).isEqualTo(100000);
        assertThat(environment.getProperty("hitlmcp.history.file")).isEqualTo("log/call-history.jsonl");
        assertThat(environment.getProperty("spring.ai.mcp.server.annotation-scanner.enabled", Boolean.class))
                .as("@McpTool methods would bypass the review").isFalse();
    }

    @Test
    void defaultPrefixKeepsItsTrailingSpace() {
        postProcess();

        assertThat(environment.getProperty("hitlmcp.review.updated-result-prefix"))
                .isEqualTo("the result was modified by the reviewer: ");
    }

    @Test
    void applicationPropertiesOverrideDefaults() {
        environment.getPropertySources().addFirst(new MapPropertySource("application",
                Map.of("hitlmcp.ui.page-size", "10", "hitlmcp.review.updated-result-prefix", "")));

        postProcess();

        assertThat(environment.getProperty("hitlmcp.ui.page-size", Integer.class)).isEqualTo(10);
        assertThat(environment.getProperty("hitlmcp.review.updated-result-prefix")).isEmpty();
    }

    @Test
    void defaultsHaveTheLowestPrecedence() {
        postProcess();

        assertThat(environment.getPropertySources().stream().toList().getLast().getName())
                .isEqualTo("hitlMcpDefaults");
    }
}
