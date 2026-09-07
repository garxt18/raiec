/* =============================================================================
   RAIEC shared UI: theming, the scanning loader, and navigation chrome.
   Loaded on every page, after config.js and before page-specific scripts.
   ============================================================================= */
(function () {
    'use strict';

    var THEME_KEY = 'raiec_theme';

    /* ---------------------------------------------------------------------
       Theme
       The applied theme is set by an inline snippet in <head> before first
       paint, so the page never flashes the wrong colours. Everything here
       only handles switching afterwards.
       --------------------------------------------------------------------- */

    function storedTheme() {
        try { return localStorage.getItem(THEME_KEY); } catch (e) { return null; }
    }

    function systemTheme() {
        return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    }

    function currentTheme() {
        return document.documentElement.getAttribute('data-theme') || systemTheme();
    }

    function applyTheme(theme) {
        var root = document.documentElement;
        // Suppress colour transitions during the swap: easing 400 properties at
        // once reads as lag, while an instant swap reads as responsive.
        root.classList.add('theme-switching');
        root.setAttribute('data-theme', theme);
        try { localStorage.setItem(THEME_KEY, theme); } catch (e) { /* storage blocked */ }

        var toggles = document.querySelectorAll('.theme-toggle');
        for (var i = 0; i < toggles.length; i++) {
            toggles[i].setAttribute('aria-label',
                theme === 'light' ? 'Switch to dark theme' : 'Switch to light theme');
            toggles[i].setAttribute('aria-pressed', theme === 'light' ? 'true' : 'false');
        }

        window.requestAnimationFrame(function () {
            window.requestAnimationFrame(function () {
                root.classList.remove('theme-switching');
            });
        });
    }

    function toggleTheme() {
        applyTheme(currentTheme() === 'light' ? 'dark' : 'light');
    }

    var TOGGLE_HTML =
        '<svg class="icon-moon" viewBox="0 0 24 24" aria-hidden="true">' +
        '<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>' +
        '<svg class="icon-sun" viewBox="0 0 24 24" aria-hidden="true">' +
        '<circle cx="12" cy="12" r="4"/>' +
        '<path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2' +
        'M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>';

    // Drop a toggle into whatever nav container this page happens to use.
    function mountThemeToggle() {
        if (document.querySelector('.theme-toggle')) return;
        var host = document.querySelector('.nav-menu')
                || document.querySelector('.wf-nav-right')
                || document.querySelector('.td-nav-right')
                || document.querySelector('.navbar .nav-actions')
                || document.querySelector('.wf-nav-container')
                || document.querySelector('.navbar');
        if (!host) return;

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'theme-toggle';
        btn.innerHTML = TOGGLE_HTML;
        btn.addEventListener('click', toggleTheme);

        var avatar = host.querySelector('.td-avatar, .wf-avatar, .user-avatar');
        if (avatar && avatar.parentNode === host) host.insertBefore(btn, avatar);
        else host.appendChild(btn);

        applyTheme(currentTheme());
    }

    /* ---------------------------------------------------------------------
       Scanning loader
       A full-screen overlay used for every wait long enough to notice: the
       page behind it blurs, a document-scan animation runs in front, and the
       status line says what is actually happening rather than showing a
       progress bar that does not track anything real.
       --------------------------------------------------------------------- */

    var loaderEl = null;
    var loaderStepTimer = null;

    function buildLoader() {
        var el = document.createElement('div');
        el.className = 'raiec-loader';
        el.setAttribute('role', 'status');
        el.setAttribute('aria-live', 'polite');
        el.innerHTML =
            '<div class="raiec-loader-inner">' +
              '<div class="raiec-scan">' +
                '<svg class="raiec-scan-doc" viewBox="0 0 80 100" aria-hidden="true">' +
                  '<path class="doc-body" d="M8 4h44l20 20v72a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8a4 4 0 0 1 4-4z"/>' +
                  '<path class="doc-fold" d="M52 4v20h20"/>' +
                  '<g class="doc-lines">' +
                    '<line x1="16" y1="40" x2="60" y2="40"/>' +
                    '<line x1="16" y1="52" x2="64" y2="52"/>' +
                    '<line x1="16" y1="64" x2="52" y2="64"/>' +
                    '<line x1="16" y1="76" x2="58" y2="76"/>' +
                  '</g>' +
                '</svg>' +
                '<div class="raiec-scan-beam"></div>' +
              '</div>' +
              '<div class="raiec-loader-title">Working…</div>' +
              '<div class="raiec-loader-step"></div>' +
            '</div>';
        return el;
    }

    /**
     * Show the loader.
     * @param {string} title  headline, e.g. "Extracting tender"
     * @param {string[]} steps optional lines cycled every ~2.2s to show real stages
     */
    function showLoader(title, steps) {
        if (!loaderEl) {
            loaderEl = buildLoader();
            document.body.appendChild(loaderEl);
        }
        loaderEl.querySelector('.raiec-loader-title').textContent = title || 'Working…';

        var stepEl = loaderEl.querySelector('.raiec-loader-step');
        clearInterval(loaderStepTimer);
        if (steps && steps.length) {
            var i = 0;
            stepEl.textContent = steps[0];
            loaderStepTimer = setInterval(function () {
                i = (i + 1) % steps.length;
                stepEl.style.opacity = '0';
                setTimeout(function () {
                    stepEl.textContent = steps[i];
                    stepEl.style.opacity = '1';
                }, 180);
            }, 2200);
        } else {
            stepEl.textContent = '';
        }

        // Same reason as the login modal: blurring moving pixels is expensive.
        document.documentElement.classList.add('modal-open', 'loader-open');
        var video = document.querySelector('video.video-bg');
        if (video) { try { video.pause(); } catch (e) { /* ignore */ } }

        void loaderEl.offsetWidth;
        loaderEl.classList.add('open');
    }

    function hideLoader() {
        clearInterval(loaderStepTimer);
        if (!loaderEl) return;
        loaderEl.classList.remove('open');
        document.documentElement.classList.remove('modal-open', 'loader-open');
        var video = document.querySelector('video.video-bg');
        if (video) { try { video.play().catch(function () {}); } catch (e) { /* ignore */ } }
    }

    /* ---------------------------------------------------------------------
       Navigation chrome
       --------------------------------------------------------------------- */

    function initNavbarScroll() {
        var navbar = document.querySelector('.navbar, .wf-navbar, .td-nav, .td-navbar');
        if (!navbar) return;
        var ticking = false;
        function update() {
            navbar.classList.toggle('scrolled', window.scrollY > 24);
            ticking = false;
        }
        window.addEventListener('scroll', function () {
            if (!ticking) { window.requestAnimationFrame(update); ticking = true; }
        }, { passive: true });
        update();
    }

    /* ---------------------------------------------------------------------
       Boot
       --------------------------------------------------------------------- */

    function init() {
        mountThemeToggle();
        initNavbarScroll();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    window.RAIEC_UI = {
        applyTheme: applyTheme,
        toggleTheme: toggleTheme,
        currentTheme: currentTheme,
        showLoader: showLoader,
        hideLoader: hideLoader
    };
})();
