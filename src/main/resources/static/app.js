'use strict';

// Plain JS client for the review WebSocket. See doc/mcp-design.md, section "WebSocket protocol".

const PHASE_LABELS = {
    AWAITING_CALL_APPROVAL: 'Call request',
    EXECUTING: 'Executing…',
    AWAITING_RESULT_APPROVAL: 'Result review',
};
const RECONNECT_DELAY_MS = 2000;

const cardsEl = document.getElementById('cards');
const emptyEl = document.getElementById('empty');
const connectionEl = document.getElementById('connection');
const template = document.getElementById('card-template');

/** id -> { review, el } */
const cards = new Map();
let socket = null;

function connect() {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(`${protocol}//${location.host}/ws`);

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
    const card = { review, el };

    el.querySelector('.tool').textContent = review.tool;
    el.querySelector('.time').textContent = new Date(review.startedAt).toLocaleTimeString();
    el.querySelector('.id').textContent = review.id.slice(0, 8);
    el.querySelector('.id').title = review.id;
    el.querySelector('.input').textContent = prettyJson(review.input);

    el.querySelector('.accept').addEventListener('click', () => decide(card, true));
    el.querySelector('.deny').addEventListener('click', () => decide(card, false));
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
        el.querySelector('.output').textContent = prettyJson(review.output ?? '');
        el.querySelector('.output').classList.toggle('error', review.outputError);
    }

    const decidable = review.phase !== 'EXECUTING';
    const reasonEl = el.querySelector('.reason');
    if (phaseChanged) reasonEl.value = '';
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
    }));
    // Wait for the server to confirm the transition; prevents double clicks.
    setButtonsEnabled(card.el, false);
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

/** Pretty-prints JSON text; returns other text unchanged. */
function prettyJson(text) {
    try {
        return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
        return text;
    }
}

connect();
