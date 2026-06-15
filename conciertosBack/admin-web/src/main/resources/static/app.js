/* Admin UI — Data Quality + Manual CRUD */

// ---------------------------------------------------------------------------
// Panel navigation
// ---------------------------------------------------------------------------

function showPanel(name) {
    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
    var panel = document.getElementById('panel-' + name);
    if (panel) panel.classList.add('active');

    // Lazy-load data when switching to a panel
    if (name === 'quality') loadIssues();
    if (name === 'concerts') loadConcerts();
    if (name === 'new-concert') loadDropdowns();
    if (name === 'edit-salas') loadSalasTab();
    if (name === 'edit-artistas') loadArtistasTab();
    if (name === 'edit-conciertos') loadConciertosTab();
}

// ---------------------------------------------------------------------------
// Data Quality (existing)
// ---------------------------------------------------------------------------

function loadIssues() {
    document.getElementById('status').textContent = 'Loading...';
    fetch('/admin/quality/severe')
        .then(res => {
            if (res.status === 401) {
                document.getElementById('status').textContent = 'Authentication required. Please reload and provide credentials.';
                return [];
            }
            if (!res.ok) {
                document.getElementById('status').textContent = 'Error loading issues: ' + res.status;
                return [];
            }
            return res.json();
        })
        .then(issues => {
            renderTable(issues);
        })
        .catch(err => {
            document.getElementById('status').textContent = 'Network error: ' + err.message;
        });
}

function renderTable(issues) {
    const tbody = document.getElementById('issues-body');
    tbody.replaceChildren();

    if (issues.length === 0) {
        document.getElementById('status').textContent = 'No unresolved severe issues.';
        return;
    }

    document.getElementById('status').textContent = issues.length + ' unresolved severe issue(s).';

    issues.forEach(issue => {
        const tr = document.createElement('tr');
        tr.id = 'row-' + issue.id;

        const suggested = issue.suggested || '';
        const score = issue.score != null ? issue.score.toFixed(2) : '-';
        const impact = issue.blockedConcertCount != null ? issue.blockedConcertCount : 0;
        const updatedAt = issue.updatedAt || '';

        appendCell(tr, issue.id);
        appendCell(tr, issue.entityType);
        appendCell(tr, issue.entityId);
        appendCell(tr, issue.field);
        appendCell(tr, issue.status);
        appendCell(tr, impact);
        appendCell(tr, suggested);
        appendCell(tr, score);
        appendCell(tr, issue.severity);
        appendCell(tr, updatedAt);
        tr.appendChild(buildFillCell(issue.id, suggested));

        tbody.appendChild(tr);
    });
}

function appendCell(row, value) {
    const td = document.createElement('td');
    td.textContent = value == null ? '' : String(value);
    row.appendChild(td);
}

function buildFillCell(id, suggested) {
    const td = document.createElement('td');
    const form = document.createElement('div');
    form.className = 'fill-form';

    const input = document.createElement('input');
    input.type = 'text';
    input.id = 'value-' + id;
    input.placeholder = 'Enter value...';
    input.value = suggested;

    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = 'Fill';
    button.addEventListener('click', () => fillIssue(id));

    const message = document.createElement('div');
    message.id = 'msg-' + id;

    form.appendChild(input);
    form.appendChild(button);
    td.appendChild(form);
    td.appendChild(message);
    return td;
}

function fillIssue(id) {
    const input = document.getElementById('value-' + id);
    const msgEl = document.getElementById('msg-' + id);
    const value = input.value.trim();

    if (!value) {
        msgEl.className = 'msg-err';
        msgEl.textContent = 'Value must not be blank.';
        return;
    }

    fetch('/admin/quality/' + id + '/fill', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ value: value })
    })
    .then(res => {
        if (res.ok) {
            msgEl.className = 'msg-ok';
            msgEl.textContent = 'Filled successfully.';
            setTimeout(() => loadIssues(), 800);
        } else {
            return res.text().then(text => {
                msgEl.className = 'msg-err';
                msgEl.textContent = 'Error ' + res.status + ': ' + text;
            });
        }
    })
    .catch(err => {
        msgEl.className = 'msg-err';
        msgEl.textContent = 'Network error: ' + err.message;
    });
}

