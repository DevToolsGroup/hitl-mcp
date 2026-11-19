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

// Loads the review page into jsdom with a fake WebSocket, so tests can play both the server and the user.

import { readFileSync } from 'node:fs';
import { JSDOM, VirtualConsole } from 'jsdom';

const UI_DIR = new URL('../../main/resources/static/mcp-review/', import.meta.url);
const HTML = readFileSync(new URL('index.html', UI_DIR), 'utf8');
const APP_JS = readFileSync(new URL('app.js', UI_DIR), 'utf8');
const SCRIPT_TAG = '<script src="app.js"></script>';

if (!HTML.includes(SCRIPT_TAG)) {
    throw new Error(`index.html no longer contains ${SCRIPT_TAG}; update src/test/js/page.js`);
}

/**
 * Opens the page and runs app.js, the way a browser does.
 *
 * @param {object} [options]
 * @param {string} [options.query] query string, e.g. '?devmode=true'
 * @param {string} [options.origin] scheme, host and port the page is served from
 */
export function openPage({ query = '', origin = 'http://localhost:8080' } = {}) {
    const sockets = [];
    const timers = [];
    const alerts = [];
    const logs = [];
    const errors = [];

    const virtualConsole = new VirtualConsole();
    virtualConsole.on('log', (...args) => logs.push(args));
    virtualConsole.on('jsdomError', error => errors.push(error));

    // The script is inlined so it runs synchronously while the page is parsed, at a normal http URL.
    const html = HTML.replace(SCRIPT_TAG, () => `<script>${APP_JS}</script>`);
    const dom = new JSDOM(html, {
        url: `${origin}/mcp-review/${query}`,
        runScripts: 'dangerously',
        virtualConsole,
        beforeParse(window) {
            window.WebSocket = class FakeWebSocket {
                static CONNECTING = 0;
                static OPEN = 1;
                static CLOSING = 2;
                static CLOSED = 3;

                constructor(url) {
                    this.url = String(url);
                    this.readyState = FakeWebSocket.CONNECTING;
                    /** Messages the page sent, parsed. */
                    this.sent = [];
                    this.listeners = {};
                    sockets.push(this);
                }

                addEventListener(type, listener) {
                    (this.listeners[type] ??= []).push(listener);
                }

                send(data) {
                    if (this.readyState !== FakeWebSocket.OPEN) {
                        throw new Error('send() on a socket that is not open');
                    }
                    this.sent.push(JSON.parse(data));
                }

                // Test controls: what the server and the network do.

                serverAccepts() {
                    this.readyState = FakeWebSocket.OPEN;
                    this.emit('open', {});
                }

                serverSends(message) {
                    this.emit('message', { data: JSON.stringify(message) });
                }

                serverCloses() {
                    this.readyState = FakeWebSocket.CLOSED;
                    this.emit('close', {});
                }

                emit(type, event) {
                    (this.listeners[type] ?? []).forEach(listener => listener(event));
                }
            };
            // The page uses timers to reconnect and to reset the "Copied" label; tests run them explicitly.
            window.setTimeout = (callback, delay) => timers.push({ callback, delay });
            window.alert = message => alerts.push(message);
        },
    });

    const { window } = dom;
    const document = window.document;

    const page = {
        window,
        document,
        sockets,
        timers,
        alerts,
        logs,
        errors,

        /** The most recently created WebSocket. */
        get socket() {
            return sockets.at(-1);
        },

        /** Connects and sends the initial snapshot. */
        connect(reviews = [], pageSize = 2000) {
            page.socket.serverAccepts();
            page.socket.serverSends({ type: 'snapshot', pageSize, reviews });
        },

        cards() {
            return [...document.querySelectorAll('#cards .card')];
        },

        card(index = 0) {
            const card = page.cards()[index];
            if (!card) throw new Error(`no card at index ${index}`);
            return card;
        },

        $(selector) {
            return document.querySelector(selector);
        },

        type(element, value) {
            element.value = value;
            element.dispatchEvent(new window.Event('input', { bubbles: true }));
        },

        choose(select, value) {
            select.value = value;
            select.dispatchEvent(new window.Event('change', { bubbles: true }));
        },

        close() {
            window.close();
            if (errors.length > 0) {
                throw errors[0];
            }
        },
    };
    return page;
}

/** A review as the server sends it (ReviewView). */
export function review(overrides = {}) {
    return {
        id: '0123456789abcdef-0000-0000-0000-000000000000',
        tool: 'sql',
        input: '{"query":"select 1"}',
        startedAt: '2026-11-18T10:00:00Z',
        phase: 'AWAITING_CALL_APPROVAL',
        output: null,
        outputError: false,
        ...overrides,
    };
}

// Readers for the parts of a card.

export function shownText(card, block) {
    return card.querySelector(`.paged.${block} pre`).textContent;
}

export function pagerInfo(card, block) {
    const pager = card.querySelector(`.paged.${block} .pager`);
    return pager.hidden ? null : pager.querySelector('.page-info').textContent;
}

export function attributeOptions(card, block) {
    const select = card.querySelector(`.${block}-attribute`);
    return select.hidden ? null : [...select.options].map(option => option.value);
}
