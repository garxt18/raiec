/* =============================================================================
   Administration — accounts, and the record of who decided what.

   This page replaced reports.html, which made no API calls and which nothing
   linked to. The accountability half is the part the problem statement actually
   asked for and the build had least of: a single approval evidences nothing, so
   what is needed is the shape of a person's decisions over time.
   ============================================================================= */
(function () {
    var API = (window.RAIEC_CONFIG && window.RAIEC_CONFIG.apiBase) || 'http://localhost:8080/api';

    var els = {
        denied:   document.getElementById('adminDenied'),
        body:     document.getElementById('adminBody'),
        kpis:     document.getElementById('adminKpis'),
        officers: document.getElementById('officerRows'),
        feed:     document.getElementById('decisionFeed'),
        users:    document.getElementById('userRows'),
        msg:      document.getElementById('userMsg'),
        addBtn:   document.getElementById('addUserBtn')
    };
    if (!els.body) return;   // not this page

    /* ------------------------------------------------------------------ helpers */

    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function inr(n) {
        var v = Number(n);
        if (n == null || isNaN(v)) return '—';
        if (Math.abs(v) >= 1e7) return '₹' + (v / 1e7).toFixed(2).replace(/\.00$/, '') + ' Cr';
        if (Math.abs(v) >= 1e5) return '₹' + (v / 1e5).toFixed(2).replace(/\.00$/, '') + ' L';
        return '₹' + v.toLocaleString('en-IN', { maximumFractionDigits: 0 });
    }

    function when(iso) {
        if (!iso) return '—';
        return window.RAIEC_UI ? RAIEC_UI.formatDateTime(iso) : String(iso);
    }

    function say(text, kind) {
        if (!els.msg) return;
        els.msg.hidden = false;
        els.msg.textContent = text;
        els.msg.setAttribute('data-kind', kind || 'info');
    }

    /* ------------------------------------------------------- accountability view */

    function loadAccountability() {
        raiecFetch(API + '/admin/accountability')
            .then(function (r) {
                if (r.status === 403) { denied(); throw new Error('forbidden'); }
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(renderAccountability)
            .catch(function (e) {
                if (e && e.message === 'forbidden') return;
                els.officers.innerHTML = row(8, 'Could not load the decision record from the server.');
                els.feed.innerHTML = '<li class="admin-empty">Could not load recent decisions.</li>';
            });
    }

    function renderAccountability(d) {
        var officers = d.officers || [];

        els.kpis.innerHTML =
            kpi('Tenders approved', String(d.totalApproved || 0), 'by anyone, all time') +
            kpi('Excess let through', inr(d.totalExcessLet), 'above reference, at the time of approval') +
            kpi('People deciding', String(officers.length), 'with at least one decision recorded') +
            // Surfaced rather than hidden: a decision with no name against it means something
            // decided a tender without a signed-in user, which is worth asking about.
            (d.unattributed
                ? kpi('Unattributed', String(d.unattributed), 'decisions with no name recorded', 'warn')
                : '');

        if (!officers.length) {
            els.officers.innerHTML = row(8, 'No decisions recorded yet. Approve or reject a tender and it will appear here.');
        } else {
            els.officers.innerHTML = officers.map(function (o) {
                var total = (o.approved || 0) + (o.rejected || 0) + (o.infoRequested || 0);
                var rate = total ? Math.round((o.approved || 0) / total * 100) : 0;
                // An officer who approves everything is not proof of anything, but it is
                // the question worth asking, so the figure is shown plainly.
                var tone = total >= 5 && rate === 100 ? ' data-flag="all"' : '';
                return '<tr' + tone + '>' +
                    '<td class="admin-actor">' + esc(o.actor) + '</td>' +
                    '<td class="num">' + (o.approved || 0) + '</td>' +
                    '<td class="num">' + (o.rejected || 0) + '</td>' +
                    '<td class="num">' + (o.infoRequested || 0) + '</td>' +
                    '<td class="num">' + rate + '%</td>' +
                    '<td class="num admin-money">' + esc(inr(o.excessApproved)) + '</td>' +
                    '<td class="num">' + esc(inr(o.largestApproved)) + '</td>' +
                    '<td>' + esc(when(o.lastDecisionAt)) + '</td>' +
                '</tr>';
            }).join('');
        }

        var recent = d.recentDecisions || [];
        els.feed.innerHTML = recent.length
            ? recent.map(function (e) {
                var kind = e.type === 'APPROVED' ? 'good' : e.type === 'REJECTED' ? 'bad' : 'warn';
                return '<li class="admin-feed-item" data-kind="' + kind + '">' +
                         '<span class="admin-feed-dot" aria-hidden="true"></span>' +
                         '<span class="admin-feed-main">' +
                           '<span class="admin-feed-text">' + esc(e.detail || e.type) + '</span>' +
                           '<span class="admin-feed-meta">' + esc(when(e.at)) +
                             ' · by ' + esc(e.actor || 'unattributed') + '</span>' +
                         '</span>' +
                       '</li>';
            }).join('')
            : '<li class="admin-empty">No decisions recorded yet.</li>';
    }

    function kpi(label, value, sub, tone) {
        return '<div class="admin-kpi"' + (tone ? ' data-tone="' + tone + '"' : '') + '>' +
                 '<span class="admin-kpi-label">' + esc(label) + '</span>' +
                 '<span class="admin-kpi-value">' + esc(value) + '</span>' +
                 '<span class="admin-kpi-sub">' + esc(sub) + '</span>' +
               '</div>';
    }

    function row(cols, text) {
        return '<tr><td colspan="' + cols + '" class="admin-empty">' + esc(text) + '</td></tr>';
    }

    function denied() {
        if (els.denied) els.denied.hidden = false;
        if (els.body) els.body.hidden = true;
    }

    /* ------------------------------------------------------------- people view */

    var ROLE_NOTE = {
        FILER:   'files estimates, cannot decide',
        OFFICER: 'approves, rejects, asks for information',
        ADMIN:   'everything, plus settings and accounts'
    };

    function loadUsers() {
        raiecFetch(API + '/admin/users')
            .then(function (r) {
                if (r.status === 403) { denied(); throw new Error('forbidden'); }
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(renderUsers)
            .catch(function (e) {
                if (e && e.message === 'forbidden') return;
                els.users.innerHTML = row(6, 'Could not load accounts from the server.');
            });
    }

    function renderUsers(list) {
        var me = (localStorage.getItem('raiec_user') || '');
        if (!list.length) { els.users.innerHTML = row(6, 'No accounts.'); return; }

        els.users.innerHTML = list.map(function (u) {
            var isMe = u.username === me;
            return '<tr' + (u.enabled ? '' : ' data-disabled="yes"') + '>' +
                '<td class="admin-actor">' + esc(u.username) +
                    (isMe ? ' <span class="admin-you">you</span>' : '') + '</td>' +
                '<td>' + esc(u.displayName || '—') + '</td>' +
                '<td>' + roleSelect(u, isMe) + '</td>' +
                '<td>' + (u.enabled
                            ? '<span class="admin-state" data-on="yes">Active</span>'
                            : '<span class="admin-state">Disabled</span>') + '</td>' +
                '<td>' + esc(when(u.createdAt)) + '</td>' +
                '<td class="admin-actions">' +
                    // Acting on your own account is refused by the server too; the control is
                    // disabled here so the refusal is not a surprise.
                    '<button type="button" class="td-btn admin-btn" data-act="toggle" data-id="' + u.id +
                        '" data-enabled="' + (u.enabled ? 'false' : 'true') + '"' + (isMe ? ' disabled' : '') + '>' +
                        (u.enabled ? 'Disable' : 'Enable') + '</button>' +
                    '<button type="button" class="td-btn admin-btn" data-act="password" data-id="' + u.id +
                        '" data-user="' + esc(u.username) + '">Reset password</button>' +
                '</td>' +
            '</tr>';
        }).join('');
    }

    function roleSelect(u, isMe) {
        var opts = ['FILER', 'OFFICER', 'ADMIN'].map(function (r) {
            return '<option value="' + r + '"' + (u.role === r ? ' selected' : '') + '>' +
                   r.charAt(0) + r.slice(1).toLowerCase() + ' — ' + ROLE_NOTE[r] + '</option>';
        }).join('');
        return '<select class="td-select admin-role" data-id="' + u.id + '"' + (isMe ? ' disabled' : '') +
               ' aria-label="Role for ' + esc(u.username) + '">' + opts + '</select>';
    }

    /* --------------------------------------------------------------- actions */

    function send(path, method, body) {
        return raiecFetch(API + path, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(function (r) {
            return r.json().catch(function () { return {}; }).then(function (data) {
                if (!r.ok) throw new Error(data.message || data.error || ('Request failed (' + r.status + ')'));
                return data;
            });
        });
    }

    document.addEventListener('click', function (e) {
        var btn = e.target.closest('button[data-act]');
        if (!btn) return;

        if (btn.dataset.act === 'toggle') {
            var enable = btn.dataset.enabled === 'true';
            send('/admin/users/' + btn.dataset.id + '/enabled', 'PUT', { enabled: enable })
                .then(function () { say(enable ? 'Account enabled.' : 'Account disabled.', 'ok'); loadUsers(); })
                .catch(function (err) { say(err.message, 'error'); });
        }

        if (btn.dataset.act === 'password') {
            openPasswordDialog(btn.dataset.id, btn.dataset.user);
        }
    });

    document.addEventListener('change', function (e) {
        var sel = e.target.closest('select.admin-role');
        if (!sel) return;
        send('/admin/users/' + sel.dataset.id + '/role', 'PUT', { role: sel.value })
            .then(function () { say('Role updated.', 'ok'); loadUsers(); })
            .catch(function (err) { say(err.message, 'error'); loadUsers(); });
    });

    if (els.addBtn) els.addBtn.addEventListener('click', openCreateDialog);

    /* --------------------------------------------------------------- dialogs */

    function dialog(titleText, innerHtml, onSubmit) {
        var ov = document.createElement('div');
        ov.className = 'admin-modal';
        ov.innerHTML =
            '<div class="admin-dialog" role="dialog" aria-modal="true" aria-labelledby="admDlgTitle">' +
              '<h3 id="admDlgTitle">' + esc(titleText) + '</h3>' +
              '<form novalidate>' + innerHtml +
                '<p class="admin-dialog-error" id="admDlgErr" role="alert" hidden></p>' +
                '<div class="admin-dialog-actions">' +
                  '<button type="button" class="td-btn" data-close>Cancel</button>' +
                  '<button type="submit" class="td-btn td-btn-primary">Save</button>' +
                '</div>' +
              '</form>' +
            '</div>';
        document.body.appendChild(ov);
        document.documentElement.classList.add('modal-open');

        var form = ov.querySelector('form');
        var err = ov.querySelector('#admDlgErr');
        var first = ov.querySelector('input, select');
        if (first) setTimeout(function () { first.focus(); }, 40);

        function close() {
            ov.remove();
            document.documentElement.classList.remove('modal-open');
            document.removeEventListener('keydown', onKey);
        }
        function onKey(ev) {
            if (ev.key === 'Escape') close();
            if (ev.key !== 'Tab') return;
            // Keep focus inside the dialog: tabbing out of a modal leaves a keyboard user
            // navigating a page they cannot see.
            var f = ov.querySelectorAll('button, input, select, [href]');
            if (!f.length) return;
            var firstEl = f[0], lastEl = f[f.length - 1];
            if (ev.shiftKey && document.activeElement === firstEl) { ev.preventDefault(); lastEl.focus(); }
            else if (!ev.shiftKey && document.activeElement === lastEl) { ev.preventDefault(); firstEl.focus(); }
        }
        document.addEventListener('keydown', onKey);
        ov.addEventListener('mousedown', function (ev) { if (ev.target === ov) close(); });
        ov.querySelector('[data-close]').addEventListener('click', close);

        form.addEventListener('submit', function (ev) {
            ev.preventDefault();
            err.hidden = true;
            onSubmit(new FormData(form), function fail(message) {
                err.hidden = false;
                err.textContent = message;
            }, close);
        });
    }

    function field(label, name, type, hint) {
        return '<label class="admin-field"><span>' + esc(label) + '</span>' +
               '<input name="' + name + '" type="' + (type || 'text') + '"' +
               (type === 'password' ? ' autocomplete="new-password"' : '') + '>' +
               (hint ? '<small>' + esc(hint) + '</small>' : '') + '</label>';
    }

    function openCreateDialog() {
        dialog('Add an account',
            field('Username', 'username', 'text', 'How they sign in. Cannot be changed later.') +
            field('Display name', 'displayName', 'text', 'Optional. Shown in the interface.') +
            '<label class="admin-field"><span>Role</span>' +
              '<select name="role" class="td-select">' +
                '<option value="FILER">Filer — ' + ROLE_NOTE.FILER + '</option>' +
                '<option value="OFFICER">Officer — ' + ROLE_NOTE.OFFICER + '</option>' +
                '<option value="ADMIN">Administrator — ' + ROLE_NOTE.ADMIN + '</option>' +
              '</select></label>' +
            field('Password', 'password', 'password', 'At least 10 characters. Give it to them directly, not by email.'),
            function (data, fail, close) {
                send('/admin/users', 'POST', {
                    username: (data.get('username') || '').trim(),
                    displayName: (data.get('displayName') || '').trim(),
                    role: data.get('role'),
                    password: data.get('password') || ''
                }).then(function () {
                    close();
                    say('Account created.', 'ok');
                    loadUsers();
                }).catch(function (e) { fail(e.message); });
            });
    }

    function openPasswordDialog(id, username) {
        dialog('Reset password for ' + username,
            field('New password', 'password', 'password', 'At least 10 characters.'),
            function (data, fail, close) {
                send('/admin/users/' + id + '/password', 'PUT', { password: data.get('password') || '' })
                    .then(function () {
                        close();
                        // Never echo the password back into the page.
                        say('Password reset for ' + username + '.', 'ok');
                    })
                    .catch(function (e) { fail(e.message); });
            });
    }

    /* ----------------------------------------------------------------- start */

    if ((localStorage.getItem('raiec_role') || '').toUpperCase() !== 'ADMIN') {
        // The server is the real gate; this only avoids drawing panels that are about to
        // be refused.
        denied();
    } else {
        loadAccountability();
        loadUsers();
    }
})();
