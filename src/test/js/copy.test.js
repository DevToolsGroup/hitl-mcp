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


// The "Copy" buttons next to the attribute dropdowns.

import { afterEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { openPage, review, shownText } from './page.js';

let page;

afterEach(() => page?.close());

const LONG = 'a'.repeat(10) + 'b'.repeat(10);

/** Opens the page with a clipboard that records what is written, or rejects when {@code fails} is set. */
function openWithClipboard({ fails = false } = {}) {
    page = openPage();
    const copied = [];
    Object.defineProperty(page.window.navigator, 'clipboard', {
        value: {
            async writeText(text) {
                if (fails) throw new Error('denied');
                copied.push(text);
            },
        },
    });
    // jsdom has no execCommand; the fallback reports that nothing was copied.
    page.document.execCommand = () => false;
    return copied;
}

function copyButton(card, block) {
    return card.querySelector(`.paged.${block}`).previousElementSibling.querySelector('.copy');
}

async function click(button) {
    button.click();
    // Lets the asynchronous clipboard write finish.
    await new Promise(resolve => setImmediate(resolve));
}

describe('copy button', () => {
    it('is right after the dropdown', () => {
        page = openPage();
        page.connect([review({ phase: 'AWAITING_RESULT_APPROVAL', output: '{"rows":[]}' })]);

        for (const block of ['input', 'output']) {
            const button = copyButton(page.card(), block);
            assert.equal(button.textContent, 'Copy');
            assert.ok(button.previousElementSibling.classList.contains(`${block}-attribute`));
        }
    });

    it('copies all pages, not only the visible one', async () => {
        const copied = openWithClipboard();
        page.connect([review({ input: LONG })], 10);
        page.card().querySelector('.paged.input .next').click();

        await click(copyButton(page.card(), 'input'));

        assert.equal(shownText(page.card(), 'input'), 'b'.repeat(10));
        assert.deepEqual(copied, [LONG]);
    });

    it('copies all pages of the selected attribute', async () => {
        const copied = openWithClipboard();
        page.connect([review({ input: JSON.stringify({ query: LONG, limit: 1 }) })], 10);
        page.choose(page.card().querySelector('.input-attribute'), 'query');
        page.card().querySelector('.paged.input .next').click();

        await click(copyButton(page.card(), 'input'));

        assert.deepEqual(copied, [LONG]);
    });

    it('copies the result', async () => {
        const copied = openWithClipboard();
        page.connect([review({ phase: 'AWAITING_RESULT_APPROVAL', output: 'done' })]);

        await click(copyButton(page.card(), 'output'));

        assert.deepEqual(copied, ['done']);
    });

    it('reads "Copied" for 2 seconds after a copy', async () => {
        openWithClipboard();
        page.connect([review()]);
        const button = copyButton(page.card(), 'input');

        await click(button);

        assert.equal(button.textContent, 'Copied');
        const timer = page.timers.at(-1);
        assert.equal(timer.delay, 2000);
        timer.callback();
        assert.equal(button.textContent, 'Copy');
    });

    it('keeps "Copy" when the text was not copied', async () => {
        const copied = openWithClipboard({ fails: true });
        page.connect([review()]);
        const button = copyButton(page.card(), 'input');

        await click(button);

        assert.deepEqual(copied, []);
        assert.equal(button.textContent, 'Copy');
        assert.equal(page.timers.length, 0);
    });
});
