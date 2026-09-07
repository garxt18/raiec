// ===== Mobile Menu Toggle =====
document.addEventListener('DOMContentLoaded', function () {
    const menuBtn = document.querySelector('.mobile-menu-btn');
    const navMenu = document.querySelector('.nav-menu');

    if (menuBtn && navMenu) {
        menuBtn.addEventListener('click', function () {
            navMenu.classList.toggle('active');
            const isExpanded = navMenu.classList.contains('active');
            menuBtn.setAttribute('aria-expanded', isExpanded);
        });

        // Close menu when clicking a link
        navMenu.querySelectorAll('.nav-link').forEach(function (link) {
            link.addEventListener('click', function () {
                navMenu.classList.remove('active');
                menuBtn.setAttribute('aria-expanded', 'false');
            });
        });
    }

    // ===== Donut Chart =====
    drawDonutChart();

    // ===== Intersection Observer for Animations =====
    initScrollAnimations();
});

// ===== Donut Chart Drawing (data-driven) =====
// Canvas has no access to CSS variables, so resolve tokens at draw time. This keeps
// the chart correct when the theme is switched without reloading the page.
function cssToken(name, fallback) {
    try {
        var v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return v || fallback;
    } catch (e) { return fallback; }
}

var HOME_DONUT_COLORS = ['#3b82f6', '#06b6d4', '#8b5cf6', '#22d3ee'];
var homeDonutValues = [0, 0, 0, 0]; // [Under Evaluation, Officer Review, Finalized, Closed]; set from live stats

function drawDonutChart() {
    const canvas = document.getElementById('donutChart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    const centerX = canvas.width / 2;
    const centerY = canvas.height / 2;
    const outerRadius = 75;
    const innerRadius = 50;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const sliceColors = [
        cssToken('--accent-blue', HOME_DONUT_COLORS[0]),
        cssToken('--accent-cyan', HOME_DONUT_COLORS[1]),
        cssToken('--accent-violet', HOME_DONUT_COLORS[2]),
        cssToken('--accent-cyan-bright', HOME_DONUT_COLORS[3])
    ];
    const total = homeDonutValues.reduce(function (sum, v) { return sum + v; }, 0);
    let startAngle = -Math.PI / 2;

    if (total <= 0) {
        // Empty state: faint full ring so the chart never looks broken.
        ctx.beginPath();
        ctx.arc(centerX, centerY, outerRadius, 0, 2 * Math.PI);
        ctx.arc(centerX, centerY, innerRadius, 2 * Math.PI, 0, true);
        ctx.closePath();
        ctx.fillStyle = cssToken('--surface-3', 'rgba(148, 163, 184, 0.15)');
        ctx.fill();
    } else {
        homeDonutValues.forEach(function (value, i) {
            if (value <= 0) return;
            const sliceAngle = (value / total) * 2 * Math.PI;
            const endAngle = startAngle + sliceAngle;
            ctx.beginPath();
            ctx.arc(centerX, centerY, outerRadius, startAngle, endAngle);
            ctx.arc(centerX, centerY, innerRadius, endAngle, startAngle, true);
            ctx.closePath();
            ctx.fillStyle = sliceColors[i];
            ctx.fill();
            startAngle = endAngle;
        });
    }

    // Inner circle (hollow center)
    ctx.beginPath();
    ctx.arc(centerX, centerY, innerRadius - 2, 0, 2 * Math.PI);
    ctx.fillStyle = cssToken('--bg-card', 'rgba(30, 41, 59, 0.8)');
    ctx.fill();
}

// Update the donut slices + legend values from live status counts.
function renderHomeDonut(evalCount, reviewCount, finalCount, closedCount) {
    homeDonutValues = [evalCount || 0, reviewCount || 0, finalCount || 0, closedCount || 0];
    var total = homeDonutValues.reduce(function (sum, v) { return sum + v; }, 0);
    var ids = ['homeDonutEval', 'homeDonutReview', 'homeDonutFinal', 'homeDonutClosed'];
    ids.forEach(function (id, i) {
        var el = document.getElementById(id);
        if (!el) return;
        var v = homeDonutValues[i];
        var pct = total > 0 ? ((v / total) * 100).toFixed(1) : '0.0';
        el.textContent = v + ' (' + pct + '%)';
    });
    drawDonutChart();
}

// The donut is raster, not CSS, so it must be repainted on a theme change.
(function () {
    var root = document.documentElement;
    new MutationObserver(function () { drawDonutChart(); })
        .observe(root, { attributes: true, attributeFilter: ['data-theme'] });
    window.matchMedia('(prefers-color-scheme: light)')
        .addEventListener('change', function () { drawDonutChart(); });
})();

// ===== Scroll Animations =====
function initScrollAnimations() {
    // Respect reduced motion preferences
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const observerOptions = {
        threshold: 0.15,
        rootMargin: '0px 0px -40px 0px'
    };

    const observer = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
            if (entry.isIntersecting) {
                entry.target.classList.add('animate-in');
                observer.unobserve(entry.target);
            }
        });
    }, observerOptions);

    // Observe feature cards with staggered timing
    document.querySelectorAll('.feature-card').forEach(function (card, index) {
        card.style.opacity = '0';
        card.style.transform = 'translateY(16px)';
        card.style.transition = 'opacity 0.7s cubic-bezier(0.16, 1, 0.3, 1) ' + (index * 0.1) + 's, transform 0.7s cubic-bezier(0.16, 1, 0.3, 1) ' + (index * 0.1) + 's';
        observer.observe(card);
    });

    // Observe KPI cards with staggered timing
    document.querySelectorAll('.kpi-card').forEach(function (card, index) {
        card.style.opacity = '0';
        card.style.transform = 'translateY(12px)';
        card.style.transition = 'opacity 0.6s cubic-bezier(0.16, 1, 0.3, 1) ' + (index * 0.08) + 's, transform 0.6s cubic-bezier(0.16, 1, 0.3, 1) ' + (index * 0.08) + 's';
        observer.observe(card);
    });

    // Observe panels with staggered timing
    document.querySelectorAll('.panel').forEach(function (panel, index) {
        panel.style.opacity = '0';
        panel.style.transform = 'translateY(14px)';
        panel.style.transition = 'opacity 0.7s cubic-bezier(0.16, 1, 0.3, 1) ' + (index * 0.12) + 's, transform 0.7s cubic-bezier(0.16, 1, 0.3, 1) ' + (index * 0.12) + 's';
        observer.observe(panel);
    });

    // Observe dashboard container
    var dashboardContainer = document.querySelector('.dashboard-container');
    if (dashboardContainer) {
        dashboardContainer.style.opacity = '0';
        dashboardContainer.style.transform = 'translateY(16px)';
        dashboardContainer.style.transition = 'opacity 0.8s cubic-bezier(0.16, 1, 0.3, 1), transform 0.8s cubic-bezier(0.16, 1, 0.3, 1)';
        observer.observe(dashboardContainer);
    }
}

