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

package org.devtoolsgroup.hitlmcp.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param pageSize        how many characters of an input or output are shown on one page of a card
 * @param maxMessageChars the longest message, in characters, the review page may send to the server.
 *                        A decision carries the updated result and the reason, JSON-encoded,
 *                        so this limits how long an updated result can be.
 *                        Without it the servlet container's default applies, 8192 characters in Tomcat.
 *                        The container may reserve a buffer of this size for each open review page.
 */
@ConfigurationProperties("hitlmcp.ui")
public record UiProperties(int pageSize, int maxMessageChars) {

    public UiProperties {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("hitlmcp.ui.page-size must be positive, got " + pageSize);
        }
        if (maxMessageChars <= 0) {
            throw new IllegalArgumentException(
                    "hitlmcp.ui.max-message-chars must be positive, got " + maxMessageChars);
        }
    }
}
