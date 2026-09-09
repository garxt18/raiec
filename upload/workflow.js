// ===== Workflow Shared JS =====

// Backend API base (local dev). Change this when deploying.
var RAIEC_API = (window.RAIEC_CONFIG && window.RAIEC_CONFIG.apiBase) || 'http://localhost:8080/api';
var raiecSelectedFile = null;

// Expandable sections
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.wf-expand-header').forEach(function(header) {
        header.addEventListener('click', function() {
            var section = header.closest('.wf-expand-section');
            section.classList.toggle('open');
        });
    });

    // Filter tabs
    document.querySelectorAll('.wf-filter-tabs').forEach(function(tabGroup) {
        tabGroup.querySelectorAll('.wf-filter-tab').forEach(function(tab) {
            tab.addEventListener('click', function() {
                tabGroup.querySelectorAll('.wf-filter-tab').forEach(function(t) {
                    t.classList.remove('active');
                });
                tab.classList.add('active');
                filterTable(tab.dataset.filter);
            });
        });
    });

    // Toggle buttons
    document.querySelectorAll('.wf-toggle-btn').forEach(function(btn) {
        btn.addEventListener('click', function() {
            var group = btn.closest('.wf-toggle-group');
            group.querySelectorAll('.wf-toggle-btn').forEach(function(b) {
                b.classList.remove('active');
            });
            btn.classList.add('active');
        });
    });
});

/* Two independent filters apply at once: the verdict (OK/WARN/FAIL) and the rate
   source (DSR/IRUSSOR/NS). A vetter usually works one rate book at a time, so the
   source tags are not just a second copy of the status tabs. */
var wfFilters = { status: 'all', source: 'all' };

function applyRowFilters() {
    var rows = document.querySelectorAll('.wf-table tbody tr');
    var shown = 0;
    rows.forEach(function (row) {
        var st = row.getAttribute('data-status');
        var src = row.getAttribute('data-source');
        var okStatus = wfFilters.status === 'all' || st === wfFilters.status;
        var okSource = wfFilters.source === 'all' || src === wfFilters.source;
        var visible = okStatus && okSource;
        row.style.display = visible ? '' : 'none';
        if (visible) shown++;
    });

    var note = document.getElementById('rmShowing');
    if (note) {
        note.textContent = (wfFilters.status === 'all' && wfFilters.source === 'all')
            ? 'Showing all ' + rows.length + ' items'
            : 'Showing ' + shown + ' of ' + rows.length + ' items';
    }

    var empty = document.getElementById('rmEmptyFilter');
    if (empty) empty.hidden = shown !== 0 || rows.length === 0;
}

document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-filter-kind]').forEach(function (group) {
        var kind = group.getAttribute('data-filter-kind');
        group.querySelectorAll('.wf-filter-tab').forEach(function (tab) {
            tab.addEventListener('click', function () {
                group.querySelectorAll('.wf-filter-tab').forEach(function (t) { t.classList.remove('active'); });
                tab.classList.add('active');
                wfFilters[kind] = kind === 'source'
                    ? tab.getAttribute('data-source')
                    : tab.getAttribute('data-filter');
                applyRowFilters();
            });
        });
    });
});

// Filter table rows
function filterTable(filter) {
    var rows = document.querySelectorAll('.wf-table tbody tr');
    rows.forEach(function(row) {
        if (filter === 'all') {
            row.style.display = '';
            return;
        }
        var st = row.getAttribute('data-status');
        if (st !== null) {
            row.style.display = (st === filter.toLowerCase()) ? '' : 'none';
            return;
        }
        var badge = row.querySelector('.wf-badge');
        if (badge) {
            var text = badge.textContent.trim().toLowerCase();
            row.style.display = text === filter.toLowerCase() ? '' : 'none';
        }
    });
}


/* =============================================================================
   Stepper
   Rendered from a single definition rather than hand-maintained on five pages,
   which is how the old markup drifted. A completed stage is a real link back to
   work already done; an upcoming one is not, because the data does not exist yet.
   ============================================================================= */
var WF_STEPS = [
    { n: 1, label: 'Upload',         page: 'step1-upload.html' },
    { n: 2, label: 'Extract',        page: 'step2-ocr-extract.html' },
    { n: 3, label: 'Rate match',     page: 'step3-rate-match.html' },
    { n: 4, label: 'AI analysis',    page: 'step4-ai-analysis.html' },
    { n: 5, label: 'Officer review', page: 'step5-officer-review.html' }
];

function renderStepper() {
    var host = document.querySelector('.wf-stepper');
    if (!host) return;
    var current = parseInt(host.getAttribute('data-step'), 10) || 1;
    var hasTender = !!getState('tenderId');

    host.innerHTML = WF_STEPS.map(function (s) {
        var state = s.n < current ? 'done' : (s.n === current ? 'active' : 'todo');
        // Going back is only meaningful once a tender is loaded.
        var clickable = state === 'done' && hasTender;
        var mark = state === 'done'
            ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6L9 17l-5-5"/></svg>'
            : s.n;
        return '<' + (clickable ? 'a href="' + s.page + '"' : 'div') +
                 ' class="wf-stepp" data-state="' + state + '"' +
                 (state === 'active' ? ' aria-current="step"' : '') + '>' +
                 '<span class="wf-stepp-dot">' + mark + '</span>' +
                 '<span class="wf-stepp-label">' + s.label + '</span>' +
               '</' + (clickable ? 'a' : 'div') + '>';
    }).join('');

    // Mark backward navigation so the destination knows not to re-announce work
    // it has already finished.
    host.querySelectorAll('a.wf-stepp').forEach(function (a) {
        a.addEventListener('click', function () {
            try { sessionStorage.setItem('raiec_nav_back', '1'); } catch (e) { /* ignore */ }
        });
    });

    // Fill the rail up to the current stage.
    var pct = ((current - 1) / (WF_STEPS.length - 1)) * 100;
    host.style.setProperty('--wf-progress', pct + '%');
    host.setAttribute('data-has-tender', hasTender ? 'yes' : 'no');
}

document.addEventListener('DOMContentLoaded', renderStepper);


/* -----------------------------------------------------------------------------
   Arrival loading
   Two rules. A page that opens straight into a fetch shows the overlay
   immediately, so it covers the page from first paint instead of appearing after
   the empty page has already rendered. And returning to a completed step shows
   nothing at all: that work is already done, so an overlay would imply it is
   being redone.
   -------------------------------------------------------------------------- */
function isReturningToCompletedStep() {
    try {
        var back = sessionStorage.getItem('raiec_nav_back') === '1';
        sessionStorage.removeItem('raiec_nav_back');   // one navigation only
        return back;
    } catch (e) { return false; }
}

var WF_RETURNING = isReturningToCompletedStep();


/* -----------------------------------------------------------------------------
   Completed-step cache

   The backward flag alone was not enough. It is consumed by the step you return
   to, so walking 4 -> 3 -> 4 arrived at step 4 with the flag already spent, and
   the page announced and re-ran work it had finished a moment earlier. Direction
   was the wrong thing to track: what matters is whether this step has already
   produced a result for this tender, which is true however you arrived.

   So each step stores its result, keyed by tender. A step with a stored result
   renders from it instantly and silently. Anything that invalidates the result
   -- a new upload, clearing the workspace -- drops the cache with it, and the
   work runs again as it should.
   -------------------------------------------------------------------------- */
function stepCacheKey(step) {
    var id = getState('tenderId');
    return id == null ? null : 'raiec_step_' + step + '_' + id;
}

function readStepCache(step) {
    var key = stepCacheKey(step);
    if (!key) return null;
    try {
        var raw = sessionStorage.getItem(key);
        return raw ? JSON.parse(raw) : null;
    } catch (e) { return null; }
}

function writeStepCache(step, payload) {
    var key = stepCacheKey(step);
    if (!key) return;
    try {
        sessionStorage.setItem(key, JSON.stringify(payload));
    } catch (e) {
        // A full quota is not worth failing over: the step simply refetches next time.
    }
}

/** Called when a new tender arrives, so no stale step result can outlive it. */
function clearStepCaches() {
    try {
        Object.keys(sessionStorage)
            .filter(function (k) { return k.indexOf('raiec_step_') === 0; })
            .forEach(function (k) { sessionStorage.removeItem(k); });
    } catch (e) { /* ignore */ }
}

function showArrivalLoader(title, steps) {
    if (WF_RETURNING) return;                 // already done; do not re-announce it
    if (window.RAIEC_UI) RAIEC_UI.showLoader(title, steps, { immediate: true });
}

/**
 * Runs a step once per tender and replays the stored result thereafter.
 *
 * @param step     stepper number, used as the cache key
 * @param url      endpoint to call on a miss
 * @param loader   {title, steps} for the overlay, shown only on a real run
 * @param render   draws the result; called with the same shape either way
 * @param onError  draws the failure state
 */
function runStepOnce(step, url, loader, render, onError) {
    var cached = readStepCache(step);
    if (cached) {
        // No overlay and no request. The result is already known, and showing a
        // progress animation over work that is not happening is a small lie.
        render(cached);
        return;
    }

    showArrivalLoader(loader.title, loader.steps);
    raiecFetch(url)
        .then(function (res) { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); })
        .then(function (d) {
            writeStepCache(step, d);
            render(d);
            if (window.RAIEC_UI) RAIEC_UI.hideLoader();
        })
        .catch(function (err) {
            if (window.RAIEC_UI) RAIEC_UI.hideLoader();
            onError(err);
        });
}

// Navigate between steps.
// A hard cut between pages made the flow feel like five separate screens rather than
// one process. Fading the current page out first, and in on arrival, reads as moving
// forward through a single document.
function navigateTo(page) {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        window.location.href = page;
        return;
    }
    document.body.classList.add('wf-leaving');
    setTimeout(function () { window.location.href = page; }, 240);
}

