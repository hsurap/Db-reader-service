(() => {
  const state = {
    apiKey: localStorage.getItem('dbReaderApiKey') || '',
    connected: false,
    currentDb: null,
    currentColl: null,
    lastResults: [],
  };

  const el = (id) => document.getElementById(id);

  const apiKeyInput = el('apiKeyInput');
  const connectBtn = el('connectBtn');
  const connDot = el('connDot');
  const connLabel = el('connLabel');
  const dbTree = el('dbTree');
  const refreshBtn = el('refreshBtn');
  const emptyState = el('emptyState');
  const workspace = el('workspace');
  const pathDb = el('pathDb');
  const pathColl = el('pathColl');
  const resultsMeta = el('resultsMeta');
  const resultsJson = el('resultsJson');
  const resultsTable = el('resultsTable');
  const viewJsonBtn = el('viewJsonBtn');
  const viewTableBtn = el('viewTableBtn');
  const copyBtn = el('copyBtn');

  apiKeyInput.value = state.apiKey;

  function setConn(status, label) {
    connDot.className = 'dot ' + (status === 'on' ? 'dot-on' : status === 'err' ? 'dot-err' : 'dot-off');
    connLabel.textContent = label;
  }

  async function api(path, options = {}) {
    const res = await fetch(path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        'X-API-KEY': state.apiKey,
        ...(options.headers || {}),
      },
    });
    if (!res.ok) {
      let msg = res.status + ' ' + res.statusText;
      try {
        const body = await res.json();
        if (body && body.error) msg = body.error;
      } catch (_) {}
      throw new Error(msg);
    }
    return res.json();
  }

  async function connect() {
    state.apiKey = apiKeyInput.value.trim();
    localStorage.setItem('dbReaderApiKey', state.apiKey);
    setConn('off', 'connecting…');
    try {
      const dbs = await api('/api/metadata/databases');
      state.connected = true;
      setConn('on', 'connected');
      renderDbTree(dbs);
    } catch (e) {
      state.connected = false;
      setConn('err', 'failed');
      dbTree.innerHTML = `<div class="empty-hint">Connection failed: ${escapeHtml(e.message)}</div>`;
    }
  }

  function renderDbTree(dbs) {
    dbTree.innerHTML = '';
    if (!dbs.length) {
      dbTree.innerHTML = '<div class="empty-hint">No databases visible to this user.</div>';
      return;
    }
    dbs.forEach((dbName) => {
      const node = document.createElement('div');
      node.className = 'db-node';

      const row = document.createElement('div');
      row.className = 'db-row';
      row.innerHTML = `<span class="caret">&#9656;</span><span>${escapeHtml(dbName)}</span>`;

      const collList = document.createElement('div');
      collList.className = 'coll-list';

      let loaded = false;
      row.addEventListener('click', async () => {
        const caret = row.querySelector('.caret');
        const isOpen = collList.classList.toggle('open');
        caret.classList.toggle('open', isOpen);
        if (isOpen && !loaded) {
          collList.innerHTML = '<div class="empty-hint">Loading…</div>';
          try {
            const colls = await api(`/api/metadata/databases/${encodeURIComponent(dbName)}/collections`);
            loaded = true;
            renderCollList(collList, dbName, colls);
          } catch (e) {
            collList.innerHTML = `<div class="empty-hint">${escapeHtml(e.message)}</div>`;
          }
        }
      });

      node.appendChild(row);
      node.appendChild(collList);
      dbTree.appendChild(node);
    });
  }

  function renderCollList(container, dbName, colls) {
    container.innerHTML = '';
    if (!colls.length) {
      container.innerHTML = '<div class="empty-hint">No collections.</div>';
      return;
    }
    colls.forEach((collName) => {
      const row = document.createElement('div');
      row.className = 'coll-row';
      row.textContent = collName;
      row.addEventListener('click', () => selectCollection(dbName, collName, row));
      container.appendChild(row);
    });
  }

  function selectCollection(dbName, collName, rowEl) {
    document.querySelectorAll('.coll-row.selected').forEach((r) => r.classList.remove('selected'));
    rowEl.classList.add('selected');
    state.currentDb = dbName;
    state.currentColl = collName;
    pathDb.textContent = dbName;
    pathColl.textContent = collName;
    emptyState.classList.add('hidden');
    workspace.classList.remove('hidden');
    clearResults();
  }

  // Tabs
  document.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tab-btn').forEach((b) => b.classList.remove('active'));
      document.querySelectorAll('.tab-panel').forEach((p) => p.classList.add('hidden'));
      btn.classList.add('active');
      document.querySelector(`.tab-panel[data-panel="${btn.dataset.tab}"]`).classList.remove('hidden');
    });
  });

  function parseJsonField(id, fallback) {
    const raw = el(id).value.trim();
    if (!raw) return fallback;
    try {
      return JSON.parse(raw);
    } catch (e) {
      throw new Error(`Invalid JSON in "${id}": ${e.message}`);
    }
  }

  function requireSelection() {
    if (!state.currentDb || !state.currentColl) {
      throw new Error('Select a collection first.');
    }
  }

  async function runFind() {
    try {
      requireSelection();
      const body = {
        filter: parseJsonField('findFilter', {}),
        projection: parseJsonField('findProjection', undefined),
        sort: parseJsonField('findSort', undefined),
        limit: numOrNull('findLimit'),
        skip: numOrNull('findSkip'),
      };
      await runQuery(`/api/query/${enc(state.currentDb)}/${enc(state.currentColl)}/find`, body);
    } catch (e) {
      showError(e);
    }
  }

  async function runAggregate() {
    try {
      requireSelection();
      const pipeline = parseJsonField('aggPipeline', []);
      if (!Array.isArray(pipeline)) throw new Error('Pipeline must be a JSON array of stages.');
      const body = { pipeline, limit: numOrNull('aggLimit') };
      await runQuery(`/api/query/${enc(state.currentDb)}/${enc(state.currentColl)}/aggregate`, body);
    } catch (e) {
      showError(e);
    }
  }

  async function runCount() {
    try {
      requireSelection();
      const body = { filter: parseJsonField('countFilter', {}) };
      const started = performance.now();
      const result = await api(`/api/query/${enc(state.currentDb)}/${enc(state.currentColl)}/count`, {
        method: 'POST',
        body: JSON.stringify(body),
      });
      const ms = Math.round(performance.now() - started);
      state.lastResults = [result];
      resultsMeta.textContent = `count: ${result.count} · ${ms}ms`;
      showJson(result);
    } catch (e) {
      showError(e);
    }
  }

  async function runQuery(path, body) {
    resultsMeta.textContent = 'running…';
    const started = performance.now();
    const results = await api(path, { method: 'POST', body: JSON.stringify(body) });
    const ms = Math.round(performance.now() - started);
    state.lastResults = results;
    resultsMeta.textContent = `${results.length} document${results.length === 1 ? '' : 's'} · ${ms}ms`;
    showJson(results);
    renderTable(results);
  }

  function numOrNull(id) {
    const v = el(id).value.trim();
    return v === '' ? null : Number(v);
  }
  function enc(v) { return encodeURIComponent(v); }

  function showError(e) {
    resultsMeta.textContent = 'error';
    resultsJson.innerHTML = `<span class="err">${escapeHtml(e.message)}</span>`;
    resultsTable.innerHTML = '';
    setView('json');
  }

  function clearResults() {
    resultsMeta.textContent = '';
    resultsJson.textContent = '';
    resultsTable.innerHTML = '';
  }

  function showJson(obj) {
    resultsJson.innerHTML = highlightJson(JSON.stringify(obj, null, 2));
  }

  function renderTable(results) {
    if (!Array.isArray(results) || results.length === 0) {
      resultsTable.innerHTML = '<div class="empty-hint" style="color:var(--text-muted); padding:14px;">No rows.</div>';
      return;
    }
    const cols = [];
    results.forEach((doc) => {
      Object.keys(doc).forEach((k) => { if (!cols.includes(k)) cols.push(k); });
    });
    const limitedCols = cols.slice(0, 12);
    let html = '<table class="grid"><thead><tr>' +
      limitedCols.map((c) => `<th>${escapeHtml(c)}</th>`).join('') + '</tr></thead><tbody>';
    results.forEach((doc) => {
      html += '<tr>' + limitedCols.map((c) => {
        let v = doc[c];
        if (v === undefined) v = '';
        else if (typeof v === 'object') v = JSON.stringify(v);
        return `<td title="${escapeHtml(String(v))}">${escapeHtml(String(v))}</td>`;
      }).join('') + '</tr>';
    });
    html += '</tbody></table>';
    resultsTable.innerHTML = html;
  }

  function setView(view) {
    if (view === 'json') {
      resultsJson.classList.remove('hidden');
      resultsTable.classList.add('hidden');
      viewJsonBtn.classList.add('active');
      viewTableBtn.classList.remove('active');
    } else {
      resultsJson.classList.add('hidden');
      resultsTable.classList.remove('hidden');
      viewTableBtn.classList.add('active');
      viewJsonBtn.classList.remove('active');
    }
  }

  function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
  }

  function highlightJson(jsonStr) {
    const escaped = escapeHtml(jsonStr);
    return escaped.replace(
      /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
      (match) => {
        let cls = 'n';
        if (/^"/.test(match)) {
          cls = /:$/.test(match) ? 'k' : 's';
        } else if (/true|false/.test(match)) {
          cls = 'b';
        } else if (/null/.test(match)) {
          cls = 'b';
        }
        return `<span class="${cls}">${match}</span>`;
      }
    );
  }

  connectBtn.addEventListener('click', connect);
  apiKeyInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') connect(); });
  refreshBtn.addEventListener('click', () => { if (state.connected) connect(); });
  el('findRun').addEventListener('click', runFind);
  el('aggRun').addEventListener('click', runAggregate);
  el('countRun').addEventListener('click', runCount);
  viewJsonBtn.addEventListener('click', () => setView('json'));
  viewTableBtn.addEventListener('click', () => setView('table'));
  copyBtn.addEventListener('click', () => {
    navigator.clipboard.writeText(JSON.stringify(state.lastResults, null, 2)).catch(() => {});
  });

  if (state.apiKey) connect();
})();
