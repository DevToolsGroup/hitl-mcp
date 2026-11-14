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

package org.devtoolsgroup.hitlmcp.example;

import org.devtoolsgroup.hitlmcp.HitlMcpTools;
import org.devtoolsgroup.hitlmcp.tools.PlainTextResultConverter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Demo tool producing long outputs, handy for trying out pagination on the review page.
 */
@HitlMcpTools
public class TextTools {

    static final int MAX_LENGTH = 1_000_000;
    private static final int MAX_WORD_LENGTH = 10;

    @Tool(name = "random_text",
            resultConverter = PlainTextResultConverter.class,
            description = "Generates random text of exactly the given number of characters: "
                    + "random lowercase words separated by spaces. Maximum length is " + MAX_LENGTH + ".")
    public String randomText(@ToolParam(description = "Number of characters to generate") int length) {
        if (length < 0 || length > MAX_LENGTH) {
            throw new IllegalArgumentException("length must be between 0 and " + MAX_LENGTH + ", got " + length);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder text = new StringBuilder(length);
        while (text.length() < length) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            int wordLength = Math.min(1 + random.nextInt(MAX_WORD_LENGTH), length - text.length());
            for (int i = 0; i < wordLength; i++) {
                text.append((char) ('a' + random.nextInt(26)));
            }
        }
        return text.toString();
    }
}