// Save workflow state
function saveState(key, value) {
    try {
        sessionStorage.setItem('raiec_' + key, JSON.stringify(value));
    } catch(e) {}
}

function getState(key) {
    try {
        var val = sessionStorage.getItem('raiec_' + key);
        return val ? JSON.parse(val) : null;
    } catch(e) {
        return null;
    }
}

// Dropzone interaction
function initDropzone() {
    var dz = document.querySelector('.wf-dropzone');
    if (!dz) return;

    dz.addEventListener('dragover', function(e) {
        e.preventDefault();
        dz.classList.add('drag-over');
    });
    dz.addEventListener('dragleave', function() {
        dz.classList.remove('drag-over');
    });
    dz.addEventListener('drop', function(e) {
        e.preventDefault();
        dz.classList.remove('drag-over');
        handleFiles(e.dataTransfer.files);
    });
    dz.addEventListener('click', function() {
        var input = document.getElementById('fileInput');
        if (input) input.click();
    });

    var input = document.getElementById('fileInput');
    if (input) {
        input.addEventListener('change', function() {
            handleFiles(input.files);
        });
    }
}

function handleFiles(files) {
    if (files.length === 0) return;
    var file = files[0];
    raiecSelectedFile = file;
    var preview = document.querySelector('.wf-file-preview');
    if (preview) {
        preview.style.display = 'flex';
        var nameEl = preview.querySelector('.wf-file-name');
        var sizeEl = preview.querySelector('.wf-file-size');
        if (nameEl) nameEl.textContent = file.name;
        if (sizeEl) sizeEl.textContent = (file.size / (1024 * 1024)).toFixed(1) + ' MB · Ready to submit';

        // The bar sat at a green 100% before anything had happened, which claimed
        // progress that did not exist. The file is simply accepted; the real work is
        // reported by the scanning overlay once submitted.
        var bar = preview.querySelector('.wf-file-progress-bar');
        if (bar) { bar.style.width = '100%'; bar.style.background = 'var(--accent-green)'; }
        var icon = preview.querySelector('.wf-file-icon');
        if (icon) { icon.style.background = 'var(--tint-green)'; icon.style.color = 'var(--accent-green)'; }

        // Replay the accept animation even when a second file replaces the first.
        preview.classList.remove('is-accepted');
        void preview.offsetWidth;
        preview.classList.add('is-accepted');
    }
    saveState('uploadedFile', { name: file.name, size: file.size });
}

document.addEventListener('DOMContentLoaded', initDropzone);


/* -----------------------------------------------------------------------------
   Recent uploads (step 1 sidebar)

   Previously three invented reference numbers. On a page whose whole purpose is
   to add a real submission, invented neighbours are worse than an empty panel:
   they teach the officer that what the sidebar says cannot be trusted, and that
   doubt does not stay confined to the sidebar.
   -------------------------------------------------------------------------- */
document.addEventListener('DOMContentLoaded', function () {
    var host = document.getElementById('recentUploads');
    if (!host) return;

    raiecFetch(RAIEC_API + '/tenders')
        .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function (list) {
            var recent = (list || []).slice().sort(function (a, b) {
                return String(b.createdAt || '').localeCompare(String(a.createdAt || ''));
            }).slice(0, 4);

            if (!recent.length) {
                host.innerHTML = '<p class="wf-recent-empty">Nothing uploaded yet. This is the first submission.</p>';
                return;
            }

            host.innerHTML = recent.map(function (t) {
                var when = window.RAIEC_UI ? RAIEC_UI.formatRelative(t.createdAt) : '';
                return '<a class="wf-recent-item" href="../tender-dashboard.html">' +
                         '<div>' +
                           '<div class="wf-recent-id">' + escapeHtml(t.tenderNo || '—') + '</div>' +
                           '<div class="wf-recent-dept">' +
                             escapeHtml(truncateRm(t.nameOfWork || 'Untitled work', 34)) +
                           '</div>' +
                           '<div class="wf-recent-when">' + escapeHtml(when) + '</div>' +
                         '</div>' +
                         statusBadge(t.status) +
                       '</a>';
            }).join('');
        })
        .catch(function () {
            // Say the panel could not load. Silence here reads as "nothing uploaded yet",
            // which is a different and misleading claim.
            host.innerHTML = '<p class="wf-recent-empty">Could not reach the server, so recent uploads are not shown.</p>';
        });
});

function statusBadge(status) {
    var map = {
        APPROVED:       ['wf-badge-green',  'Approved'],
        REJECTED:       ['wf-badge-red',    'Rejected'],
        OFFICER_REVIEW: ['wf-badge-yellow', 'In review'],
        INFO_REQUESTED: ['wf-badge-yellow', 'Info sought'],
        OCR_EXTRACTED:  ['',                'Extracted']
    };
    var hit = map[status] || ['', status ? String(status).toLowerCase() : 'Pending'];
    return '<span class="wf-badge ' + hit[0] + '">' + escapeHtml(hit[1]) + '</span>';
}

// ===== Step 1: upload the estimate to the backend =====
function uploadEstimate() {
    var btn = document.getElementById('submitBtn');
    if (!raiecSelectedFile) {
        showUploadMessage('Please choose a PDF file first.', 'error');
        return;
    }
    if (btn) { btn.disabled = true; btn.textContent = 'Uploading & extracting...'; }
    // A blurred overlay with the real stage named, rather than a progress bar that
    // is not measuring anything.
    // Submitting is a deliberate action on this page, not an arrival, so it uses the
    // normal armed loader: if the parse were ever instant, no overlay should flash.
    if (window.RAIEC_UI) {
        RAIEC_UI.showLoader('Reading the tender', [
            'Uploading the document…',
            'Extracting text from the PDF…',
            'Detecting schedules and line items…',
            'Structuring the estimate…'
        ]);
    }

    var form = new FormData();
    form.append('file', raiecSelectedFile);

    raiecFetch(RAIEC_API + '/tenders/upload', {
        method: 'POST',
        body: form,
        // Parsing runs on half a CPU on the free tier, so ~30s is normal here and a
        // cold start adds another minute on top.
        timeoutMs: 180000,
        onSlow: function () {
            showUploadMessage('Still working. The first upload after a quiet spell also has to start the server, which can take a minute.', 'info');
        }
    })
        .then(function(res) {
            return res.json().then(function(body) {
                return { ok: res.ok, status: res.status, body: body };
            }).catch(function() {
                return { ok: res.ok, status: res.status, body: null };
            });
        })
        .then(function(r) {
            if (!r.ok) {
                if (window.RAIEC_UI) RAIEC_UI.hideLoader();
                if (btn) { btn.disabled = false; btn.textContent = 'Submit for validation'; }
                // A duplicate is not really an error, it is a decision point: the officer
                // needs to see which submission this collides with, so it gets a dialog
                // rather than a line of red text under the form.
                if (r.status === 409 && r.body && r.body.existing) {
                    showDuplicateDialog(r.body.existing);
                    return;
                }
                var msg = (r.body && r.body.message) ? r.body.message : ('Upload failed (HTTP ' + r.status + ').');
                showUploadMessage(msg, 'error');
                return;
            }
            // A new tender invalidates every stored step result; without this the next
            // page would replay the previous submission's numbers under a new reference.
            clearStepCaches();
            saveState('tenderId', r.body.id);
            saveState('tenderSummary', r.body);
            // Show what was actually read before leaving the page, so the parse result
            // is visible rather than flashing past.
            fillDetectedReference(r.body);
            setTimeout(function () { navigateTo('step2-ocr-extract.html'); }, 900);
        })
        .catch(function() {
            if (window.RAIEC_UI) RAIEC_UI.hideLoader();
            showUploadMessage('Could not reach the server (' + ((window.RAIEC_CONFIG && window.RAIEC_CONFIG.apiOrigin) || 'http://localhost:8080') + ').', 'error');
            if (btn) { btn.disabled = false; btn.textContent = 'Submit for validation'; }
        });
}

function showUploadMessage(text, type) {
    var el = document.getElementById('uploadMessage');
    if (!el) return;
    el.hidden = false;
    el.textContent = text;
    el.style.marginTop = '12px';
    el.style.fontSize = '14px';
    el.style.color = type === 'error' ? '#f87171' : (type === 'info' ? 'var(--text-secondary)' : '#34d399');
}

// ===== Step 2: render real extracted line items =====
document.addEventListener('DOMContentLoaded', function() {
    if (document.getElementById('ocrTableBody')) {
        initOcrExtract();
    }
});

function initOcrExtract() {
    var tbody = document.getElementById('ocrTableBody');
    var id = getState('tenderId');
    if (!id) {
        tbody.innerHTML = '<tr><td colspan="3">No tender loaded. Please upload an estimate first.</td></tr>';
        return;
    }
    tbody.innerHTML = '<tr><td colspan="3">Loading extracted items...</td></tr>';

    runStepOnce(2, RAIEC_API + '/tenders/' + id,
        {
            title: 'Extracting the estimate',
            steps: [
                'Reading schedules…',
                'Collecting line items and rate breakups…',
                'Separating Non-Scheduled items…'
            ]
        },
        renderOcr,
        function () {
            tbody.innerHTML = '<tr><td colspan="3">Could not load the tender from the server (is it running on :8080?).</td></tr>';
        });
}