// Add animate-in class styles dynamically
document.addEventListener('DOMContentLoaded', function () {
    var style = document.createElement('style');
    style.textContent = '.animate-in { opacity: 1 !important; transform: translateY(0) !important; }';
    document.head.appendChild(style);
});

// ===== Navbar scroll effect =====
var lastScrollY = 0;
window.addEventListener('scroll', function () {
    var navbar = document.querySelector('.navbar');
    var scrollY = window.scrollY;
    if (scrollY > 50) {
        navbar.style.background = 'rgba(10, 10, 26, 0.95)';
        navbar.style.boxShadow = '0 1px 20px rgba(0, 0, 0, 0.2)';
    } else {
        navbar.style.background = 'rgba(10, 10, 26, 0.85)';
        navbar.style.boxShadow = 'none';
    }
    lastScrollY = scrollY;
}, { passive: true });

// ===== Tender Search Interaction =====
// Real status lookup is wired in the "Home page live data" block at the end of this file.

// ===== Feature Card Cursor Glow =====
(function() {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    
    var cards = document.querySelectorAll('.feature-card');
    cards.forEach(function(card) {
        var glow = card.querySelector('.feature-card-glow');
        if (!glow) return;

        card.addEventListener('mousemove', function(e) {
            var rect = card.getBoundingClientRect();
            var x = e.clientX - rect.left;
            var y = e.clientY - rect.top;
            glow.style.background = 'radial-gradient(circle 200px at ' + x + 'px ' + y + 'px, rgba(120, 119, 255, 0.08), rgba(59, 130, 246, 0.04), transparent 70%)';
        });

        card.addEventListener('mouseleave', function() {
            glow.style.background = 'transparent';
        });
    });
})();

