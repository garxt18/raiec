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

    // This block lives in its own IIFE, so it needs its own handle on the API base
    // and its own escaper — larEsc belongs to the live-data block further down.
    var API = (window.RAIEC_CONFIG && window.RAIEC_CONFIG.apiBase) || 'http://localhost:8080/api';

    function larEscLocal(v) {
        return String(v == null ? '' : v)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    /* ---------------------------------------------------------------------
       Add a rate by hand
       Was four chained prompt() boxes writing to a client-side array, so
       nothing survived a refresh. It is a real form posting to the API now:
       a hand-entered rate becomes the benchmark future tenders are vetted
       against, so it deserves the same care as one earned by approval.
       ------------------------------------------------------------------- */
    var addBtn = document.getElementById('larAddBtn');
    if (addBtn) addBtn.addEventListener('click', openAddLarDialog);

    function openAddLarDialog() {
        var isAdmin = (localStorage.getItem('raiec_role') || '').toUpperCase() === 'ADMIN';
        var ov = document.createElement('div');
        ov.className = 'raiec-loader open lar-modal';
        ov.innerHTML =
            '<div class="lar-dialog" role="dialog" aria-modal="true" aria-labelledby="larDlgTitle">' +
              '<button type="button" class="lar-dialog-close" id="larClose" aria-label="Close">&times;</button>' +
              '<h3 id="larDlgTitle">Add a last accepted rate</h3>' +
              '<p class="lar-dialog-sub">Use this for work accepted outside RAIEC. The dataset keeps the ' +
              'lowest rate per item, so a higher rate for an item already held will be refused.</p>' +
              (isAdmin ? '' : '<p class="lar-dialog-warn">You are signed in as an officer. Only an admin can add rates.</p>') +
              '<div class="lar-form">' +
                larField('Description', 'larDesc', 'text', 'What the rate is for — this is what future tenders are matched against', true) +
                '<div class="lar-form-row">' +
                  larField('Rate (₹)', 'larRate', 'number', '0.00', true) +
                  larField('Unit', 'larUnit', 'text', 'cum / sqm / metre / each', false) +
                '</div>' +
                '<div class="lar-form-row">' +
                  larField('Division', 'larDivision', 'text', 'e.g. Bikaner', false) +
                  larField('Accepted on', 'larDate', 'date', '', false) +
                '</div>' +
                larField('Source tender no. (optional)', 'larTender', 'text', 'e.g. 232-25-26', false) +
              '</div>' +
              '<p class="lar-dialog-error" id="larError" hidden></p>' +
              '<div class="lar-dialog-actions">' +
                '<button type="button" class="td-btn" id="larCancel">Cancel</button>' +
                '<button type="button" class="td-btn td-btn-primary" id="larSave"' + (isAdmin ? '' : ' disabled') + '>Add rate</button>' +
              '</div>' +
            '</div>';
        document.body.appendChild(ov);
        document.documentElement.classList.add('modal-open');

        var dateEl = document.getElementById('larDate');
        if (dateEl) {
            dateEl.value = new Date().toISOString().slice(0, 10);
            dateEl.max = dateEl.value;          // a rate cannot be accepted in the future
        }
        setTimeout(function () { var d = document.getElementById('larDesc'); if (d) d.focus(); }, 60);

        function close() {
            ov.classList.remove('open');
            document.documentElement.classList.remove('modal-open');
            setTimeout(function () { ov.remove(); }, 260);
        }
        document.getElementById('larClose').addEventListener('click', close);
        document.getElementById('larCancel').addEventListener('click', close);
        ov.addEventListener('mousedown', function (e) { if (e.target === ov) close(); });
        document.addEventListener('keydown', function esc(e) {
            if (e.key === 'Escape') { close(); document.removeEventListener('keydown', esc); }
        });

        var saveBtn = document.getElementById('larSave');
        if (saveBtn) saveBtn.addEventListener('click', function () {
            var err = document.getElementById('larError');
            var body = {
                description: document.getElementById('larDesc').value.trim(),
                rate: parseFloat(document.getElementById('larRate').value),
                unit: document.getElementById('larUnit').value.trim() || null,
                division: document.getElementById('larDivision').value.trim() || null,
                sourceTenderNo: document.getElementById('larTender').value.trim() || null,
                approvedOn: document.getElementById('larDate').value || null
            };

            // Catch the obvious mistakes here so the officer is not waiting on a round
            // trip to be told the description is too short.
            if (body.description.length < 5) {
                return fail(err, 'Give a description of at least 5 characters — it is what future tenders are matched against.');
            }
            if (!(body.rate > 0)) {
                return fail(err, 'Enter a rate greater than zero.');
            }

            err.hidden = true;
            saveBtn.disabled = true;
            saveBtn.textContent = 'Saving…';
            raiecFetch(API + '/lar', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            }).then(function (res) {
                return res.json().then(function (b) { return { ok: res.ok, body: b }; });
            }).then(function (r) {
                if (!r.ok) throw new Error(r.body && r.body.message ? r.body.message : 'Could not save the rate.');
                close();
                reloadLar();                    // show the dataset as it now stands
            }).catch(function (e) {
                fail(err, e.message);
                saveBtn.disabled = false;
                saveBtn.textContent = 'Add rate';
            });
        });
    }

    function fail(el, msg) {
        el.hidden = false;
        el.textContent = msg;
    }

    function larField(label, id, type, placeholder, required) {
        return '<label class="lar-field"><span>' + larEscLocal(label) + (required ? ' <b>*</b>' : '') + '</span>' +
               '<input id="' + id + '" type="' + type + '" placeholder="' + larEscLocal(placeholder) + '"' +
               (type === 'number' ? ' step="0.01" min="0"' : '') + '></label>';
    }

    // Re-pull the dataset so the table, KPIs and recent list all agree.
    function reloadLar() {
        raiecFetch(API + '/lar')
            .then(function (r) { return r.json(); })
            .then(function (list) {
                var mapped = list.map(function (r) {
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
                renderLarUpdates(list);
                var total = document.getElementById('larKpiTotal');
                if (total) total.textContent = list.length.toLocaleString('en-IN');
            })
            .catch(function () { /* leave the current view in place */ });
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

    raiecFetch(API + '/tenders')
        .then(function(r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function(list) {
            allTenders = Array.isArray(list) ? list : [];
            renderTabs();
            renderCalendar();
            wireStatTiles();
            initCalendarToggle();
            applyFilters();
        })
        .catch(function() { /* server offline: keep the static demo rows as a fallback */ });

    // ----- Search & filter -----
    function val(id) { var el = document.getElementById(id); return el ? el.value.trim() : ''; }

    // Tab, calendar day and the search fields all narrow the same list, so they live in
    // one place rather than three competing render paths.
    var tdTab = 'all';
    var tdDay = null;

    function tenderDate(t) {
        var d = t.closingDateTime || t.createdAt;
        return d ? new Date(d) : null;
    }

    function currentPredicate() {
        var no = val('filterNo').toLowerCase();
        var title = val('filterTitle').toLowerCase();
        var status = val('filterStatus');
        var from = val('filterFrom');
        var to = val('filterTo');
        return function(t) {
            if (no && (t.tenderNo || '').toLowerCase().indexOf(no) === -1) return false;
            if (title && (t.nameOfWork || '').toLowerCase().indexOf(title) === -1) return false;
            if (status && status !== 'All Statuses' && mapStatus(t.status).label !== status) return false;
            if (tdTab !== 'all' && mapStatus(t.status).group !== tdTab) return false;

            if (from || to || tdDay) {
                var d = tenderDate(t);
                if (!d) return false;
                var iso = d.toISOString().slice(0, 10);
                if (from && iso < from) return false;
                if (to && iso > to) return false;
                if (tdDay && iso !== tdDay) return false;
            }
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
    ['filterFrom', 'filterTo'].forEach(function (id) {
        var el = document.getElementById(id);
        if (el) el.addEventListener('change', applyFilters);
    });

    var statusSel = document.getElementById('filterStatus');
    if (statusSel) statusSel.addEventListener('change', applyFilters);
    var resetBtn2 = document.getElementById('resetFilters');
    // The generic reset (other block) clears the inputs; re-render all just after.
    if (resetBtn2) resetBtn2.addEventListener('click', function() { setTimeout(applyFilters, 0); });



    /* ---------------------------------------------------------------------
       Status tabs
       Approve / reject / request-info live here rather than in the upload
       flow: the upload flow is for one tender at a time, while deciding what
       still needs attention is a question about the whole queue.
       ------------------------------------------------------------------- */
    var TD_TABS = [
        { key: 'all',        label: 'All' },
        { key: 'evaluating', label: 'Under evaluation' },
        { key: 'review',     label: 'Officer review' },
        { key: 'info',       label: 'Info requested' },
        { key: 'approved',   label: 'Approved' },
        { key: 'rejected',   label: 'Rejected' }
    ];

    function renderTabs() {
        var host = document.getElementById('tdTabs');
        if (!host) return;
        var counts = { all: allTenders.length };
        allTenders.forEach(function (t) {
            var g = mapStatus(t.status).group;
            counts[g] = (counts[g] || 0) + 1;
        });
        host.innerHTML = TD_TABS.map(function (tb) {
            var n = counts[tb.key] || 0;
            return '<button type="button" class="td-tab' + (tdTab === tb.key ? ' active' : '') +
                   '" data-tab="' + tb.key + '"' + (tb.key !== 'all' && n === 0 ? ' disabled' : '') + '>' +
                   esc(tb.label) + '<span class="td-tab-count">' + n + '</span></button>';
        }).join('');
        host.querySelectorAll('.td-tab').forEach(function (b) {
            b.addEventListener('click', function () {
                tdTab = b.getAttribute('data-tab');
                tdDay = null;               // a tab choice supersedes a picked day
                renderTabs();
                renderCalendar();
                applyFilters();
            });
        });
    }

    /* ---------------------------------------------------------------------
       Calendar
       Shows how much work landed on each day. Density is the point, so the
       count is what is emphasised; clicking a day filters to it.
       ------------------------------------------------------------------- */
    // Opening on the current month is useless when the tenders in hand close months
    // earlier — the officer would land on an empty grid and have to page backwards to
    // find their own data. Start on the busiest month that actually has tenders.
    var tdMonth = new Date();
    tdMonth.setDate(1);
    var tdMonthPinned = false;

    function focusCalendarOnData() {
        if (tdMonthPinned || !allTenders.length) return;
        var byMonth = {};
        allTenders.forEach(function (t) {
            var d = tenderDate(t);
            if (!d) return;
            var k = d.getFullYear() + '-' + d.getMonth();
            byMonth[k] = (byMonth[k] || 0) + 1;
        });
        var best = null, bestN = 0;
        Object.keys(byMonth).forEach(function (k) {
            if (byMonth[k] > bestN) { bestN = byMonth[k]; best = k; }
        });
        if (best) {
            var parts = best.split('-');
            tdMonth = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10), 1);
        }
        tdMonthPinned = true;   // respect the officer's paging from here on
    }

    function renderCalendar() {
        var host = document.getElementById('tdCalendar');
        if (!host) return;
        focusCalendarOnData();

        var byDay = {};
        allTenders.forEach(function (t) {
            var d = tenderDate(t);
            if (!d) return;
            var k = d.toISOString().slice(0, 10);
            (byDay[k] = byDay[k] || []).push(t);
        });

        var year = tdMonth.getFullYear(), month = tdMonth.getMonth();
        var first = new Date(year, month, 1);
        var startPad = (first.getDay() + 6) % 7;          // Monday-first
        var daysInMonth = new Date(year, month + 1, 0).getDate();
        var maxCount = Math.max.apply(null, [1].concat(Object.keys(byDay).map(function (k) { return byDay[k].length; })));
        var todayIso = new Date().toISOString().slice(0, 10);

        var cells = '';
        for (var i = 0; i < startPad; i++) cells += '<div class="td-cal-cell is-empty"></div>';
        for (var day = 1; day <= daysInMonth; day++) {
            var iso = new Date(Date.UTC(year, month, day)).toISOString().slice(0, 10);
            var onDay = byDay[iso] || [];
            var n = onDay.length;
            // Four steps rather than a continuous scale: the eye reads bands, not gradients.
            var level = n === 0 ? 0 : Math.min(4, Math.ceil((n / maxCount) * 4));

            // Name the tenders rather than only counting them: a bare number tells the
            // officer something happened but not what, so they would have to click to
            // find out. Two fit; the rest are summarised.
            var SHOWN = 2;
            var chips = onDay.slice(0, SHOWN).map(function (t) {
                return '<span class="td-cal-ref" title="' + esc(t.nameOfWork || '') + '">' +
                       esc(t.tenderNo || '\u2014') + '</span>';
            }).join('');
            if (n > SHOWN) {
                chips += '<span class="td-cal-more">+' + (n - SHOWN) + ' more\u2026</span>';
            }

            var titles = onDay.map(function (t) { return t.tenderNo; }).join(', ');
            cells += '<button type="button" class="td-cal-cell' +
                     (tdDay === iso ? ' is-selected' : '') + (iso === todayIso ? ' is-today' : '') +
                     '" data-level="' + level + '" data-day="' + iso + '"' +
                     (n === 0 ? ' disabled' : '') +
                     ' title="' + esc(titles) + '"' +
                     ' aria-label="' + day + ' \u2014 ' + n + ' tender(s): ' + esc(titles) + '">' +
                     '<span class="td-cal-num">' + day + '</span>' +
                     (n ? '<span class="td-cal-refs">' + chips + '</span>' : '') +
                     '</button>';
        }

        var monthName = tdMonth.toLocaleString('en-IN', { month: 'long', year: 'numeric' });
        host.innerHTML =
            '<div class="td-cal-head">' +
              '<button type="button" class="td-cal-nav" id="tdCalPrev" aria-label="Previous month">&#8249;</button>' +
              '<span class="td-cal-month">' + esc(monthName) + '</span>' +
              '<button type="button" class="td-cal-nav" id="tdCalNext" aria-label="Next month">&#8250;</button>' +
            '</div>' +
            '<div class="td-cal-dow">' + ['M','T','W','T','F','S','S'].map(function (d) {
                return '<span>' + d + '</span>'; }).join('') + '</div>' +
            '<div class="td-cal-grid">' + cells + '</div>' +
            (tdDay ? '<button type="button" class="td-cal-clear" id="tdCalClear">Clear ' + esc(tdDay) + '</button>' : '');

        document.getElementById('tdCalPrev').addEventListener('click', function () {
            tdMonthPinned = true;
            tdMonth.setMonth(tdMonth.getMonth() - 1); renderCalendar();
        });
        document.getElementById('tdCalNext').addEventListener('click', function () {
            tdMonthPinned = true;
            tdMonth.setMonth(tdMonth.getMonth() + 1); renderCalendar();
        });
        var clear = document.getElementById('tdCalClear');
        if (clear) clear.addEventListener('click', function () { tdDay = null; renderCalendar(); applyFilters(); });

        host.querySelectorAll('.td-cal-cell[data-day]').forEach(function (c) {
            c.addEventListener('click', function () {
                var d = c.getAttribute('data-day');
                tdDay = (tdDay === d) ? null : d;    // clicking the same day clears it
                renderCalendar();
                applyFilters();
            });
        });
    }

    /* ---------------------------------------------------------------------
       Stat tiles double as filters
       The tiles already say how many are in each state; making them inert
       means reading a number then going elsewhere to act on it.
       ------------------------------------------------------------------- */
    function wireStatTiles() {
        var map = [
            { sel: '#statTotal',       tab: 'all' },
            { sel: '#statActive',      tab: 'evaluating' },
            { sel: '#statUnderReview', tab: 'review' },
            { sel: '#statInfoReq',     tab: 'info' },
            { sel: '#statFinalized',   tab: 'approved' },
            { sel: '#statClosed',      tab: 'rejected' }
        ];
        map.forEach(function (m) {
            var el = document.querySelector(m.sel);
            var card = el && el.closest('.td-stat-card');
            if (!card) return;
            card.classList.add('is-clickable');
            card.setAttribute('role', 'button');
            card.setAttribute('tabindex', '0');
            function go() {
                tdTab = m.tab; tdDay = null;
                renderTabs(); renderCalendar(); applyFilters();
                var table = document.querySelector('.td-table-wrap');
                if (table) table.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
            card.addEventListener('click', go);
            card.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); go(); }
            });
        });
    }


    /* ---------------------------------------------------------------------
       Calendar is opt-in
       Most visits to this page are about the list. The calendar answers a
       different question, so it stays closed until asked for rather than
       pushing the table down for everyone.
       ------------------------------------------------------------------- */
    function initCalendarToggle() {
        var btn = document.getElementById('tdCalToggle');
        var card = document.getElementById('tdCalendarCard');
        var label = document.getElementById('tdCalToggleText');
        if (!btn || !card) return;

        var open = false;
        try { open = localStorage.getItem('raiec_cal_open') === '1'; } catch (e) { /* ignore */ }
        apply(open);

        btn.addEventListener('click', function () { apply(!open); });

        function apply(next) {
            open = next;
            card.hidden = !open;
            btn.setAttribute('aria-expanded', open ? 'true' : 'false');
            btn.classList.toggle('is-active', open);
            if (label) label.textContent = open ? 'Hide calendar' : 'Calendar view';
            try { localStorage.setItem('raiec_cal_open', open ? '1' : '0'); } catch (e) { /* ignore */ }
            if (open) {
                renderCalendar();
                card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
        }
    }

    // Row click / keyboard opens the tender.
    document.addEventListener('click', function (e) {
        var row = e.target.closest && e.target.closest('.td-row');
        if (!row || e.target.closest('button')) return;   // let the action buttons work
        viewTender(row.getAttribute('data-id'));
    });
    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Enter') return;
        var row = document.activeElement && document.activeElement.closest
                ? document.activeElement.closest('.td-row') : null;
        if (row) viewTender(row.getAttribute('data-id'));
    });

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
        // The row itself is the target. Hunting for a small eye icon to open a record
        // is friction the officer pays on every single tender.
        return '<tr class="td-row" data-id="' + t.id + '" data-group="' + st.group + '" tabindex="0" role="link" aria-label="Open tender ' + esc(t.tenderNo) + '">' +
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
            case 'OFFICER_REVIEW': return { label: 'Officer Review', cls: 'td-pill-review', group: 'review' };
            case 'INFO_REQUESTED': return { label: 'Info requested', cls: 'td-pill-info', group: 'info' };
            case 'APPROVED': return { label: 'Approved', cls: 'td-pill-final', group: 'approved' };
            case 'REJECTED': return { label: 'Rejected', cls: 'td-pill-closed', group: 'rejected' };
            case 'UPLOADED':
            case 'OCR_EXTRACTED':
            case 'RATE_MATCHED':
            case 'AI_ANALYZED': return { label: 'Under Evaluation', cls: 'td-pill-eval', group: 'evaluating' };
            default: return { label: s || '\u2014', cls: 'td-pill-open', group: 'other' };
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

    raiecFetch(API + '/tenders/stats')
        .then(function(r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function(s) { applyStats(s); })
        .catch(function() { /* server offline: keep static demo numbers */ });

    function setStat(id, val) {
        var el = document.getElementById(id);
        if (!el) return;
        var target = (val == null ? 0 : Number(val));
        el.setAttribute('data-count', target);
        // Same reason as setLarKpi: show the figure, then animate. A stat that only
        // appears once rAF runs is blank in a background tab.
        el.textContent = target.toLocaleString('en-IN');
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
        setStat('statInfoReq', s.infoRequested);
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

    raiecFetch(API + '/lar')
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

        // Write the real number first, then animate up to it. requestAnimationFrame does
        // not run in a background tab, so a count-up that starts from the placeholder
        // leaves the figure showing an em dash until the tab is focused. The number is
        // the point; the animation is decoration.
        el.textContent = target.toLocaleString('en-IN');
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

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

        // Ten, not five: the panel exists to give a sense of what is arriving in the
        // dataset, and five entries in a cramped box gave neither detail nor pattern.
        var sorted = list.slice().sort(function (a, b) {
            return String(b.approvedOn || '').localeCompare(String(a.approvedOn || ''));
        }).slice(0, 10);

        if (!sorted.length) {
            box.innerHTML = '<p class="lar-upd-empty">No rates recorded yet. They are added automatically when a tender is approved.</p>';
            return;
        }

        box.innerHTML = sorted.map(function (r) {
            var rate = r.rate != null ? Number(r.rate).toLocaleString('en-IN') : '\u2014';
            var src = r.sourceTenderNo ? 'from ' + larEsc(r.sourceTenderNo) : 'added manually';
            return '<article class="lar-upd">' +
                     '<div class="lar-upd-main">' +
                       '<p class="lar-upd-desc" title="' + larEsc(r.description || '') + '">' +
                         larEsc(larTrunc(r.description || r.larCode, 72)) + '</p>' +
                       '<p class="lar-upd-meta">' + src +
                         (r.division ? ' \u00b7 ' + larEsc(r.division) : '') + '</p>' +
                     '</div>' +
                     '<div class="lar-upd-side">' +
                       '<span class="lar-upd-rate">\u20b9' + rate + '</span>' +
                       '<span class="lar-upd-unit">' + (r.unit ? 'per ' + larEsc(r.unit) : '') + '</span>' +
                       '<span class="lar-upd-date">' + larDate(r.approvedOn) + '</span>' +
                     '</div>' +
                   '</article>';
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