function renderOcr(tender) {
    var tbody = document.getElementById('ocrTableBody');
    var items = [];
    (tender.schedules || []).forEach(function(s) {
        (s.entries || []).forEach(function(e) {
            if (e.kind === 'NS_ITEM') {
                items.push({ desc: e.description || e.itemCode, qty: e.quantity, unit: e.qtyUnit, rate: e.unitRate });
            } else {
                (e.breakupItems || []).forEach(function(b) {
                    if (!b.heading && b.rate != null) {
                        items.push({ desc: b.description || b.itemCode, qty: b.quantity, unit: b.unit, rate: b.rate });
                    }
                });
            }
        });
    });

    if (items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="3">No priced line items were extracted.</td></tr>';
    } else {
        tbody.innerHTML = items.map(function(it) {
            var qty = (it.qty != null ? it.qty : '') + (it.unit ? ' ' + it.unit : '');
            // Rate carries its own colour: it is the number being vetted, and it should
            // not read as just another figure alongside the quantity.
            return '<tr>'
                + '<td>' + escapeHtml(it.desc) + '</td>'
                + '<td class="ocr-qty">' + escapeHtml(qty) + '</td>'
                + '<td class="ocr-rate">₹ ' + formatNum(it.rate) + '</td>'
                + '</tr>';
        }).join('');
    }

    var countEl = document.getElementById('ocrItemCount');
    if (countEl) countEl.textContent = items.length;
    var schEl = document.getElementById('ocrScheduleCount');
    if (schEl) schEl.textContent = (tender.schedules || []).length;
    var showingEl = document.getElementById('ocrShowing');
    if (showingEl) showingEl.textContent = 'Showing ' + items.length + ' extracted line items';
}

function formatNum(n) {
    if (n == null) return '';
    var num = Number(n);
    if (isNaN(num)) return String(n);
    return num.toLocaleString('en-IN');
}

function escapeHtml(s) {
    if (s == null) return '';
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}


// ===== Step 5: officer approve / reject =====
function approveTender() {
    decideTender('approve', 'Estimate approved.');
}

function rejectTender() {
    decideTender('reject', 'Estimate rejected and sent back.');
}

function decideTender(action, label, remark) {
    var id = getState('tenderId');
    if (!id) {
        showReviewMessage('No tender loaded. Please upload an estimate first.', 'error');
        return;
    }
    var opts = { method: 'POST' };
    if (remark) {
        opts.headers = { 'Content-Type': 'application/json' };
        opts.body = JSON.stringify({ remark: remark });
    }
    raiecFetch(RAIEC_API + '/tenders/' + id + '/' + action, opts)
        .then(function(res) {
            return res.json().then(function(b) {
                return { ok: res.ok, status: res.status, body: b };
            }).catch(function() {
                return { ok: res.ok, status: res.status, body: null };
            });
        })
        .then(function(r) {
            if (!r.ok) {
                showReviewMessage((r.body && r.body.message) ? r.body.message : ('Request failed (HTTP ' + r.status + ').'), 'error');
                return;
            }
            var msg = label;
            if (action === 'approve' && r.body) {
                msg = 'Estimate approved — ' + (r.body.larAdded || 0) + ' item(s) added and '
                    + (r.body.larUpdated || 0) + ' updated in the LAR dataset.';
            }
            showReviewMessage(msg, 'success');
            // The decision has just been written to the trail, and the trail is sitting
            // on screen. Leaving it showing the state before the decision would make the
            // panel look like it does not keep up with what the officer just did.
            initAuditTrail();
            // The page used to just sit there after a decision, giving no sense that
            // anything had concluded. Confirm the outcome and offer the obvious next move.
            showOutcome(action, msg, r.body);
        })
        .catch(function() {
            showReviewMessage('Could not reach the server (' + ((window.RAIEC_CONFIG && window.RAIEC_CONFIG.apiOrigin) || 'http://localhost:8080') + ').', 'error');
        });
}

function showReviewMessage(text, type) {
    var el = document.getElementById('reviewMessage');
    if (!el) return;
    el.hidden = false;
    el.textContent = text;
    el.style.marginTop = '12px';
    el.style.fontSize = '14px';
    el.style.color = type === 'error' ? '#f87171' : '#34d399';
}


// ===== Step 3: rate match (from backend) =====
document.addEventListener('DOMContentLoaded', function() {
    if (document.getElementById('rmTableBody')) initRateMatch();
});

function initRateMatch() {
    var tbody = document.getElementById('rmTableBody');
    var id = getState('tenderId');
    if (!id) {
        tbody.innerHTML = '<tr><td colspan="10">No tender loaded. Please upload an estimate first.</td></tr>';
        return;
    }
    tbody.innerHTML = '<tr><td colspan="10">Running rate match…</td></tr>';

    runStepOnce(3, RAIEC_API + '/tenders/' + id + '/rate-match',
        {
            title: 'Matching rates',
            steps: [
                'Reading the extracted line items…',
                'Looking up IRUSSOR and CPWD DSR…',
                'Checking Non-Scheduled items against the LAR dataset…',
                'Calculating variance against tolerance…'
            ]
        },
        renderRateMatch,
        function () {
            tbody.innerHTML = '<tr><td colspan="10">Could not load rate match from the server (:8080).</td></tr>';
        });
}

function renderRateMatch(d) {
    renderFinancials(d.financials);
    setRmText('rmTotal', d.totalItems);
    setRmText('rmMatched', d.matched);
    setRmText('rmWarn', d.warn);
    setRmText('rmFail', d.fail);

    var schedNoRef = (d.items || []).filter(function(it) { return it.status === 'NO_REFERENCE' && it.source !== 'NS'; }).length;
    var note = document.getElementById('rmNoRefNote');
    if (note) {
        if (schedNoRef > 0) {
            note.hidden = false;
            note.style.color = '#fbbf24';
            note.textContent = '⚠ ' + schedNoRef + ' scheduled item(s) could not be matched to a rate book (code not found in IRUSSOR/DSR) — please verify these manually.';
        } else {
            note.hidden = true;
        }
    }

    var tbody = document.getElementById('rmTableBody');
    if (!d.items || !d.items.length) {
        tbody.innerHTML = '<tr><td colspan="10">No rateable items.</td></tr>';
        return;
    }
    tbody.innerHTML = d.items.map(function(it) {
        var vCls = '', vTxt;
        if (it.variancePct != null) {
            vCls = it.status === 'OK' ? 'variance-green' : (it.status === 'WARN' ? 'variance-yellow' : (it.status === 'FAIL' ? 'variance-red' : ''));
            vTxt = (it.variancePct > 0 ? '+' : '') + it.variancePct + '%';
        } else if (it.atPar) {
            vTxt = 'At Par';
        } else {
            vTxt = '—';
        }
        var st = it.status === 'OK' ? 'ok' : (it.status === 'WARN' ? 'warn' : (it.status === 'FAIL' ? 'fail' : 'noref'));
        var refCell = buildRefCell(it);
        return '<tr data-status="' + st + '" data-source="' + escapeHtml(it.source || '') + '">' +
            '<td>' + escapeHtml(truncateRm(it.description || it.itemCode, 64)) + '</td>' +
            '<td>' + escapeHtml(it.source || '') + '</td>' +
            '<td>' + (it.quantity != null ? formatNum(it.quantity) : '—') + '</td>' +
            '<td>' + escapeHtml(it.unit || '—') + '</td>' +
            '<td>' + formatNum(it.tenderRate) + '</td>' +
            '<td>' + (it.amount != null ? formatNum(it.amount) : '—') + '</td>' +
            '<td>' + refCell + '</td>' +
            '<td class="rm-impact" data-tone="' + impactTone(it.excessAmount) + '">' + impactCell(it.excessAmount) + '</td>' +
            '<td class="' + vCls + '">' + vTxt + '</td>' +
            '<td>' + rmStatusBadge(it.status, it.source) + '</td>' +
        '</tr>';
    }).join('');

    // A re-render (for example after the benchmark changes) must not silently drop
    // the filters the vetter had applied.
    applyRowFilters();
}

// Reference column: the published rate and where it came from. Rates are compared
// pre-escalation on both sides, so no escalated figure is shown here — the tender's
// percentage applies to the schedule total, not to individual unit rates.
function buildRefCell(it) {
    if (it.referenceRate == null) {
        return it.source === 'NS' ? '<span style="color:var(--text-secondary)">no prior rate</span>' : '—';
    }
    var html = formatNum(it.referenceRate);

    if (it.referenceSource) {
        var colour = it.referenceStale ? '#fbbf24' : 'var(--text-secondary)';
        html += '<br><span style="font-size:11px;color:' + colour + '">'
             + (it.referenceStale ? '⚠ ' : '') + escapeHtml(it.referenceSource) + '</span>';
    }
    return html;
}



/* =============================================================================
   Money
   Indian digit grouping throughout (12,34,567 not 1,234,567), and crore/lakh for
   headline figures — that is how these values are read and discussed in a railway
   office, and a figure nobody can read at a glance is not informing anyone.
   ============================================================================= */
function inr(n) {
    if (n == null || isNaN(Number(n))) return '\u2014';
    return '\u20b9' + Number(n).toLocaleString('en-IN', { maximumFractionDigits: 0 });
}

function inrShort(n) {
    if (n == null || isNaN(Number(n))) return '\u2014';
    var v = Math.abs(Number(n));
    var sign = Number(n) < 0 ? '-' : '';
    if (v >= 1e7)  return sign + '\u20b9' + (v / 1e7).toFixed(2).replace(/\.00$/, '') + ' Cr';
    if (v >= 1e5)  return sign + '\u20b9' + (v / 1e5).toFixed(2).replace(/\.00$/, '') + ' L';
    return sign + '\u20b9' + v.toLocaleString('en-IN', { maximumFractionDigits: 0 });
}

/**
 * The financial summary above the comparison.
 *
 * Leads with excess rather than the tender's total value: the total is a fact about
 * the document, while the excess is the finding — the number that decides whether
 * this estimate needs work. Coverage sits alongside it because an excess of zero
 * means nothing until you know how much was actually checked.
 */
