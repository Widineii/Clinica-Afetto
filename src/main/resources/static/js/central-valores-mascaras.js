(function () {
    function teclaNavegacaoOuAtalho(evento) {
        var tecla = evento.key;
        if (tecla === 'Tab' || tecla === 'Enter' || tecla === 'Escape' || tecla === 'Backspace'
            || tecla === 'Delete' || tecla === 'ArrowLeft' || tecla === 'ArrowRight'
            || tecla === 'ArrowUp' || tecla === 'ArrowDown' || tecla === 'Home' || tecla === 'End') {
            return true;
        }
        return evento.ctrlKey || evento.metaKey || evento.altKey;
    }

    function formatarMoedaBr(numero) {
        if (numero == null || !Number.isFinite(numero)) {
            return '';
        }
        return numero.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function normalizarPercentual(texto) {
        var digitos = String(texto || '').replace(/\D/g, '');
        if (!digitos) {
            return '';
        }
        var numero = parseInt(digitos, 10);
        if (!Number.isFinite(numero)) {
            return '';
        }
        if (numero > 100) {
            return '100';
        }
        return String(numero);
    }

    function aplicarMascaraPercentual(input) {
        if (input.dataset.mascaraPercentual === '1') {
            return;
        }
        input.dataset.mascaraPercentual = '1';
        input.setAttribute('inputmode', 'numeric');
        input.setAttribute('maxlength', '3');
        input.setAttribute('autocomplete', 'off');
        if (input.value) {
            input.value = normalizarPercentual(input.value);
        }
        input.addEventListener('beforeinput', function (evento) {
            if (evento.inputType && evento.inputType.indexOf('delete') === 0) {
                return;
            }
            if (evento.data && !/^\d$/.test(evento.data)) {
                evento.preventDefault();
            }
        });
        input.addEventListener('keydown', function (evento) {
            if (teclaNavegacaoOuAtalho(evento)) {
                return;
            }
            if (!/^\d$/.test(evento.key)) {
                evento.preventDefault();
            }
        });
        input.addEventListener('input', function () {
            input.value = normalizarPercentual(input.value);
        });
        input.addEventListener('paste', function (evento) {
            evento.preventDefault();
            var texto = (evento.clipboardData || window.clipboardData).getData('text') || '';
            input.value = normalizarPercentual(texto);
        });
    }

    function aplicarMascaraMoeda(input) {
        if (input.dataset.mascaraMoeda === '1') {
            return;
        }
        input.dataset.mascaraMoeda = '1';
        input.addEventListener('keydown', function (evento) {
            if (teclaNavegacaoOuAtalho(evento)) {
                return;
            }
            if (!/^\d$/.test(evento.key)) {
                evento.preventDefault();
            }
        });
        input.addEventListener('input', function () {
            var digitos = input.value.replace(/\D/g, '');
            if (!digitos) {
                input.value = '';
                return;
            }
            var numero = parseInt(digitos, 10) / 100;
            input.value = formatarMoedaBr(numero);
        });
        input.addEventListener('paste', function (evento) {
            evento.preventDefault();
            var texto = (evento.clipboardData || window.clipboardData).getData('text') || '';
            var digitos = texto.replace(/\D/g, '');
            if (!digitos) {
                input.value = '';
                return;
            }
            var numero = parseInt(digitos, 10) / 100;
            input.value = formatarMoedaBr(numero);
        });
    }

    function iniciarMascarasValores() {
        var pane = document.getElementById('central-pane-valores');
        if (!pane) {
            return;
        }
        pane.querySelectorAll('.valor-moeda-brl').forEach(aplicarMascaraMoeda);
        pane.querySelectorAll('.valor-percentual').forEach(aplicarMascaraPercentual);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciarMascarasValores);
    } else {
        iniciarMascarasValores();
    }

    document.addEventListener('toggle', function (evento) {
        var alvo = evento.target;
        if (!alvo || !alvo.classList || !alvo.classList.contains('valores-prof-item') || !alvo.open) {
            return;
        }
        alvo.querySelectorAll('.valor-percentual').forEach(aplicarMascaraPercentual);
        alvo.querySelectorAll('.valor-moeda-brl').forEach(aplicarMascaraMoeda);
    }, true);

    function iniciarExclusaoValoresLote() {
        var botao = document.getElementById('btnValoresExcluirToggle');
        var painel = document.getElementById('valoresExcluirPainel');
        var resumo = document.getElementById('valoresExcluirResumo');
        if (!botao || !painel) {
            return;
        }

        function atualizarResumo() {
            if (!resumo) {
                return;
            }
            var marcados = painel.querySelectorAll('.valores-prof-excluir__check:checked').length;
            if (marcados === 0) {
                resumo.hidden = true;
                resumo.textContent = '';
                return;
            }
            resumo.hidden = false;
            resumo.textContent = marcados === 1
                ? '1 pessoa não será alterada.'
                : marcados + ' pessoas não serão alteradas.';
        }

        botao.addEventListener('click', function () {
            var aberto = !painel.hidden;
            painel.hidden = aberto;
            botao.setAttribute('aria-expanded', aberto ? 'false' : 'true');
            botao.classList.toggle('is-open', !aberto);
            if (!aberto) {
                var primeiro = painel.querySelector('.valores-prof-excluir__check');
                if (primeiro) {
                    primeiro.focus();
                }
            }
            atualizarResumo();
        });

        painel.querySelectorAll('.valores-prof-excluir__check').forEach(function (check) {
            check.addEventListener('change', atualizarResumo);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciarExclusaoValoresLote);
    } else {
        iniciarExclusaoValoresLote();
    }
})();
