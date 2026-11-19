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

'use strict';

// Plain JS client for the review WebSocket. See doc/mcp-design.md, section "WebSocket protocol".

const PHASE_LABELS = {
    AWAITING_CALL_APPROVAL: 'Call request',
    EXECUTING: 'Executing…',
    AWAITING_RESULT_APPROVAL: 'Result review',
};
const RECONNECT_DELAY_MS = 2000;
const DEFAULT_PAGE_SIZE = 2000;
/** How long a "Copy" button reads "Copied" after a successful copy. */
const COPIED_LABEL_MS = 5000;
/** Open the page with ?devmode=true to test the UI without a server; see {@link startDevMode}. */
const DEV_MODE = new URLSearchParams(location.search).get('devmode') === 'true';

const cardsEl = document.getElementById('cards');
const emptyEl = document.getElementById('empty');
const connectionEl = document.getElementById('connection');
const template = document.getElementById('card-template');

/** id -> { review, el } */
const cards = new Map();
let socket = null;
/** Characters per page; the server sends the configured value (app.ui.page-size) in the snapshot. */
let pageSize = DEFAULT_PAGE_SIZE;

function connect() {
    // Relative to the page, so it also works when the application has a servlet context path
    const url = new URL('ws', location.href);
    url.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(url);

    socket.addEventListener('open', () => setConnected(true));
    socket.addEventListener('close', () => {
        setConnected(false);
        setTimeout(connect, RECONNECT_DELAY_MS);
    });
    socket.addEventListener('message', event => handleMessage(JSON.parse(event.data)));
}

function setConnected(connected) {
    connectionEl.textContent = connected ? 'connected' : 'disconnected, reconnecting…';
    connectionEl.classList.toggle('offline', !connected);
}

function handleMessage(message) {
    switch (message.type) {
        case 'snapshot': {
            if (message.pageSize > 0 && message.pageSize !== pageSize) {
                pageSize = message.pageSize;
                cards.forEach(card => {
                    card.inputPager.repaginate();
                    card.outputPager.repaginate();
                });
            }
            const ids = new Set(message.reviews.map(review => review.id));
            for (const id of [...cards.keys()]) {
                if (!ids.has(id)) removeCard(id);
            }
            message.reviews.forEach(upsertCard);
            break;
        }
        case 'upsert':
            upsertCard(message.review);
            break;
        case 'remove':
            removeCard(message.id);
            break;
    }
    updateSummary();
}

function upsertCard(review) {
    let card = cards.get(review.id);
    if (!card) {
        card = createCard(review);
        cards.set(review.id, card);
        cardsEl.appendChild(card.el);
    }
    const phaseChanged = card.review.phase !== review.phase;
    card.review = review;
    renderCard(card, phaseChanged);
}

function createCard(review) {
    const el = template.content.firstElementChild.cloneNode(true);
    const card = {
        review,
        el,
        inputPager: createPager(el.querySelector('.paged.input')),
        outputPager: createPager(el.querySelector('.paged.output')),
    };
    card.inputView = createAttributeView(el.querySelector('.input-attribute'), card.inputPager);
    card.outputView = createAttributeView(el.querySelector('.output-attribute'), card.outputPager);

    el.querySelector('.tool').textContent = review.tool;
    el.querySelector('.time').textContent = new Date(review.startedAt).toLocaleTimeString();
    el.querySelector('.id').textContent = review.id.slice(0, 8);
    el.querySelector('.id').title = review.id;

    el.querySelectorAll('.copy').forEach(createCopyButton);

    el.querySelector('.accept').addEventListener('click', () => decide(card, true));
    el.querySelector('.deny').addEventListener('click', () => decide(card, false));
    el.querySelector('.updated-result').addEventListener('input', () => updateButtonLabels(el));
    return card;
}

function renderCard(card, phaseChanged) {
    const { review, el } = card;
    const phaseEl = el.querySelector('.phase');
    phaseEl.textContent = PHASE_LABELS[review.phase] ?? review.phase;
    el.dataset.phase = review.phase;
    // The server never changes the input of a call, but the dev mode does.
    card.inputView.setRaw(review.input);

    const hasOutput = review.phase === 'AWAITING_RESULT_APPROVAL';
    el.querySelector('.output-block').hidden = !hasOutput;
    if (hasOutput) {
        el.querySelector('.output-title').textContent = review.outputError ? 'Result (tool failed)' : 'Result';
        card.outputView.setRaw(review.output ?? '');
        el.querySelector('.paged.output pre').classList.toggle('error', review.outputError);
    }

    const decidable = review.phase !== 'EXECUTING';
    if (phaseChanged) {
        el.querySelector('.reason').value = '';
        el.querySelector('.updated-result').value = '';
    }
    // On every render: a card may first arrive already in the result phase, e.g. after a page refresh.
    updateButtonLabels(el);
    el.querySelector('.controls').hidden = !decidable;
    setButtonsEnabled(el, decidable);
}