function renderFinancials(f) {
    var host = document.getElementById('wfFinancials');
    if (!host) return;

    // The section is a bordered card with its own background, so returning early on no
    // data leaves an empty box on the page rather than nothing. Collapse it instead.
    if (!f) { host.hidden = true; return; }
    host.hidden = false;

    var coverage = Number(f.coveragePct);
    var lowCoverage = coverage < 60;

    host.innerHTML =
        '<div class="wf-fin-lead" data-tone="' + (Number(f.excessTotal) > 0 ? 'over' : 'clear') + '">' +
          '<span class="wf-fin-lead-label">Quoted above reference</span>' +
          '<span class="wf-fin-lead-value">' + inrShort(f.excessTotal) + '</span>' +
          // The exact figure only earns its line when the headline is abbreviated. Below a
          // lakh the two are identical, and printing the same number twice reads as a bug.
          (inrShort(f.excessTotal) !== inr(f.excessTotal)
            ? '<span class="wf-fin-lead-sub">' + inr(f.excessTotal) + '</span>' : '') +
        '</div>' +
        '<dl class="wf-fin-grid">' +
          finStat('Estimate value', inrShort(f.quotedValue), inr(f.quotedValue)) +
          finStat('Checked against a reference', coverage.toFixed(1) + '%',
                  inrShort(f.comparableValue) + ' of ' + inrShort(f.quotedValue)) +
          finStat('Quoted below reference', inrShort(f.savingTotal), 'counted separately from excess') +
          finStat('Net position', inrShort(f.netImpact),
                  Number(f.netImpact) > 0 ? 'above reference overall' : 'at or below reference overall') +
          materialityStat(f) +
          concentrationStat(f) +
        '</dl>' +
        (lowCoverage
          ? '<p class="wf-fin-warn">Only ' + coverage.toFixed(1) + '% of this estimate could be checked \u2014 ' +
            inrShort(f.unreferencedValue) + ' has no reference rate. The figures above describe the checked part only.</p>'
          : '') +
        renderClustering(f.clustering) +
        renderBreakdowns(f);
}

function finStat(label, value, sub) {
    return '<div class="wf-fin-stat">' +
             '<dt>' + escapeHtml(label) + '</dt>' +
             '<dd>' + escapeHtml(value) + '</dd>' +
             '<small>' + escapeHtml(sub) + '</small>' +
           '</div>';
}

/**
 * Materiality: the excess measured against the size of the estimate.
 *
 * Without it, every tender's excess is an isolated number and the only way to compare
 * two of them is to remember both totals. With it, a queue of tenders can be ranked.
 */
function materialityStat(f) {
    if (f.materialityPct == null) return '';
    var pct = Number(f.materialityPct);
    var reading = pct === 0 ? 'at or below reference overall'
                : pct < 1 ? 'a marginal share of the estimate'
                : pct < 5 ? 'a noticeable share of the estimate'
                : 'a large share of the estimate';
    return finStat('Excess as share of estimate', pct.toFixed(2) + '%', reading);
}

/**
 * Concentration: how few items carry most of the excess.
 *
 * A hundred flagged items is a week of work. "Four of them carry 80% of the money" is
 * an afternoon, and it is the same information.
 */
function concentrationStat(f) {
    if (!f.concentrationCount) return '';
    var n = f.concentrationCount;
    return finStat('Where the excess sits',
                   n + (n === 1 ? ' item' : ' items'),
                   'carry ' + Number(f.concentrationPct).toFixed(0) + '% of the excess');
}

/**
 * The clustering signal, shown only when there is something to say.
 *
 * Deliberately worded as an observation rather than an accusation, and always paired
 * with what the clustered items are worth: a share on its own invites a shrug, while
 * the money attached to it is what justifies asking the question.
 */
function renderClustering(c) {
    if (!c || !c.suspicious) return '';
    return '<div class="wf-fin-signal" role="note">' +
             '<div class="wf-fin-signal-head">' +
               '<span class="wf-fin-signal-tag">Pattern</span>' +
               '<span class="wf-fin-signal-title">Rates bunched just under the flag line</span>' +
             '</div>' +
             '<p class="wf-fin-signal-body">' + escapeHtml(c.note) + '</p>' +
             '<p class="wf-fin-signal-meta">' +
               escapeHtml(inrShort(c.valueInBand)) + ' of work sits in this band, ' +
               escapeHtml(inrShort(c.excessInBand)) + ' of it above reference. ' +
               'This is a pattern worth a question, not a finding on its own.' +
             '</p>' +
           '</div>';
}

/** Excess by schedule and by rate book, as proportion bars. */
function renderBreakdowns(f) {
    var bySchedule = f.bySchedule || [];
    var bySource = f.bySource || [];
    if (!bySchedule.length && !bySource.length) return '';

    return '<div class="wf-fin-splits">' +
             splitBlock('By schedule', bySchedule, 'schedule') +
             splitBlock('By rate reference', bySource, 'source') +
           '</div>';
}

function splitBlock(title, slices, kind) {
    if (!slices.length) return '';

    // Widest bar in the block sets the scale, so small slices stay visible instead of
    // collapsing to a sliver against a dominant one.
    var peak = slices.reduce(function (m, s) {
        return Math.max(m, Number(s.excessTotal) || 0, 0);
    }, 0);

    var rows = slices.slice(0, 6).map(function (s) {
        var excess = Number(s.excessTotal) || 0;
        var width = peak > 0 ? Math.max((excess / peak) * 100, excess > 0 ? 4 : 0) : 0;
        // An unreferenced slice has no excess by definition. Reporting its quoted value
        // instead keeps unexamined money on screen rather than letting it total to zero
        // and disappear, which is the opposite of what it deserves.
        var unchecked = kind === 'source' && s.label === 'No reference';
        var figure = unchecked ? inrShort(s.quotedValue) + ' unchecked' : inrShort(excess);

        return '<li class="wf-fin-split-row"' + (unchecked ? ' data-unchecked="yes"' : '') + '>' +
                 '<span class="wf-fin-split-label" title="' + escapeHtml(s.label) + '">' +
                   escapeHtml(truncateRm(s.label, 26)) + '</span>' +
                 '<span class="wf-fin-split-bar"><i style="width:' + width.toFixed(1) + '%"></i></span>' +
                 '<span class="wf-fin-split-value">' + escapeHtml(figure) + '</span>' +
                 '<span class="wf-fin-split-count">' + s.itemCount +
                   (s.itemCount === 1 ? ' item' : ' items') + '</span>' +
               '</li>';
    }).join('');

    return '<section class="wf-fin-split">' +
             '<h3 class="wf-fin-split-title">' + escapeHtml(title) + '</h3>' +
             '<ul class="wf-fin-split-list">' + rows + '</ul>' +
           '</section>';
}


/**
 * What this line costs against its reference. Shown beside the percentage rather than
 * instead of it: the percentage says how far off the rate is, this says what that
 * distance is worth, and an officer needs both to decide whether to act.
 */
function impactCell(excess) {
    if (excess == null) return '<span class="rm-impact-none">not checked</span>';
    var n = Number(excess);
    if (n === 0) return '<span class="rm-impact-none">\u2014</span>';
    return (n > 0 ? '+' : '\u2212') + inrShort(Math.abs(n)).replace('\u20b9', '\u20b9\u2009');
}

function impactTone(excess) {
    if (excess == null) return 'none';
    var n = Number(excess);
    return n > 0 ? 'over' : (n < 0 ? 'under' : 'none');
}

function rmStatusBadge(s, source) {
    if (s === 'OK') return '<span class="wf-badge wf-badge-green">OK</span>';
    if (s === 'WARN') return '<span class="wf-badge wf-badge-yellow">WARN</span>';
    if (s === 'FAIL') return '<span class="wf-badge wf-badge-red">FAIL</span>';
    if (source === 'NS') return '<span class="wf-badge">New · no LAR</span>';
    return '<span class="wf-badge wf-badge-yellow">No reference ⚠</span>';
}
function setRmText(id, v) { var el = document.getElementById(id); if (el) el.textContent = v; }
function truncateRm(s, n) { s = s || ''; return s.length > n ? s.slice(0, n - 1) + '…' : s; }


// ===== Step 4: AI analysis (from backend) =====
document.addEventListener('DOMContentLoaded', function() {
    if (document.getElementById('aiCards')) initAiAnalysis();
    if (document.getElementById('aiAssessment')) initAiAssessment();
});

function initAiAssessment() {
    var id = getState('tenderId');
    var txt = document.getElementById('aiAssessText');
    var badge = document.getElementById('aiRiskBadge');
    var src = document.getElementById('aiAssessSource');
    if (!id) {
        if (txt) txt.textContent = 'No tender loaded. Please upload an estimate first.';
        if (badge) badge.textContent = '';
        return;
    }
    var renderAssessment = function (d) {
            if (txt) txt.textContent = d.summary || '';
            if (badge) {
                var risk = (d.riskLevel || '').toUpperCase();
                badge.textContent = (risk || '—') + ' RISK';
                badge.className = 'wf-badge ' + (risk === 'HIGH' ? 'wf-badge-red'
                    : risk === 'MEDIUM' ? 'wf-badge-yellow' : 'wf-badge-green');
            }
            if (src) src.textContent = d.source === 'llm' ? 'Generated by AI model' : 'Rule-based assessment';
    };

    var cached = readStepCache('summary');
    if (cached) { renderAssessment(cached); return; }

    raiecFetch(RAIEC_API + '/tenders/' + id + '/ai-summary')
        .then(function(res) { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); })
        .then(function(d) { writeStepCache('summary', d); renderAssessment(d); })
        .catch(function() {
            if (txt) txt.textContent = 'Could not load the AI assessment from the server (:8080).';
            if (badge) badge.textContent = '';
        });
}

function initAiAnalysis() {
    var box = document.getElementById('aiCards');
    var id = getState('tenderId');
    if (!id) {
        box.innerHTML = '<div class="wf-ai-card"><div class="wf-ai-content"><div class="wf-ai-desc">No tender loaded. Please upload an estimate first.</div></div></div>';
        return;
    }
    runStepOnce(4, RAIEC_API + '/tenders/' + id + '/ai-analysis',
        {
            title: 'Running analysis',
            steps: [
                'Checking rates against tolerance…',
                'Comparing NS items with accepted rates…',
                'Verifying quantity × rate = amount…',
                'Looking for duplicate proposals…'
            ]
        },
        renderAiAnalysis,
        function () {
            box.innerHTML = '<div class="wf-ai-card"><div class="wf-ai-content"><div class="wf-ai-desc">Could not load AI analysis from the server (:8080).</div></div></div>';
        });
}