// ===== KPI Count-Up Animation =====
(function() {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    function easeOutCubic(t) {
        return 1 - Math.pow(1 - t, 3);
    }

    function animateValue(el, target, duration) {
        var start = 0;
        var startTime = null;

        function step(timestamp) {
            if (!startTime) startTime = timestamp;
            var progress = Math.min((timestamp - startTime) / duration, 1);
            var easedProgress = easeOutCubic(progress);
            var current = Math.round(easedProgress * target);
            el.textContent = current;
            if (progress < 1) {
                requestAnimationFrame(step);
            } else {
                el.textContent = target;
            }
        }

        requestAnimationFrame(step);
    }

    var kpiRow = document.querySelector('.kpi-row');
    if (!kpiRow) return;

    var animated = false;
    var observer = new IntersectionObserver(function(entries) {
        entries.forEach(function(entry) {
            if (entry.isIntersecting && !animated) {
                animated = true;
                var values = kpiRow.querySelectorAll('.kpi-value');
                values.forEach(function(el, index) {
                    var target = parseInt(el.textContent, 10);
                    if (isNaN(target)) return;
                    el.textContent = '0';
                    setTimeout(function() {
                        animateValue(el, target, 1000);
                    }, index * 80);
                });
                observer.unobserve(kpiRow);
            }
        });
    }, { threshold: 0.3 });

    observer.observe(kpiRow);
})();

// ===== Login Modal =====
// The overlay uses backdrop-filter: blur(). Blurring a *moving* background forces the
// compositor to re-blur the whole page on every repaint, which makes typing in the form
// visibly lag. Freezing the background video and the looping CSS animations while the
// modal is open keeps the same look with none of the cost.
function freezeBackground(frozen) {
    document.documentElement.classList.toggle('modal-open', frozen);
    var video = document.querySelector('video.video-bg');
    if (!video) return;
    try {
        if (frozen) video.pause();
        else video.play().catch(function () { /* autoplay may be blocked; harmless */ });
    } catch (e) { /* ignore */ }
}

function openLoginModal() {
    var modal = document.getElementById('loginModal');
    if (!modal) return;
    modal.hidden = false;
    // Force reflow before adding class
    void modal.offsetWidth;
    modal.classList.add('open');
    document.body.style.overflow = 'hidden';
    freezeBackground(true);
    // Focus first input
    setTimeout(function() {
        var input = modal.querySelector('.login-input');
        if (input) input.focus();
    }, 300);
}

function closeLoginModal() {
    var modal = document.getElementById('loginModal');
    if (!modal) return;
    modal.classList.remove('open');
    document.body.style.overflow = '';
    freezeBackground(false);
    setTimeout(function() {
        modal.hidden = true;
    }, 300);
}

// Close on overlay click
document.addEventListener('click', function(e) {
    var modal = document.getElementById('loginModal');
    if (modal && modal.classList.contains('open') && e.target === modal) {
        closeLoginModal();
    }
});

// Close on ESC
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        var modal = document.getElementById('loginModal');
        if (modal && modal.classList.contains('open')) {
            closeLoginModal();
        }
    }
});

// Login switcher
(function() {
    var switcher = document.querySelector('.login-switcher');
    if (!switcher) return;
    var btns = switcher.querySelectorAll('.login-switch-btn');
    btns.forEach(function(btn) {
        btn.addEventListener('click', function() {
            btns.forEach(function(b) { b.classList.remove('active'); });
            btn.classList.add('active');
            switcher.setAttribute('data-active', btn.dataset.role);
        });
    });
})();

// Toggle password visibility
function togglePassword() {
    var pw = document.getElementById('loginPassword');
    if (!pw) return;
    pw.type = pw.type === 'password' ? 'text' : 'password';
}


