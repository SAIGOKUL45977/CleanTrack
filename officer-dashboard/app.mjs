import { CleanTrackApi, CATEGORIES, STATUSES, TRANSITIONS } from './api.mjs';
import { DEFAULT_CONFIG } from './config.mjs';
const $ = id => document.getElementById(id);
const el = (tag, className, text) => { const n = document.createElement(tag); if (className) n.className = className; if (text !== undefined) n.textContent = text; return n; };
const option = (text, value = text) => { const n = el('option', '', text); n.value = value; return n; };
const read = (storage, key) => { try { return JSON.parse(storage.getItem(key)); } catch { return null; } };
const formatDate = value => new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
const badge = (node, status) => {
  node.textContent = status;
  node.className = 'status ' + ({ Submitted: 'review', 'Pending Manual Review': 'review', 'In Progress': 'progress', Resolved: 'resolved', Rejected: 'rejected' }[status] || '');
};
for (const s of STATUSES) $('filter-status').append(option(s));
for (const c of CATEGORIES) $('filter-category').append(option(c));
const saved = read(localStorage, 'cleantrack_config');
$('project-url').value = saved?.url || DEFAULT_CONFIG.url;
$('project-key').value = saved?.key || DEFAULT_CONFIG.key;
$('connection').open = !saved;
let api = null, profile = null, records = [], officers = [], selectedId = null, editorRecord = null;
let dirty = false, polling = null, refreshing = false, saving = false, epoch = 0, detailEpoch = 0, detailLoadedAt = 0;
let pendingPhoto = null;
function connection() {
  const config = { url: $('project-url').value.trim(), key: $('project-key').value.trim() };
  const cached = read(sessionStorage, 'cleantrack_session');
  const result = new CleanTrackApi(config, {
    session: cached?.url === config.url.replace(/\/$/, '') ? cached.session : null,
    onSession: session => {
      if (session) sessionStorage.setItem('cleantrack_session', JSON.stringify({ url: result.url, session }));
      else sessionStorage.removeItem('cleantrack_session');
    }
  });
  localStorage.setItem('cleantrack_config', JSON.stringify({ url: result.url, key: result.key }));
  return result;
}
function deskError(message) { $('desk-error').textContent = message; $('desk-error').hidden = !message; }
function resetDesk() {
  epoch++; detailEpoch++; clearInterval(polling); polling = null;
  records = []; officers = []; selectedId = null; editorRecord = null; dirty = false; pendingPhoto = null;
  $('queue-list').replaceChildren(); $('history-list').replaceChildren();
  $('before-image').removeAttribute('src'); $('after-image').removeAttribute('src');
  $('detail-content').hidden = true; $('detail-empty').hidden = false;
  $('desk-view').hidden = true; $('login-view').hidden = false;
  $('password').value = ''; $('login-button').disabled = false;
  deskError('');
}
async function startDesk(officer) {
  profile = officer; epoch++; const current = epoch;
  $('officer-name').textContent = officer.full_name;
  $('login-view').hidden = true; $('desk-view').hidden = false;
  try {
    officers = await api.officers();
    if (current !== epoch) return;
    $('assigned-officer').replaceChildren(option('Unassigned', ''), ...officers.map(o => option(o.full_name, o.id)));
    await refresh();
    if (current === epoch && profile) polling = setInterval(() => { if (!document.hidden) refresh(); }, 5000);
  } catch (e) { if (current === epoch) deskError(e.message); }
}
$('login-form').addEventListener('submit', async event => {
  event.preventDefault(); $('login-button').disabled = true; $('login-error').textContent = '';
  try {
    api = connection();
    const officer = await api.signIn($('email').value.trim(), $('password').value);
    $('password').value = '';
    await startDesk(officer);
  } catch (e) { $('login-error').textContent = e.message; }
  finally { $('login-button').disabled = false; }
});
$('logout').addEventListener('click', () => {
  const old = api; resetDesk(); api = null; profile = null; old?.signOut();
});
$('refresh').addEventListener('click', () => refresh());
document.addEventListener('visibilitychange', () => { if (!document.hidden && profile) refresh(); });
async function refresh() {
  if (refreshing || !api || !profile) return;
  refreshing = true; const current = epoch; const client = api;
  $('refresh').disabled = true;
  try {
    const result = await client.listComplaints();
    if (current !== epoch) return;
    records = result;
    $('count-all').textContent = records.length;
    $('count-review').textContent = records.filter(c => ['Submitted', 'Pending Manual Review'].includes(c.status)).length;
    $('count-progress').textContent = records.filter(c => c.status === 'In Progress').length;
    $('count-resolved').textContent = records.filter(c => c.status === 'Resolved').length;
    renderQueue();
    $('sync-status').textContent = 'Updated ' + new Date().toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    deskError('');
    const selected = records.find(c => c.id === selectedId);
    if (!selectedId && records.length) await selectReport(records[0]);
    else if (selected && editorRecord && (selected.updated_at !== editorRecord.updated_at || Date.now() - detailLoadedAt > 3000000)) {
      if (dirty || saving) $('conflict').hidden = selected.updated_at === editorRecord.updated_at;
      else await selectReport(selected);
    }
  } catch (e) {
    if (current !== epoch) return;
    if (!client.session) { resetDesk(); profile = null; api = null; $('login-error').textContent = 'Session ended. Sign in again.'; }
    else { deskError('Refresh failed: ' + e.message); $('sync-status').textContent = 'Refresh failed · data may be outdated'; }
  } finally { refreshing = false; $('refresh').disabled = false; }
}
function renderQueue() {
  const query = $('search').value.trim().toLowerCase();
  const visible = records.filter(c => (!$('filter-status').value || c.status === $('filter-status').value)
    && (!$('filter-category').value || c.category === $('filter-category').value)
    && `${c.id} ${c.citizen_name} ${c.category} ${c.location_address} ${c.description || ''}`.toLowerCase().includes(query));
  $('queue-count').textContent = `${visible.length} reports`;
  const nodes = visible.map(c => {
    const button = el('button', 'report-card' + (c.id === selectedId ? ' selected' : ''));
    button.type = 'button'; button.disabled = saving;
    const top = el('div', 'report-top');
    const status = el('span'); badge(status, c.status);
    top.append(el('span', 'report-id', `CT-${String(c.id).padStart(4, '0')}`), status);
    const bottom = el('div', 'report-bottom');
    bottom.append(el('span', '', c.citizen_name), el('span', '', new Date(c.created_at).toLocaleDateString()));
    button.append(top, el('h3', '', c.category), el('p', '', c.location_address), bottom);
    button.addEventListener('click', () => {
      if (dirty && c.id !== selectedId && !confirm('Discard the unsaved officer note and selected cleanup photo?')) return;
      selectReport(c);
    });
    return button;
  });
  if (!nodes.length) nodes.push(el('div', 'empty', records.length ? 'No reports match your filters.' : 'No citizen reports yet. Submit a report from the connected Android app.'));
  $('queue-list').replaceChildren(...nodes);
}
for (const id of ['search', 'filter-status', 'filter-category']) $(id).addEventListener('input', renderQueue);
async function selectReport(record) {
  selectedId = record.id; editorRecord = { ...record }; dirty = false; pendingPhoto = null;
  const current = ++detailEpoch; const client = api;
  $('detail-empty').hidden = true; $('detail-content').hidden = false; $('conflict').hidden = true;
  $('detail-id').textContent = `CT-${String(record.id).padStart(4, '0')}`;
  $('detail-category').textContent = record.category; badge($('detail-status'), record.status);
  $('detail-citizen').textContent = record.citizen_name;
  $('detail-mobile').textContent = record.citizen_mobile_number || 'Not provided in this earlier report';
  $('detail-date').textContent = formatDate(record.created_at);
  $('detail-address').textContent = record.location_address;
  $('map-link').href = 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(`${Number(record.latitude)},${Number(record.longitude)}`);
  $('detail-description').textContent = record.description || 'No additional description.';
  $('update-status').replaceChildren(...(TRANSITIONS[record.status] || []).map(s => option(s)));
  $('update-status').value = record.status; $('assigned-officer').value = record.assigned_to || '';
  $('officer-note').value = ''; $('cleanup-file').value = ''; $('save-status').textContent = '';
  $('photo-error').textContent = ''; $('before-image').removeAttribute('src'); $('after-image').removeAttribute('src');
  $('after-figure').hidden = !record.resolution_photo_path;
  $('history-list').replaceChildren(el('li', '', 'Loading history…'));
  renderQueue();
  const work = await Promise.allSettled([client.signedImage(record.photo_path), client.signedImage(record.resolution_photo_path), client.history(record.id)]);
  if (current !== detailEpoch || client !== api) return;
  if (work[0].status === 'fulfilled') $('before-image').src = work[0].value;
  if (work[1].status === 'fulfilled' && work[1].value) $('after-image').src = work[1].value;
  if (work.slice(0, 2).some(r => r.status === 'rejected')) $('photo-error').textContent = 'Photo could not be loaded. Refresh to retry.';
  if (work[2].status === 'fulfilled') {
    $('history-list').replaceChildren(...work[2].value.map(event => {
      const item = el('li');
      const assignee = officers.find(o => o.id === event.assigned_to)?.full_name;
      item.append(el('strong', '', event.status), el('p', '', event.note + (assignee ? ` · Assigned to ${assignee}` : '')), el('time', '', formatDate(event.created_at)));
      return item;
    }));
  } else $('history-list').replaceChildren(el('li', '', 'History could not be loaded. Refresh to retry.'));
  detailLoadedAt = work.some(r => r.status === 'rejected') ? 0 : Date.now();
}
$('reload-detail').addEventListener('click', () => {
  const latest = records.find(c => c.id === selectedId); if (latest) selectReport(latest);
});
$('update-form').addEventListener('input', () => { dirty = true; });
$('cleanup-file').addEventListener('change', () => { dirty = true; pendingPhoto = null; });
$('update-form').addEventListener('submit', async event => {
  event.preventDefault(); if (saving || !editorRecord) return;
  saving = true; const current = epoch; const client = api; const record = { ...editorRecord };
  const status = $('update-status').value, note = $('officer-note').value, assignedTo = $('assigned-officer').value;
  const file = $('cleanup-file').files[0];
  for (const input of $('update-form').elements) input.disabled = true;
  $('save-status').textContent = 'Saving…'; renderQueue();
  try {
    if (file && !pendingPhoto) pendingPhoto = await client.uploadResolution(file);
    if (current !== epoch) return;
    const updated = await client.update(record, { status, note, assignedTo, photoPath: pendingPhoto });
    if (current !== epoch) return;
    records = records.map(c => c.id === updated.id ? updated : c);
    await selectReport(updated);
    $('save-status').textContent = 'Saved. The citizen app will receive this update.';
    await refresh();
  } catch (e) {
    if (current === epoch) {
      $('save-status').textContent = e.message;
      if (e.message.includes('changed')) { $('conflict').hidden = false; await refresh(); }
    }
  } finally {
    saving = false;
    for (const input of $('update-form').elements) input.disabled = false;
    renderQueue();
  }
});
(async () => {
  if (!saved) return;
  try {
    api = connection();
    if (api.session) await startDesk(await api.officerProfile());
  } catch (e) { $('login-error').textContent = e.message; }
})();