// ---------------------------------------------------------------------------
// Concert list
// ---------------------------------------------------------------------------

function loadConcerts() {
    var statusEl = document.getElementById('concerts-status');
    statusEl.textContent = 'Loading...';

    // Fetch concerts plus salas/artists so the list can show names instead of raw ids.
    // The name lookups are best-effort: if either fails we fall back to the id.
    Promise.all([
        fetchJsonOrThrow('/admin/concerts'),
        fetchJsonOrEmpty('/admin/salas-list'),
        fetchJsonOrEmpty('/admin/artists-list')
    ])
        .then(([concerts, salas, artists]) => {
            const salaNames = new Map(salas.map(s => [s.id, s.name]));
            const artistNames = new Map(artists.map(a => [a.id, a.name]));
            renderConcertsTable(concerts, salaNames, artistNames);
        })
        .catch(err => {
            statusEl.textContent = 'Error loading concerts: ' + err.message;
        });
}

function fetchJsonOrThrow(path) {
    return fetch(path).then(res => {
        if (!res.ok) throw new Error(String(res.status));
        return res.json();
    });
}

function fetchJsonOrEmpty(path) {
    return fetch(path).then(res => res.ok ? res.json() : []).catch(() => []);
}

function renderConcertsTable(concerts, salaNames, artistNames) {
    var statusEl = document.getElementById('concerts-status');
    var tbody = document.getElementById('concerts-body');
    tbody.replaceChildren();

    if (!concerts || concerts.length === 0) {
        statusEl.textContent = 'No active concerts.';
        return;
    }

    statusEl.textContent = concerts.length + ' concert(s).';

    concerts.forEach(concert => {
        const salaId = concert.salaConcierto_id || '';
        const salaLabel = salaNames.get(salaId) || salaId;
        const artistLabel = (concert.artist_ids || [])
            .map(id => artistNames.get(id) || id)
            .join(', ');

        const tr = document.createElement('tr');
        appendCell(tr, concert.id);
        appendCell(tr, concert.date || '');
        appendCell(tr, salaLabel);
        appendCell(tr, artistLabel);

        // Delete action cell
        const td = document.createElement('td');
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = 'Delete';
        btn.addEventListener('click', () => confirmAndDelete(concert.id));
        td.appendChild(btn);
        tr.appendChild(td);

        tbody.appendChild(tr);
    });
}

function confirmAndDelete(id) {
    if (!window.confirm('Delete concert ' + id + '? This cannot be undone.')) return;

    fetch('/admin/concerts/' + encodeURIComponent(id) + '/delete', {
        method: 'POST',
        headers: { 'X-Requested-With': 'XMLHttpRequest' }
    })
        .then(res => {
            // 303 redirect is followed by fetch automatically; 2xx = success
            if (res.ok || res.redirected) {
                loadConcerts();
            } else {
                return res.text().then(text => {
                    alert('Error ' + res.status + ': ' + text);
                });
            }
        })
        .catch(err => {
            alert('Network error: ' + err.message);
        });
}

// ---------------------------------------------------------------------------
// Create forms — sala / artist / concert
// ---------------------------------------------------------------------------

function submitSala(event) {
    event.preventDefault();
    var resultEl = document.getElementById('sala-result');
    var errorEl = document.getElementById('sala-error');
    resultEl.style.display = 'none';
    errorEl.style.display = 'none';

    var params = new URLSearchParams();
    params.append('name', document.getElementById('sala-name').value.trim());
    params.append('address', document.getElementById('sala-address').value.trim());
    params.append('city', document.getElementById('sala-city').value.trim());
    params.append('province', document.getElementById('sala-province').value.trim());
    var lat = document.getElementById('sala-lat').value.trim();
    var lng = document.getElementById('sala-lng').value.trim();
    if (lat) params.append('lat', lat);
    if (lng) params.append('lng', lng);
    var imageUrl = document.getElementById('sala-image-url').value.trim();
    var description = document.getElementById('sala-description').value.trim();
    if (imageUrl) params.append('imageUrl', imageUrl);
    if (description) params.append('description', description);

    postForm('/admin/salas', params, resultEl, errorEl, 'Sala created with id: ');
}