function renderAiAnalysis(d) {
    setRmText('aiTotal', d.totalChecks + ' / ' + d.totalChecks);
    setRmText('aiPass', d.pass);
    setRmText('aiWarn', d.warn);
    setRmText('aiFail', d.fail);
    var box = document.getElementById('aiCards');
    if (!d.checks || !d.checks.length) {
        box.innerHTML = '<div class="wf-ai-card"><div class="wf-ai-content"><div class="wf-ai-desc">No checks available.</div></div></div>';
        return;
    }
    // Each check states what it looked at, what it concluded, and why that matters.
    // The old card showed a code and a sentence, which told the officer nothing about
    // what had actually been examined.
    box.innerHTML = d.checks.map(function(c, idx) {
        var tone = c.status === 'FAIL' ? 'fail' : (c.status === 'WARN' ? 'warn' : 'pass');
        var meta = AI_CHECK_META[c.code] || { what: '', why: '' };
        return '<article class="wf-check" data-tone="' + tone + '" style="--i:' + idx + '">' +
            '<div class="wf-check-rail" aria-hidden="true"></div>' +
            '<header class="wf-check-head">' +
                '<span class="wf-check-code">' + escapeHtml(c.code) + '</span>' +
                '<h3 class="wf-check-title">' + escapeHtml(c.title) + '</h3>' +
                '<span class="wf-check-verdict" data-tone="' + tone + '">' + escapeHtml(c.status) + '</span>' +
            '</header>' +
            '<p class="wf-check-detail">' + escapeHtml(c.detail) + '</p>' +
            (meta.what ? '<dl class="wf-check-meta">' +
                '<div><dt>Checks</dt><dd>' + escapeHtml(meta.what) + '</dd></div>' +
                '<div><dt>Why</dt><dd>' + escapeHtml(meta.why) + '</dd></div>' +
            '</dl>' : '') +
        '</article>';
    }).join('');
}

/* What each problem-statement check actually does. Kept beside the renderer so the
   explanation cannot drift away from the check it describes. */
var AI_CHECK_META = {
    'PS-01': {
        what: 'Every line item’s quoted rate against its IRUSSOR, CPWD DSR or LAR reference.',
        why: 'Over-quoting against a published schedule is the most direct way an estimate inflates public cost.'
    },
    'PS-02': {
        what: 'Non-Scheduled items against the lowest rate the railway has previously accepted.',
        why: 'NS items have no published rate, so past accepted rates are the only defensible benchmark.'
    },
    'PS-03': {
        what: 'That quantity × rate equals the stated amount on every priced row.',
        why: 'Arithmetic slips carry straight through to the contract value and are easily missed by eye.'
    },
    'PS-04': {
        what: 'Other tenders in the system describing the same work.',
        why: 'The same work tendered twice risks paying for it twice.'
    }
};




// ===== Step 5: officer review summary (from backend) =====
document.addEventListener('DOMContentLoaded', function() {
    if (document.getElementById('orEstValue')) initOfficerReview();
    if (document.getElementById('orAuditTrail')) initAuditTrail();
});


/* -----------------------------------------------------------------------------
   Audit trail

   This panel used to show five fixed lines with times in June 2026. On the page
   where an officer commits to a decision, that is the worst possible place for
   invented history: the trail's entire value is that it can be relied on later,
   and a decorative one is indistinguishable from a real one until someone needs
   it. It now shows what the server recorded, or says plainly that it could not
   be loaded.
   -------------------------------------------------------------------------- */
var AUDIT_TONE = {
    APPROVED:       'good',
    REJECTED:       'bad',
    INFO_REQUESTED: 'warn',
    RATE_MATCHED:   'info',
    AI_ANALYSED:    'info',
    SENT_TO_REVIEW: 'info'
};

function initAuditTrail() {
    var host = document.getElementById('orAuditTrail');
    var id = getState('tenderId');
    if (!host) return;

    if (!id) {
        host.innerHTML = '<li class="wf-audit-empty">No tender loaded.</li>';
        return;
    }

    raiecFetch(RAIEC_API + '/tenders/' + id + '/events')
        .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function (list) {
            if (!list || !list.length) {
                host.innerHTML = '<li class="wf-audit-empty">Nothing recorded for this tender yet.</li>';
                return;
            }
            host.innerHTML = list.map(function (e) {
                var tone = AUDIT_TONE[e.type] || '';
                var when = window.RAIEC_UI ? RAIEC_UI.formatDateTime(e.at) : '';
                // The actor is the whole point of an audit trail, so it is shown on every
                // row rather than only on the rows where a person happened to act.
                var who = e.actor && e.actor !== 'system'
                    ? 'by ' + escapeHtml(e.actor)
                    : 'automatic';
                return '<li class="wf-audit-item">' +
                         '<div class="wf-audit-dot" data-tone="' + tone + '"></div>' +
                         '<div class="wf-audit-content">' +
                           '<div class="wf-audit-title">' + escapeHtml(e.detail || e.type) + '</div>' +
                           '<div class="wf-audit-time">' + escapeHtml(when) +
                             ' · <span class="wf-audit-actor">' + who + '</span></div>' +
                         '</div>' +
                       '</li>';
            }).join('');
        })
        .catch(function () {
            host.innerHTML = '<li class="wf-audit-empty">Could not load the audit trail from the server.</li>';
        });
}

function initOfficerReview() {
    var id = getState('tenderId');
    if (!id) {
        setRmText('orAlertTitle', 'No tender loaded');
        return;
    }
    var base = RAIEC_API + '/tenders/' + id;
    showArrivalLoader('Preparing the review', [
            'Loading the tender record…',
            'Fetching rate comparison…',
            'Collecting analysis results…'
    ]);
    Promise.all([
        fetch(base).then(orOkJson),
        fetch(base + '/rate-match').then(orOkJson),
        fetch(base + '/ai-analysis').then(orOkJson)
    ]).then(function(r) {
        fillOfficerReview(r[0], r[1], r[2]);
    }).catch(function() {
        setRmText('orAlertTitle', 'Could not load review from the server (:8080)');
    }).finally(function() {
        // finally, not then: the overlay must clear whether or not the reads succeeded.
        if (window.RAIEC_UI) RAIEC_UI.hideLoader();
    });
}

function orOkJson(res) { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); }

function fillOfficerReview(detail, rm, ai) {
    // Alert banner
    var failN = rm.fail || 0;
    setRmText('orAlertTitle', failN + ' critical rate flag' + (failN === 1 ? '' : 's') + ' need your decision');
    var fails = (rm.items || []).filter(function(it) { return it.status === 'FAIL'; });
    var names = fails.slice(0, 2).map(function(it) { return (it.description || it.itemCode || '').slice(0, 40); });
    setRmText('orAlertDesc', detail.tenderNo + (names.length ? ' · ' + names.join('; ') + (fails.length > 2 ? ' …' : '') : ' · No items exceed reference tolerance.'));
    setRmText('orEstValue', detail.advertisedValue != null ? '₹' + (Number(detail.advertisedValue) / 10000000).toFixed(2) + ' Cr' : '—');

    // Submission details
    setRmText('orSubId', detail.tenderNo || '—');
    // Was hard-coded to "Civil & Construction". The row now carries the tendering section
    // the PDF actually names, and is labelled as such: every tender here comes from the
    // Construction department, so a "Department" row was a constant pretending to be data.
    setRmText('orSubDept', detail.tenderingSection || '—');
    setRmText('orSubZone', detail.division || '—');
    setRmText('orSubContract', detail.contractType || '—');
    setRmText('orSubBy', detail.post || '—');
    setRmText('orSubUploaded', window.RAIEC_UI ? RAIEC_UI.formatDateTime(detail.createdAt) : '—');

    // Upload & OCR
    setRmText('orOcrMeta', rm.totalItems + ' line items');
    setRmText('orLineItems', rm.totalItems + ' / ' + rm.totalItems);
    setRmText('orTenderRef', detail.tenderNo || '—');

    // Rate match
    setRmText('orRmMeta', (rm.matched || 0) + ' matched · ' + (rm.warn || 0) + ' WARN · ' + (rm.fail || 0) + ' FAIL');
    var flagged = (rm.items || []).filter(function(it) { return it.status === 'FAIL' || it.status === 'WARN'; }).slice(0, 6);
    var rmBody = document.getElementById('orRmBody');
    if (rmBody) {
        rmBody.innerHTML = flagged.length ? flagged.map(function(it) {
            var cls = it.status === 'FAIL' ? 'variance-red' : 'variance-yellow';
            var v = it.variancePct != null ? ((it.variancePct > 0 ? '+' : '') + it.variancePct + '%') : it.status;
            return '<div class="wf-expand-row"><span class="wf-expand-row-label">' + escapeHtml(truncateRm(it.description || it.itemCode, 50)) + '</span><span class="wf-expand-row-value ' + cls + '">' + v + '</span></div>';
        }).join('') : '<div class="wf-expand-row"><span class="wf-expand-row-label">No items outside tolerance.</span><span class="wf-expand-row-value"></span></div>';
    }

    // AI analysis
    setRmText('orAiTitle', 'AI analysis — ' + ai.totalChecks + ' problem-statement checks');
    setRmText('orAiMeta', (ai.pass || 0) + ' Pass · ' + (ai.warn || 0) + ' WARN · ' + (ai.fail || 0) + ' FAIL');
    var aiBody = document.getElementById('orAiBody');
    if (aiBody) {
        var checks = ai.checks || [];
        aiBody.innerHTML = checks.map(function(c) {
            var badge = c.status === 'FAIL' ? '<span class="wf-badge wf-badge-red">FAIL</span>'
                : (c.status === 'WARN' ? '<span class="wf-badge wf-badge-yellow">WARN</span>'
                : '<span class="wf-badge wf-badge-green">Pass</span>');
            return '<div class="wf-expand-row"><span class="wf-expand-row-label">' + escapeHtml(c.code + ' ' + c.title) + '</span><span class="wf-expand-row-value">' + badge + '</span></div>';
        }).join('');
    }
}



