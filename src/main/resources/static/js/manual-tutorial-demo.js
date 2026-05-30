/**
 * Tutorial animado no manual (substitui placeholder ate haver MP4 ou YouTube oficial).
 */
(function () {
    var root = document.getElementById('manualDemoVideo');
    if (!root) {
        return;
    }

    var slides = Array.prototype.slice.call(root.querySelectorAll('[data-slide]'));
    if (slides.length === 0) {
        return;
    }

    var progress = root.querySelector('[data-demo-progress]');
    var label = root.querySelector('[data-demo-step-label]');
    var btnPlay = root.querySelector('[data-demo-play]');
    var durationMs = 5500;
    var index = 0;
    var timer = null;
    var playing = true;

    function showSlide(i) {
        slides.forEach(function (slide, idx) {
            slide.classList.toggle('is-active', idx === i);
        });
        if (label) {
            label.textContent = 'Passo ' + (i + 1) + ' de ' + slides.length;
        }
        if (progress) {
            progress.style.width = ((i + 1) / slides.length * 100) + '%';
        }
    }

    function nextSlide() {
        index = (index + 1) % slides.length;
        showSlide(index);
    }

    function stopTimer() {
        if (timer) {
            clearInterval(timer);
            timer = null;
        }
    }

    function startTimer() {
        stopTimer();
        if (!playing) {
            return;
        }
        timer = setInterval(nextSlide, durationMs);
    }

    function togglePlay() {
        playing = !playing;
        if (btnPlay) {
            btnPlay.textContent = playing ? 'Pausar' : 'Continuar';
            btnPlay.setAttribute('aria-pressed', playing ? 'true' : 'false');
        }
        if (playing) {
            startTimer();
        } else {
            stopTimer();
        }
    }

    if (btnPlay) {
        btnPlay.addEventListener('click', togglePlay);
    }

    showSlide(0);
    startTimer();
})();