function submitArtist(event) {
    event.preventDefault();
    var resultEl = document.getElementById('artist-result');
    var errorEl = document.getElementById('artist-error');
    resultEl.style.display = 'none';
    errorEl.style.display = 'none';

    var params = new URLSearchParams();
    params.append('name', document.getElementById('artist-name').value.trim());
    var genre = document.getElementById('artist-genre').value.trim();
    var imageUrl = document.getElementById('artist-image-url').value.trim();
    var website = document.getElementById('artist-website').value.trim();
    var description = document.getElementById('artist-description').value.trim();
    if (genre) params.append('genre', genre);
    if (imageUrl) params.append('imageUrl', imageUrl);
    if (website) params.append('website', website);
    if (description) params.append('description', description);

    postForm('/admin/artists', params, resultEl, errorEl, 'Artist created with id: ');
}

function submitConcert(event) {
    event.preventDefault();
    var resultEl = document.getElementById('concert-result');
    var errorEl = document.getElementById('concert-error');
    resultEl.style.display = 'none';
    errorEl.style.display = 'none';

    var salaSelect = document.getElementById('concert-sala');
    var artistSelect = document.getElementById('concert-artists');
    var selectedArtists = Array.from(artistSelect.selectedOptions).map(o => o.value);

    if (!salaSelect.value) {
        errorEl.textContent = 'Please select a sala.';
        errorEl.style.display = 'block';
        return;
    }
    if (selectedArtists.length === 0) {
        errorEl.textContent = 'Please select at least one artist.';
        errorEl.style.display = 'block';
        return;
    }

    var params = new URLSearchParams();
    params.append('salaConciertoId', salaSelect.value);
    selectedArtists.forEach(id => params.append('artistIds', id));
    params.append('date', document.getElementById('concert-date').value);
    var time = document.getElementById('concert-time').value.trim();
    var price = document.getElementById('concert-price').value.trim();
    if (time) params.append('time', time);
    if (price) params.append('price', price);

    postForm('/admin/concerts', params, resultEl, errorEl, 'Concert created with id: ');
}

/**
 * Posts form-encoded params; on success shows the minted id (via redirect response),
 * on error shows the api {error} message from the JSON response body.
 */
function postForm(path, params, resultEl, errorEl, successPrefix) {
    fetch(path, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Requested-With': 'XMLHttpRequest'
        },
        body: params.toString(),
        redirect: 'manual'
    })
    .then(res => {
        if (res.type === 'opaqueredirect' || res.status === 303 || res.status === 302) {
            // Success — server redirected; extract id from Location header if possible
            var location = res.headers.get('Location') || '';
            var match = location.match(/id=([^&]+)/);
            var id = match ? decodeURIComponent(match[1]) : '';
            resultEl.textContent = successPrefix + id;
            resultEl.style.display = 'block';
            return;
        }
        return res.json().then(data => {
            if (data && data.error) {
                errorEl.textContent = data.error;
                errorEl.style.display = 'block';
            } else {
                errorEl.textContent = 'Unexpected response.';
                errorEl.style.display = 'block';
            }
        });
    })
    .catch(err => {
        errorEl.textContent = 'Network error: ' + err.message;
        errorEl.style.display = 'block';
    });
}

// ---------------------------------------------------------------------------
// Dropdown population for concert create form
// ---------------------------------------------------------------------------

function loadDropdowns() {
    loadSalasDropdown();
    loadArtistsDropdown();
}

function loadSalasDropdown() {
    var salaSelect = document.getElementById('concert-sala');
    // Keep existing options intact if already populated
    if (salaSelect.options.length > 1) return;

    fetch('/admin/salas-list')
        .then(res => res.ok ? res.json() : [])
        .then(salas => {
            salas
                .slice()
                .sort((a, b) => (a.name || '').localeCompare(b.name || '', 'es', { sensitivity: 'base' }))
                .forEach(sala => {
                    var opt = document.createElement('option');
                    opt.value = sala.id;
                    opt.textContent = sala.name + ' (' + (sala.city || '') + ')';
                    salaSelect.appendChild(opt);
                });
        })
        .catch(function() { /* non-fatal: operator can select manually */ });
}

