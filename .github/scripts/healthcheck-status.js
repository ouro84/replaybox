// Builds the public health-check status file (SPEC §CI) from the per-Provider
// results written by the nightly probes in app/build/healthcheck/<provider>.json.
// The workflow force-pushes it as healthcheck.json on the orphan `status` branch;
// the app reads it from raw.githubusercontent.com.
//
// CLI:   node .github/scripts/healthcheck-status.js <results-dir> <out-file>
//        env RUN_URL = the Actions run URL
// Tests: node --test .github/scripts/healthcheck-status.test.js

const fs = require('fs');
const path = require('path');

// Every Provider the status file must always list, even when the build broke
// before any probe ran. Keep in sync with Probes.all (app/src/healthCheck).
const KNOWN_PROVIDERS = [
  { provider: 'tf1', label: 'TF1+ — Le 20h (anonyme)' },
  { provider: 'francetv', label: 'France TV — france-2 (anonyme)' },
  { provider: 'm6', label: 'M6+ (anonyme)' },
];

/** ISO-8601 UTC, second precision. */
function isoSeconds(date) {
  return date.toISOString().replace(/\.\d{3}Z$/, 'Z');
}

/**
 * Pure aggregation. [results] = parsed probe files; a known Provider without
 * a result (build or setup failure) is reported KO at step `build`, as the
 * alert does. Probes not in [known] are kept, so a new probe shows up even
 * before KNOWN_PROVIDERS is updated.
 */
function buildStatus({ results, generatedAt, runUrl, known = KNOWN_PROVIDERS }) {
  const byName = new Map(results.map(r => [r.provider, r]));
  const names = [...known.map(k => k.provider), ...results.map(r => r.provider)]
    .filter((name, i, all) => all.indexOf(name) === i);
  const providers = names.map(name => {
    const r = byName.get(name);
    if (!r) {
      return {
        provider: name,
        label: known.find(k => k.provider === name).label,
        ok: false,
        step: 'build',
        error: 'no result produced: build or setup failed, see the run',
        checkedAt: generatedAt,
      };
    }
    return {
      provider: name,
      label: r.label ?? name,
      ok: r.ok === true,
      step: r.ok === true ? null : (r.step ?? null),
      error: r.ok === true ? null : (r.error ?? null),
      checkedAt: r.checkedAt ?? generatedAt,
    };
  });
  return { generatedAt, runUrl, providers };
}

/**
 * Reads every <provider>.json of [dir]; a missing dir means no result. An
 * unreadable file is skipped, so its Provider falls back to KO at `build`.
 */
function readResults(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir)
    .filter(f => f.endsWith('.json'))
    .sort()
    .flatMap(f => {
      try {
        const r = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
        return typeof r?.provider === 'string' ? [r] : [];
      } catch (e) {
        console.warn(`skipping ${f}: ${e.message}`);
        return [];
      }
    });
}

module.exports = { KNOWN_PROVIDERS, buildStatus, readResults, isoSeconds };

if (require.main === module) {
  const [dir, out] = process.argv.slice(2);
  if (!dir || !out) {
    console.error('usage: healthcheck-status.js <results-dir> <out-file>');
    process.exit(2);
  }
  const status = buildStatus({
    results: readResults(dir),
    generatedAt: isoSeconds(new Date()),
    runUrl: process.env.RUN_URL ?? null,
  });
  fs.mkdirSync(path.dirname(path.resolve(out)), { recursive: true });
  fs.writeFileSync(out, JSON.stringify(status, null, 2) + '\n');
  console.log(JSON.stringify(status, null, 2));
}
