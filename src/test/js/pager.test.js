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

// Pagination of long inputs and results.

import { afterEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { openPage, pagerInfo, review, shownText } from './page.js';

let page;

afterEach(() => page?.close());

describe('pageStarts', () => {
    it('splits into pages of the given size', () => {
        page = openPage();

        assert.deepEqual([...page.window.pageStarts('', 3)], [0]);
        assert.deepEqual([...page.window.pageStarts('abc', 3)], [0]);
        assert.deepEqual([...page.window.pageStarts('abcd', 3)], [0, 3]);
        assert.deepEqual([...page.window.pageStarts('abcdefg', 3)], [0, 3, 6]);
    });

    it('never splits an emoji', () => {
        page = openPage();
        const text = 'ab😀cd'; // the emoji takes two UTF-16 code units, at 2 and 3

        assert.deepEqual([...page.window.pageStarts(text, 3)], [0, 2, 5]);
    });

    it('a page of size 1 still makes progress on an emoji', () => {
        page = openPage();

        const starts = [...page.window.pageStarts('😀😀', 1)];

        assert.equal(starts.length, 4, 'with size 1 a pair cannot be kept together, but the loop must end');
    });
});

describe('prettyJson', () => {
    it('indents JSON and leaves other text alone', () => {
        page = openPage();

        assert.equal(page.window.prettyJson('{"a":[1]}'), '{\n  "a": [\n    1\n  ]\n}');
        assert.equal(page.window.prettyJson('"text"'), '"text"');
        assert.equal(page.window.prettyJson('plain text'), 'plain text');
        assert.equal(page.window.prettyJson(''), '');
    });
});

describe('pager controls', () => {
    const LONG = 'a'.repeat(10) + 'b'.repeat(10) + 'c'.repeat(5);

    function next(card, block) {
        card.querySelector(`.paged.${block} .next`).click();
    }

    function prev(card, block) {
        card.querySelector(`.paged.${block} .prev`).click();
    }

    it('are hidden when the text fits on one page', () => {
        page = openPage();
        page.connect([review({ input: 'short' })], 10);

        assert.equal(pagerInfo(page.card(), 'input'), null);
    });

    it('page through a long input', () => {
        page = openPage();
        page.connect([review({ input: LONG })], 10);
        const card = page.card();
        const prevButton = card.querySelector('.paged.input .prev');
        const nextButton = card.querySelector('.paged.input .next');

        assert.equal(pagerInfo(card, 'input'), '1 / 3');
        assert.equal(shownText(card, 'input'), 'a'.repeat(10));
        assert.equal(prevButton.disabled, true);

        next(card, 'input');
        assert.equal(pagerInfo(card, 'input'), '2 / 3');
        assert.equal(shownText(card, 'input'), 'b'.repeat(10));
        assert.equal(prevButton.disabled, false);

        next(card, 'input');
        assert.equal(shownText(card, 'input'), 'c'.repeat(5));
        assert.equal(nextButton.disabled, true);

        prev(card, 'input');
        assert.equal(pagerInfo(card, 'input'), '2 / 3');
    });

    it('work while the tool is executing', () => {
        page = openPage();
        page.connect([review({ input: LONG, phase: 'EXECUTING' })], 10);
        const card = page.card();

        assert.equal(card.querySelector('.paged.input .next').disabled, false);
        next(card, 'input');
        assert.equal(pagerInfo(card, 'input'), '2 / 3');
    });

    it('work after a decision was sent', () => {
        page = openPage();
        page.connect([review({ input: LONG })], 10);
        const card = page.card();

        card.querySelector('.accept').click();
        next(card, 'input');

        assert.equal(pagerInfo(card, 'input'), '2 / 3');
        assert.equal(card.querySelector('.paged.input .prev').disabled, false);
    });

    it('input and result page independently', () => {
        page = openPage();
        page.connect([review({ input: LONG, phase: 'AWAITING_RESULT_APPROVAL', output: LONG })], 10);
        const card = page.card();

        next(card, 'output');

        assert.equal(pagerInfo(card, 'input'), '1 / 3');
        assert.equal(pagerInfo(card, 'output'), '2 / 3');
    });

    it('stay on the current page when the same text arrives again', () => {
        page = openPage();
        page.connect([review({ id: 'x', input: LONG })], 10);
        const card = page.card();
        next(card, 'input');

        page.socket.serverSends({ type: 'upsert', review: review({ id: 'x', input: LONG, phase: 'EXECUTING' }) });

        assert.equal(pagerInfo(card, 'input'), '2 / 3');
    });

    it('go back to the first page when the text changes', () => {
        page = openPage();
        page.connect([review({ id: 'x', phase: 'AWAITING_RESULT_APPROVAL', output: LONG })], 10);
        const card = page.card();
        next(card, 'output');

        page.socket.serverSends({
            type: 'upsert', review: review({ id: 'x', phase: 'AWAITING_RESULT_APPROVAL', output: LONG + 'more' }),
        });

        assert.equal(pagerInfo(card, 'output'), '1 / 3');
    });

    it('repaginate when the server sends another page size', () => {
        page = openPage();
        page.connect([review({ id: 'x', input: LONG })], 10);
        const card = page.card();
        next(card, 'input');
        next(card, 'input');

        page.socket.serverSends({ type: 'snapshot', pageSize: 20, reviews: [review({ id: 'x', input: LONG })] });

        assert.equal(pagerInfo(card, 'input'), '2 / 2', 'stays on the last page that still exists');
        assert.equal(shownText(card, 'input'), 'c'.repeat(5));
    });

    it('ignore an invalid page size', () => {
        page = openPage();
        page.connect([review({ input: LONG })], 0);

        assert.equal(pagerInfo(page.card(), 'input'), null, 'the default of 2000 characters is kept');
    });
});
