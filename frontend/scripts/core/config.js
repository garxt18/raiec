// ===== RAIEC runtime configuration =====
// Loaded before every other script. Decides which backend the frontend talks to, so the
// same static files work unchanged on a laptop and on the deployed site.
//
// Resolution order:
//   1. ?api=<url> in the address bar        - one-off override, handy for testing a branch
//   2. localStorage 'raiec_api_base'        - sticky override for this browser
//   3. localhost / 127.0.0.1                - local development, assumes backend on :8080
//   4. RAIEC_DEPLOYED_API below             - everything else (the live deployment)
(function () {
    // Set this to the deployed backend origin once the API is hosted, e.g.
    // 'https://raiec-api.onrender.com/api'. Kept as a named constant so deployment
    // is a one-line change rather than a search-and-replace across five files.
    var RAIEC_DEPLOYED_API = 'https://raiec-api.onrender.com/api';

    function resolveApiBase() {
        try {
            var fromQuery = new URLSearchParams(window.location.search).get('api');
            if (fromQuery) {
                localStorage.setItem('raiec_api_base', fromQuery);
                return fromQuery;
            }
            var stored = localStorage.getItem('raiec_api_base');
            if (stored) return stored;
        } catch (e) { /* private mode / storage blocked - fall through */ }

        var host = window.location.hostname;
        if (host === 'localhost' || host === '127.0.0.1' || host === '') {
            return 'http://localhost:8080/api';
        }
        return RAIEC_DEPLOYED_API;
    }

    var apiBase = resolveApiBase();

    /**
     * Where the site's top level is, worked out from this script's own URL.
     *
     * Pages sit at two depths (index.html at the top, pages/ and workflow/ one below),
     * so any script that builds a link to another page needs to know which. Guessing it
     * from location.pathname was how this used to work, and it meant a hard-coded check
     * for the folder name '/upload/' that silently broke the moment the folder was
     * renamed. This script is always at <root>/scripts/core/config.js, so stripping that
     * suffix off its own src gives the root exactly, whether the site is served from a
     * domain root, a subfolder, or a local file server.
     */
    function resolveSiteRoot() {
        var self = document.currentScript && document.currentScript.src;
        if (self) {
            var at = self.indexOf('scripts/core/config.js');
            if (at !== -1) return self.slice(0, at);
        }
        // currentScript is unavailable only in unusual loading modes; falling back to the
        // document's own folder is right for the top-level page and wrong one level down,
        // which is still better than throwing.
        return '';
    }

    var siteRoot = resolveSiteRoot();

    window.RAIEC_CONFIG = {
        apiBase: apiBase,
        // Origin without the /api suffix - used in "server unreachable" messages.
        apiOrigin: apiBase.replace(/\/api\/?$/, ''),
        isLocal: apiBase.indexOf('localhost') !== -1,
        siteRoot: siteRoot
    };

    /**
     * A URL for a page, given from the site's top level: raiecUrl('workflow/step1-upload.html').
     * Use this instead of a relative path whenever the calling script runs on pages at
     * more than one depth.
     */
    window.raiecUrl = function (path) {
        return siteRoot + String(path).replace(/^\/+/, '');
    };
})();

/* =============================================================================
   raiecFetch — fetch with a deadline and a "still working" signal.

   fetch() has no timeout. A request that never answers never settles: the
   promise neither resolves nor rejects, so a spinner spins forever and the user
   is left guessing whether it is broken or merely slow.

   That distinction matters here more than usual. The API is hosted on a free
   tier that suspends when idle and runs on half a CPU, so real timings are:

       waking from sleep    ~80s
       upload + parse PDF   ~27s
       rate match           ~3-5s

   Those are slow but correct. A deadline has to be generous enough not to abort
   honest work, while still ending eventually — and something has to say "still
   going" in the meantime, or the wait is indistinguishable from a hang.
   ============================================================================= */
(function () {
    var DEFAULT_TIMEOUT_MS = 150000;   // 2.5 min: covers a cold start plus a parse
    var SLOW_AFTER_MS = 5000;          // past this, say something

    /**
     * @param {string} url
     * @param {object} [options]  standard fetch options, plus:
     *   {number}   timeoutMs  override the deadline
     *   {function} onSlow     called once when the request passes SLOW_AFTER_MS
     */
    window.raiecFetch = function (url, options) {
        options = options || {};
        var timeoutMs = options.timeoutMs || DEFAULT_TIMEOUT_MS;
        var onSlow = options.onSlow;

        var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
        var timedOut = false;

        var timeoutId = setTimeout(function () {
            timedOut = true;
            if (controller) controller.abort();
        }, timeoutMs);

        var slowId = setTimeout(function () {
            if (typeof onSlow === 'function') onSlow();
        }, SLOW_AFTER_MS);

        function clear() { clearTimeout(timeoutId); clearTimeout(slowId); }

        var opts = {};
        for (var k in options) {
            if (k !== 'timeoutMs' && k !== 'onSlow') opts[k] = options[k];
        }
        if (controller) opts.signal = controller.signal;

        return fetch(url, opts).then(function (res) {
            clear();
            return res;
        }).catch(function (e) {
            clear();
            if (timedOut) {
                var err = new Error('The server did not respond in time. It may be starting up \u2014 wait a moment and try again.');
                err.isTimeout = true;
                throw err;
            }
            // A network failure is not the same as a rejected request, and saying
            // "failed" for both sends people looking in the wrong place.
            if (e && (e.name === 'TypeError' || e.message === 'Failed to fetch')) {
                var netErr = new Error('Could not reach the server. Check your connection and try again.');
                netErr.isNetwork = true;
                throw netErr;
            }
            throw e;
        });
    };
})();
