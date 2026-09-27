// ===== RAIEC Reports Page =====

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

    // ===== Scroll Reveal + count-up =====
    var revealEls = document.querySelectorAll('.td-reveal');
    var countedStats = false;

    function runCountUp() {
        if (countedStats) return;
        countedStats = true;
        document.querySelectorAll('.td-stat-value').forEach(function(el, i) {
            var target = parseInt(el.getAttribute('data-count'), 10);
            setTimeout(function() { animateValue(el, target, 1000); }, i * 80);
        });
    }

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
                    if (entry.target.classList.contains('td-stat-card')) {
                        runCountUp();
                    }
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.15, rootMargin: '0px 0px -40px 0px' });

        revealEls.forEach(function(el) { observer.observe(el); });
    }

    // ===== Report Type Card Selection =====
    var typeCards = document.querySelectorAll('.rp-type-card');
    var reportTypeSelect = document.getElementById('rpReportType');
    typeCards.forEach(function(card) {
        card.addEventListener('click', function() {
            typeCards.forEach(function(c) { c.classList.remove('selected'); });
            card.classList.add('selected');
            // Sync dropdown
            if (reportTypeSelect && card.dataset.report) {
                var options = reportTypeSelect.options;
                for (var i = 0; i < options.length; i++) {
                    if (options[i].text === card.dataset.report) {
                        reportTypeSelect.selectedIndex = i;
                        break;
                    }
                }
            }
        });
    });

    // Sync dropdown -> cards
    if (reportTypeSelect) {
        reportTypeSelect.addEventListener('change', function() {
            var val = reportTypeSelect.value;
            typeCards.forEach(function(c) {
                c.classList.toggle('selected', c.dataset.report === val);
            });
        });
    }

    // ===== Reset Filters =====
    var resetBtn = document.getElementById('rpReset');
    if (resetBtn) {
        resetBtn.addEventListener('click', function() {
            document.querySelectorAll('.td-input').forEach(function(input) { input.value = ''; });
            document.querySelectorAll('.td-select').forEach(function(select) { select.selectedIndex = 0; });
            typeCards.forEach(function(c, i) { c.classList.toggle('selected', i === 0); });
        });
    }
})();
