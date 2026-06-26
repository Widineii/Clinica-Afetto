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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            "assin-contratado-nome",
            "assin-contratado-cpf"
    );

    private static final List<String> CAMPOS_CONTRATANTE_TEXTO = List.of(
            "contratante-nome",
            "contratante-doc",
            "contratante-endereco",
            "contratante-representante",
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
    public ContratoRascunhoView buscarRascunho() {
        return repository.findById(ContratoLicenciamentoRascunho.ID_PADRAO)
                .map(this::paraView)
                .orElseGet(this::viewVazia);
    }

    @Transactional(readOnly = true)
    public SuporteContratoView resolverStatusSuporte() {
        return repository.findById(ContratoLicenciamentoRascunho.ID_PADRAO)
                .map(ContratoLicenciamentoRascunho::getContratanteFinalizadoEm)
                .filter(inicio -> inicio != null)
                .map(inicio -> SuporteContratoContador.calcular(inicio, LocalDate.now()))
                .orElseGet(SuporteContratoView::inativo);
    }

    @Transactional
    public ContratoRascunhoView salvarRascunho(Usuario usuario, Map<String, Object> dadosBrutos, String grupo) {
        if (!GRUPO_CONTRATADO.equals(grupo) && !GRUPO_CONTRATANTE.equals(grupo)) {
            throw new IllegalArgumentException("Grupo de contrato invalido.");
        }

        ContratoLicenciamentoRascunho existente = repository
                .findById(ContratoLicenciamentoRascunho.ID_PADRAO)
                .orElse(null);
        if (GRUPO_CONTRATANTE.equals(grupo) && existente != null && existente.isContratanteFinalizado()) {
            throw new IllegalStateException("Os dados da clinica estao finalizados e nao podem ser alterados.");
        }

        Map<String, Object> atual = new LinkedHashMap<>(lerDadosJson(buscarJsonAtual()));
        Map<String, Object> novosDados = sanitizarDados(dadosBrutos, grupo);
        for (String campo : camposDoGrupo(grupo)) {
            atual.put(campo, novosDados.get(campo));
        }

        ContratoLicenciamentoRascunho rascunho = repository
                .findById(ContratoLicenciamentoRascunho.ID_PADRAO)
                .orElseGet(this::novoRascunho);

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
    public ContratoRascunhoView finalizarContratante(Usuario usuario) {
        ContratoLicenciamentoRascunho rascunho = repository
                .findById(ContratoLicenciamentoRascunho.ID_PADRAO)
                .orElseThrow(() -> new IllegalStateException("Salve os dados da clinica antes de finalizar o contrato."));

        if (rascunho.isContratanteFinalizado()) {
            throw new IllegalStateException("O contrato da clinica ja foi finalizado.");
        }

        Map<String, Object> dados = lerDadosJson(rascunho.getDadosJson());
        if (!possuiDadosContratante(dados)) {
            throw new IllegalStateException("Preencha e salve os dados da clinica antes de finalizar.");
        }

        rascunho.setContratanteFinalizado(true);
        if (rascunho.getContratanteFinalizadoEm() == null) {
            rascunho.setContratanteFinalizadoEm(LocalDateTime.now());
            rascunho.setContratanteFinalizadoPorNome(usuario.getNome());
        }
        return paraView(repository.save(rascunho));
    }

    @Transactional
    public ContratoRascunhoView liberarContratante(Usuario usuario) {
        ContratoLicenciamentoRascunho rascunho = repository
                .findById(ContratoLicenciamentoRascunho.ID_PADRAO)
                .orElseGet(this::novoRascunho);

        if (!rascunho.isContratanteFinalizado()) {
            throw new IllegalStateException("Os dados da clinica nao estao finalizados.");
        }

        rascunho.setContratanteFinalizado(false);
        rascunho.setAtualizadoEm(LocalDateTime.now());
        rascunho.setAtualizadoPorUsuarioId(usuario.getId());
        rascunho.setAtualizadoPorNome(usuario.getNome());
        return paraView(repository.save(rascunho));
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

    private String buscarJsonAtual() {
        return repository.findById(ContratoLicenciamentoRascunho.ID_PADRAO)
                .map(ContratoLicenciamentoRascunho::getDadosJson)
                .orElse("{}");
    }

    private ContratoLicenciamentoRascunho novoRascunho() {
        ContratoLicenciamentoRascunho rascunho = new ContratoLicenciamentoRascunho();
        rascunho.setId(ContratoLicenciamentoRascunho.ID_PADRAO);
        return rascunho;
    }

    private ContratoRascunhoView viewVazia() {
        return new ContratoRascunhoView(Map.of(), false, null, null, 0L, false, null, null);
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
        return new ContratoRascunhoView(
                dados,
                salvo,
                atualizadoEm,
                rascunho.getAtualizadoPorNome(),
                versao,
                rascunho.isContratanteFinalizado(),
                finalizadoEm,
                rascunho.getContratanteFinalizadoPorNome()
        );
    }

    private boolean possuiDadosContratante(Map<String, Object> dados) {
        if (dados == null || dados.isEmpty()) {
            return false;
        }
        return CAMPOS_CONTRATANTE_TEXTO.stream()
                .filter(campo -> !"dia".equals(campo) && !"mes".equals(campo) && !"ano".equals(campo))
                .anyMatch(campo -> {
                    Object valor = dados.get(campo);
                    return valor != null && !String.valueOf(valor).trim().isEmpty();
                });
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
