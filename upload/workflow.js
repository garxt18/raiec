// ===== Workflow Shared JS =====

// Backend API base (local dev). Change this when deploying.
var RAIEC_API = 'http://localhost:8080/api';
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

// Navigate between steps
function navigateTo(page) {
    window.location.href = page;
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
        // Clear green "ready" state (no misleading fake progress).
        var bar = preview.querySelector('.wf-file-progress-bar');
        if (bar) { bar.style.width = '100%'; bar.style.background = '#34d399'; }
        var icon = preview.querySelector('.wf-file-icon');
        if (icon) { icon.style.background = 'rgba(52,211,153,0.15)'; icon.style.color = '#34d399'; }
    }
    saveState('uploadedFile', { name: file.name, size: file.size });
}

document.addEventListener('DOMContentLoaded', initDropzone);

// ===== Step 1: upload the estimate to the backend =====
function uploadEstimate() {
    var btn = document.getElementById('submitBtn');
    if (!raiecSelectedFile) {
        showUploadMessage('Please choose a PDF file first.', 'error');
        return;
    }
    if (btn) { btn.disabled = true; btn.textContent = 'Uploading & extracting...'; }
    showUploadMessage('Uploading and extracting the tender. This can take a few seconds...', 'info');

    var form = new FormData();
    form.append('file', raiecSelectedFile);

    fetch(RAIEC_API + '/tenders/upload', { method: 'POST', body: form })
        .then(function(res) {
            return res.json().then(function(body) {
                return { ok: res.ok, status: res.status, body: body };
            }).catch(function() {
                return { ok: res.ok, status: res.status, body: null };
            });
        })
        .then(function(r) {
            if (!r.ok) {
                var msg = (r.body && r.body.message) ? r.body.message : ('Upload failed (HTTP ' + r.status + ').');
                showUploadMessage(msg, 'error');
                if (btn) { btn.disabled = false; btn.textContent = 'Submit for validation'; }
                return;
            }
            saveState('tenderId', r.body.id);
            saveState('tenderSummary', r.body);
            navigateTo('step2-ocr-extract.html');
        })
        .catch(function() {
            showUploadMessage('Could not reach the server. Make sure the backend is running on http://localhost:8080.', 'error');
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
    el.style.color = type === 'error' ? '#f87171' : (type === 'info' ? '#94a3b8' : '#34d399');
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

    fetch(RAIEC_API + '/tenders/' + id)
        .then(function(res) { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); })
        .then(function(t) { renderOcr(t); })
        .catch(function() {
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
            return '<tr><td>' + escapeHtml(it.desc) + '</td><td>' + escapeHtml(qty) + '</td><td>' + formatNum(it.rate) + '</td></tr>';
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

function decideTender(action, label) {
    var id = getState('tenderId');
    if (!id) {
        showReviewMessage('No tender loaded. Please upload an estimate first.', 'error');
        return;
    }
    fetch(RAIEC_API + '/tenders/' + id + '/' + action, { method: 'POST' })
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
        })
        .catch(function() {
            showReviewMessage('Could not reach the server. Make sure the backend is running on http://localhost:8080.', 'error');
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
        tbody.innerHTML = '<tr><td colspan="9">No tender loaded. Please upload an estimate first.</td></tr>';
        return;
    }
    tbody.innerHTML = '<tr><td colspan="9">Running rate match…</td></tr>';
    fetch(RAIEC_API + '/tenders/' + id + '/rate-match')
        .then(function(res) { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); })
        .then(function(d) { renderRateMatch(d); })
        .catch(function() {
            tbody.innerHTML = '<tr><td colspan="9">Could not load rate match from the server (:8080).</td></tr>';
        });
}

function renderRateMatch(d) {
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
        tbody.innerHTML = '<tr><td colspan="9">No rateable items.</td></tr>';
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
        return '<tr data-status="' + st + '">' +
            '<td>' + escapeHtml(truncateRm(it.description || it.itemCode, 64)) + '</td>' +
            '<td>' + escapeHtml(it.source || '') + '</td>' +
            '<td>' + (it.quantity != null ? formatNum(it.quantity) : '—') + '</td>' +
            '<td>' + escapeHtml(it.unit || '—') + '</td>' +
            '<td>' + formatNum(it.tenderRate) + '</td>' +
            '<td>' + (it.amount != null ? formatNum(it.amount) : '—') + '</td>' +
            '<td>' + refCell + '</td>' +
            '<td class="' + vCls + '">' + vTxt + '</td>' +
            '<td>' + rmStatusBadge(it.status, it.source) + '</td>' +
        '</tr>';
    }).join('');
}

