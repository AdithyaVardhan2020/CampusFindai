import { test, describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';

const testDir = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
const staticJsDir = fs.existsSync(path.resolve('src/main/resources/static/js'))
  ? path.resolve('src/main/resources/static/js')
  : path.resolve(testDir, '../../main/resources/static/js');

describe('CampusFind Frontend Unit Tests', () => {
  let context;

  beforeEach(() => {
    // Setup clean browser-like global context
    const storage = new Map();
    const sessionStorageMock = {
      getItem: (k) => storage.get(k) || null,
      setItem: (k, v) => storage.set(k, String(v)),
      removeItem: (k) => storage.delete(k),
      clear: () => storage.clear()
    };

    context = {
      window: {},
      sessionStorage: sessionStorageMock,
      btoa: (str) => Buffer.from(str, 'binary').toString('base64'),
      atob: (b64) => Buffer.from(b64, 'base64').toString('binary'),
      URLSearchParams: URLSearchParams,
      FormData: class FormData {},
      AbortController: AbortController,
      setTimeout: setTimeout,
      clearTimeout: clearTimeout,
      console: console,
      document: {
        addEventListener: () => {},
        querySelectorAll: () => [],
        getElementById: () => null
      }
    };
    context.window = context;

    // Load config.js, auth.js, api.js, app.js into context
    ['config.js', 'auth.js', 'api.js', 'app.js'].forEach(file => {
      const code = fs.readFileSync(path.join(staticJsDir, file), 'utf8');
      vm.runInNewContext(code, context);
    });
  });

  test('config.js provides default categories and API base URL', () => {
    const config = context.window.CAMPUSFIND_CONFIG;
    assert.ok(config);
    assert.equal(config.apiBaseUrl, '/api/v1');
    assert.ok(Array.isArray(config.defaultCategories));
    assert.ok(config.defaultCategories.includes('Bags'));
    assert.ok(config.defaultCategories.includes('Phones'));
    assert.ok(config.defaultCategories.includes('Electronics'));
  });

  test('auth.js correctly manages user session and roles', () => {
    const auth = context.window.auth;
    assert.equal(auth.isAuthenticated(), false);
    assert.equal(auth.isAdmin(), false);

    // Set student session
    auth.setUser({ id: 1, fullName: 'Alice Student', email: 'alice@campus.edu', role: 'ROLE_STUDENT' }, 'YWxpY2U6cGFzcw==');
    assert.equal(auth.isAuthenticated(), true);
    assert.equal(auth.isAdmin(), false);
    assert.equal(auth.getBasicHeader(), 'Basic YWxpY2U6cGFzcw==');

    // Update to admin
    auth.setUser({ id: 2, fullName: 'Admin User', email: 'admin@campus.edu', role: 'ROLE_ADMIN' }, 'YWRtaW46cGFzcw==');
    assert.equal(auth.isAdmin(), true);

    // Logout clears session
    auth.clearUser();
    assert.equal(auth.isAuthenticated(), false);
    assert.equal(auth.getUser(), null);
  });

  test('app.js escapeHtml prevents XSS injections', () => {
    const app = context.window.app;
    assert.equal(app.escapeHtml('<script>alert("xss")</script>'), '&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;');
    assert.equal(app.escapeHtml('Safe Text'), 'Safe Text');
    assert.equal(app.escapeHtml('Tom & Jerry'), 'Tom &amp; Jerry');
  });

  test('app.js formatDate handles valid dates and empty values', () => {
    const app = context.window.app;
    assert.equal(app.formatDate(''), 'Recent');
    const formatted = app.formatDate('2026-10-09T12:00:00Z');
    assert.ok(formatted.includes('2026') || formatted.includes('Oct'));
  });

  test('api.js request constructs correct basic auth headers from session', async () => {
    const auth = context.window.auth;
    const api = context.window.api;

    auth.setUser({ id: 1, fullName: 'Student', email: 'student@campus.edu', role: 'ROLE_STUDENT' }, 'c3R1ZGVudDpwYXNz');

    // Mock fetch in context
    let capturedUrl = '';
    let capturedHeaders = {};
    context.fetch = async (url, options) => {
      capturedUrl = url;
      capturedHeaders = options.headers;
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => [{ id: 10, title: 'Lost Keys' }]
      };
    };

    const reports = await api.getReports({ type: 'LOST', status: 'OPEN' });
    assert.ok(capturedUrl.includes('/api/v1/reports?type=LOST&status=OPEN'));
    assert.equal(capturedHeaders['Authorization'], 'Basic c3R1ZGVudDpwYXNz');
    assert.equal(reports.length, 1);
    assert.equal(reports[0].title, 'Lost Keys');
  });

  test('config.js automatically routes to live Render backend on Vercel domain', () => {
    const vercelContext = {
      window: {
        location: { hostname: 'campusfind-demo.vercel.app' }
      },
      sessionStorage: { getItem: () => null },
      localStorage: { getItem: () => null },
      console: console
    };
    vercelContext.window.location = { hostname: 'campusfind-demo.vercel.app' };

    const configCode = fs.readFileSync(path.join(staticJsDir, 'config.js'), 'utf8');
    vm.runInNewContext(configCode, vercelContext);

    assert.equal(vercelContext.window.CAMPUSFIND_CONFIG.apiBaseUrl, 'https://campusfind-backend-wary.onrender.com/api/v1');
  });

  test('api.sendChatMessage routes through api.baseUrl and sends message body', async () => {
    const api = context.window.api;
    let capturedUrl = '';
    let capturedBody = null;

    context.fetch = async (url, options) => {
      capturedUrl = url;
      capturedBody = JSON.parse(options.body);
      return {
        ok: true,
        status: 200,
        headers: new Map([['content-type', 'application/json']]),
        json: async () => ({ reply: 'Hello! I can help you find lost items.' })
      };
    };

    const res = await api.sendChatMessage('Where is my bag?', []);
    assert.ok(capturedUrl.endsWith('/chatbot/message'));
    assert.equal(capturedBody.message, 'Where is my bag?');
    assert.equal(res.reply, 'Hello! I can help you find lost items.');
  });
});