function loadArtistsDropdown() {
    var artistSelect = document.getElementById('concert-artists');
    if (artistSelect.options.length > 0) return;

    fetch('/admin/artists-list')
        .then(res => res.ok ? res.json() : [])
        .then(artists => {
            artists
                .slice()
                .sort((a, b) => (a.name || '').localeCompare(b.name || '', 'es', { sensitivity: 'base' }))
                .forEach(artist => {
                    var opt = document.createElement('option');
                    opt.value = artist.id;
                    opt.textContent = artist.name;
                    artistSelect.appendChild(opt);
                });
        })
        .catch(() => {/* non-fatal */});
}

// ---------------------------------------------------------------------------
// Edit tabs — salas / artistas / conciertos (including SEVERE-blocked entities)
// ---------------------------------------------------------------------------

var editSalasData = [];
var editArtistasData = [];
var editConciertosData = [];

// --- Salas tab ---

function loadSalasTab() {
    var statusEl = document.getElementById('salas-status');
    statusEl.textContent = 'Loading...';
    closeAnyOpenEditForm();

    fetchJsonOrThrow('/admin/salas-all')
        .then(salas => {
            editSalasData = salas || [];
            renderSalasTable(editSalasData);
        })
        .catch(err => {
            statusEl.textContent = 'Error loading salas: ' + err.message;
        });
}

function filterSalas() {
    var term = document.getElementById('salas-search').value.trim().toLowerCase();
    if (!term) {
        renderSalasTable(editSalasData);
        return;
    }
    var filtered = editSalasData.filter(sala =>
        (sala.name || '').toLowerCase().includes(term) ||
        (sala.city || '').toLowerCase().includes(term)
    );
    renderSalasTable(filtered);
}

function renderSalasTable(salas) {
    var statusEl = document.getElementById('salas-status');
    var tbody = document.getElementById('salas-body');
    tbody.replaceChildren();

    if (!salas || salas.length === 0) {
        statusEl.textContent = 'No salas found.';
        return;
    }

    statusEl.textContent = salas.length + ' sala(s).';

    salas.forEach(sala => {
        const tr = document.createElement('tr');
        appendCell(tr, sala.id);
        appendCell(tr, sala.name || '');
        appendCell(tr, sala.city || '');
        appendCell(tr, sala.province || '');

        const td = document.createElement('td');
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = 'Edit';
        btn.addEventListener('click', () => openSalaEditForm(tr, sala));
        td.appendChild(btn);
        tr.appendChild(td);

        tbody.appendChild(tr);
    });
}

function openSalaEditForm(rowEl, sala) {
    closeAnyOpenEditForm();

    const formRow = document.createElement('tr');
    formRow.className = 'edit-form-row';
    const td = document.createElement('td');
    td.colSpan = 5;

    const form = document.createElement('form');
    form.className = 'edit-form';

    appendTextInput(form, 'sala-edit-name', 'Name (required)', sala.name || '', true);
    appendTextInput(form, 'sala-edit-address', 'Address', sala.address || '');
    appendTextInput(form, 'sala-edit-city', 'City', sala.city || '');
    appendTextInput(form, 'sala-edit-province', 'Province', sala.province || '');
    appendNumberInput(form, 'sala-edit-lat', 'Lat', sala.lat);
    appendNumberInput(form, 'sala-edit-lng', 'Lng', sala.lng);
    appendTextInput(form, 'sala-edit-image-url', 'Image URL', sala.image_url || '');
    appendTextInput(form, 'sala-edit-description', 'Description', sala.description || '', false, true);

    const msgOk = document.createElement('div');
    msgOk.className = 'msg-ok full-width';
    msgOk.style.display = 'none';
    const msgErr = document.createElement('div');
    msgErr.className = 'msg-err full-width';
    msgErr.style.display = 'none';

    const actions = document.createElement('div');
    actions.className = 'full-width';
    const saveBtn = document.createElement('button');
    saveBtn.type = 'submit';
    saveBtn.className = 'btn-submit';
    saveBtn.textContent = 'Save';
    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn-submit';
    cancelBtn.textContent = 'Cancel';
    cancelBtn.addEventListener('click', () => closeAnyOpenEditForm());
    actions.appendChild(saveBtn);
    actions.appendChild(cancelBtn);

    form.appendChild(msgOk);
    form.appendChild(msgErr);
    form.appendChild(actions);

    form.addEventListener('submit', event => {
        event.preventDefault();
        submitSalaEdit(sala.id, form, msgOk, msgErr);
    });

    td.appendChild(form);
    formRow.appendChild(td);
    rowEl.insertAdjacentElement('afterend', formRow);
}