function decide(card, approved) {
    const message = JSON.stringify({
        type: 'decision',
        id: card.review.id,
        phase: card.review.phase,
        approved,
        reason: card.el.querySelector('.reason').value,
        // The server uses it only in the AWAITING_RESULT_APPROVAL phase; blank means "keep the tool's result".
        updatedResult: card.el.querySelector('.updated-result').value,
    });
    if (DEV_MODE) {
        // No server: show what would be sent and leave the card as it is.
        console.log('[devmode] would send:', message);
        return;
    }
    if (!socket || socket.readyState !== WebSocket.OPEN) {
        alert('Not connected to the server.');
        return;
    }
    socket.send(message);
    // Wait for the server to confirm the transition; prevents double clicks.
    setButtonsEnabled(card.el, false);
}

/**
 * Names what the buttons decide on: the input in the call phase, the result in the result phase.
 * In the result phase, Accept tells the user whether the agent gets the tool's result or the updated one.
 * Blank updated-result text counts as empty, the same way the server treats it.
 */
function updateButtonLabels(el) {
    const resultPhase = el.dataset.phase === 'AWAITING_RESULT_APPROVAL';
    const updated = el.querySelector('.updated-result').value.trim() !== '';
    el.querySelector('.accept').textContent =
        !resultPhase ? 'Accept input' : updated ? 'Accept updated result' : 'Accept result';
    el.querySelector('.deny').textContent = resultPhase ? 'Deny result' : 'Deny input';
}

/** Enables or disables the decision buttons; the pager buttons manage their own state. */
function setButtonsEnabled(el, enabled) {
    el.querySelectorAll('.controls button').forEach(button => button.disabled = !enabled);
}

function removeCard(id) {
    const card = cards.get(id);
    if (card) {
        card.el.remove();
        cards.delete(id);
    }
}

function updateSummary() {
    const waiting = [...cards.values()].filter(card => card.review.phase !== 'EXECUTING').length;
    emptyEl.hidden = cards.size > 0;
    document.title = waiting > 0 ? `(${waiting}) HITL MCP` : 'HITL MCP';
}

/**
 * Makes a "Copy" button copy the text currently shown in the block below its header:
 * only the current page, narrowed to the selected attribute.
 * Once the text is copied, the button reads "Copied" for {@link COPIED_LABEL_MS}.
 */
function createCopyButton(buttonEl) {
    const preEl = buttonEl.closest('.block-head').nextElementSibling.querySelector('pre');
    let resetTimer = null;

    buttonEl.addEventListener('click', async () => {
        if (!await copyText(preEl.textContent)) return;
        buttonEl.textContent = 'Copied';
        clearTimeout(resetTimer);
        resetTimer = setTimeout(() => buttonEl.textContent = 'Copy', COPIED_LABEL_MS);
    });
}

/**
 * Copies the text to the clipboard; returns whether it was copied.
 * The Clipboard API exists only in secure contexts (https or localhost),
 * so over plain http the text is copied through a temporary hidden textarea.
 */
async function copyText(text) {
    if (navigator.clipboard?.writeText) {
        try {
            await navigator.clipboard.writeText(text);
            return true;
        } catch {
            // Denied or unavailable: try the fallback.
        }
    }
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
        return document.execCommand('copy');
    } catch {
        return false;
    } finally {
        textarea.remove();
    }
}

/**
 * Shows a long text in pages of {@link pageSize} characters, with "previous", "current / total" and "next" controls.
 * The controls are hidden when the text fits on one page.
 */
function createPager(blockEl) {
    const preEl = blockEl.querySelector('pre');
    const pagerEl = blockEl.querySelector('.pager');
    const prevEl = blockEl.querySelector('.prev');
    const nextEl = blockEl.querySelector('.next');
    const infoEl = blockEl.querySelector('.page-info');

    let text = '';
    let starts = [0];
    let page = 0;

    function render() {
        const end = page + 1 < starts.length ? starts[page + 1] : text.length;
        preEl.textContent = text.slice(starts[page], end);
        pagerEl.hidden = starts.length <= 1;
        infoEl.textContent = `${page + 1} / ${starts.length}`;
        prevEl.disabled = page === 0;
        nextEl.disabled = page === starts.length - 1;
    }

    prevEl.addEventListener('click', () => {
        if (page > 0) page--;
        render();
    });
    nextEl.addEventListener('click', () => {
        if (page < starts.length - 1) page++;
        render();
    });

    return {
        /** Sets the text; stays on the current page if the text did not change. */
        setText(newText) {
            if (newText === text && preEl.textContent !== '') return;
            text = newText;
            starts = pageStarts(text, pageSize);
            page = 0;
            render();
        },
        /** Recomputes the pages after the page size changed. */
        repaginate() {
            starts = pageStarts(text, pageSize);
            page = Math.min(page, starts.length - 1);
            render();
        },
    };
}

