#!/usr/bin/env node

import {mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {performance} from 'node:perf_hooks';

const envInt = (name, fallback, min = 0) => {
    const raw = process.env[name];
    if (!raw) return fallback;
    const value = Number.parseInt(raw, 10);
    if (!Number.isInteger(value) || value < min) throw new Error(`${name} must be >= ${min}`);
    return value;
};
const wait = (ms) => new Promise((done) => setTimeout(done, ms));
const round = (value) => Number(value.toFixed(2));
const quantile = (values, q) => {
    if (!values.length) return null;
    const sorted = [...values].sort((a, b) => a - b);
    return sorted[Math.max(0, Math.ceil(q * sorted.length) - 1)];
};
const parseCookies = (header = '') => new Map(header.split(';').map((part) => part.trim()).filter(Boolean).map((part) => {
    const index = part.indexOf('=');
    return [part.slice(0, index), part.slice(index + 1)];
}).filter(([name]) => name));
const cookieHeader = (cookies) => [...cookies].map(([name, value]) => `${name}=${value}`).join('; ');
const updateCookies = (cookies, headers) => {
    const values = typeof headers.getSetCookie === 'function'
        ? headers.getSetCookie()
        : [headers.get('set-cookie')].filter(Boolean);
    for (const value of values) {
        const pair = value.split(';', 1)[0];
        const index = pair.indexOf('=');
        if (index > 0) cookies.set(pair.slice(0, index), pair.slice(index + 1));
    }
};

const config = {
    baseUrl: (process.env.BASE_URL || 'https://api.ballotbox.io.vn').replace(/\/$/, ''),
    scenarios: (process.env.SCENARIOS || 'public-feed').split(',').map((item) => item.trim()).filter(Boolean),
    samples: envInt('SAMPLES', 50, 1),
    warmup: envInt('WARMUP', 5, 0),
    timeoutMs: envInt('TIMEOUT_MS', 15_000, 1),
    feedDelayMs: envInt('FEED_DELAY_MS', 100, 0),
    voteDelayMs: envInt('VOTE_DELAY_MS', 2_100, 0),
    refreshDelayMs: envInt('REFRESH_DELAY_MS', 3_100, 0),
    accessToken: process.env.ACCESS_TOKEN || '',
    postId: process.env.POST_ID || '',
    voteType: (process.env.VOTE_TYPE || 'UP').toUpperCase(),
    cookies: parseCookies(process.env.COOKIE_HEADER),
    outputDir: resolve(process.env.OUTPUT_DIR || 'perf-results'),
    dryRun: ['1', 'true'].includes((process.env.DRY_RUN || '').toLowerCase()),
    regions: {
        railway: process.env.RAILWAY_REGION || '',
        supabase: process.env.SUPABASE_REGION || '',
        upstash: process.env.UPSTASH_REGION || '',
    },
};

const supported = new Set(['public-feed', 'authenticated-feed', 'vote', 'refresh']);
for (const scenario of config.scenarios) {
    if (!supported.has(scenario)) throw new Error(`Unsupported scenario: ${scenario}`);
}
if (config.scenarios.includes('authenticated-feed') && !config.accessToken) {
    throw new Error('ACCESS_TOKEN is required for authenticated-feed');
}
if (config.scenarios.includes('vote')) {
    if (!config.accessToken || !config.postId) throw new Error('ACCESS_TOKEN and POST_ID are required for vote');
    if (config.samples < 2 || config.samples % 2) throw new Error('SAMPLES must be even and >= 2 for vote');
    if (!['UP', 'DOWN'].includes(config.voteType)) throw new Error('VOTE_TYPE must be UP or DOWN');
}
if (config.scenarios.includes('refresh') && !config.cookies.size) {
    throw new Error('COOKIE_HEADER is required for refresh');
}

async function request({scenario, iteration, method, route, path, headers = {}, body, cookies}) {
    const started = performance.now();
    let status = 0;
    let requestId = '';
    let errorName = '';
    try {
        const response = await fetch(`${config.baseUrl}${path}`, {
            method,
            headers,
            body,
            redirect: 'manual',
            signal: AbortSignal.timeout(config.timeoutMs),
        });
        status = response.status;
        requestId = response.headers.get('x-request-id') || '';
        if (cookies) updateCookies(cookies, response.headers);
        await response.arrayBuffer();
    } catch (error) {
        errorName = error?.name || 'Error';
    }
    return {
        scenario,
        iteration,
        method,
        route,
        status,
        ok: status >= 200 && status < 400 && !errorName,
        durationMs: round(performance.now() - started),
        requestId,
        errorName,
    };
}

async function repeat(count, warmup, delayMs, factory, results) {
    for (let index = -warmup; index < count; index += 1) {
        const sample = await request(factory(index));
        if (index >= 0) {
            results.push(sample);
            console.log(`${sample.ok ? '✓' : '✗'} ${sample.scenario} ${index + 1}/${count} ${sample.durationMs} ms`);
        }
        if (delayMs) await wait(delayMs);
    }
}

async function publicFeed(results, authenticated) {
    await repeat(config.samples, config.warmup, config.feedDelayMs, (index) => ({
        scenario: authenticated ? 'authenticated-feed' : 'public-feed',
        iteration: Math.max(0, index + 1),
        method: 'GET',
        route: '/api/v1/posts',
        path: '/api/v1/posts?feed=LATEST&page=0&size=8',
        headers: {
            Accept: 'application/json',
            ...(authenticated ? {Authorization: `Bearer ${config.accessToken}`} : {}),
        },
    }), results);
}

async function vote(results) {
    const base = {
        route: '/api/v1/posts/{postId}/vote',
        path: `/api/v1/posts/${encodeURIComponent(config.postId)}/vote`,
        headers: {Accept: 'application/json', Authorization: `Bearer ${config.accessToken}`},
    };
    await request({scenario: 'vote-normalize', iteration: 0, method: 'DELETE', ...base});
    await wait(config.voteDelayMs);

    const warmup = config.warmup - (config.warmup % 2);
    for (let index = -warmup; index < config.samples; index += 1) {
        const cast = (index + warmup) % 2 === 0;
        const sample = await request({
            scenario: cast ? 'vote-cast' : 'vote-remove',
            iteration: Math.max(0, index + 1),
            method: cast ? 'PUT' : 'DELETE',
            ...base,
            headers: {...base.headers, ...(cast ? {'Content-Type': 'application/json'} : {})},
            body: cast ? JSON.stringify({type: config.voteType}) : undefined,
        });
        if (index >= 0) {
            results.push(sample);
            console.log(`${sample.ok ? '✓' : '✗'} ${sample.scenario} ${index + 1}/${config.samples} ${sample.durationMs} ms`);
        }
        await wait(config.voteDelayMs);
    }
}

async function refresh(results) {
    await repeat(config.samples, config.warmup, config.refreshDelayMs, (index) => ({
        scenario: 'refresh',
        iteration: Math.max(0, index + 1),
        method: 'POST',
        route: '/api/v1/auth/refresh',
        path: '/api/v1/auth/refresh',
        headers: {Accept: 'application/json', Cookie: cookieHeader(config.cookies)},
        cookies: config.cookies,
    }), results);
}

function summarize(results) {
    const groups = Map.groupBy(results, (sample) => sample.scenario);
    return [...groups].map(([scenario, samples]) => {
        const durations = samples.filter((sample) => sample.ok).map((sample) => sample.durationMs);
        return {
            scenario,
            count: samples.length,
            failures: samples.length - durations.length,
            minMs: durations.length ? round(Math.min(...durations)) : null,
            meanMs: durations.length ? round(durations.reduce((a, b) => a + b, 0) / durations.length) : null,
            p50Ms: durations.length ? round(quantile(durations, 0.50)) : null,
            p95Ms: durations.length ? round(quantile(durations, 0.95)) : null,
            p99Ms: durations.length ? round(quantile(durations, 0.99)) : null,
            maxMs: durations.length ? round(Math.max(...durations)) : null,
        };
    });
}

const csv = (results) => {
    const fields = ['scenario', 'iteration', 'method', 'route', 'status', 'ok', 'durationMs', 'requestId', 'errorName'];
    const escape = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;
    return [fields.join(','), ...results.map((row) => fields.map((field) => escape(row[field])).join(','))].join('\n') + '\n';
};

async function main() {
    const publicConfig = {
        baseUrl: config.baseUrl,
        scenarios: config.scenarios,
        samples: config.samples,
        warmup: config.warmup,
        timeoutMs: config.timeoutMs,
        regions: config.regions,
    };
    console.log(JSON.stringify(publicConfig, null, 2));
    if (config.dryRun) return console.log('DRY_RUN: no requests sent.');

    const results = [];
    for (const scenario of config.scenarios) {
        if (scenario === 'public-feed') await publicFeed(results, false);
        if (scenario === 'authenticated-feed') await publicFeed(results, true);
        if (scenario === 'vote') await vote(results);
        if (scenario === 'refresh') await refresh(results);
    }

    const summary = summarize(results);
    console.table(summary);
    await mkdir(config.outputDir, {recursive: true});
    const stamp = new Date().toISOString().replaceAll(':', '-');
    const name = `api-benchmark-${stamp}`;
    const report = {generatedAt: new Date().toISOString(), configuration: publicConfig, summary, samples: results};
    await Promise.all([
        writeFile(resolve(config.outputDir, `${name}.json`), JSON.stringify(report, null, 2) + '\n'),
        writeFile(resolve(config.outputDir, `${name}.csv`), csv(results)),
    ]);
    if (results.some((sample) => !sample.ok)) process.exitCode = 2;
}

main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
});
