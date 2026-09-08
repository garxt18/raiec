// ===== RAIEC auth: token storage, fetch interception, login/logout =====
(function () {
    var API = (window.RAIEC_CONFIG && window.RAIEC_CONFIG.apiBase) || 'http://localhost:8080/api';
    var K = { token: 'raiec_token', role: 'raiec_role', user: 'raiec_user', name: 'raiec_name' };

    function get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
    function set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
    function del(k) { try { localStorage.removeItem(k); } catch (e) {} }
    function clear() { del(K.token); del(K.role); del(K.user); del(K.name); }

    // The home page carries the login modal; there we never redirect on 401 — the page
    // simply shows its empty state and the user can log in via the modal.
    function isHome() { return !!document.getElementById('loginModal'); }
    function pathPrefix() { return location.pathname.indexOf('/upload/') !== -1 ? '../' : ''; }

    function handle401() {
        if (isHome()) return;
        clear();
        window.location.href = pathPrefix() + 'index.html?login=1';
    }

    // Attach the bearer token to API calls and centralise 401 handling.
    var orig = window.fetch.bind(window);
    window.fetch = function (input, init) {
        var url = (typeof input === 'string') ? input : (input && input.url) || '';
        if (url.indexOf(API) === 0) {
            init = init || {};
            var token = get(K.token);
            if (token) {
                var headers = new Headers(init.headers || {});
                if (!headers.has('Authorization')) headers.set('Authorization', 'Bearer ' + token);
                init.headers = headers;
            }
            return orig(input, init).then(function (res) {
                if (res.status === 401) handle401();
                return res;
            });
        }
        return orig(input, init);
    };

    var RAIEC = {
        apiBase: API,
        token: function () { return get(K.token); },
        role: function () { return get(K.role); },
        user: function () { return get(K.user); },
        name: function () { return get(K.name); },
        isLoggedIn: function () { return !!get(K.token); },
        /**
         * @param {function} [onSlow] called once the request passes SLOW_AFTER_MS, so the
         *        caller can explain the wait instead of leaving a dead "Logging in..."
         */
        login: function (username, password, onSlow) {
            // A hosted free tier suspends the service when idle, and the first request
            // then has to start it. Without a deadline a hung request never settles:
            // fetch neither resolves nor rejects, so the button sits on "Logging in..."
            // indefinitely with nothing explaining why.
            var LOGIN_TIMEOUT_MS = 60000;   // generous: a cold start can take ~45s
            var SLOW_AFTER_MS = 4000;       // past this, say something rather than nothing

            var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
            var timedOut = false;
            var timeoutId = setTimeout(function () {
                timedOut = true;
                if (controller) controller.abort();
            }, LOGIN_TIMEOUT_MS);
            var slowId = setTimeout(function () {
                if (typeof onSlow === 'function') onSlow();
            }, SLOW_AFTER_MS);

            function done() { clearTimeout(timeoutId); clearTimeout(slowId); }

            // Use the un-patched fetch so the 401 handler doesn't interfere with a failed login.
            return orig(API + '/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: username, password: password }),
                signal: controller ? controller.signal : undefined
            }).then(function (res) {
                return res.json().catch(function () { return {}; }).then(function (data) {
                    done();
                    if (!res.ok) throw new Error(data.error || 'Login failed');
                    set(K.token, data.token);
                    set(K.role, data.role);
                    set(K.user, data.username);
                    set(K.name, data.displayName || data.username);
                    return data;
                });
            }).catch(function (e) {
                done();
                if (timedOut) {
                    throw new Error('The server did not respond in time. It may be starting up — wait a moment and try again.');
                }
                // A network-level failure is not a wrong password, and saying so sends
                // people off checking credentials that were never the problem.
                if (e && (e.name === 'TypeError' || e.message === 'Failed to fetch')) {
                    throw new Error('Could not reach the server. Check your connection and try again.');
                }
                throw e;
            });
        },
        logout: function () {
            clear();
            window.location.href = pathPrefix() + 'index.html';
        }
    };
    window.RAIEC = RAIEC;

    function escHtml(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

    // ----- UI touches: avatar initial + custom logout popover, home login link -----
    document.addEventListener('DOMContentLoaded', function () {
        var loggedIn = !!get(K.token);
        var name = get(K.name) || get(K.user) || '';
        var role = (get(K.role) || '').toLowerCase();
        var initial = name ? name.charAt(0).toUpperCase() : 'G';

        var avatars = document.querySelectorAll('.td-avatar, .g-avatar, .wf-avatar, .user-avatar');
        avatars.forEach(function (el) { el.textContent = initial; });

        if (loggedIn && avatars.length) {
            var menu = document.createElement('div');
            menu.className = 'raiec-user-menu';
            menu.style.cssText = 'position:absolute;min-width:210px;background:var(--bg-elevated);border:1px solid var(--border-subtle);border-radius:12px;padding:12px;box-shadow:var(--shadow-lg);z-index:2000;display:none;font-family:Inter,system-ui,sans-serif;';
            menu.innerHTML =
                '<div style="display:flex;align-items:center;gap:10px;padding-bottom:10px;border-bottom:1px solid var(--border-subtle);margin-bottom:10px;">' +
                    '<div style="width:36px;height:36px;border-radius:50%;display:grid;place-items:center;font-weight:700;color:#fff;background:linear-gradient(135deg,#3b82f6,#8b5cf6);flex:0 0 auto;">' + escHtml(initial) + '</div>' +
                    '<div style="min-width:0;"><div style="color:var(--text-primary);font-weight:600;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">' + escHtml(name) + '</div>' +
                    '<div style="color:var(--text-secondary);font-size:12px;text-transform:capitalize;">' + escHtml(role) + '</div></div>' +
                '</div>' +
                '<button type="button" class="raiec-logout-btn" style="width:100%;padding:9px 10px;border:none;border-radius:8px;cursor:pointer;font-weight:600;font-size:14px;color:#fff;background:linear-gradient(135deg,var(--accent-red),#b91c1c);">Log out</button>';
            document.body.appendChild(menu);
            menu.querySelector('.raiec-logout-btn').addEventListener('click', function () { RAIEC.logout(); });

            var anchor = null;
            function place() {
                if (!anchor) return;
                var r = anchor.getBoundingClientRect();
                menu.style.top = (window.scrollY + r.bottom + 10) + 'px';
                menu.style.left = Math.max(8, window.scrollX + r.right - menu.offsetWidth) + 'px';
            }
            function toggle(el) {
                if (menu.style.display === 'block' && anchor === el) { menu.style.display = 'none'; return; }
                anchor = el;
                menu.style.display = 'block';
                place();
            }
            avatars.forEach(function (el) {
                el.style.cursor = 'pointer';
                el.title = name + ' \u00B7 ' + role;
                el.addEventListener('click', function (e) { e.stopPropagation(); toggle(el); });
            });
            document.addEventListener('click', function (e) { if (e.target !== menu && !menu.contains(e.target)) menu.style.display = 'none'; });
            document.addEventListener('keydown', function (e) { if (e.key === 'Escape') menu.style.display = 'none'; });
            window.addEventListener('resize', function () { if (menu.style.display === 'block') place(); });
        }

        if (loggedIn) {
            document.querySelectorAll('a.nav-link, .td-nav-menu a').forEach(function (a) {
                if (a.textContent.trim().toUpperCase() === 'LOGIN') {
                    a.textContent = 'LOGOUT';
                    a.onclick = function () { RAIEC.logout(); return false; };
                }
            });
        }
    });
})();