/**
 * Shows a raw input or output text in a pager, optionally narrowed to one top-level attribute.
 * When the text is a JSON object, the dropdown lists its top-level attribute names after an empty entry.
 * The empty entry shows the whole text, pretty-printed.
 * An attribute shows only its value: a string as plain text (pretty-printed if it is JSON itself),
 * anything else as pretty-printed JSON.
 * The dropdown is hidden when the text is not a JSON object.
 */
function createAttributeView(selectEl, pager) {
    let raw = null;
    let object = null;

    function render() {
        const attribute = selectEl.value;
        if (object === null || attribute === '' || !Object.hasOwn(object, attribute)) {
            pager.setText(prettyJson(raw));
            return;
        }
        const value = object[attribute];
        pager.setText(typeof value === 'string' ? prettyJson(value) : JSON.stringify(value, null, 2));
    }

    function rebuildOptions() {
        const selected = selectEl.value;
        const names = object === null ? [] : Object.keys(object);
        selectEl.replaceChildren(new Option('', ''), ...names.map(name => new Option(name, name)));
        // Keeps the selection when the attribute is still there, e.g. while the dev mode input is edited.
        selectEl.value = names.includes(selected) ? selected : '';
        selectEl.hidden = names.length === 0;
    }

    selectEl.addEventListener('change', render);

    return {
        setRaw(newRaw) {
            if (newRaw === raw) return;
            raw = newRaw;
            object = parseJsonObject(raw);
            rebuildOptions();
            render();
        },
    };
}

/** Parses the text as a JSON object; returns null for other JSON values and for text that is not JSON. */
function parseJsonObject(text) {
    try {
        const value = JSON.parse(text);
        return value !== null && typeof value === 'object' && !Array.isArray(value) ? value : null;
    } catch {
        return null;
    }
}

/**
 * Start offsets of the pages of a text. A page boundary never splits a surrogate pair,
 * so characters such as emoji are not broken in half.
 */
function pageStarts(text, size) {
    const starts = [0];
    let next = size;
    while (next < text.length) {
        const code = text.charCodeAt(next - 1);
        if (code >= 0xD800 && code <= 0xDBFF && next - 1 > starts[starts.length - 1]) {
            next--;
        }
        starts.push(next);
        next += size;
    }
    return starts;
}

/** Pretty-prints JSON text; returns other text unchanged. */
function prettyJson(text) {
    try {
        return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
        return text;
    }
}

/**
 * Dev mode: no WebSocket. The test panel drives a single fake card through the same code
 * that handles server messages, so the card looks exactly as it would for a real call:
 * - empty "test input": no card;
 * - "executing" checked: the call is being executed;
 * - non-empty "test output": the result waits for approval;
 * - otherwise: the call waits for approval.
 */
function startDevMode() {
    const DEV_ID = 'devmode-00000000-0000-0000-0000-000000000000';
    const inputEl = document.getElementById('dev-input');
    const outputEl = document.getElementById('dev-output');
    const executingEl = document.getElementById('dev-executing');
    let startedAt = null;

    function update() {
        const input = inputEl.value;
        if (input === '') {
            startedAt = null;
            handleMessage({ type: 'remove', id: DEV_ID });
            return;
        }
        startedAt ??= new Date().toISOString();
        const output = outputEl.value;
        const phase = executingEl.checked ? 'EXECUTING'
            : output !== '' ? 'AWAITING_RESULT_APPROVAL'
            : 'AWAITING_CALL_APPROVAL';
        handleMessage({
            type: 'upsert',
            review: {
                id: DEV_ID,
                tool: 'dev_tool',
                input,
                phase,
                startedAt,
                output: phase === 'AWAITING_RESULT_APPROVAL' ? output : null,
                outputError: false,
            },
        });
    }

    document.getElementById('dev-panel').hidden = false;
    connectionEl.textContent = 'dev mode, no server';
    [inputEl, outputEl, executingEl].forEach(el => el.addEventListener('input', update));
    update();
}

if (DEV_MODE) {
    startDevMode();
} else {
    connect();
}
