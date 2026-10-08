import test from 'node:test';
import assert from 'node:assert/strict';
import { CleanTrackApi, validateConfig } from './api.mjs';
const config = { url: 'https://pilot.supabase.co', key: 'sb_publishable_test' };
const session = { access_token: 'citizen-token', refresh_token: 'refresh-token', expires_at: 9999999999, user: { id: 'user-id' } };
const response = (data, status = 200) => new Response(JSON.stringify(data), { status });

test('rejects secret keys and invalid project URLs', () => {
  assert.throws(() => validateConfig(config.url, 'sb_secret_no'), /Never use a secret/);
  const service = 'eyJ.' + btoa(JSON.stringify({ role: 'service_role' })) + '.test';
  assert.throws(() => validateConfig(config.url, service), /service_role/);
  assert.throws(() => validateConfig('http://pilot.supabase.co', config.key), /HTTPS/);
  assert.throws(() => validateConfig(config.url + '/rest', config.key), /without a path/);
});
test('citizen login is refused by officer desk and clears tokens', async () => {
  const paths = [];
  const api = new CleanTrackApi(config, { fetchImpl: async (url) => {
    paths.push(url);
    if (url.includes('grant_type=password')) return response({ ...session, expires_in: 3600 });
    if (url.endsWith('/user')) return response({ id: 'user-id' });
    return response([{ id: 'user-id', full_name: 'Citizen', role: 'citizen' }]);
  } });
  await assert.rejects(() => api.signIn('citizen@example.com', 'secret'), /Officer access/);
  assert.equal(api.session, null);
  assert.equal(paths.some(url => url.includes('/complaints')), false);
});
test('concurrent expired requests perform one refresh and use the new JWT', async () => {
  let refreshes = 0; const auth = [];
  const api = new CleanTrackApi(config, { session: { ...session, expires_at: 0 }, fetchImpl: async (url, options) => {
    if (url.includes('refresh_token')) { refreshes++; await new Promise(resolve => setTimeout(resolve, 10)); return response({ ...session, access_token: 'new-token', expires_at: 9999999999 }); }
    auth.push(options.headers.Authorization); return response([]);
  } });
  await Promise.all([api.request('/rest/v1/complaints'), api.request('/rest/v1/profiles')]);
  assert.equal(refreshes, 1); assert.deepEqual(auth, ['Bearer new-token', 'Bearer new-token']);
});
test('logout during refresh cannot restore the previous session', async () => {
  let release;
  const api = new CleanTrackApi(config, { session: { ...session, expires_at: 0 }, fetchImpl: async () => {
    await new Promise(resolve => { release = resolve; }); return response({ ...session, expires_at: 9999999999 });
  } });
  const task = api.request('/rest/v1/complaints');
  await new Promise(resolve => setImmediate(resolve));
  api.clearSession(); release();
  await assert.rejects(task, /Session changed/); assert.equal(api.session, null);
});
test('a 401 refreshes once and retries with the refreshed JWT', async () => {
  let requests = 0, refreshes = 0;
  const api = new CleanTrackApi(config, { session, fetchImpl: async (url, options) => {
    if (url.includes('refresh_token')) { refreshes++; return response({ ...session, access_token: 'new-token' }); }
    requests++; if (requests === 1) return response({ message: 'expired' }, 401);
    assert.equal(options.headers.Authorization, 'Bearer new-token'); return response([]);
  } });
  await api.request('/rest/v1/complaints'); assert.equal(requests, 2); assert.equal(refreshes, 1);
});
test('resolution requires a photo and transitions are enforced before requests', async () => {
  let calls = 0;
  const api = new CleanTrackApi(config, { session, fetchImpl: async () => { calls++; return response([]); } });
  await assert.rejects(() => api.update({ status: 'Submitted' }, { status: 'Resolved', note: 'Cleaned' }), /valid next/);
  await assert.rejects(() => api.update({ status: 'In Progress' }, { status: 'Resolved', note: 'Cleaned' }), /cleanup photo/);
  await assert.rejects(() => api.update({ status: 'In Progress' }, { status: 'In Progress', note: ' ' }), /officer note/);
  assert.equal(calls, 0);
});
test('updates send the original server timestamp to detect conflicting edits', async () => {
  const record = { id: 8, status: 'In Progress', updated_at: '2026-10-07T08:00:00.123456+00:00' };
  const api = new CleanTrackApi(config, { session, fetchImpl: async (url, options) => {
    assert.match(url, /rpc\/officer_update_complaint/);
    const body = JSON.parse(options.body);
    assert.equal(body.p_expected_updated_at, record.updated_at);
    assert.equal(body.p_resolution_photo_path, 'user-id/resolutions/cleanup.jpg');
    assert.equal(body.p_status, 'Resolved');
    return response([{ ...record, status: 'Resolved' }]);
  } });
  const result = await api.update(record, { status: 'Resolved', note: 'Waste removed', photoPath: 'user-id/resolutions/cleanup.jpg' });
  assert.equal(result.status, 'Resolved');
});
test('private signed photo URLs are cached and other hosts are rejected', async () => {
  let calls = 0;
  const api = new CleanTrackApi(config, { session, fetchImpl: async () => { calls++; return response({ signedURL: '/object/sign/complaint-images/user/reports/a.jpg?token=test' }); } });
  const first = await api.signedImage('user/reports/a.jpg');
  assert.equal(first, await api.signedImage('user/reports/a.jpg')); assert.equal(calls, 1);
  const bad = new CleanTrackApi(config, { session, fetchImpl: async () => response({ signedURL: 'https://other.example/image.jpg' }) });
  await assert.rejects(() => bad.signedImage('user/a.jpg'), /Unexpected photo host/);
});
test('complaint pagination retrieves records beyond the first page', async () => {
  const pages = [];
  const api = new CleanTrackApi(config, { session, fetchImpl: async (url) => {
    const offset = Number(new URL(url).searchParams.get('offset')); pages.push(offset);
    return response(offset === 0 ? Array.from({ length: 500 }, (_, id) => ({ id })) : [{ id: 500 }]);
  } });
  assert.equal((await api.listComplaints()).length, 501); assert.deepEqual(pages, [0, 500]);
});
test('unsupported and oversized cleanup files are rejected before upload', async () => {
  const api = new CleanTrackApi(config, { session, fetchImpl: async () => { throw new Error('Should not upload'); } });
  await assert.rejects(() => api.uploadResolution({ type: 'text/html', size: 5 }), /JPG/);
  await assert.rejects(() => api.uploadResolution({ type: 'image/jpeg', size: 5242881 }), /5 MB/);
});