// ===== Home page live data (KPIs, donut, recent tenders, alerts, search) =====
(function () {
    var API = (window.RAIEC_CONFIG && window.RAIEC_CONFIG.apiBase) || 'http://localhost:8080/api';
    // Only run on the home page (it has the KPI row / donut).
    if (!document.getElementById('donutChart') && !document.getElementById('homeTotal')) return;

    var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    var tendersCache = [];

    // Live date label
    (function () {
        var d = new Date();
        var lbl = document.getElementById('homeDateLabel');
        if (lbl) lbl.textContent = 'Live · ' + String(d.getDate()).padStart(2, '0') + ' ' + MONTHS[d.getMonth()] + ' ' + d.getFullYear();
    })();

    // Fake "↑ %" change badges are not real data — remove them.
    document.querySelectorAll('.kpi-change').forEach(function (el) { el.remove(); });

    // ---- Stats: KPIs + donut + alerts ----
    fetch(API + '/tenders/stats')
        .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function (s) {
            setKpi('homeTotal', s.total);
            setKpi('homeEval', s.active);
            setKpi('homeDone', s.finalized);
            setKpi('homeFlagged', s.underReview);
            setKpi('homeClosed', s.closed);
            if (typeof renderHomeDonut === 'function') {
                renderHomeDonut(s.active, s.underReview, s.finalized, s.closed);
            }
            renderAlerts(s);
        })
        .catch(function () { /* backend offline: leave zeros + static alert fallback */ });

    // ---- Recent tenders table ----
    fetch(API + '/tenders')
        .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function (list) {
            tendersCache = Array.isArray(list) ? list : [];
            renderRecent(tendersCache);
        })
        .catch(function () {
            var body = document.getElementById('homeRecentTenders');
            if (body) body.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:18px;color:var(--text-secondary)">Backend offline — start the server to see live tenders.</td></tr>';
        });

    function setKpi(id, val) {
        var el = document.getElementById(id);
        if (!el) return;
        var v = (val == null ? 0 : val);
        el.textContent = v;
        // Re-assert after the count-up animation (~1s) so it lands on the real value.
        setTimeout(function () { el.textContent = v; }, 1300);
    }

    function renderRecent(list) {
        var body = document.getElementById('homeRecentTenders');
        if (!body) return;
        if (!list.length) {
            body.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:18px;color:var(--text-secondary)">No tenders uploaded yet.</td></tr>';
            return;
        }
        var recent = list.slice().sort(function (a, b) { return (b.id || 0) - (a.id || 0); }).slice(0, 5);
        body.innerHTML = recent.map(function (t) {
            var st = homeStatus(t.status);
            return '<tr>' +
                '<td>' + esc(t.tenderNo) + '</td>' +
                '<td>' + esc(truncate(t.nameOfWork || '', 40)) + '</td>' +
                '<td>Civil &amp; Construction</td>' +
                '<td><span class="status-badge ' + st.cls + '">' + st.label + '</span></td>' +
            '</tr>';
        }).join('');
    }

    function renderAlerts(s) {
        var box = document.getElementById('homeAlerts');
        if (!box) return;
        var alerts = [];
        if (s.underReview > 0) {
            alerts.push(alertHtml('warning', '#f59e0b', s.underReview + ' tender(s) awaiting officer review', 'Pending an approve / reject decision'));
        }
        if (s.active > 0) {
            alerts.push(alertHtml('info', '#3b82f6', s.active + ' tender(s) under evaluation', 'Rate-match & AI analysis in progress'));
        }
        if (s.closed > 0) {
            alerts.push(alertHtml('flag', '#8b5cf6', s.closed + ' tender(s) closed', 'Rejected or archived'));
        }
        if (!alerts.length) {
            alerts.push(alertHtml('info', '#3b82f6', 'No active alerts', 'Upload an estimate to begin evaluation'));
        }
        box.innerHTML = alerts.join('');
    }

    function alertHtml(kind, color, title, desc) {
        return '<div class="alert-item alert-' + kind + '">' +
            '<div class="alert-icon">' +
                '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">' +
                    '<circle cx="12" cy="12" r="10" stroke="' + color + '" stroke-width="2"/>' +
                    '<path d="M12 8v4M12 16h.01" stroke="' + color + '" stroke-width="2"/>' +
                '</svg>' +
            '</div>' +
            '<div class="alert-content">' +
                '<span class="alert-title">' + esc(title) + '</span>' +
                '<span class="alert-desc">' + esc(desc) + '</span>' +
            '</div>' +
        '</div>';
    }

    function homeStatus(s) {
        switch (s) {
            case 'OFFICER_REVIEW': return { label: 'Officer Review', cls: 'status-review' };
            case 'APPROVED': return { label: 'Finalized', cls: 'status-completed' };
            case 'REJECTED': return { label: 'Closed', cls: 'status-notstarted' };
            default: return { label: 'Under Evaluation', cls: 'status-evaluation' };
        }
    }

    // ---- Tender search: real status lookup ----
    var btn = document.getElementById('homeCheckBtn');
    var input = document.getElementById('homeSearchInput');
    var result = document.getElementById('homeSearchResult');

    function runSearch() {
        if (!input || !result) return;
        var q = input.value.trim().toLowerCase();
        result.hidden = false;
        if (!q) {
            result.innerHTML = '<span style="color:var(--text-secondary)">Enter a tender number to check its status.</span>';
            return;
        }
        if (!tendersCache.length) {
            result.innerHTML = '<span style="color:var(--text-secondary)">No tender data available (is the backend running?).</span>';
            return;
        }
        var match = tendersCache.filter(function (t) {
            return (t.tenderNo || '').toLowerCase().indexOf(q) !== -1;
        })[0];
        if (!match) {
            result.innerHTML = '<span style="color:#f87171">No tender found matching “' + esc(input.value.trim()) + '”.</span>';
            return;
        }
        var st = homeStatus(match.status);
        result.innerHTML =
            '<div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">' +
                '<strong>' + esc(match.tenderNo) + '</strong>' +
                '<span class="status-badge ' + st.cls + '">' + st.label + '</span>' +
                '<span style="color:var(--text-secondary)">' + esc(truncate(match.nameOfWork || '', 60)) + '</span>' +
                '<button class="btn-check-status" style="margin-left:auto" onclick="openTender(' + match.id + ')">View</button>' +
            '</div>';
    }

    if (btn) btn.addEventListener('click', runSearch);
    if (input) input.addEventListener('keydown', function (e) { if (e.key === 'Enter') runSearch(); });

    // ---- helpers ----
    function truncate(s, n) { s = s || ''; return s.length > n ? s.slice(0, n - 1) + '…' : s; }
    function esc(s) {
        if (s == null) return '';
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
})();

