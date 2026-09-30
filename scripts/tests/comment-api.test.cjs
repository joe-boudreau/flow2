const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = readFileSync(path.join(__dirname, '../../src/main/resources/assets/js/comment-api.js'), 'utf8');

function api(fetch) {
    const context = {window: {}, fetch};
    vm.runInNewContext(source, context);
    return context.window.CommentApi;
}

function response(status, body) {
    return {status, ok: status >= 200 && status < 300, json: async () => JSON.parse(body)};
}

test('empty CORS rejection produces a friendly message', async () => {
    await assert.rejects(api(async () => response(403, '')).request('/comments'), /request was blocked/);
});

test('HTML proxy errors and JSON errors without a message have a status fallback', async () => {
    for (const body of ['<html>Bad gateway</html>', '{}', '{"error":null}']) {
        await assert.rejects(api(async () => response(502, body)).request('/comments'), /temporarily unavailable/);
    }
});

test('validation messages and quota reset times are preserved', async () => {
    await assert.rejects(api(async () => response(400, '{"error":"Please enter a name."}')).request('/comments'), /Please enter a name/);
    await assert.rejects(api(async () => response(429, '{"error":"Daily limit reached.","resetAt":1790812800000}')).request('/comments'), /Daily limit reached\. Resets/);
});

test('network failures do not expose browser exception messages', async () => {
    const client = api(async () => { throw new TypeError('Failed to fetch'); });
    try {
        await client.request('/comments');
        assert.fail('Expected request failure');
    }
    catch (error) {
        assert.match(client.errorMessage(error, 'fallback'), /Check your connection/);
    }
    assert.equal(client.errorMessage(new Error('internal details'), 'Something went wrong.'), 'Something went wrong.');
});

test('successful empty or malformed responses advise checking before retrying', async () => {
    for (const body of ['', '<html>Error</html>', 'null', 'true']) {
        await assert.rejects(api(async () => response(201, body)).request('/comments'), /check whether your change was saved/);
    }
});

test('successful JSON and empty delete responses are accepted', async () => {
    assert.equal((await api(async () => response(201, '{"id":"123"}')).request('/comments')).id, '123');
    assert.equal(await api(async () => response(204, '')).request('/comments'), null);
});

test('login redirects have a readable message', async () => {
    await assert.rejects(api(async () => ({redirected: true})).request('/comments'), /session may have expired/);
});