// Reference column: the published rate, the escalated rate actually acceptable under this
// tender's quoted percentage (only when it differs), and where the reference came from.
function buildRefCell(it) {
    if (it.referenceRate == null) {
        return it.source === 'NS' ? '<span style="color:#94a3b8">no prior rate</span>' : '—';
    }
    var html = formatNum(it.referenceRate);

    if (it.effectiveReferenceRate != null && Number(it.effectiveReferenceRate) !== Number(it.referenceRate)) {
        var pct = Number(it.escalationPct);
        var sign = pct > 0 ? '+' : '';
        html += '<br><span style="font-size:11px;color:#cbd5e1">&rarr; ' + formatNum(it.effectiveReferenceRate)
             + ' <span style="color:#94a3b8">(' + sign + pct + '% escalation)</span></span>';
    }
    if (it.referenceSource) {
        var colour = it.referenceStale ? '#fbbf24' : '#94a3b8';
        html += '<br><span style="font-size:11px;color:' + colour + '">'
             + (it.referenceStale ? '⚠ ' : '') + escapeHtml(it.referenceSource) + '</span>';
    }
    return html;
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
    fetch(RAIEC_API + '/tenders/' + id + '/ai-summary')
        .then(function(res) { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); })
        .then(function(d) {
            if (txt) txt.textContent = d.summary || '';
            if (badge) {
                var risk = (d.riskLevel || '').toUpperCase();
                badge.textContent = (risk || '—') + ' RISK';
                badge.className = 'wf-badge ' + (risk === 'HIGH' ? 'wf-badge-red'
                    : risk === 'MEDIUM' ? 'wf-badge-yellow' : 'wf-badge-green');
            }
            if (src) src.textContent = d.source === 'llm' ? 'Generated by AI model' : 'Rule-based assessment';
        })
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
    fetch(RAIEC_API + '/tenders/' + id + '/ai-analysis')
        .then(function(res) { if (!res.ok) throw new Error('HTTP ' + res.status); return res.json(); })
        .then(function(d) { renderAiAnalysis(d); })
        .catch(function() {
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
    box.innerHTML = d.checks.map(function(c) {
        var cardCls = c.status === 'FAIL' ? 'fail' : (c.status === 'WARN' ? 'warn' : 'pass');
        var badge = c.status === 'FAIL' ? '<span class="wf-badge wf-badge-red">FAIL</span>'
            : (c.status === 'WARN' ? '<span class="wf-badge wf-badge-yellow">WARN</span>'
            : '<span class="wf-badge wf-badge-green">Pass</span>');
        return '<div class="wf-ai-card ' + cardCls + '">' +
            '<div class="wf-ai-id">' + escapeHtml(c.code) + '</div>' +
            '<div class="wf-ai-content">' +
                '<div class="wf-ai-title">' + escapeHtml(c.title) + '</div>' +
                '<div class="wf-ai-desc">' + escapeHtml(c.detail) + '</div>' +
            '</div>' +
            '<div class="wf-ai-status">' + badge + '</div>' +
        '</div>';
    }).join('');
}



// ===== Step 5: officer review summary (from backend) =====
document.addEventListener('DOMContentLoaded', function() {
    if (document.getElementById('orEstValue')) initOfficerReview();
});

function initOfficerReview() {
    var id = getState('tenderId');
    if (!id) {
        setRmText('orAlertTitle', 'No tender loaded');
        return;
    }
    var base = RAIEC_API + '/tenders/' + id;
    Promise.all([
        fetch(base).then(orOkJson),
        fetch(base + '/rate-match').then(orOkJson),
        fetch(base + '/ai-analysis').then(orOkJson)
    ]).then(function(r) {
        fillOfficerReview(r[0], r[1], r[2]);
    }).catch(function() {
        setRmText('orAlertTitle', 'Could not load review from the server (:8080)');
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
    setRmText('orSubDept', 'Civil & Construction');
    setRmText('orSubZone', detail.division || '—');
    setRmText('orSubContract', detail.contractType || '—');
    setRmText('orSubBy', detail.post || '—');

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
    fetch(RAIEC_API + '/tenders/' + id + '/send-to-review', { method: 'POST' })
        .catch(function() {})
        .finally(function() { navigateTo('step5-officer-review.html'); });
}