// Open a tender's extracted view (used by the home search "View" action).
function openTender(id) {
    try { sessionStorage.setItem('raiec_tenderId', JSON.stringify(id)); } catch (e) {}
    window.location.href = 'upload/step2-ocr-extract.html';
}



// ===== Login modal -> auth API =====
(function () {
    var form = document.querySelector('.login-form');
    if (!form || !window.RAIEC) return;

    var btn = form.querySelector('.login-submit-btn');
    var err = document.createElement('div');
    err.id = 'loginError';
    err.setAttribute('role', 'alert');
    err.style.cssText = 'color:#f87171;font-size:13px;margin-top:10px;min-height:16px;text-align:center;';
    form.appendChild(err);

    function doLogin() {
        var uEl = document.getElementById('loginUsername');
        var pEl = document.getElementById('loginPassword');
        var u = (uEl ? uEl.value : '').trim();
        var p = pEl ? pEl.value : '';
        err.textContent = '';
        if (!u || !p) { err.textContent = 'Enter both username and password.'; return; }

        var label = btn ? btn.textContent : '';
        if (btn) { btn.textContent = 'Logging in…'; btn.disabled = true; }

        RAIEC.login(u, p).then(function () {
            // Stay on the home page: signing in is not a request to go somewhere.
            closeLoginModal();
            window.location.reload();
        }).catch(function (e) {
            err.textContent = (e && e.message) ? e.message : 'Login failed. Please try again.';
            if (btn) { btn.textContent = label || 'LOGIN'; btn.disabled = false; }
        });
    }

    form.addEventListener('submit', function (e) { e.preventDefault(); doLogin(); });

    // Switching the Admin/Officer tab prefills the matching demo username.
    document.querySelectorAll('.login-switch-btn').forEach(function (b) {
        b.addEventListener('click', function () {
            var uEl = document.getElementById('loginUsername');
            if (uEl) uEl.value = (b.dataset.role === 'officer') ? 'officer' : 'admin';
        });
    });

    // If redirected here because a protected page returned 401, open the modal automatically.
    if (location.search.indexOf('login=1') !== -1 && typeof openLoginModal === 'function') {
        openLoginModal();
    }
})();