// ===== Step 4 -> 5: mark the tender as under officer review =====
function sendToOfficerReview() {
    var id = getState('tenderId');
    if (!id) {
        navigateTo('step5-officer-review.html');
        return;
    }
    raiecFetch(RAIEC_API + '/tenders/' + id + '/send-to-review', { method: 'POST' })
        .catch(function() {})
        .finally(function() { navigateTo('step5-officer-review.html'); });
}


/* -----------------------------------------------------------------------------
   Tender reference
   The field on step 1 is read-only and filled from the parse: the number comes
   out of the PDF, so asking the officer to retype it would only invite a
   mismatch between what was uploaded and what is recorded.
   -------------------------------------------------------------------------- */
function fillDetectedReference(summary) {
    if (!summary) return;
    var field = document.getElementById('tenderRefInput')
             || document.querySelector('.wf-form-input[data-role="tender-ref"]');
    if (field) {
        field.value = summary.tenderNo || '';
        field.classList.add('is-detected');
    }
    var msg = document.getElementById('uploadMessage');
    if (msg && summary.tenderNo) {
        msg.hidden = false;
        msg.innerHTML = '<span style="color:var(--accent-green)">✓</span> Detected tender '
            + '<strong>' + escapeHtml(summary.tenderNo) + '</strong>'
            + (summary.nameOfWork ? ' — ' + escapeHtml(truncateRm(summary.nameOfWork, 70)) : '');
        msg.style.color = 'var(--text-secondary)';
    }
}


/* =============================================================================
   Benchmark panel (step 3)
   Shows the tolerance the verdicts were measured against, because a FAIL means
   nothing unless you know the band it failed. Admins can edit it in place;
   officers see the same values read-only.
   ============================================================================= */
function initThresholdPanel() {
    var host = document.getElementById('wfThresholds');
    if (!host) return;
    var isAdmin = (localStorage.getItem('raiec_role') || '').toUpperCase() === 'ADMIN';

    raiecFetch(RAIEC_API + '/settings/thresholds')
        .then(orOkJson)
        .then(function (t) { renderThresholds(host, t, isAdmin); })
        .catch(function () { host.innerHTML = '<div class="wf-th-err">Benchmark unavailable.</div>'; });
}

function renderThresholds(host, t, isAdmin) {
    host.innerHTML =
        '<div class="wf-th-head">' +
          '<span class="wf-th-title">Benchmark</span>' +
          (isAdmin ? '<button type="button" class="wf-th-edit" id="wfThEdit">Edit</button>' : '') +
        '</div>' +
        '<dl class="wf-th-list">' +
          thRow('Acceptable', '≤ +' + fmtPct(t.warnPct) + '%', 'ok') +
          thRow('Warn', '+' + fmtPct(t.warnPct) + '% to +' + fmtPct(t.failPct) + '%', 'warn') +
          thRow('Fail', '> +' + fmtPct(t.failPct) + '%', 'fail') +
          thRow('LAR valid', t.larValidityMonths + ' months', 'muted') +
        '</dl>' +
        (t.updatedBy ? '<p class="wf-th-meta">Last changed by ' + escapeHtml(t.updatedBy) + '</p>' : '');

    var editBtn = document.getElementById('wfThEdit');
    if (editBtn) editBtn.addEventListener('click', function () { openThresholdEditor(host, t); });
}

function thRow(label, value, tone) {
    return '<div class="wf-th-row" data-tone="' + tone + '">' +
             '<dt>' + escapeHtml(label) + '</dt><dd>' + escapeHtml(value) + '</dd>' +
           '</div>';
}

function fmtPct(v) {
    var n = Number(v);
    return isNaN(n) ? v : (n % 1 === 0 ? n.toFixed(0) : n.toFixed(2).replace(/0$/, ''));
}

