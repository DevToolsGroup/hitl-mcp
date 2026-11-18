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

// The dropdown that narrows an input or result to one top-level attribute.

import { afterEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { attributeOptions, openPage, pagerInfo, review, shownText } from './page.js';

let page;

afterEach(() => page?.close());

const SQL_CALL = JSON.stringify({
    query: 'select *\nfrom table where ID in :ids',
    params: { ids: [1, 2, 3] },
    payload: JSON.stringify({ customer: { id: 42 } }),
    limit: 100,
    empty: '',
});

function input(card) {
    return card.querySelector('.input-attribute');
}

describe('attribute dropdown', () => {
    it('lists the top-level attributes after an empty entry', () => {
        page = openPage();
        page.connect([review({ input: SQL_CALL })]);

        assert.deepEqual(attributeOptions(page.card(), 'input'), ['', 'query', 'params', 'payload', 'limit', 'empty']);
        assert.equal(input(page.card()).value, '');
    });

    it('is right after the label', () => {
        page = openPage();
        page.connect([review({ input: SQL_CALL })]);

        assert.equal(input(page.card()).previousElementSibling.textContent, 'Input');
    });

    for (const [name, text] of [['an array', '[1,2]'], ['plain text', 'hello'], ['a number', '42'],
        ['a string', '"x"'], ['null', 'null'], ['an empty object', '{}']]) {
        it(`is hidden for ${name}`, () => {
            page = openPage();
            page.connect([review({ input: text })]);

            assert.equal(attributeOptions(page.card(), 'input'), null);
        });
    }

    it('the empty entry shows the whole input', () => {
        page = openPage();
        page.connect([review({ input: SQL_CALL })]);

        assert.equal(shownText(page.card(), 'input'), JSON.stringify(JSON.parse(SQL_CALL), null, 2));
    });

    it('a string attribute is shown as plain text', () => {
        page = openPage();
        page.connect([review({ input: SQL_CALL })]);
        const card = page.card();

        page.choose(input(card), 'query');

        assert.equal(shownText(card, 'input'), 'select *\nfrom table where ID in :ids');
    });

    it('a string attribute holding JSON is pretty-printed', () => {
        page = openPage();
        page.connect([review({ input: SQL_CALL })]);
        const card = page.card();

        page.choose(input(card), 'payload');

        assert.equal(shownText(card, 'input'), '{\n  "customer": {\n    "id": 42\n  }\n}');
    });

    it('other attributes are shown as pretty-printed JSON', () => {
        page = openPage();
        page.connect([review({ input: SQL_CALL })]);
        const card = page.card();

        page.choose(input(card), 'params');
        assert.equal(shownText(card, 'input'), '{\n  "ids": [\n    1,\n    2,\n    3\n  ]\n}');

        page.choose(input(card), 'limit');
        assert.equal(shownText(card, 'input'), '100');

        page.choose(input(card), 'empty');
        assert.equal(shownText(card, 'input'), '');
    });

    it('choosing the empty entry again shows the whole input', () => {
        page = openPage();
        page.connect([review({ input: SQL_CALL })]);
        const card = page.card();
        page.choose(input(card), 'query');

        page.choose(input(card), '');

        assert.equal(shownText(card, 'input'), JSON.stringify(JSON.parse(SQL_CALL), null, 2));
    });

    it('a long attribute value is paginated', () => {
        page = openPage();
        page.connect([review({ input: JSON.stringify({ query: 'x'.repeat(25) }) })], 10);
        const card = page.card();

        page.choose(input(card), 'query');

        assert.equal(pagerInfo(card, 'input'), '1 / 3');
        assert.equal(shownText(card, 'input'), 'x'.repeat(10));
    });

    it('works on the result, independently of the input', () => {
        page = openPage();
        page.connect([review({ input: SQL_CALL, phase: 'AWAITING_RESULT_APPROVAL', output: '{"rows":3,"note":"a\\nb"}' })]);
        const card = page.card();

        assert.deepEqual(attributeOptions(card, 'output'), ['', 'rows', 'note']);
        assert.equal(card.querySelector('.output-attribute').previousElementSibling.textContent, 'Result');

        page.choose(card.querySelector('.output-attribute'), 'note');

        assert.equal(shownText(card, 'output'), 'a\nb');
        assert.equal(input(card).value, '');
        assert.equal(shownText(card, 'input'), JSON.stringify(JSON.parse(SQL_CALL), null, 2));
    });

    it('is hidden for a failed tool error message', () => {
        page = openPage();
        page.connect([review({ phase: 'AWAITING_RESULT_APPROVAL', output: 'boom', outputError: true })]);

        assert.equal(attributeOptions(page.card(), 'output'), null);
    });

    it('keeps the selection when other cards update', () => {
        page = openPage();
        page.connect([review({ id: 'x', input: SQL_CALL })]);
        const card = page.card();
        page.choose(input(card), 'query');

        page.socket.serverSends({ type: 'upsert', review: review({ id: 'x', input: SQL_CALL, phase: 'EXECUTING' }) });

        assert.equal(input(card).value, 'query');
        assert.equal(shownText(card, 'input'), 'select *\nfrom table where ID in :ids');
    });

    it('a prototype property name is not taken for an attribute', () => {
        page = openPage();
        page.connect([review({ input: '{"a":1}' })]);
        const select = input(page.card());

        // Not in the list, so the browser cannot select it; the view must still not show Object.prototype members.
        page.choose(select, 'toString');

        assert.equal(shownText(page.card(), 'input'), '{\n  "a": 1\n}');
    });
});