function submitSalaEdit(id, form, msgOk, msgErr) {
    msgOk.style.display = 'none';
    msgErr.style.display = 'none';

    var params = new URLSearchParams();
    params.append('name', form.querySelector('#sala-edit-name').value.trim());
    params.append('address', form.querySelector('#sala-edit-address').value.trim());
    params.append('city', form.querySelector('#sala-edit-city').value.trim());
    params.append('province', form.querySelector('#sala-edit-province').value.trim());
    var lat = form.querySelector('#sala-edit-lat').value.trim();
    var lng = form.querySelector('#sala-edit-lng').value.trim();
    if (lat) params.append('lat', lat);
    if (lng) params.append('lng', lng);
    var imageUrl = form.querySelector('#sala-edit-image-url').value.trim();
    var description = form.querySelector('#sala-edit-description').value.trim();
    if (imageUrl) params.append('imageUrl', imageUrl);
    if (description) params.append('description', description);

    putForm('/admin/salas/' + encodeURIComponent(id), params, msgOk, msgErr, function() {
        loadSalasTab();
    });
}

// --- Artistas tab ---

function loadArtistasTab() {
    var statusEl = document.getElementById('artistas-status');
    statusEl.textContent = 'Loading...';
    closeAnyOpenEditForm();

    fetchJsonOrThrow('/admin/artists-all')
        .then(artists => {
            editArtistasData = artists || [];
            renderArtistasTable(editArtistasData);
        })
        .catch(err => {
            statusEl.textContent = 'Error loading artists: ' + err.message;
        });
}

function filterArtistas() {
    var term = document.getElementById('artistas-search').value.trim().toLowerCase();
    if (!term) {
        renderArtistasTable(editArtistasData);
        return;
    }
    var filtered = editArtistasData.filter(artist =>
        (artist.name || '').toLowerCase().includes(term)
    );
    renderArtistasTable(filtered);
}

function renderArtistasTable(artists) {
    var statusEl = document.getElementById('artistas-status');
    var tbody = document.getElementById('artistas-body');
    tbody.replaceChildren();

    if (!artists || artists.length === 0) {
        statusEl.textContent = 'No artists found.';
        return;
    }

    statusEl.textContent = artists.length + ' artist(s).';

    artists.forEach(artist => {
        const tr = document.createElement('tr');
        appendCell(tr, artist.id);
        appendCell(tr, artist.name || '');
        appendCell(tr, artist.genre || '');

        const td = document.createElement('td');
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = 'Edit';
        btn.addEventListener('click', () => openArtistEditForm(tr, artist));
        td.appendChild(btn);
        tr.appendChild(td);

        tbody.appendChild(tr);
    });
}

function openArtistEditForm(rowEl, artist) {
    closeAnyOpenEditForm();

    const formRow = document.createElement('tr');
    formRow.className = 'edit-form-row';
    const td = document.createElement('td');
    td.colSpan = 4;

    const form = document.createElement('form');
    form.className = 'edit-form';

    appendTextInput(form, 'artist-edit-name', 'Name (required)', artist.name || '', true);
    appendTextInput(form, 'artist-edit-genre', 'Genre', artist.genre || '');
    appendTextInput(form, 'artist-edit-image-url', 'Image URL', artist.image_url || '');
    appendTextInput(form, 'artist-edit-description', 'Description', artist.description || '', false, true);

    const msgOk = document.createElement('div');
    msgOk.className = 'msg-ok full-width';
    msgOk.style.display = 'none';
    const msgErr = document.createElement('div');
    msgErr.className = 'msg-err full-width';
    msgErr.style.display = 'none';

    const actions = document.createElement('div');
    actions.className = 'full-width';
    const saveBtn = document.createElement('button');
    saveBtn.type = 'submit';
    saveBtn.className = 'btn-submit';
    saveBtn.textContent = 'Save';
    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn-submit';
    cancelBtn.textContent = 'Cancel';
    cancelBtn.addEventListener('click', () => closeAnyOpenEditForm());
    actions.appendChild(saveBtn);
    actions.appendChild(cancelBtn);

    form.appendChild(msgOk);
    form.appendChild(msgErr);
    form.appendChild(actions);

    form.addEventListener('submit', event => {
        event.preventDefault();
        submitArtistEdit(artist.id, form, msgOk, msgErr);
    });

    td.appendChild(form);
    formRow.appendChild(td);
    rowEl.insertAdjacentElement('afterend', formRow);
}

