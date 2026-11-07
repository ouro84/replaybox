// Run: node --test .github/scripts/healthcheck-status.test.js
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { buildStatus, readResults, isoSeconds, KNOWN_PROVIDERS } = require('./healthcheck-status.js');

const NOW = '2026-11-07T02:14:03Z';
const RUN = 'https://github.com/ouro84/replaybox/actions/runs/1';

const tf1Ok = {
  provider: 'tf1', label: 'TF1+ — Le 20h (anonyme)', ok: true, step: null, error: null,
  detail: 'resolved', checkedAt: '2026-11-07T02:13:00Z',
};
const ftvKo = {
  provider: 'francetv', label: 'France TV — france-2 (anonyme)', ok: false, step: 'signature',
  error: 'IllegalStateException: no exp', detail: null, checkedAt: '2026-11-07T02:13:30Z',
};
const m6Ok = {
  provider: 'm6', label: 'M6+ (anonyme)', ok: true, step: null, error: null,
  detail: 'resolved', checkedAt: '2026-11-07T02:13:45Z',
};

test('all probes reported: one entry each, in known order, detail dropped', () => {
  const status = buildStatus({ results: [m6Ok, ftvKo, tf1Ok], generatedAt: NOW, runUrl: RUN });
  assert.deepEqual(status, {
    generatedAt: NOW,
    runUrl: RUN,
    providers: [
      { provider: 'tf1', label: 'TF1+ — Le 20h (anonyme)', ok: true, step: null, error: null,
        checkedAt: '2026-11-07T02:13:00Z' },
      { provider: 'francetv', label: 'France TV — france-2 (anonyme)', ok: false, step: 'signature',
        error: 'IllegalStateException: no exp', checkedAt: '2026-11-07T02:13:30Z' },
      { provider: 'm6', label: 'M6+ (anonyme)', ok: true, step: null, error: null,
        checkedAt: '2026-11-07T02:13:45Z' },
    ],
  });
});

test('known Providers mirror Probes.all: tf1, francetv, m6', () => {
  assert.deepEqual(KNOWN_PROVIDERS.map(k => k.provider), ['tf1', 'francetv', 'm6']);
});

test('build failure (no result at all): every known Provider is KO at step build', () => {
  const status = buildStatus({ results: [], generatedAt: NOW, runUrl: RUN });
  assert.deepEqual(status.providers.map(p => [p.provider, p.ok, p.step, p.checkedAt]),
    KNOWN_PROVIDERS.map(k => [k.provider, false, 'build', NOW]));
  assert.ok(status.providers.every(p => p.error && p.label));
});

test('a known Provider missing from a partial run is KO at step build', () => {
  const status = buildStatus({ results: [tf1Ok], generatedAt: NOW, runUrl: RUN });
  assert.equal(status.providers[0].ok, true);
  assert.deepEqual([status.providers[1].provider, status.providers[1].ok, status.providers[1].step],
    ['francetv', false, 'build']);
});

test('an unknown probe (added before KNOWN_PROVIDERS is updated) is kept', () => {
  const next = { provider: 'next', label: 'Next', ok: true, step: null, error: null, checkedAt: NOW };
  const status = buildStatus({ results: [tf1Ok, ftvKo, m6Ok, next], generatedAt: NOW, runUrl: RUN });
  assert.deepEqual(status.providers.map(p => p.provider), ['tf1', 'francetv', 'm6', 'next']);
});

test('a probe file without checkedAt falls back to generatedAt', () => {
  const { checkedAt, ...old } = tf1Ok;
  const status = buildStatus({ results: [old, ftvKo], generatedAt: NOW, runUrl: RUN });
  assert.equal(status.providers[0].checkedAt, NOW);
});

test('readResults: missing dir is empty, unreadable files are skipped', () => {
  assert.deepEqual(readResults(path.join(os.tmpdir(), 'no-such-healthcheck-dir')), []);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'healthcheck-'));
  fs.writeFileSync(path.join(dir, 'tf1.json'), JSON.stringify(tf1Ok));
  fs.writeFileSync(path.join(dir, 'francetv.json'), '{ truncated');
  fs.writeFileSync(path.join(dir, 'notes.txt'), 'ignored');
  assert.deepEqual(readResults(dir), [tf1Ok]);
});

test('isoSeconds: UTC, no milliseconds', () => {
  assert.equal(isoSeconds(new Date(Date.UTC(2026, 10, 7, 2, 14, 3, 456))), NOW);
});
