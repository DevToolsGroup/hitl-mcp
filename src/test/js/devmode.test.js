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

// Dev mode: ?devmode=true drives one fake card from a test panel, without a server.

import { afterEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { attributeOptions, openPage, shownText } from './page.js';

let page;

afterEach(() => page?.close());

function openDevMode() {
    page = openPage({ query: '?devmode=true' });
    return {
        input: page.$('#dev-input'),
        output: page.$('#dev-output'),
        executing: page.$('#dev-executing'),
    };
}

describe('dev mode', () => {
    it('does not connect and shows the test panel', () => {
        openDevMode();

        assert.equal(page.sockets.length, 0);
        assert.equal(page.$('#dev-panel').hidden, false);
        assert.equal(page.$('#connection').textContent, 'dev mode, no server');
        assert.equal(page.cards().length, 0);
    });

    it('is off for any other devmode value', () => {
        page = openPage({ query: '?devmode=false' });

        assert.equal(page.sockets.length, 1);
        assert.equal(page.$('#dev-panel').hidden, true);
    });

    it('test input creates a call request card', () => {
        const panel = openDevMode();

        page.type(panel.input, '{"query":"select 1"}');

        assert.equal(page.cards().length, 1);
        assert.equal(page.card().dataset.phase, 'AWAITING_CALL_APPROVAL');
        assert.equal(shownText(page.card(), 'input'), '{\n  "query": "select 1"\n}');
        assert.equal(page.document.title, '(1) HITL MCP');
    });

    it('editing the input updates the same card', () => {
        const panel = openDevMode();
        page.type(panel.input, '{"a":1}');
        const card = page.card();

        page.type(panel.input, '{"a":1,"b":2}');

        assert.equal(page.card(), card);
        assert.deepEqual(attributeOptions(card, 'input'), ['', 'a', 'b']);
    });

    it('clearing the input removes the card', () => {
        const panel = openDevMode();
        page.type(panel.input, 'x');

        page.type(panel.input, '');

        assert.equal(page.cards().length, 0);
        assert.equal(page.$('#empty').hidden, false);
    });

    it('output without input creates no card', () => {
        const panel = openDevMode();

        page.type(panel.output, 'result');

        assert.equal(page.cards().length, 0);
    });

    it('test output moves the card to result review', () => {
        const panel = openDevMode();
        page.type(panel.input, 'in');

        page.type(panel.output, 'out');

        assert.equal(page.card().dataset.phase, 'AWAITING_RESULT_APPROVAL');
        assert.equal(shownText(page.card(), 'output'), 'out');

        page.type(panel.output, '');
        assert.equal(page.card().dataset.phase, 'AWAITING_CALL_APPROVAL');
    });

    it('executing wins over the output and returns to it when unchecked', () => {
        const panel = openDevMode();
        page.type(panel.input, 'in');
        page.type(panel.output, 'out');

        panel.executing.click();
        assert.equal(page.card().dataset.phase, 'EXECUTING');
        assert.equal(page.card().querySelector('.controls').hidden, true);

        panel.executing.click();
        assert.equal(page.card().dataset.phase, 'AWAITING_RESULT_APPROVAL');
    });

    it('buttons log the message instead of sending it and leave the card as it is', () => {
        const panel = openDevMode();
        page.type(panel.input, 'in');
        page.type(panel.output, 'out');
        const card = page.card();
        page.type(card.querySelector('.reason'), 'why');
        page.type(card.querySelector('.updated-result'), 'better');

        card.querySelector('.accept').click();
        card.querySelector('.deny').click();

        assert.equal(page.logs.length, 2);
        const [prefix, json] = page.logs[0];
        assert.equal(prefix, '[devmode] would send:');
        assert.deepEqual(JSON.parse(json), {
            type: 'decision', id: 'devmode-00000000-0000-0000-0000-000000000000',
            phase: 'AWAITING_RESULT_APPROVAL', approved: true, reason: 'why', updatedResult: 'better',
        });
        assert.equal(JSON.parse(page.logs[1][1]).approved, false);
        assert.equal(card.dataset.phase, 'AWAITING_RESULT_APPROVAL');
        assert.equal(card.querySelector('.accept').disabled, false);
        assert.deepEqual(page.alerts, []);
    });
});