function submitArtistEdit(id, form, msgOk, msgErr) {
    msgOk.style.display = 'none';
    msgErr.style.display = 'none';

    var params = new URLSearchParams();
    params.append('name', form.querySelector('#artist-edit-name').value.trim());
    var genre = form.querySelector('#artist-edit-genre').value.trim();
    var imageUrl = form.querySelector('#artist-edit-image-url').value.trim();
    var description = form.querySelector('#artist-edit-description').value.trim();
    if (genre) params.append('genre', genre);
    if (imageUrl) params.append('imageUrl', imageUrl);
    if (description) params.append('description', description);

    putForm('/admin/artists/' + encodeURIComponent(id), params, msgOk, msgErr, function() {
        loadArtistasTab();
    });
}

// --- Conciertos tab ---

function loadConciertosTab() {
    var statusEl = document.getElementById('conciertos-status');
    statusEl.textContent = 'Loading...';
    closeAnyOpenEditForm();

    Promise.all([
        fetchJsonOrThrow('/admin/concerts-all'),
        fetchJsonOrEmpty('/admin/salas-all'),
        fetchJsonOrEmpty('/admin/artists-all')
    ])
        .then(([concerts, salas, artists]) => {
            const salaNames = new Map(salas.map(s => [s.id, s.name]));
            const artistNames = new Map(artists.map(a => [a.id, a.name]));
            editConciertosData = (concerts || []).map(c => ({
                concert: c,
                salaName: salaNames.get(c.salaConcierto_id) || c.salaConcierto_id || '',
                artistNames: (c.artist_ids || []).map(id => artistNames.get(id) || id)
            }));
            renderConciertosTable(editConciertosData);
        })
        .catch(err => {
            statusEl.textContent = 'Error loading concerts: ' + err.message;
        });
}

function filterConciertos() {
    var term = document.getElementById('conciertos-search').value.trim().toLowerCase();
    if (!term) {
        renderConciertosTable(editConciertosData);
        return;
    }
    var filtered = editConciertosData.filter(entry =>
        entry.salaName.toLowerCase().includes(term) ||
        entry.artistNames.some(name => name.toLowerCase().includes(term)) ||
        (entry.concert.date || '').toLowerCase().includes(term)
    );
    renderConciertosTable(filtered);
}

function renderConciertosTable(entries) {
    var statusEl = document.getElementById('conciertos-status');
    var tbody = document.getElementById('conciertos-body');
    tbody.replaceChildren();

    if (!entries || entries.length === 0) {
        statusEl.textContent = 'No concerts found.';
        return;
    }

    statusEl.textContent = entries.length + ' concert(s).';

    entries.forEach(entry => {
        const concert = entry.concert;
        const tr = document.createElement('tr');
        appendCell(tr, concert.id);
        appendCell(tr, concert.date || '');
        appendCell(tr, entry.salaName);
        appendCell(tr, entry.artistNames.join(', '));

        const td = document.createElement('td');
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = 'Edit';
        btn.addEventListener('click', () => openConcertEditForm(tr, concert, entry));
        td.appendChild(btn);
        tr.appendChild(td);

        tbody.appendChild(tr);
    });
}

