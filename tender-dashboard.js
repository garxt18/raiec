// ===== RAIEC Tender Management Dashboard =====

(function() {
    var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // ===== KPI Count-Up =====
    function easeOutCubic(t) { return 1 - Math.pow(1 - t, 3); }

    function animateValue(el, target, duration) {
        var startTime = null;
        function step(ts) {
            if (!startTime) startTime = ts;
            var progress = Math.min((ts - startTime) / duration, 1);
            el.textContent = Math.round(easeOutCubic(progress) * target);
            if (progress < 1) {
                requestAnimationFrame(step);
            } else {
                el.textContent = target;
            }
        }
        requestAnimationFrame(step);
    }

    // ===== Scroll Reveal + count-up trigger =====
    var revealEls = document.querySelectorAll('.td-reveal');
    var countedStats = false;

    if (reduceMotion) {
        revealEls.forEach(function(el) { el.classList.add('in'); });
        document.querySelectorAll('.td-stat-value').forEach(function(el) {
            el.textContent = el.getAttribute('data-count');
        });
    } else {
        var observer = new IntersectionObserver(function(entries) {
            entries.forEach(function(entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add('in');

                    // NOTE: stat count-up is now driven by the API setter (setStat),
                    // which is the single authoritative writer. This avoids the race
                    // where a scroll-triggered animation overwrote the real value with 0.
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.15, rootMargin: '0px 0px -40px 0px' });

        revealEls.forEach(function(el) { observer.observe(el); });
    }

    // ===== Reset Filters =====
    var resetBtn = document.getElementById('resetFilters');
    if (resetBtn) {
        resetBtn.addEventListener('click', function() {
            document.querySelectorAll('.td-input').forEach(function(input) {
                if (!input.readOnly) input.value = '';
            });
            document.querySelectorAll('.td-select').forEach(function(select) {
                select.selectedIndex = 0;
            });
        });
    }

    // ===== Pagination =====
    document.querySelectorAll('.td-page-btn').forEach(function(btn) {
        btn.addEventListener('click', function() {
            var label = btn.textContent.trim();
            if (label === 'Previous' || label === 'Next') return;
            document.querySelectorAll('.td-page-btn').forEach(function(b) {
                if (b.textContent.trim() !== 'Previous' && b.textContent.trim() !== 'Next') {
                    b.classList.remove('active');
                }
            });
            btn.classList.add('active');
        });
    });
})();


// ===== LAR Intelligence Center (dynamic, data-driven) =====
(function() {
    var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    /* -------------------------------------------------------------------
     * DATA LAYER
     * All values below are DEMO/PLACEHOLDER data only.
     * Replace `LARData.records`, `.insights`, `.updates` with a backend
     * API/database response in the future. The UI renders entirely from
     * these structures, so no markup changes are needed when wiring a API.
     * ----------------------------------------------------------------- */
    var LARData = {
        records: [
            { itemCode: 'USSOR-C-001',   description: 'Earthwork in excavation', value: 85.50,  unit: 'Cum', updatedOn: '2026-06-15' },
            { itemCode: 'USSOR-C-014',   description: 'Cement Concrete M15',     value: 4250,   unit: 'Cum', updatedOn: '2026-06-10' },
            { itemCode: 'LAR-JP-2024-07',description: 'Reinforcement Steel',      value: 72800,  unit: 'MT',  updatedOn: '2026-06-08' },
            { itemCode: 'DSR-E-008',     description: 'GI Pipe 25mm',            value: 186,    unit: 'm',   updatedOn: '2026-06-05' },
            { itemCode: 'LAR-JU-2024-11',description: 'Bitumen VG-30',           value: 58400,  unit: 'MT',  updatedOn: '2026-06-02' },
            { itemCode: 'USSOR-C-089',   description: 'Brick Masonry CM 1:6',    value: 3820,   unit: 'Cum', updatedOn: '2026-05-28' }
        ],
        totalRecords: 1247,
        insights: [
            { label: 'Highest Increase', value: '+8.3%',     sub: 'Steel Fe-500',       accent: 'green' },
            { label: 'Most Used Item',   value: 'CC M-20',   sub: 'Used in 47 tenders', accent: 'blue' },
            { label: 'AI Confidence',    value: '94.2%',     sub: 'High accuracy',      accent: 'purple' },
            { label: 'Last Auto Sync',   value: '15 Jun',    sub: '14:32 IST',          accent: 'cyan' }
        ],
        updates: [
            { dot: 'green',  title: 'Steel Reinforcement Added', sub: '₹72,800 / MT',  date: '10 Jun 2025' },
            { dot: 'blue',   title: 'Cement Rate Updated',       sub: '₹4,250 / bag',  date: '15 Jun 2025' },
            { dot: 'yellow', title: 'Bitumen Rate Revised',      sub: '₹58,400 / MT',  date: '05 Jun 2025' },
            { dot: 'purple', title: 'Concrete Mix M25',          sub: '₹180 / cum',    date: '02 Jun 2025' }
        ]
    };

    // Expose for future backend integration / debugging
    window.LARData = LARData;

    /* ---------------- Helpers ---------------- */
    function formatINR(num) {
        // Indian number grouping
        var parts = num.toString().split('.');
        var intPart = parts[0];
        var lastThree = intPart.slice(-3);
        var rest = intPart.slice(0, -3);
        if (rest) lastThree = ',' + lastThree;
        var grouped = rest.replace(/\B(?=(\d{2})+(?!\d))/g, ',') + lastThree;
        return parts[1] ? grouped + '.' + parts[1] : grouped;
    }

    function formatDate(iso) {
        var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
        var d = new Date(iso);
        if (isNaN(d)) return iso;
        return d.getDate().toString().padStart(2, '0') + ' ' + months[d.getMonth()] + ' ' + d.getFullYear();
    }

    /* ---------------- Render functions ---------------- */
    var tableBody = document.getElementById('larTableBody');
    if (!tableBody) return; // LAR section not on this page

    var state = { search: '', category: '', sortKey: null, sortDir: 1 };

    function getVisibleRecords() {
        var rows = LARData.records.slice();
        // Search
        if (state.search) {
            var q = state.search.toLowerCase();
            rows = rows.filter(function(r) {
                return r.itemCode.toLowerCase().indexOf(q) !== -1 ||
                       r.description.toLowerCase().indexOf(q) !== -1;
            });
        }
        // Category filter (prefix of item code)
        if (state.category) rows = rows.filter(function(r) { return r.itemCode.indexOf(state.category) === 0; });
        // Sort
        if (state.sortKey) {
            rows.sort(function(a, b) {
                var x = a[state.sortKey], y = b[state.sortKey];
                if (typeof x === 'string') { x = x.toLowerCase(); y = y.toLowerCase(); }
                if (x < y) return -1 * state.sortDir;
                if (x > y) return 1 * state.sortDir;
                return 0;
            });
        }
        return rows;
    }

    function renderTable() {
        var rows = getVisibleRecords();
        if (rows.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="5" class="lar-empty">No matching LAR records found.</td></tr>';
            return;
        }
        tableBody.innerHTML = rows.map(function(r) {
            return '<tr>' +
                '<td class="td-tender-no">' + r.itemCode + '</td>' +
                '<td class="td-tender-title">' + r.description + '</td>' +
                '<td class="lar-value">₹' + formatINR(r.value) + '</td>' +
                '<td>' + r.unit + '</td>' +
                '<td>' + formatDate(r.updatedOn) + '</td>' +
            '</tr>';
        }).join('');
    }

    function renderInsights() {
        var grid = document.getElementById('larInsightsGrid');
        if (!grid) return;
        grid.innerHTML = LARData.insights.map(function(i) {
            return '<div class="lar-insight" data-accent="' + i.accent + '">' +
                '<div class="lar-insight-label">' + i.label + '</div>' +
                '<div class="lar-insight-value">' + i.value + '</div>' +
                '<div class="lar-insight-sub">' + i.sub + '</div>' +
            '</div>';
        }).join('');
    }

    function renderUpdates() {
        var list = document.getElementById('larUpdatesList');
        if (!list) return;
        var dotColor = { green:'#34d399', blue:'#60a5fa', yellow:'#fbbf24', purple:'#a78bfa' };
        list.innerHTML = LARData.updates.map(function(u) {
            return '<div class="td-notif-item">' +
                '<div class="td-notif-icon" style="background:rgba(255,255,255,0.04)">' +
                    '<span style="width:10px;height:10px;border-radius:50%;background:' + (dotColor[u.dot]||'#60a5fa') + ';display:block;box-shadow:0 0 8px ' + (dotColor[u.dot]||'#60a5fa') + '"></span>' +
                '</div>' +
                '<div class="td-notif-content">' +
                    '<div class="td-notif-title">' + u.title + '</div>' +
                    '<div class="td-notif-sub">' + u.sub + '</div>' +
                '</div>' +
                '<div class="td-notif-time">' + u.date + '</div>' +
            '</div>';
        }).join('');
    }

    function updateTotalCount() {
        var el = document.getElementById('larTotalCount');
        if (el) el.textContent = formatINR(LARData.totalRecords);
    }

    /* ---------------- CRUD API (reusable, future-ready) ---------------- */
    var LAR = {
        getAll: function() { return LARData.records.slice(); },
        add: function(record) {
            LARData.records.unshift(record);
            LARData.totalRecords += 1;
            renderTable();
            updateTotalCount();
            return record;
        },
        update: function(itemCode, changes) {
            var rec = LARData.records.filter(function(r) { return r.itemCode === itemCode; })[0];
            if (rec) { Object.keys(changes).forEach(function(k) { rec[k] = changes[k]; }); renderTable(); }
            return rec;
        },
        remove: function(itemCode) {
            LARData.records = LARData.records.filter(function(r) { return r.itemCode !== itemCode; });
            renderTable();
            updateTotalCount();
        },
        setData: function(records, total) {
            LARData.records = records || [];
            if (typeof total === 'number') LARData.totalRecords = total;
            renderTable();
            updateTotalCount();
        }
    };
    window.LAR = LAR;

    /* ---------------- Wire up controls ---------------- */
    var searchInput = document.getElementById('larSearch');
    var catFilter = document.getElementById('larCatFilter');

    if (searchInput) searchInput.addEventListener('input', function() { state.search = this.value; renderTable(); });
    if (catFilter) catFilter.addEventListener('change', function() { state.category = this.value; renderTable(); });

    // Sortable headers
    document.querySelectorAll('.lar-table thead th[data-sort]').forEach(function(th) {
        th.addEventListener('click', function() {
            var key = th.getAttribute('data-sort');
            if (state.sortKey === key) {
                state.sortDir *= -1;
            } else {
                state.sortKey = key;
                state.sortDir = 1;
            }
            document.querySelectorAll('.lar-table thead th').forEach(function(h) {
                h.classList.remove('sorted-asc', 'sorted-desc');
            });
            th.classList.add(state.sortDir === 1 ? 'sorted-asc' : 'sorted-desc');
            renderTable();
        });
    });

    // Add New LAR (demo prompt-based; replace with modal/form when backend ready)
    var addBtn = document.getElementById('larAddBtn');
    if (addBtn) {
        addBtn.addEventListener('click', function() {
            var code = prompt('New LAR Item Code (e.g. USSOR-C-100):');
            if (!code) return;
            var desc = prompt('Description:') || 'Untitled item';
            var value = parseFloat(prompt('LAR Value (₹):')) || 0;
            var unit = prompt('Unit (Cum / MT / m):') || 'Cum';
            LAR.add({
                itemCode: code,
                description: desc,
                value: value,
                unit: unit,
                updatedOn: new Date().toISOString().slice(0, 10)
            });
        });
    }

    /* ---------------- KPI count-up ---------------- */
    function easeOutCubic(t) { return 1 - Math.pow(1 - t, 3); }
    function animateKpi(el) {
        var target = parseInt(el.getAttribute('data-count'), 10);
        var useComma = el.getAttribute('data-format') === 'comma';
        if (reduceMotion) { el.textContent = useComma ? formatINR(target) : target; return; }
        var startTime = null;
        function step(ts) {
            if (!startTime) startTime = ts;
            var p = Math.min((ts - startTime) / 1000, 1);
            var v = Math.round(easeOutCubic(p) * target);
            el.textContent = useComma ? formatINR(v) : v;
            if (p < 1) requestAnimationFrame(step);
            else el.textContent = useComma ? formatINR(target) : target;
        }
        requestAnimationFrame(step);
    }

    // LAR KPI count-up is now driven by the API setter (setLarKpi) as the single
    // authoritative writer, so no scroll-triggered animation here (avoids the value race).

    /* ---------------- Initial render ---------------- */
    renderTable();
    renderInsights();
    renderUpdates();
    updateTotalCount();
})();


// ===== Live Tender Results (from backend API) =====
(function() {
    var API = (window.RAIEC_CONFIG && window.RAIEC_CONFIG.apiBase) || 'http://localhost:8080/api';
    var body = document.getElementById('tenderResultsBody');
    if (!body) return;

    var allTenders = [];

    fetch(API + '/tenders')
        .then(function(r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function(list) { allTenders = Array.isArray(list) ? list : []; applyFilters(); })
        .catch(function() { /* server offline: keep the static demo rows as a fallback */ });

    // ----- Search & filter -----
    function val(id) { var el = document.getElementById(id); return el ? el.value.trim() : ''; }

    function currentPredicate() {
        var no = val('filterNo').toLowerCase();
        var title = val('filterTitle').toLowerCase();
        var status = val('filterStatus');
        return function(t) {
            if (no && (t.tenderNo || '').toLowerCase().indexOf(no) === -1) return false;
            if (title && (t.nameOfWork || '').toLowerCase().indexOf(title) === -1) return false;
            if (status && status !== 'All Statuses' && mapStatus(t.status).label !== status) return false;
            return true;
        };
    }

    function applyFilters() { renderTenders(allTenders.filter(currentPredicate())); }

    var searchBtn = document.getElementById('searchTenderBtn');
    if (searchBtn) searchBtn.addEventListener('click', applyFilters);
    ['filterNo', 'filterTitle'].forEach(function(id) {
        var el = document.getElementById(id);
        if (el) el.addEventListener('keydown', function(e) { if (e.key === 'Enter') applyFilters(); });
    });
    var statusSel = document.getElementById('filterStatus');
    if (statusSel) statusSel.addEventListener('change', applyFilters);
    var resetBtn2 = document.getElementById('resetFilters');
    // The generic reset (other block) clears the inputs; re-render all just after.
    if (resetBtn2) resetBtn2.addEventListener('click', function() { setTimeout(applyFilters, 0); });

    // ----- Export filtered results to CSV -----
    var exportBtn = document.getElementById('exportResultsBtn');
    if (exportBtn) exportBtn.addEventListener('click', function() {
        var rows = allTenders.filter(currentPredicate());
        if (!rows.length) { alert('No tenders to export.'); return; }
        var header = ['Tender No', 'Tender Title', 'Est. Cost (INR)', 'Status', 'Closing Date'];
        var lines = [header.join(',')];
        rows.forEach(function(t) {
            lines.push([
                csv(t.tenderNo),
                csv(t.nameOfWork),
                t.advertisedValue != null ? t.advertisedValue : '',
                csv(mapStatus(t.status).label),
                csv(formatDate(t.closingDateTime))
            ].join(','));
        });
        var blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = 'raiec-tenders.csv';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    });

    function csv(s) {
        s = (s == null ? '' : String(s));
        return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    }

    function renderTenders(list) {
        var meta = document.getElementById('tenderResultsMeta');
        if (meta) meta.textContent = 'Showing ' + list.length + ' of ' + allTenders.length + ' Results';
        if (!list.length) {
            var msg = allTenders.length ? 'No tenders match your filters.' : 'No tenders uploaded yet. Use “Upload Estimate” to add one.';
            body.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:24px;color:var(--text-secondary)">' + msg + '</td></tr>';
            return;
        }
        body.innerHTML = list.map(rowHtml).join('');
    }

    function rowHtml(t) {
        var st = mapStatus(t.status);
        return '<tr>' +
            '<td class="td-tender-no">' + esc(t.tenderNo) + '</td>' +
            '<td class="td-tender-title">' + esc(t.nameOfWork || '') + '</td>' +
            '<td class="td-cost">' + formatCr(t.advertisedValue) + '</td>' +
            '<td><span class="td-pill ' + st.cls + '">' + st.label + '</span></td>' +
            '<td>' + formatDate(t.closingDateTime) + '</td>' +
            '<td>' + dueText(t.closingDateTime) + '</td>' +
            '<td><div class="td-row-actions">' +
                '<button class="td-action-btn" data-tooltip="View Tender" aria-label="View Tender" onclick="viewTender(' + t.id + ')"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg></button>' +
                '<button class="td-action-btn" data-tooltip="Scan / Upload Estimate" aria-label="Scan / Upload Estimate" onclick="window.location.href=\'upload/step1-upload.html\'"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg></button>' +
            '</div></td>' +
        '</tr>';
    }

    function mapStatus(s) {
        switch (s) {
            case 'OFFICER_REVIEW': return { label: 'Officer Review', cls: 'td-pill-review' };
            case 'APPROVED': return { label: 'Finalized', cls: 'td-pill-final' };
            case 'REJECTED': return { label: 'Closed', cls: 'td-pill-closed' };
            case 'UPLOADED':
            case 'OCR_EXTRACTED':
            case 'RATE_MATCHED':
            case 'AI_ANALYZED': return { label: 'Under Evaluation', cls: 'td-pill-eval' };
            default: return { label: s || '—', cls: 'td-pill-open' };
        }
    }

    function formatCr(v) {
        if (v == null) return '—';
        var n = Number(v);
        if (isNaN(n)) return '—';
        return (n / 10000000).toFixed(2) + ' Cr';
    }

    var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    function formatDate(iso) {
        if (!iso) return '—';
        var d = new Date(iso);
        if (isNaN(d)) return '—';
        return d.getDate().toString().padStart(2, '0') + ' ' + MONTHS[d.getMonth()] + ' ' + d.getFullYear();
    }

    function dueText(iso) {
        if (!iso) return '—';
        var d = new Date(iso);
        if (isNaN(d)) return '—';
        var days = Math.ceil((d - new Date()) / (1000 * 60 * 60 * 24));
        if (days < 0) return 'Closed';
        if (days === 0) return 'Today';
        return days + ' Days';
    }

    function esc(s) {
        if (s == null) return '';
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
})();

// View a tender's extracted items (used by the results table View action)
function viewTender(id) {
    try { sessionStorage.setItem('raiec_tenderId', JSON.stringify(id)); } catch (e) {}
    window.location.href = 'upload/step2-ocr-extract.html';
}



// ===== Live dashboard stats + recent notifications (from backend API) =====
(function() {
    var API = (window.RAIEC_CONFIG && window.RAIEC_CONFIG.apiBase) || 'http://localhost:8080/api';

    fetch(API + '/tenders/stats')
        .then(function(r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function(s) { applyStats(s); })
        .catch(function() { /* server offline: keep static demo numbers */ });

    function setStat(id, val) {
        var el = document.getElementById(id);
        if (!el) return;
        var target = (val == null ? 0 : Number(val));
        el.setAttribute('data-count', target);
        animateStat(el, target, 900);
    }

    // Single authoritative count-up for a stat box: animates 0 -> target and ends on target.
    function animateStat(el, target, duration) {
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            el.textContent = target;
            return;
        }
        var start = null;
        function ease(t) { return 1 - Math.pow(1 - t, 3); }
        function step(ts) {
            if (!start) start = ts;
            var p = Math.min((ts - start) / duration, 1);
            el.textContent = Math.round(ease(p) * target);
            if (p < 1) requestAnimationFrame(step);
            else el.textContent = target;
        }
        requestAnimationFrame(step);
    }

    function applyStats(s) {
        setStat('statTotal', s.total);
        setStat('statActive', s.active);
        setStat('statFinalized', s.finalized);
        setStat('statUnderReview', s.underReview);
        setStat('statClosed', s.closed);
        renderNotifications(s.recent || []);
    }

    function renderNotifications(list) {
        var box = document.getElementById('recentNotifList');
        if (!box) return;
        if (!list.length) {
            box.innerHTML = '<div class="td-notif-item"><div class="td-notif-content"><div class="td-notif-sub">No recent activity yet.</div></div></div>';
            return;
        }
        box.innerHTML = list.map(function(a) {
            var st = statusInfo(a.status);
            return '<div class="td-notif-item">' +
                '<div class="td-notif-icon ' + st.icon + '">' + st.svg + '</div>' +
                '<div class="td-notif-content">' +
                    '<div class="td-notif-title">' + esc(a.tenderNo) + ' — ' + st.label + '</div>' +
                    '<div class="td-notif-sub">' + esc(truncate(a.nameOfWork, 60)) + '</div>' +
                '</div>' +
                '<div class="td-notif-time">' + relTime(a.at) + '</div>' +
            '</div>';
        }).join('');
    }

    function statusInfo(s) {
        switch (s) {
            case 'APPROVED': return { label: 'Approved', icon: 'td-icon-green', svg: checkSvg() };
            case 'REJECTED': return { label: 'Rejected', icon: 'td-icon-gray', svg: docSvg() };
            case 'OFFICER_REVIEW': return { label: 'Sent to officer review', icon: 'td-icon-yellow', svg: docSvg() };
            case 'RATE_MATCHED': return { label: 'Rate matched', icon: 'td-icon-purple', svg: docSvg() };
            case 'AI_ANALYZED': return { label: 'AI analysis done', icon: 'td-icon-purple', svg: docSvg() };
            case 'OCR_EXTRACTED': return { label: 'Extracted', icon: 'td-icon-blue', svg: docSvg() };
            default: return { label: 'Uploaded', icon: 'td-icon-blue', svg: docSvg() };
        }
    }

    function checkSvg() {
        return '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M8 12l3 3 5-5"/></svg>';
    }
    function docSvg() {
        return '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>';
    }
    function truncate(s, n) { s = s || ''; return s.length > n ? s.slice(0, n - 1) + '…' : s; }
    function relTime(iso) {
        if (!iso) return '';
        var d = new Date(iso);
        if (isNaN(d)) return '';
        var sec = (Date.now() - d.getTime()) / 1000;
        if (sec < 60) return 'Just now';
        var m = Math.floor(sec / 60);
        if (m < 60) return m + ' Min Ago';
        var h = Math.floor(m / 60);
        if (h < 24) return h + ' Hour' + (h > 1 ? 's' : '') + ' Ago';
        var dd = Math.floor(h / 24);
        return dd + ' Day' + (dd > 1 ? 's' : '') + ' Ago';
    }
    function esc(s) {
        if (s == null) return '';
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
})();



// ===== Live LAR records + KPIs + recent updates (LAR Intelligence page) =====
(function() {
    if (!document.getElementById('larTableBody')) return;
    var API = (window.RAIEC_CONFIG && window.RAIEC_CONFIG.apiBase) || 'http://localhost:8080/api';

    fetch(API + '/lar')
        .then(function(r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function(list) {
            var mapped = list.map(function(r) {
                return {
                    itemCode: r.larCode,
                    description: r.description,
                    value: r.rate != null ? Number(r.rate) : 0,
                    unit: r.unit || '',
                    updatedOn: r.approvedOn || ''
                };
            });
            if (window.LAR && typeof window.LAR.setData === 'function') {
                window.LAR.setData(mapped, mapped.length);
            }

            var now = new Date();
            var newThisMonth = 0;
            var sources = {};
            list.forEach(function(r) {
                if (r.approvedOn) {
                    var d = new Date(r.approvedOn);
                    if (!isNaN(d) && d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear()) newThisMonth++;
                }
                if (r.sourceTenderNo) sources[r.sourceTenderNo] = true;
            });
            setLarKpi('larKpiTotal', list.length);
            setLarKpi('larKpiNew', newThisMonth);
            setLarKpi('larKpiAuto', list.length);
            setLarKpi('larKpiSources', Object.keys(sources).length);

            renderLarUpdates(list);
            renderRealInsights(list);
        })
        .catch(function() { /* server offline: keep demo records */ });

    function setLarKpi(id, val) {
        var el = document.getElementById(id);
        if (!el) return;
        var target = (val == null ? 0 : Number(val));
        el.setAttribute('data-count', target);
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            el.textContent = target.toLocaleString('en-IN');
            return;
        }
        var start = null;
        function ease(t) { return 1 - Math.pow(1 - t, 3); }
        function step(ts) {
            if (!start) start = ts;
            var p = Math.min((ts - start) / 900, 1);
            el.textContent = Math.round(ease(p) * target).toLocaleString('en-IN');
            if (p < 1) requestAnimationFrame(step);
            else el.textContent = target.toLocaleString('en-IN');
        }
        requestAnimationFrame(step);
    }

    function renderLarUpdates(list) {
        var box = document.getElementById('larUpdatesList');
        if (!box) return;
        var sorted = list.slice().sort(function(a, b) {
            return String(b.approvedOn || '').localeCompare(String(a.approvedOn || ''));
        }).slice(0, 5);
        if (!sorted.length) {
            box.innerHTML = '<div class="td-notif-item"><div class="td-notif-content"><div class="td-notif-sub">No LAR updates yet.</div></div></div>';
            return;
        }
        var dots = ['#34d399', '#60a5fa', '#fbbf24', '#a78bfa', '#22d3ee'];
        box.innerHTML = sorted.map(function(r, i) {
            var rate = r.rate != null ? Number(r.rate).toLocaleString('en-IN') : '—';
            return '<div class="td-notif-item">' +
                '<div class="td-notif-icon" style="background:rgba(255,255,255,0.04)"><span style="width:10px;height:10px;border-radius:50%;background:' + dots[i % dots.length] + ';display:block"></span></div>' +
                '<div class="td-notif-content">' +
                    '<div class="td-notif-title">' + larEsc(larTrunc(r.description || r.larCode, 46)) + '</div>' +
                    '<div class="td-notif-sub">₹' + rate + (r.unit ? ' / ' + r.unit : '') + '</div>' +
                '</div>' +
                '<div class="td-notif-time">' + larDate(r.approvedOn) + '</div>' +
            '</div>';
        }).join('');
    }

    function renderRealInsights(list) {
        var grid = document.getElementById('larInsightsGrid');
        if (!grid) return;
        var withRate = list.filter(function (r) { return r.rate != null; });
        var highest = withRate.slice().sort(function (a, b) { return Number(b.rate) - Number(a.rate); })[0];
        var lowest = withRate.slice().sort(function (a, b) { return Number(a.rate) - Number(b.rate); })[0];
        var sources = {};
        var latest = null;
        list.forEach(function (r) {
            if (r.sourceTenderNo) sources[r.sourceTenderNo] = true;
            if (r.approvedOn && (!latest || String(r.approvedOn) > String(latest))) latest = r.approvedOn;
        });
        var insights = [
            { label: 'Highest Rate', value: highest ? '\u20B9' + Number(highest.rate).toLocaleString('en-IN') : '\u2014', sub: highest ? larTrunc(highest.description, 26) : 'No data yet', accent: 'green' },
            { label: 'Lowest Rate', value: lowest ? '\u20B9' + Number(lowest.rate).toLocaleString('en-IN') : '\u2014', sub: lowest ? larTrunc(lowest.description, 26) : 'No data yet', accent: 'blue' },
            { label: 'Source Tenders', value: String(Object.keys(sources).length), sub: 'Approved tenders', accent: 'purple' },
            { label: 'Last Updated', value: latest ? larDate(latest) : '\u2014', sub: 'Most recent entry', accent: 'cyan' }
        ];
        grid.innerHTML = insights.map(function (i) {
            return '<div class="lar-insight" data-accent="' + i.accent + '">' +
                '<div class="lar-insight-label">' + larEsc(i.label) + '</div>' +
                '<div class="lar-insight-value">' + larEsc(i.value) + '</div>' +
                '<div class="lar-insight-sub">' + larEsc(i.sub) + '</div>' +
            '</div>';
        }).join('');
    }

    var MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    function larDate(iso) {
        if (!iso) return '';
        var d = new Date(iso);
        if (isNaN(d)) return '';
        return d.getDate().toString().padStart(2, '0') + ' ' + MON[d.getMonth()] + ' ' + d.getFullYear();
    }
    function larTrunc(s, n) { s = s || ''; return s.length > n ? s.slice(0, n - 1) + '…' : s; }
    function larEsc(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
})();
