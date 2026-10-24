export const STATUSES = ['Submitted', 'Pending Manual Review', 'In Progress', 'Resolved', 'Rejected'];
export const CATEGORIES = ['Overflowing Waste Bin', 'Plastic Waste Accumulation', 'Organic/Food Waste', 'Dry Leaf & Bio Litter', 'E-Waste Disposal'];
export const TRANSITIONS = {
  Submitted: ['Submitted', 'Pending Manual Review', 'In Progress', 'Rejected'],
  'Pending Manual Review': ['Pending Manual Review', 'In Progress', 'Rejected'],
  'In Progress': ['In Progress', 'Resolved', 'Pending Manual Review'],
  Resolved: ['Resolved', 'Pending Manual Review'],
  Rejected: ['Rejected', 'Pending Manual Review']
};
export class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
export function validateConfig(url, key) {
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.search || parsed.hash || parsed.pathname !== '/')
    throw new Error('Enter the HTTPS Supabase project URL without a path.');
  if (!key?.trim() || key.includes('REPLACE') || key.startsWith('sb_secret_'))
    throw new Error('Use the Supabase publishable key or legacy anon key. Never use a secret key.');
  if (key.startsWith('eyJ')) {
    try {
      const part = key.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      if (JSON.parse(atob(part)).role === 'service_role') throw new Error('service_role');
    } catch (e) { if (e.message === 'service_role') throw new Error('A service_role key must never be used in this dashboard.'); }
  }
  return { url: parsed.origin, key: key.trim() };
}
export class CleanTrackApi {
  constructor(config, { fetchImpl = fetch, onSession = () => {}, session = null, now = () => Date.now() } = {}) {
    Object.assign(this, validateConfig(config.url, config.key));
    this.fetch = fetchImpl; this.onSession = onSession; this.session = session; this.now = now;
    this.refreshing = null; this.generation = 0; this.imageCache = new Map();
  }
  saveSession(data) {
    this.session = { ...data, expires_at: data.expires_at ?? Math.floor(this.now() / 1000) + data.expires_in };
    this.onSession(this.session);
  }
  clearSession() { this.generation++; this.session = null; this.imageCache.clear(); this.onSession(null); }
  async raw(path, { method = 'GET', body, token, mime = 'application/json' } = {}) {
    const headers = { apikey: this.key };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers['Content-Type'] = mime;
    const response = await this.fetch(this.url + path, { method, headers, credentials: 'omit',
      signal: AbortSignal.timeout(30000), body: body === undefined ? undefined :
        mime === 'application/json' ? JSON.stringify(body) : body });
    const text = await response.text();
    let data;
    try { data = text ? JSON.parse(text) : null; } catch { data = null; }
    if (!response.ok) throw new ApiError(response.status, data?.msg || data?.message || data?.error_description || `Request failed (${response.status}).`);
    return data;
  }
  async token(force = false, failedToken = null) {
    if (!this.session) throw new Error('Sign in again.');
    if (force && failedToken && this.session.access_token !== failedToken) return this.session.access_token;
    if (!force && this.session.expires_at > this.now() / 1000 + 60) return this.session.access_token;
    if (!this.refreshing) {
      const generation = this.generation;
      const refreshToken = this.session.refresh_token;
      this.refreshing = (async () => {
        try {
          const data = await this.raw('/auth/v1/token?grant_type=refresh_token', {
            method: 'POST', body: { refresh_token: refreshToken }
          });
          if (generation !== this.generation) throw new Error('Session changed. Sign in again.');
          this.saveSession(data);
          return data.access_token;
        } catch (e) {
          if ([400, 401, 403].includes(e.status)) this.clearSession();
          throw e;
        } finally { this.refreshing = null; }
      })();
    }
    return this.refreshing;
  }
  async request(path, options = {}) {
    const token = await this.token();
    try { return await this.raw(path, { ...options, token }); }
    catch (e) {
      if (e.status !== 401) throw e;
      return this.raw(path, { ...options, token: await this.token(true, token) });
    }
  }
  async officerProfile() {
    const user = await this.request('/auth/v1/user');
    const rows = await this.request(`/rest/v1/profiles?id=eq.${encodeURIComponent(user.id)}&select=id,full_name,role`);
    if (rows?.[0]?.role !== 'officer') {
      this.clearSession();
      throw new Error('Officer access is not assigned. Ask the project administrator to promote this account.');
    }
    return rows[0];
  }
  async signIn(email, password) {
    const data = await this.raw('/auth/v1/token?grant_type=password', { method: 'POST', body: { email, password } });
    this.generation++; this.saveSession(data);
    return this.officerProfile();
  }
  async signOut() {
    const old = this.session?.access_token;
    this.clearSession();
    if (old) { try { await this.raw('/auth/v1/logout?scope=local', { method: 'POST', token: old }); } catch {} }
  }
  async listComplaints() {
    // Paginated fetch: avoid silently dropping records at Supabase's default row limit.
    const result = [];
    for (let offset = 0; ; offset += 500) {
      const page = await this.request(`/rest/v1/complaints?select=*&order=created_at.desc,id.desc&limit=500&offset=${offset}`);
      result.push(...page);
      if (page.length < 500) return result;
    }
  }
  async officers() { return this.request('/rest/v1/profiles?role=eq.officer&select=id,full_name&order=full_name.asc'); }
  async history(id) { return this.request(`/rest/v1/complaint_events?complaint_id=eq.${encodeURIComponent(id)}&select=*&order=created_at.asc,id.asc`); }
  async signedImage(path) {
    if (!path) return null;
    const cached = this.imageCache.get(path);
    if (cached?.until > this.now()) return cached.url;
    const result = await this.request('/storage/v1/object/sign/complaint-images/' + path.split('/').map(encodeURIComponent).join('/'),
      { method: 'POST', body: { expiresIn: 3600 } });
    const url = new URL(result.signedURL.startsWith('https://') ? result.signedURL : this.url + '/storage/v1' + result.signedURL);
    if (url.origin !== this.url) throw new Error('Unexpected photo host.');
    this.imageCache.set(path, { url: url.href, until: this.now() + 3000000 });
    return url.href;
  }
  async uploadResolution(file) {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024 || file.size === 0)
      throw new Error('Choose a JPG, PNG, or WebP cleanup photo smaller than 5 MB.');
    const extension = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' }[file.type];
    const path = `${this.session.user.id}/resolutions/${crypto.randomUUID()}.${extension}`;
    await this.request('/storage/v1/object/complaint-images/' + path, { method: 'POST', body: file, mime: file.type });
    return path;
  }
  async update(record, { status, note, assignedTo = null, photoPath = null }) {
    if (!TRANSITIONS[record.status]?.includes(status)) throw new Error('Choose a valid next status.');
    if (!note?.trim() || note.trim().length > 2000) throw new Error('Add an officer note (1–2000 characters).');
    if (status === 'Resolved' && !photoPath && !record.resolution_photo_path) throw new Error('Upload a cleanup photo before resolving.');
    const rows = await this.request('/rest/v1/rpc/officer_update_complaint', { method: 'POST', body: {
      p_id: record.id, p_expected_updated_at: record.updated_at, p_status: status,
      p_note: note.trim(), p_assigned_to: assignedTo || null, p_resolution_photo_path: photoPath
    } });
    return rows[0];
  }
}
