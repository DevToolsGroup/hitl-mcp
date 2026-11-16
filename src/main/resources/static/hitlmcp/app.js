'use strict';

// Plain JS client for the review WebSocket. See doc/mcp-design.md, section "WebSocket protocol".

const PHASE_LABELS = {
    AWAITING_CALL_APPROVAL: 'Call request',
    EXECUTING: 'Executing…',
    AWAITING_RESULT_APPROVAL: 'Result review',
};
const RECONNECT_DELAY_MS = 2000;
const DEFAULT_PAGE_SIZE = 2000;

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

    el.querySelector('.tool').textContent = review.tool;
    el.querySelector('.time').textContent = new Date(review.startedAt).toLocaleTimeString();
    el.querySelector('.id').textContent = review.id.slice(0, 8);
    el.querySelector('.id').title = review.id;
    card.inputPager.setText(prettyJson(review.input));

    el.querySelector('.accept').addEventListener('click', () => decide(card, true));
    el.querySelector('.deny').addEventListener('click', () => decide(card, false));
    el.querySelector('.updated-result').addEventListener('input', () => updateAcceptLabel(el));
    return card;
}

function renderCard(card, phaseChanged) {
    const { review, el } = card;
    const phaseEl = el.querySelector('.phase');
    phaseEl.textContent = PHASE_LABELS[review.phase] ?? review.phase;
    el.dataset.phase = review.phase;

    const hasOutput = review.phase === 'AWAITING_RESULT_APPROVAL';
    el.querySelector('.output-block').hidden = !hasOutput;
    if (hasOutput) {
        el.querySelector('.output-title').textContent = review.outputError ? 'Result (tool failed)' : 'Result';
        card.outputPager.setText(prettyJson(review.output ?? ''));
        el.querySelector('.paged.output pre').classList.toggle('error', review.outputError);
    }

    const decidable = review.phase !== 'EXECUTING';
    if (phaseChanged) {
        el.querySelector('.reason').value = '';
        el.querySelector('.updated-result').value = '';
        updateAcceptLabel(el);
    }
    el.querySelector('.controls').hidden = !decidable;
    setButtonsEnabled(el, decidable);
}

function decide(card, approved) {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
        alert('Not connected to the server.');
        return;
    }
    socket.send(JSON.stringify({
        type: 'decision',
        id: card.review.id,
        phase: card.review.phase,
        approved,
        reason: card.el.querySelector('.reason').value,
        // The server uses it only in the AWAITING_RESULT_APPROVAL phase; blank means "keep the tool's result".
        updatedResult: card.el.querySelector('.updated-result').value,
    }));
    // Wait for the server to confirm the transition; prevents double clicks.
    setButtonsEnabled(card.el, false);
}

/**
 * Tells the user which result the agent will get on Accept.
 * Blank text counts as empty, the same way the server treats it.
 */
function updateAcceptLabel(el) {
    const updated = el.querySelector('.updated-result').value.trim() !== '';
    el.querySelector('.accept').textContent = updated ? 'Approve updated result' : 'Accept';
}

function setButtonsEnabled(el, enabled) {
    el.querySelectorAll('button').forEach(button => button.disabled = !enabled);
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

connect();
