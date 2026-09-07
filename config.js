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

    window.RAIEC_CONFIG = {
        apiBase: apiBase,
        // Origin without the /api suffix - used in "server unreachable" messages.
        apiOrigin: apiBase.replace(/\/api\/?$/, ''),
        isLocal: apiBase.indexOf('localhost') !== -1
    };
})();
