package com.clinica.sistema.service;

import com.clinica.sistema.dto.ContratoRascunhoView;
import com.clinica.sistema.dto.SuporteContratoView;
import com.clinica.sistema.model.ContratoLicenciamentoRascunho;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.ContratoLicenciamentoRascunhoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class ContratoLicenciamentoService {

    public static final String GRUPO_CONTRATADO = "contratado";
    public static final String GRUPO_CONTRATANTE = "contratante";

    private static final DateTimeFormatter ROTULO_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm", Locale.forLanguageTag("pt-BR"));

    private static final List<String> CAMPOS_CONTRATADO = List.of(
            "contratado-nome",
            "contratado-cpf",
            "contratado-rg",
            "contratado-endereco",
            "contratado-whatsapp",
            "contratado-email",
            "contratado-pix",
            "pag-valor-total",
            "pag-valor-mensal",
            "assin-contratado-nome",
            "assin-contratado-cpf"
    );

    private static final List<String> CAMPOS_CONTRATANTE_TEXTO = List.of(
            "contratante-nome",
            "contratante-doc",
            "contratante-endereco",
            "contratante-representante",
            "pag-dia-vencimento",
            "pag-parcelas",
            "pag-outro-texto",
            "foro",
            "local",
            "dia",
            "mes",
            "ano",
            "assin-contratante-nome",
            "assin-contratante-doc",
            "test1-nome",
            "test1-cpf",
            "test2-nome",
            "test2-cpf"
    );

    private static final List<String> CAMPOS_CONTRATANTE_CHECKBOX = List.of(
            "pag-avista",
            "pag-parcelado",
            "pag-pix",
            "pag-transferencia",
            "pag-outro"
    );

    private final ContratoLicenciamentoRascunhoRepository repository;
    private final JsonMapper jsonMapper;

    public ContratoLicenciamentoService(
            ContratoLicenciamentoRascunhoRepository repository,
            JsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public ContratoRascunhoView buscarRascunho(String tipoContrato) {
        return repository.findById(resolverIdContrato(tipoContrato))
                .map(this::paraView)
                .orElseGet(this::viewVazia);
    }

    @Transactional(readOnly = true)
    public SuporteContratoView resolverStatusSuporte() {
        return idsContratosAtivos().stream()
                .map(repository::findById)
                .flatMap(Optional::stream)
                .filter(ContratoLicenciamentoRascunho::isContratanteFinalizado)
                .map(ContratoLicenciamentoRascunho::getContratanteFinalizadoEm)
                .filter(inicio -> inicio != null)
                .findFirst()
                .map(inicio -> SuporteContratoContador.calcular(inicio, LocalDate.now()))
                .orElseGet(SuporteContratoView::inativo);
    }

    @Transactional(readOnly = true)
    public String buscarValorCampo(String tipoContrato, String campo, String valorPadrao) {
        return repository.findById(resolverIdContrato(tipoContrato))
                .map(ContratoLicenciamentoRascunho::getDadosJson)
                .map(this::lerDadosJson)
                .map(dados -> dados.get(campo))
                .map(valor -> valor == null ? "" : String.valueOf(valor).trim())
                .filter(texto -> !texto.isEmpty())
                .orElse(valorPadrao);
    }

    @Transactional(readOnly = true)
    public String buscarTipoContratoFinalizado() {
        return idsContratosAtivos().stream()
                .map(repository::findById)
                .flatMap(Optional::stream)
                .filter(ContratoLicenciamentoRascunho::isContratanteFinalizado)
                .map(ContratoLicenciamentoRascunho::getId)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public ContratoRascunhoView salvarRascunho(
            String tipoContrato,
            Usuario usuario,
            Map<String, Object> dadosBrutos,
            String grupo
    ) {
        if (!GRUPO_CONTRATADO.equals(grupo) && !GRUPO_CONTRATANTE.equals(grupo)) {
            throw new IllegalArgumentException("Grupo de contrato invalido.");
        }

        String idContrato = resolverIdContrato(tipoContrato);
        ContratoLicenciamentoRascunho existente = repository.findById(idContrato).orElse(null);
        if (GRUPO_CONTRATANTE.equals(grupo) && existente != null && existente.isContratanteFinalizado()) {
            throw new IllegalStateException("Os dados da clinica estao finalizados e nao podem ser alterados.");
        }

        Map<String, Object> atual = new LinkedHashMap<>(lerDadosJson(buscarJsonAtual(idContrato)));
        Map<String, Object> novosDados = sanitizarDados(dadosBrutos, grupo);
        for (String campo : camposDoGrupo(grupo)) {
            atual.put(campo, novosDados.get(campo));
        }

        ContratoLicenciamentoRascunho rascunho = repository.findById(idContrato).orElseGet(() -> novoRascunho(idContrato));

        try {
            rascunho.setDadosJson(jsonMapper.writeValueAsString(atual));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Nao foi possivel salvar os dados do contrato.", e);
        }

        rascunho.setAtualizadoEm(LocalDateTime.now());
        rascunho.setAtualizadoPorUsuarioId(usuario.getId());
        rascunho.setAtualizadoPorNome(usuario.getNome());
        return paraView(repository.save(rascunho));
    }

    @Transactional
    public ContratoRascunhoView finalizarContratante(String tipoContrato, Usuario usuario) {
        String idContrato = resolverIdContrato(tipoContrato);
        String tipoJaFinalizado = buscarTipoContratoFinalizado();
        if (tipoJaFinalizado != null && !tipoJaFinalizado.equals(idContrato)) {
            throw new IllegalStateException(
                    "A clinica ja iniciou o contrato de " + rotuloTipoContrato(tipoJaFinalizado) + "."
            );
        }
        ContratoLicenciamentoRascunho rascunho = repository.findById(idContrato)
                .orElseThrow(() -> new IllegalStateException("Salve os dados da clinica antes de finalizar o contrato."));

        if (rascunho.isContratanteFinalizado()) {
            throw new IllegalStateException("O contrato da clinica ja foi finalizado.");
        }

        Map<String, Object> dados = lerDadosJson(rascunho.getDadosJson());
        validarDadosContratanteParaFinalizar(idContrato, dados);

        rascunho.setContratanteFinalizado(true);
        if (rascunho.getContratanteFinalizadoEm() == null) {
            rascunho.setContratanteFinalizadoEm(LocalDateTime.now());
            rascunho.setContratanteFinalizadoPorNome(usuario.getNome());
        }
        return paraView(repository.save(rascunho));
    }

    @Transactional
    public ContratoRascunhoView liberarContratante(String tipoContrato, Usuario usuario) {
        String idContrato = resolverIdContrato(tipoContrato);
        ContratoLicenciamentoRascunho rascunho = repository.findById(idContrato).orElseGet(() -> novoRascunho(idContrato));

        if (!rascunho.isContratanteFinalizado()) {
            throw new IllegalStateException("Os dados da clinica nao estao finalizados.");
        }

        rascunho.setContratanteFinalizado(false);
        rascunho.setAtualizadoEm(LocalDateTime.now());
        rascunho.setAtualizadoPorUsuarioId(usuario.getId());
        rascunho.setAtualizadoPorNome(usuario.getNome());
        return paraView(repository.save(rascunho));
    }

    public static boolean tipoContratoValido(String tipoContrato) {
        return ContratoLicenciamentoRascunho.ID_BRUTO.equals(tipoContrato)
                || ContratoLicenciamentoRascunho.ID_MENSALIDADE.equals(tipoContrato);
    }

    public static String rotuloTipoContrato(String tipoContrato) {
        if (ContratoLicenciamentoRascunho.ID_MENSALIDADE.equals(tipoContrato)) {
            return "Mensalidade";
        }
        return "Valor bruto";
    }

    private String resolverIdContrato(String tipoContrato) {
        if (!tipoContratoValido(tipoContrato)) {
            throw new IllegalArgumentException("Tipo de contrato invalido.");
        }
        return tipoContrato;
    }

    @Transactional
    public void migrarRascunhoPadraoSeNecessario() {
        if (!repository.existsById(ContratoLicenciamentoRascunho.ID_BRUTO)
                && repository.existsById(ContratoLicenciamentoRascunho.ID_PADRAO)) {
            repository.findById(ContratoLicenciamentoRascunho.ID_PADRAO).ifPresent(padrao -> {
                ContratoLicenciamentoRascunho bruto = novoRascunho(ContratoLicenciamentoRascunho.ID_BRUTO);
                bruto.setDadosJson(padrao.getDadosJson());
                bruto.setAtualizadoEm(padrao.getAtualizadoEm());
                bruto.setAtualizadoPorUsuarioId(padrao.getAtualizadoPorUsuarioId());
                bruto.setAtualizadoPorNome(padrao.getAtualizadoPorNome());
                bruto.setContratanteFinalizado(padrao.isContratanteFinalizado());
                bruto.setContratanteFinalizadoEm(padrao.getContratanteFinalizadoEm());
                bruto.setContratanteFinalizadoPorNome(padrao.getContratanteFinalizadoPorNome());
                repository.save(bruto);
            });
        }
    }

    public List<String> camposDoGrupo(String grupo) {
        if (GRUPO_CONTRATADO.equals(grupo)) {
            return CAMPOS_CONTRATADO;
        }
        LinkedHashMap<String, Object> campos = new LinkedHashMap<>();
        CAMPOS_CONTRATANTE_TEXTO.forEach(campo -> campos.put(campo, ""));
        CAMPOS_CONTRATANTE_CHECKBOX.forEach(campo -> campos.put(campo, false));
        return List.copyOf(campos.keySet());
    }

    private List<String> idsContratosAtivos() {
        return List.of(ContratoLicenciamentoRascunho.ID_BRUTO, ContratoLicenciamentoRascunho.ID_MENSALIDADE);
    }

    private String buscarJsonAtual(String idContrato) {
        return repository.findById(idContrato)
                .map(ContratoLicenciamentoRascunho::getDadosJson)
                .orElse("{}");
    }

    private ContratoLicenciamentoRascunho novoRascunho(String idContrato) {
        ContratoLicenciamentoRascunho rascunho = new ContratoLicenciamentoRascunho();
        rascunho.setId(idContrato);
        return rascunho;
    }

    private ContratoRascunhoView viewVazia() {
        String tipoFinalizado = buscarTipoContratoFinalizado();
        return new ContratoRascunhoView(
                Map.of(),
                false,
                null,
                null,
                0L,
                false,
                null,
                null,
                tipoFinalizado,
                tipoFinalizado != null ? rotuloTipoContrato(tipoFinalizado) : null
        );
    }

    private ContratoRascunhoView paraView(ContratoLicenciamentoRascunho rascunho) {
        Map<String, Object> dados = lerDadosJson(rascunho.getDadosJson());
        boolean salvo = !dados.isEmpty();
        String atualizadoEm = rascunho.getAtualizadoEm() != null
                ? ROTULO_DATA.format(rascunho.getAtualizadoEm())
                : null;
        long versao = rascunho.getAtualizadoEm() != null
                ? rascunho.getAtualizadoEm().atZone(ZoneId.systemDefault()).toEpochSecond()
                : 0L;
        String finalizadoEm = rascunho.getContratanteFinalizadoEm() != null
                ? ROTULO_DATA.format(rascunho.getContratanteFinalizadoEm())
                : null;
        String tipoFinalizado = buscarTipoContratoFinalizado();
        return new ContratoRascunhoView(
                dados,
                salvo,
                atualizadoEm,
                rascunho.getAtualizadoPorNome(),
                versao,
                rascunho.isContratanteFinalizado(),
                finalizadoEm,
                rascunho.getContratanteFinalizadoPorNome(),
                tipoFinalizado,
                tipoFinalizado != null ? rotuloTipoContrato(tipoFinalizado) : null
        );
    }

    private void validarDadosContratanteParaFinalizar(String tipoContrato, Map<String, Object> dados) {
        if (dados == null || dados.isEmpty()) {
            throw new IllegalStateException("Preencha e salve os dados da clinica antes de iniciar o contrato.");
        }

        List<String> camposObrigatorios = new ArrayList<>(List.of(
                "contratante-nome",
                "contratante-doc",
                "contratante-endereco",
                "contratante-representante"
        ));
        if (ContratoLicenciamentoRascunho.ID_MENSALIDADE.equals(tipoContrato)) {
            camposObrigatorios.add("pag-valor-mensal");
            camposObrigatorios.add("pag-dia-vencimento");
        } else {
            camposObrigatorios.add("pag-valor-total");
        }

        List<String> faltando = camposObrigatorios.stream()
                .filter(campo -> textoVazio(dados.get(campo)))
                .toList();
        if (!faltando.isEmpty()) {
            throw new IllegalStateException("Preencha e salve os dados principais da clinica antes de iniciar o contrato.");
        }

        boolean temFormaPagamento = Boolean.TRUE.equals(dados.get("pag-pix"))
                || Boolean.TRUE.equals(dados.get("pag-transferencia"))
                || Boolean.TRUE.equals(dados.get("pag-outro"))
                || Boolean.TRUE.equals(dados.get("pag-avista"))
                || Boolean.TRUE.equals(dados.get("pag-parcelado"));
        if (!temFormaPagamento) {
            throw new IllegalStateException("Selecione e salve uma forma de pagamento antes de iniciar o contrato.");
        }

        if (Boolean.TRUE.equals(dados.get("pag-parcelado")) && textoVazio(dados.get("pag-parcelas"))) {
            throw new IllegalStateException("Informe e salve a quantidade de parcelas antes de iniciar o contrato.");
        }
        if (Boolean.TRUE.equals(dados.get("pag-outro")) && textoVazio(dados.get("pag-outro-texto"))) {
            throw new IllegalStateException("Informe e salve qual e a outra forma de pagamento antes de iniciar o contrato.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lerDadosJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return jsonMapper.readValue(json, Map.class);
        } catch (JacksonException e) {
            return Map.of();
        }
    }

    private Map<String, Object> sanitizarDados(Map<String, Object> dadosBrutos, String grupo) {
        Map<String, Object> dados = new LinkedHashMap<>();
        if (dadosBrutos == null) {
            dadosBrutos = Map.of();
        }
        if (GRUPO_CONTRATADO.equals(grupo)) {
            for (String campo : CAMPOS_CONTRATADO) {
                dados.put(campo, limitarTexto(dadosBrutos.get(campo), 500));
            }
            return dados;
        }
        for (String campo : CAMPOS_CONTRATANTE_TEXTO) {
            dados.put(campo, limitarTexto(dadosBrutos.get(campo), 500));
        }
        for (String campo : CAMPOS_CONTRATANTE_CHECKBOX) {
            dados.put(campo, interpretarBooleano(dadosBrutos.get(campo)));
        }
        return dados;
    }

    private String limitarTexto(Object valor, int maximo) {
        if (valor == null) {
            return "";
        }
        String texto = String.valueOf(valor).trim();
        if (texto.length() <= maximo) {
            return texto;
        }
        return texto.substring(0, maximo);
    }

    private boolean textoVazio(Object valor) {
        return valor == null || String.valueOf(valor).trim().isEmpty();
    }

    private boolean interpretarBooleano(Object valor) {
        if (valor instanceof Boolean bool) {
            return bool;
        }
        if (valor == null) {
            return false;
        }
        String texto = String.valueOf(valor).trim().toLowerCase(Locale.ROOT);
        return "true".equals(texto) || "1".equals(texto) || "on".equals(texto);
    }
}