function openConcertEditForm(rowEl, concert, entry) {
    closeAnyOpenEditForm();

    const formRow = document.createElement('tr');
    formRow.className = 'edit-form-row';
    const td = document.createElement('td');
    td.colSpan = 5;

    const form = document.createElement('form');
    form.className = 'edit-form';

    // Read-only FK info — concert edit only changes date/time/price.
    const fkInfo = document.createElement('div');
    fkInfo.className = 'full-width readonly-field';
    fkInfo.textContent = 'Sala: ' + entry.salaName + ' | Artist(s): ' + entry.artistNames.join(', ') +
        ' (sala and artists cannot be changed here)';
    form.appendChild(fkInfo);

    appendDateInput(form, 'concert-edit-date', 'Date (required)', concert.date || '', true);
    appendTextInput(form, 'concert-edit-time', 'Time', concert.time || '');
    appendTextInput(form, 'concert-edit-price', 'Price', concert.price || '');

    const msgOk = document.createElement('div');
    msgOk.className = 'msg-ok full-width';
    msgOk.style.display = 'none';
    const msgErr = document.createElement('div');
    msgErr.className = 'msg-err full-width';
    msgErr.style.display = 'none';

    const actions = document.createElement('div');
    actions.className = 'full-width';
    const saveBtn = document.createElement('button');
    saveBtn.type = 'submit';
    saveBtn.className = 'btn-submit';
    saveBtn.textContent = 'Save';
    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn-submit';
    cancelBtn.textContent = 'Cancel';
    cancelBtn.addEventListener('click', () => closeAnyOpenEditForm());
    actions.appendChild(saveBtn);
    actions.appendChild(cancelBtn);

    form.appendChild(msgOk);
    form.appendChild(msgErr);
    form.appendChild(actions);

    form.addEventListener('submit', event => {
        event.preventDefault();
        submitConcertEdit(concert.id, form, msgOk, msgErr);
    });

    td.appendChild(form);
    formRow.appendChild(td);
    rowEl.insertAdjacentElement('afterend', formRow);
}

function submitConcertEdit(id, form, msgOk, msgErr) {
    msgOk.style.display = 'none';
    msgErr.style.display = 'none';

    var params = new URLSearchParams();
    params.append('date', form.querySelector('#concert-edit-date').value.trim());
    var time = form.querySelector('#concert-edit-time').value.trim();
    var price = form.querySelector('#concert-edit-price').value.trim();
    if (time) params.append('time', time);
    if (price) params.append('price', price);

    putForm('/admin/concerts/' + encodeURIComponent(id), params, msgOk, msgErr, function() {
        loadConciertosTab();
    });
}

// --- Shared edit-form helpers ---

function appendTextInput(form, id, labelText, value, required, fullWidth) {
    const label = document.createElement('label');
    if (fullWidth) label.className = 'full-width';
    label.textContent = labelText;

    const input = document.createElement('input');
    input.type = 'text';
    input.id = id;
    input.value = value == null ? '' : String(value);
    if (required) input.required = true;

    label.appendChild(input);
    form.appendChild(label);
    return input;
}

function appendNumberInput(form, id, labelText, value) {
    const label = document.createElement('label');
    label.textContent = labelText;

    const input = document.createElement('input');
    input.type = 'number';
    input.step = 'any';
    input.id = id;
    input.value = value == null ? '' : String(value);

    label.appendChild(input);
    form.appendChild(label);
    return input;
}

function appendDateInput(form, id, labelText, value, required) {
    const label = document.createElement('label');
    label.textContent = labelText;

    const input = document.createElement('input');
    input.type = 'date';
    input.id = id;
    input.value = value == null ? '' : String(value);
    if (required) input.required = true;

    label.appendChild(input);
    form.appendChild(label);
    return input;
}

function closeAnyOpenEditForm() {
    document.querySelectorAll('.edit-form-row').forEach(row => row.remove());
}

/**
 * Sends a PUT with form-encoded params; on success shows .msg-ok and runs onSuccess
 * (typically reload the tab), on error shows .msg-err with the proxied {error} message.
 */
function putForm(path, params, msgOk, msgErr, onSuccess) {
    fetch(path, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Requested-With': 'XMLHttpRequest'
        },
        body: params.toString()
    })
    .then(res => res.json().then(data => ({ ok: res.ok, data: data })))
    .then(({ ok, data }) => {
        if (data && data.error) {
            msgErr.textContent = data.error;
            msgErr.style.display = 'block';
            return;
        }
        if (!ok) {
            msgErr.textContent = 'Unexpected response.';
            msgErr.style.display = 'block';
            return;
        }
        msgOk.textContent = 'Saved successfully.';
        msgOk.style.display = 'block';
        setTimeout(() => { if (onSuccess) onSuccess(); }, 600);
    })
    .catch(err => {
        msgErr.textContent = 'Network error: ' + err.message;
        msgErr.style.display = 'block';
    });
}

document.addEventListener('DOMContentLoaded', loadIssues);
