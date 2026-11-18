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

// The page against the server: connection, the messages it receives and the decisions it sends.

import { afterEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { openPage, review, shownText } from './page.js';

let page;

afterEach(() => page?.close());

describe('connection', () => {
    it('connects to the ws endpoint next to the page', () => {
        page = openPage();

        assert.equal(page.sockets.length, 1);
        assert.equal(page.socket.url, 'ws://localhost:8080/mcp-review/ws');
        assert.equal(page.$('#connection').textContent, 'connecting…');
    });

    it('uses wss for https pages', () => {
        page = openPage({ origin: 'https://127.0.0.1:8443' });

        assert.equal(page.socket.url, 'wss://127.0.0.1:8443/mcp-review/ws');
    });

    it('shows the connection state', () => {
        page = openPage();
        const label = page.$('#connection');

        page.socket.serverAccepts();
        assert.equal(label.textContent, 'connected');
        assert.equal(label.classList.contains('offline'), false);

        page.socket.serverCloses();
        assert.equal(label.textContent, 'disconnected, reconnecting…');
        assert.equal(label.classList.contains('offline'), true);
    });

    it('reconnects after 2 seconds when the connection is lost', () => {
        page = openPage();
        page.socket.serverAccepts();
        page.socket.serverCloses();

        assert.equal(page.timers.length, 1);
        assert.equal(page.timers[0].delay, 2000);
        page.timers[0].callback();

        assert.equal(page.sockets.length, 2);
        assert.equal(page.socket.url, 'ws://localhost:8080/mcp-review/ws');
    });

    it('does not show the dev panel', () => {
        page = openPage();

        assert.equal(page.$('#dev-panel').hidden, true);
    });
});

describe('server messages', () => {
    it('shows "no pending calls" before anything arrives', () => {
        page = openPage();
        page.connect([]);

        assert.equal(page.cards().length, 0);
        assert.equal(page.$('#empty').hidden, false);
        assert.equal(page.document.title, 'HITL MCP');
    });

    it('renders a card per review of the snapshot', () => {
        page = openPage();
        page.connect([review({ id: 'a'.repeat(36), tool: 'first' }), review({ id: 'b'.repeat(36), tool: 'second' })]);

        assert.deepEqual(page.cards().map(card => card.querySelector('.tool').textContent), ['first', 'second']);
        assert.equal(page.$('#empty').hidden, true);
        assert.equal(page.document.title, '(2) HITL MCP');
    });

    it('fills the card head', () => {
        page = openPage();
        page.connect([review({ id: '0123456789abcdef', tool: 'sql' })]);
        const card = page.card();

        assert.equal(card.querySelector('.phase').textContent, 'Call request');
        assert.equal(card.querySelector('.tool').textContent, 'sql');
        assert.equal(card.querySelector('.id').textContent, '01234567');
        assert.equal(card.querySelector('.id').title, '0123456789abcdef');
        assert.notEqual(card.querySelector('.time').textContent, '');
    });

    it('pretty-prints JSON input', () => {
        page = openPage();
        page.connect([review({ input: '{"a":1,"b":[true]}' })]);

        assert.equal(shownText(page.card(), 'input'), '{\n  "a": 1,\n  "b": [\n    true\n  ]\n}');
    });

    it('shows other input as is', () => {
        page = openPage();
        page.connect([review({ input: 'not {json' })]);

        assert.equal(shownText(page.card(), 'input'), 'not {json');
    });

    it('a new snapshot drops the cards that are gone', () => {
        page = openPage();
        page.connect([review({ id: 'a' }), review({ id: 'b' })]);

        page.socket.serverSends({ type: 'snapshot', pageSize: 2000, reviews: [review({ id: 'b' })] });

        assert.deepEqual(page.cards().map(card => card.querySelector('.id').title), ['b']);
    });

    it('upsert adds a card and remove deletes it', () => {
        page = openPage();
        page.connect([]);

        page.socket.serverSends({ type: 'upsert', review: review({ id: 'x' }) });
        assert.equal(page.cards().length, 1);
        assert.equal(page.$('#empty').hidden, true);

        page.socket.serverSends({ type: 'remove', id: 'x' });
        assert.equal(page.cards().length, 0);
        assert.equal(page.$('#empty').hidden, false);
        assert.equal(page.document.title, 'HITL MCP');
    });

    it('removing an unknown card is harmless', () => {
        page = openPage();
        page.connect([review({ id: 'x' })]);

        page.socket.serverSends({ type: 'remove', id: 'unknown' });

        assert.equal(page.cards().length, 1);
    });

    it('ignores unknown message types', () => {
        page = openPage();
        page.connect([review()]);

        page.socket.serverSends({ type: 'something-new' });

        assert.equal(page.cards().length, 1);
    });
});

describe('phases', () => {
    it('call request: input buttons, no result', () => {
        page = openPage();
        page.connect([review()]);
        const card = page.card();

        assert.equal(card.dataset.phase, 'AWAITING_CALL_APPROVAL');
        assert.equal(card.querySelector('.output-block').hidden, true);
        assert.equal(card.querySelector('.controls').hidden, false);
        assert.equal(card.querySelector('.accept').textContent, 'Accept input');
        assert.equal(card.querySelector('.deny').textContent, 'Deny input');
    });

    it('executing: nothing to decide, not counted in the title', () => {
        page = openPage();
        page.connect([review({ phase: 'EXECUTING' })]);
        const card = page.card();

        assert.equal(card.querySelector('.phase').textContent, 'Executing…');
        assert.equal(card.querySelector('.controls').hidden, true);
        assert.equal(card.querySelector('.accept').disabled, true);
        assert.equal(page.document.title, 'HITL MCP');
    });

    it('result review: shows the result and result buttons', () => {
        page = openPage();
        page.connect([review({ phase: 'AWAITING_RESULT_APPROVAL', output: '{"rows":2}' })]);
        const card = page.card();

        assert.equal(card.querySelector('.phase').textContent, 'Result review');
        assert.equal(card.querySelector('.output-block').hidden, false);
        assert.equal(card.querySelector('.output-title').textContent, 'Result');
        assert.equal(shownText(card, 'output'), '{\n  "rows": 2\n}');
        assert.equal(card.querySelector('.accept').textContent, 'Accept result');
        assert.equal(card.querySelector('.deny').textContent, 'Deny result');
    });

    it('result review of a failed tool shows the error', () => {
        page = openPage();
        page.connect([review({ phase: 'AWAITING_RESULT_APPROVAL', output: 'boom', outputError: true })]);
        const card = page.card();

        assert.equal(card.querySelector('.output-title').textContent, 'Result (tool failed)');
        assert.equal(card.querySelector('.paged.output pre').classList.contains('error'), true);
        assert.equal(shownText(card, 'output'), 'boom');
    });

    it('a missing output is shown as empty', () => {
        page = openPage();
        page.connect([review({ phase: 'AWAITING_RESULT_APPROVAL', output: null })]);

        assert.equal(shownText(page.card(), 'output'), '');
    });

    it('the accept button names the updated result while one is typed', () => {
        page = openPage();
        page.connect([review({ phase: 'AWAITING_RESULT_APPROVAL', output: 'x' })]);
        const card = page.card();
        const updated = card.querySelector('.updated-result');

        page.type(updated, 'replacement');
        assert.equal(card.querySelector('.accept').textContent, 'Accept updated result');

        page.type(updated, '   ');
        assert.equal(card.querySelector('.accept').textContent, 'Accept result');
    });

    it('goes through all phases of a call in place', () => {
        page = openPage();
        page.connect([review({ id: 'x' })]);
        const card = page.card();

        page.socket.serverSends({ type: 'upsert', review: review({ id: 'x', phase: 'EXECUTING' }) });
        assert.equal(card.dataset.phase, 'EXECUTING');

        page.socket.serverSends({
            type: 'upsert', review: review({ id: 'x', phase: 'AWAITING_RESULT_APPROVAL', output: 'done' }),
        });
        assert.equal(page.cards().length, 1);
        assert.equal(page.card(), card, 'the same card element is updated');
        assert.equal(card.dataset.phase, 'AWAITING_RESULT_APPROVAL');
        assert.equal(shownText(card, 'output'), 'done');
        assert.equal(card.querySelector('.accept').disabled, false);
    });

    it('a phase change clears what the user typed for the previous phase', () => {
        page = openPage();
        page.connect([review({ id: 'x' })]);
        const card = page.card();
        page.type(card.querySelector('.reason'), 'typed in call phase');

        page.socket.serverSends({
            type: 'upsert', review: review({ id: 'x', phase: 'AWAITING_RESULT_APPROVAL', output: 'r' }),
        });

        assert.equal(card.querySelector('.reason').value, '');
    });

    it('an update in the same phase keeps what the user typed', () => {
        page = openPage();
        page.connect([review({ id: 'x', phase: 'AWAITING_RESULT_APPROVAL', output: 'r' })]);
        const card = page.card();
        page.type(card.querySelector('.reason'), 'my reason');
        page.type(card.querySelector('.updated-result'), 'my result');

        page.socket.serverSends({ type: 'snapshot', pageSize: 2000, reviews: [
            review({ id: 'x', phase: 'AWAITING_RESULT_APPROVAL', output: 'r' }),
        ] });

        assert.equal(card.querySelector('.reason').value, 'my reason');
        assert.equal(card.querySelector('.updated-result').value, 'my result');
        assert.equal(card.querySelector('.accept').textContent, 'Accept updated result');
    });
});

describe('decisions', () => {
    it('accepting a call sends the decision and disables the buttons', () => {
        page = openPage();
        page.connect([review({ id: 'x' })]);
        const card = page.card();

        card.querySelector('.accept').click();

        assert.deepEqual(page.socket.sent, [{
            type: 'decision', id: 'x', phase: 'AWAITING_CALL_APPROVAL', approved: true, reason: '', updatedResult: '',
        }]);
        assert.equal(card.querySelector('.accept').disabled, true);
        assert.equal(card.querySelector('.deny').disabled, true);
    });

    it('a double click sends one decision', () => {
        page = openPage();
        page.connect([review()]);
        const accept = page.card().querySelector('.accept');

        accept.click();
        accept.click();

        assert.equal(page.socket.sent.length, 1);
    });

    it('denying sends the reason', () => {
        page = openPage();
        page.connect([review({ id: 'x' })]);
        const card = page.card();
        page.type(card.querySelector('.reason'), 'too risky');

        card.querySelector('.deny').click();

        assert.equal(page.socket.sent[0].approved, false);
        assert.equal(page.socket.sent[0].reason, 'too risky');
    });

    it('accepting a result sends the updated result', () => {
        page = openPage();
        page.connect([review({ id: 'x', phase: 'AWAITING_RESULT_APPROVAL', output: 'r' })]);
        const card = page.card();
        page.type(card.querySelector('.updated-result'), 'line 1\nline 2');

        card.querySelector('.accept').click();

        assert.deepEqual(page.socket.sent, [{
            type: 'decision', id: 'x', phase: 'AWAITING_RESULT_APPROVAL', approved: true,
            reason: '', updatedResult: 'line 1\nline 2',
        }]);
    });

    it('the buttons come back with the next phase', () => {
        page = openPage();
        page.connect([review({ id: 'x' })]);
        const card = page.card();
        card.querySelector('.accept').click();

        page.socket.serverSends({
            type: 'upsert', review: review({ id: 'x', phase: 'AWAITING_RESULT_APPROVAL', output: 'r' }),
        });

        assert.equal(card.querySelector('.accept').disabled, false);
    });

    it('does not send while disconnected and tells the user', () => {
        page = openPage();
        page.connect([review()]);
        page.socket.serverCloses();
        const card = page.card();

        card.querySelector('.accept').click();

        assert.deepEqual(page.alerts, ['Not connected to the server.']);
        assert.equal(page.socket.sent.length, 0);
        assert.equal(card.querySelector('.accept').disabled, false, 'the user can try again after reconnecting');
    });
});