function openThresholdEditor(host, t) {
    var ov = document.createElement('div');
    ov.className = 'raiec-loader open wf-th-modal';   // reuse the blurred overlay
    ov.innerHTML =
        '<div class="wf-th-dialog" role="dialog" aria-modal="true" aria-labelledby="wfThDlgTitle">' +
          '<h3 id="wfThDlgTitle">Edit benchmark</h3>' +
          '<p class="wf-th-dialog-sub">Applies to every tender checked from now on.</p>' +
          thField('Warn above (%)', 'thWarn', t.warnPct) +
          thField('Fail above (%)', 'thFail', t.failPct) +
          thField('LAR valid for (months)', 'thLar', t.larValidityMonths) +
          '<p class="wf-th-error" id="thError" hidden></p>' +
          '<div class="wf-th-actions">' +
            '<button type="button" class="wf-btn wf-btn-secondary" id="thCancel">Cancel</button>' +
            '<button type="button" class="wf-btn wf-btn-primary" id="thSave">Save</button>' +
          '</div>' +
        '</div>';
    document.body.appendChild(ov);
    document.documentElement.classList.add('modal-open');

    function close() {
        ov.remove();
        document.documentElement.classList.remove('modal-open');
    }
    ov.addEventListener('mousedown', function (e) { if (e.target === ov) close(); });
    document.getElementById('thCancel').addEventListener('click', close);

    document.getElementById('thSave').addEventListener('click', function () {
        var err = document.getElementById('thError');
        var body = {
            warnPct: parseFloat(document.getElementById('thWarn').value),
            failPct: parseFloat(document.getElementById('thFail').value),
            larValidityMonths: parseInt(document.getElementById('thLar').value, 10)
        };
        // Check the one rule the officer is most likely to trip before a round trip.
        if (!(body.warnPct < body.failPct)) {
            err.hidden = false;
            err.textContent = 'Warn must be below Fail, otherwise nothing could ever be a warning.';
            return;
        }
        err.hidden = true;
        raiecFetch(RAIEC_API + '/settings/thresholds', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(function (res) {
            if (!res.ok) return res.json().then(function (b) { throw new Error(b.message || 'Update failed'); });
            return res.json();
        }).then(function (updated) {
            close();
            renderThresholds(host, updated, true);
            // The verdicts on screen were measured against the old bands.
            if (typeof initRateMatch === 'function') initRateMatch();
        }).catch(function (e) {
            err.hidden = false;
            err.textContent = e.message || 'Could not save.';
        });
    });
}

function thField(label, id, value) {
    return '<label class="wf-th-field"><span>' + escapeHtml(label) + '</span>' +
           '<input type="number" step="0.01" min="0" id="' + id + '" value="' + escapeHtml(String(value)) + '"></label>';
}

document.addEventListener('DOMContentLoaded', initThresholdPanel);


/* =============================================================================
   Analysis report
   The button previously had no handler. It now builds a self-contained HTML
   report from the same endpoints the screen uses and downloads it. Self-contained
   HTML rather than a rendered PDF: it opens anywhere, prints to PDF from the
   browser, and stays readable without RAIEC running.
   ============================================================================= */
function downloadAnalysisReport() {
    var id = getState('tenderId');
    if (!id) { alert('No tender loaded.'); return; }
    var btn = document.getElementById('wfReportBtn');
    if (btn) { btn.disabled = true; btn.textContent = 'Preparing\u2026'; }

    var base = RAIEC_API + '/tenders/' + id;
    Promise.all([
        fetch(base).then(orOkJson),
        fetch(base + '/rate-match').then(orOkJson),
        fetch(base + '/ai-analysis').then(orOkJson),
        fetch(base + '/ai-summary').then(orOkJson).catch(function () { return null; })
    ]).then(function (r) {
        var html = buildReportHtml(r[0], r[1], r[2], r[3]);
        var blob = new Blob([html], { type: 'text/html;charset=utf-8' });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = 'RAIEC-analysis-' + String(r[0].tenderNo || id).replace(/[^\w.-]+/g, '-') + '.html';
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
    }).catch(function () {
        alert('Could not build the report \u2014 the server did not return the analysis.');
    }).finally(function () {
        if (btn) { btn.disabled = false; btn.textContent = 'Download analysis report'; }
    });
}

function buildReportHtml(t, rm, ai, summary) {
    var esc = escapeHtml;
    var generated = new Date().toLocaleString('en-IN');
    var flagged = (rm.items || []).filter(function (i) { return i.status === 'FAIL' || i.status === 'WARN'; });

    function money(v) { return v == null ? '\u2014' : Number(v).toLocaleString('en-IN'); }

    function itemRows(list) {
        if (!list.length) return '<tr><td colspan="7" class="muted">None.</td></tr>';
        return list.map(function (i) {
            return '<tr>'
                + '<td>' + esc(i.description || i.itemCode || '') + '</td>'
                + '<td>' + esc(i.source || '') + '</td>'
                + '<td class="num">' + money(i.tenderRate) + '</td>'
                + '<td class="num">' + money(i.referenceRate) + '</td>'
                + '<td class="num">' + (i.variancePct != null ? (i.variancePct > 0 ? '+' : '') + i.variancePct + '%' : '\u2014') + '</td>'
                + '<td><span class="v v-' + esc(i.status) + '">' + esc(i.status) + '</span></td>'
                + '</tr>';
        }).join('');
    }

    var css = 'body{font:14px/1.6 -apple-system,Segoe UI,Roboto,sans-serif;color:#111827;margin:0;padding:40px;background:#fff}'
        + '.wrap{max-width:900px;margin:0 auto}'
        + 'h1{font-size:24px;margin:0 0 4px}'
        + 'h2{font-size:17px;margin:34px 0 10px;padding-bottom:6px;border-bottom:1px solid #e5e7eb}'
        + '.sub{color:#6b7280;margin:0 0 22px;font-size:13px}'
        + 'table{width:100%;border-collapse:collapse;margin-top:8px;font-size:12.5px}'
        + 'th,td{text-align:left;padding:7px 9px;border-bottom:1px solid #eef0f3;vertical-align:top}'
        + 'th{background:#f8fafc;font-size:11px;text-transform:uppercase;letter-spacing:.5px;color:#6b7280}'
        + '.num{text-align:right;white-space:nowrap;font-variant-numeric:tabular-nums}'
        + '.muted{color:#9ca3af}'
        + '.kv{display:grid;grid-template-columns:190px 1fr;gap:6px 16px;font-size:13px}'
        + '.kv dt{color:#6b7280}.kv dd{margin:0;font-weight:600}'
        + '.cards{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:14px 0 4px}'
        + '.card{border:1px solid #e5e7eb;border-radius:8px;padding:10px 12px;font-size:12px;color:#6b7280}'
        + '.card b{display:block;font-size:22px;color:#111827}'
        + '.v{padding:2px 8px;border-radius:20px;font-size:11px;font-weight:700}'
        + '.v-OK{background:#dcfce7;color:#166534}.v-WARN{background:#fef3c7;color:#92400e}'
        + '.v-FAIL{background:#fee2e2;color:#991b1b}.v-NO_REFERENCE{background:#f1f5f9;color:#475569}'
        + '.chk{border-left:3px solid #cbd5e1;padding:8px 0 8px 12px;margin-bottom:10px}'
        + '.chk.PASS{border-color:#16a34a}.chk.WARN{border-color:#d97706}.chk.FAIL{border-color:#dc2626}'
        + '.chk b{display:block}'
        + '.foot{margin-top:40px;padding-top:14px;border-top:1px solid #e5e7eb;color:#9ca3af;font-size:11.5px}'
        + '@media print{body{padding:0}h2{page-break-after:avoid}tr{page-break-inside:avoid}}';

    var checks = (ai.checks || []).map(function (c) {
        return '<div class="chk ' + esc(c.status) + '"><b>' + esc(c.code) + ' \u00b7 ' + esc(c.title)
             + ' \u2014 ' + esc(c.status) + '</b>' + esc(c.detail) + '</div>';
    }).join('');

    var assessment = (summary && summary.summary)
        ? '<h2>Assessment</h2><p>' + esc(summary.summary) + '</p>'
          + '<p class="sub">Risk: ' + esc(summary.riskLevel || '') + ' \u00b7 Source: '
          + esc(summary.source === 'llm' ? 'AI language model' : 'Rule-based') + '</p>'
        : '';

    return '<!doctype html><html lang="en"><head><meta charset="utf-8">'
      + '<title>RAIEC analysis \u2014 ' + esc(t.tenderNo || '') + '</title><style>' + css + '</style></head>'
      + '<body><div class="wrap">'
      + '<h1>Estimate analysis report</h1>'
      + '<p class="sub">RAIEC \u2014 Railway Automated Intelligent Estimate Checker \u00b7 North Western Railway, Civil &amp; Construction</p>'
      + '<h2>Tender</h2><dl class="kv">'
        + '<dt>Tender number</dt><dd>' + esc(t.tenderNo || '\u2014') + '</dd>'
        + '<dt>Name of work</dt><dd>' + esc(t.nameOfWork || '\u2014') + '</dd>'
        + '<dt>Division</dt><dd>' + esc(t.division || '\u2014') + '</dd>'
        + '<dt>Originating post</dt><dd>' + esc(t.post || '\u2014') + '</dd>'
        + '<dt>Advertised value</dt><dd>' + (t.advertisedValue != null ? '\u20b9 ' + money(t.advertisedValue) : '\u2014') + '</dd>'
        + '<dt>Status at analysis</dt><dd>' + esc(t.status || '\u2014') + '</dd>'
      + '</dl>'
      + (rm.financials ? '<h2>Financial impact</h2><div class="cards">'
            + '<div class="card"><b>' + inrShort(rm.financials.excessTotal) + '</b>quoted above reference</div>'
            + '<div class="card"><b>' + inrShort(rm.financials.quotedValue) + '</b>estimate value</div>'
            + '<div class="card"><b>' + Number(rm.financials.coveragePct).toFixed(1) + '%</b>checked against a reference</div>'
            + '<div class="card"><b>' + inrShort(rm.financials.savingTotal) + '</b>quoted below reference</div>'
          + '</div>'
          + (Number(rm.financials.coveragePct) < 60
              ? '<p class="sub"><strong>Only ' + Number(rm.financials.coveragePct).toFixed(1)
                + '% of this estimate could be checked.</strong> ' + inrShort(rm.financials.unreferencedValue)
                + ' has no reference rate, so the figures above describe the checked part only.</p>'
              : '')
          + reportAnalysis(rm.financials)
        : '')
      + '<h2>Rate comparison</h2><div class="cards">'
        + '<div class="card"><b>' + rm.totalItems + '</b>items checked</div>'
        + '<div class="card"><b>' + rm.matched + '</b>within tolerance</div>'
        + '<div class="card"><b>' + rm.warn + '</b>warnings</div>'
        + '<div class="card"><b>' + rm.fail + '</b>failures</div>'
      + '</div>'
      + '<p class="sub">' + rm.noReference + ' item(s) had no reference rate available and were not scored.</p>'
      + '<h2>Flagged items (' + flagged.length + ')</h2>'
      + '<p class="sub">Ordered by rupee impact — the costliest first, not the largest percentage.</p>'
      + '<table><thead><tr><th>Description</th><th>Source</th><th class="num">Quoted</th>'
      + '<th class="num">Reference</th><th class="num">Impact</th><th class="num">Variance</th><th>Verdict</th></tr></thead>'
      + '<tbody>' + itemRows(flagged) + '</tbody></table>'
      + '<h2>Automated checks</h2>' + checks
      + assessment
      + '<div class="foot">Generated ' + esc(generated)
      + '. Advisory only \u2014 the vetting decision rests with the reviewing officer.</div>'
      + '</div></body></html>';
}


/**
 * The analysis section of the printable report: materiality, concentration, where the
 * excess sits, and the clustering signal.
 *
 * The report is what gets attached to a file and read months later by someone who was
 * not at the screen, so it has to carry the reasoning and not just the totals.
 */
function reportAnalysis(f) {
    if (!f) return '';
    var out = '';

    var rows = [];
    if (f.materialityPct != null) {
        rows.push(['Excess as a share of the estimate', Number(f.materialityPct).toFixed(2) + '%']);
    }
    if (f.concentrationCount) {
        rows.push(['Concentration',
            f.concentrationCount + ' item(s) carry ' + Number(f.concentrationPct).toFixed(0) + '% of the excess']);
    }
    if (rows.length) {
        out += '<dl class="kv">' + rows.map(function (r) {
            return '<dt>' + esc(r[0]) + '</dt><dd>' + esc(r[1]) + '</dd>';
        }).join('') + '</dl>';
    }

    out += reportSplit('Excess by schedule', f.bySchedule, false);
    out += reportSplit('Excess by rate reference', f.bySource, true);

    var c = f.clustering;
    if (c && c.suspicious) {
        out += '<h2>Pattern noted</h2>'
             + '<p>' + esc(c.note) + '</p>'
             + '<p class="sub">' + esc(inrShort(c.valueInBand)) + ' of work sits in this band, '
             + esc(inrShort(c.excessInBand)) + ' of it above reference. '
             + 'This is a pattern worth a question, not a finding on its own.</p>';
    }
    return out;
}

function reportSplit(title, slices, markUnchecked) {
    if (!slices || !slices.length) return '';
    var rows = slices.slice(0, 8).map(function (s) {
        var unchecked = markUnchecked && s.label === 'No reference';
        return '<tr><td>' + esc(s.label) + '</td>'
             + '<td class="num">' + s.itemCount + '</td>'
             + '<td class="num">' + esc(inrShort(s.quotedValue)) + '</td>'
             + '<td class="num">' + (unchecked
                    ? '<span class="muted">not checked</span>'
                    : esc(inrShort(s.excessTotal))) + '</td>'
             + '<td class="num">' + (unchecked ? '<span class="muted">—</span>'
                    : Number(s.sharePct).toFixed(1) + '%') + '</td></tr>';
    }).join('');

    return '<h2>' + esc(title) + '</h2>'
         + '<table><thead><tr><th>' + esc(title.replace('Excess by ', '')) + '</th>'
         + '<th class="num">Items</th><th class="num">Quoted</th>'
         + '<th class="num">Excess</th><th class="num">Share</th></tr></thead>'
         + '<tbody>' + rows + '</tbody></table>';
}


/* =============================================================================
   Officer decision
   ============================================================================= */

/**
 * "Request more info" is a hold, not a verdict, so it asks what is being queried.
 * The question is stored on the tender rather than lost in conversation.
 */
function requestMoreInfo() {
    var ov = document.createElement('div');
    ov.className = 'raiec-loader open wf-th-modal';
    ov.innerHTML =
        '<div class="wf-th-dialog" role="dialog" aria-modal="true" aria-labelledby="riTitle">' +
          '<h3 id="riTitle">Request more information</h3>' +
          '<p class="wf-th-dialog-sub">The tender stays open. You can still approve or reject it later.</p>' +
          '<label class="wf-th-field"><span>What do you need from the filing department?</span>' +
            '<textarea id="riRemark" rows="4" placeholder="e.g. Attach market quotations for the Non-Scheduled items."></textarea>' +
          '</label>' +
          '<p class="wf-th-error" id="riError" hidden></p>' +
          '<div class="wf-th-actions">' +
            '<button type="button" class="wf-btn wf-btn-secondary" id="riCancel">Cancel</button>' +
            '<button type="button" class="wf-btn wf-btn-primary" id="riSend">Send request</button>' +
          '</div>' +
        '</div>';
    document.body.appendChild(ov);
    document.documentElement.classList.add('modal-open');

    function close() {
        ov.remove();
        document.documentElement.classList.remove('modal-open');
    }
    ov.addEventListener('mousedown', function (e) { if (e.target === ov) close(); });
    document.getElementById('riCancel').addEventListener('click', close);
    setTimeout(function () { var t = document.getElementById('riRemark'); if (t) t.focus(); }, 60);

    document.getElementById('riSend').addEventListener('click', function () {
        var remark = document.getElementById('riRemark').value.trim();
        var err = document.getElementById('riError');
        if (!remark) {
            err.hidden = false;
            err.textContent = 'Say what is needed \u2014 an empty request cannot be actioned.';
            return;
        }
        close();
        decideTender('request-info', 'Clarification requested from the filing department.', remark);
    });
}

/**
 * Closing confirmation. The officer has finished with this tender, so the page
 * says so plainly and offers the next tender rather than leaving them on a screen
 * whose buttons no longer apply. Dismissing it keeps them here.
 */
function showOutcome(action, message, body) {
    var tone = action === 'approve' ? 'ok' : (action === 'reject' ? 'fail' : 'warn');
    var heading = action === 'approve' ? 'Estimate approved'
                : (action === 'reject' ? 'Estimate rejected' : 'Clarification requested');
    var summary = getState('tenderSummary') || {};

    var mark = action === 'approve'
        ? '<path d="M20 6L9 17l-5-5"/>'
        : (action === 'reject' ? '<path d="M18 6L6 18M6 6l12 12"/>'
                               : '<path d="M12 8v5M12 17h.01"/><circle cx="12" cy="12" r="9"/>');

    var ov = document.createElement('div');
    ov.className = 'raiec-loader open wf-outcome';
    ov.innerHTML =
        '<div class="wf-outcome-card" role="dialog" aria-modal="true" aria-labelledby="ocTitle" data-tone="' + tone + '">' +
          '<button type="button" class="wf-outcome-close" id="ocClose" aria-label="Stay on this page">&times;</button>' +
          '<div class="wf-outcome-mark"><svg viewBox="0 0 24 24" aria-hidden="true">' + mark + '</svg></div>' +
          '<h3 id="ocTitle">' + escapeHtml(heading) + '</h3>' +
          '<p class="wf-outcome-tender">' + escapeHtml(summary.tenderNo || '') + '</p>' +
          '<p class="wf-outcome-msg">' + escapeHtml(message) + '</p>' +
          (action === 'approve' && body
            ? '<dl class="wf-outcome-stats">' +
                '<div><dt>Added to LAR</dt><dd>' + (body.larAdded || 0) + '</dd></div>' +
                '<div><dt>Rates improved</dt><dd>' + (body.larUpdated || 0) + '</dd></div>' +
              '</dl>'
            : '') +
          '<div class="wf-outcome-actions">' +
            '<button type="button" class="wf-btn wf-btn-secondary" id="ocStay">Stay here</button>' +
            '<a class="wf-btn wf-btn-primary" href="step1-upload.html" id="ocNext">Scan another estimate</a>' +
          '</div>' +
        '</div>';
    document.body.appendChild(ov);
    document.documentElement.classList.add('modal-open');

    function close() {
        ov.classList.remove('open');
        document.documentElement.classList.remove('modal-open');
        setTimeout(function () { ov.remove(); }, 260);
    }
    document.getElementById('ocClose').addEventListener('click', close);
    document.getElementById('ocStay').addEventListener('click', close);
    ov.addEventListener('mousedown', function (e) { if (e.target === ov) close(); });
    document.addEventListener('keydown', function esc(e) {
        if (e.key === 'Escape') { close(); document.removeEventListener('keydown', esc); }
    });

    // Starting a new estimate must not inherit the finished one's state.
    document.getElementById('ocNext').addEventListener('click', function () {
        try {
            clearStepCaches();
            sessionStorage.removeItem('raiec_tenderId');
            sessionStorage.removeItem('raiec_tenderSummary');
            sessionStorage.removeItem('raiec_uploadedFile');
        } catch (e) { /* ignore */ }
    });
}


/* =============================================================================
   Rate report export
   CSV rather than a formatted document: this sheet exists to be opened in Excel
   and worked through, which is what a vetter actually does with it. Honours the
   active filters, because exporting a filtered view is usually the point.
   ============================================================================= */
function exportRateReport() {
    var rows = Array.prototype.slice.call(document.querySelectorAll('.wf-table tbody tr'))
        .filter(function (r) { return r.style.display !== 'none'; });
    if (!rows.length) { alert('Nothing to export with the current filters.'); return; }

    var summary = getState('tenderSummary') || {};
    var header = ['Description', 'Source', 'Qty', 'Unit', 'Quoted rate', 'Amount',
                  'Reference rate', 'Reference source', 'Impact (INR)', 'Variance %', 'Verdict'];

    function cell(v) {
        var t = (v == null ? '' : String(v)).replace(/\s+/g, ' ').trim();
        // Quote everything and double any embedded quote: descriptions contain commas.
        return '"' + t.replace(/"/g, '""') + '"';
    }

    var lines = [header.map(cell).join(',')];
    rows.forEach(function (tr) {
        var td = tr.querySelectorAll('td');
        if (td.length < 10) return;
        // The reference cell holds the rate on one line and its source on the next.
        var refParts = td[6].innerText.split('\n');
        lines.push([
            td[0].innerText,
            td[1].innerText,
            td[2].innerText,
            td[3].innerText,
            td[4].innerText,
            td[5].innerText,
            refParts[0] || '',
            refParts.slice(1).join(' ').trim(),
            td[7].innerText,
            td[8].innerText,
            td[9].innerText
        ].map(cell).join(','));
    });

    // BOM so Excel opens UTF-8 (and the rupee sign) correctly instead of mojibake.
    var blob = new Blob(['\ufeff' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = 'RAIEC-rates-' + String(summary.tenderNo || 'tender').replace(/[^\w.-]+/g, '-') + '.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
}


/* =============================================================================
   Duplicate tender
   ============================================================================= */

var WF_STATUS_LABELS = {
    UPLOADED: 'Uploaded',
    OCR_EXTRACTED: 'Extracted',
    RATE_MATCHED: 'Rate matched',
    AI_ANALYZED: 'Analysed',
    OFFICER_REVIEW: 'With the officer',
    INFO_REQUESTED: 'Awaiting clarification',
    APPROVED: 'Approved',
    REJECTED: 'Rejected'
};

function wfFormatDateTime(iso) {
    if (!iso) return 'Unknown';
    var d = new Date(iso);
    if (isNaN(d)) return iso;
    return d.toLocaleString('en-IN', {
        day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

/**
 * Shown when the uploaded PDF names a tender already in the system. It states what is
 * already held and offers the only two useful moves: open the existing record, or pick
 * a different file.
 */
function showDuplicateDialog(existing) {
    var ov = document.createElement('div');
    ov.className = 'raiec-loader open wf-dup';
    ov.innerHTML =
        '<div class="wf-dup-card" role="dialog" aria-modal="true" aria-labelledby="dupTitle">' +
          '<button type="button" class="wf-outcome-close" id="dupClose" aria-label="Close">&times;</button>' +
          '<div class="wf-dup-mark"><svg viewBox="0 0 24 24" aria-hidden="true">' +
            '<rect x="9" y="9" width="11" height="11" rx="2"/>' +
            '<path d="M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1"/>' +
          '</svg></div>' +
          '<h3 id="dupTitle">This tender has already been scanned</h3>' +
          '<p class="wf-dup-sub">Nothing was uploaded. RAIEC already holds a record for this tender number.</p>' +
          '<dl class="wf-dup-facts">' +
            dupFact('Tender number', existing.tenderNo) +
            dupFact('Name of work', existing.nameOfWork) +
            dupFact('Scanned on', wfFormatDateTime(existing.uploadedAt)) +
            dupFact('Current stage', WF_STATUS_LABELS[existing.status] || existing.status) +
            dupFact('Original file', existing.originalFileName) +
          '</dl>' +
          '<div class="wf-outcome-actions">' +
            '<button type="button" class="wf-btn wf-btn-secondary" id="dupAnother">Choose another file</button>' +
            '<button type="button" class="wf-btn wf-btn-primary" id="dupOpen">Open the existing tender</button>' +
          '</div>' +
        '</div>';
    document.body.appendChild(ov);
    document.documentElement.classList.add('modal-open');

    function close() {
        ov.classList.remove('open');
        document.documentElement.classList.remove('modal-open');
        setTimeout(function () { ov.remove(); }, 260);
    }
    document.getElementById('dupClose').addEventListener('click', close);
    ov.addEventListener('mousedown', function (e) { if (e.target === ov) close(); });
    document.addEventListener('keydown', function esc(e) {
        if (e.key === 'Escape') { close(); document.removeEventListener('keydown', esc); }
    });

    document.getElementById('dupAnother').addEventListener('click', function () {
        close();
        raiecSelectedFile = null;
        var preview = document.querySelector('.wf-file-preview');
        if (preview) preview.style.display = 'none';
        var input = document.getElementById('fileInput');
        if (input) { input.value = ''; input.click(); }
    });

    // Jump straight to the existing record rather than making them find it.
    document.getElementById('dupOpen').addEventListener('click', function () {
        clearStepCaches();
        saveState('tenderId', existing.id);
        saveState('tenderSummary', {
            tenderNo: existing.tenderNo,
            nameOfWork: existing.nameOfWork
        });
        close();
        navigateTo('step2-ocr-extract.html');
    });
}

function dupFact(label, value) {
    if (!value) return '';
    return '<div><dt>' + escapeHtml(label) + '</dt><dd>' + escapeHtml(String(value)) + '</dd></div>';
}
