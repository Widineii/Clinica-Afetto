package com.clinica.sistema.service;

import com.clinica.sistema.config.PerfilFotoProperties;
import com.clinica.sistema.model.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Service
public class PerfilFotoService {

    private static final Set<String> EXTENSOES_PERMITIDAS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> CONTENT_TYPES_PERMITIDOS = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final PerfilFotoProperties properties;

    public PerfilFotoService(PerfilFotoProperties properties) {
        this.properties = properties;
    }

    public String salvarFoto(Usuario usuario, MultipartFile arquivo) {
        if (usuario == null || usuario.getId() == null) {
            throw new RuntimeException("Usuario invalido para salvar foto de perfil.");
        }
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RuntimeException("Selecione uma imagem para enviar.");
        }
        if (arquivo.getSize() > properties.getTamanhoMaxBytes()) {
            throw new RuntimeException("A foto deve ter no maximo 2 MB.");
        }
        String extensao = resolverExtensao(arquivo);
        try {
            Path diretorio = garantirDiretorio();
            removerArquivosDoUsuario(diretorio, usuario.getId());
            Path destino = diretorio.resolve(usuario.getId() + "." + extensao);
            arquivo.transferTo(destino);
            return "/uploads/perfis/" + usuario.getId() + "." + extensao;
        } catch (IOException ex) {
            throw new RuntimeException("Nao foi possivel salvar a foto de perfil.", ex);
        }
    }

    public void removerFoto(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return;
        }
        try {
            removerArquivosDoUsuario(garantirDiretorio(), usuario.getId());
        } catch (IOException ex) {
            throw new RuntimeException("Nao foi possivel remover a foto de perfil.", ex);
        }
        usuario.setFotoPerfil(null);
    }

    public String resolverUrlPublica(Usuario usuario) {
        if (usuario == null || usuario.getFotoPerfil() == null || usuario.getFotoPerfil().isBlank()) {
            return null;
        }
        if (!arquivoExisteNoDisco(usuario.getFotoPerfil())) {
            return null;
        }
        return usuario.getFotoPerfil();
    }

    private boolean arquivoExisteNoDisco(String caminhoPublico) {
        if (caminhoPublico == null || !caminhoPublico.startsWith("/uploads/perfis/")) {
            return false;
        }
        String nomeArquivo = caminhoPublico.substring("/uploads/perfis/".length());
        if (nomeArquivo.isBlank() || nomeArquivo.contains("..")) {
            return false;
        }
        Path arquivo = properties.resolverDiretorio().resolve(nomeArquivo);
        return Files.isRegularFile(arquivo);
    }

    private Path garantirDiretorio() throws IOException {
        Path diretorio = properties.resolverDiretorio();
        Files.createDirectories(diretorio);
        return diretorio;
    }

    private void removerArquivosDoUsuario(Path diretorio, Long usuarioId) throws IOException {
        if (!Files.isDirectory(diretorio) || usuarioId == null) {
            return;
        }
        String prefixo = usuarioId + ".";
        try (var stream = Files.list(diretorio)) {
            stream.filter(path -> path.getFileName().toString().startsWith(prefixo))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            /* melhor esforco */
                        }
                    });
        }
    }

    private String resolverExtensao(MultipartFile arquivo) {
        String nomeOriginal = arquivo.getOriginalFilename() != null
                ? arquivo.getOriginalFilename().toLowerCase(Locale.ROOT)
                : "";
        String extensao = extrairExtensao(nomeOriginal);
        String contentType = resolverContentType(arquivo.getContentType(), extensao, nomeOriginal);
        if (!CONTENT_TYPES_PERMITIDOS.contains(contentType)) {
            if ("heic".equals(extensao) || "heif".equals(extensao)) {
                throw new RuntimeException(
                        "Fotos HEIC do iPhone nao sao suportadas. Em Ajustes > Camera > Formatos, use Mais compativel (JPEG).");
            }
            throw new RuntimeException("Use uma imagem JPG, PNG ou WebP.");
        }
        if ("jpeg".equals(extensao)) {
            extensao = "jpg";
        }
        if (!EXTENSOES_PERMITIDAS.contains(extensao)) {
            extensao = switch (contentType) {
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                default -> "jpg";
            };
        }
        return extensao;
    }

    private static String extrairExtensao(String nomeOriginal) {
        int ponto = nomeOriginal.lastIndexOf('.');
        if (ponto >= 0 && ponto < nomeOriginal.length() - 1) {
            return nomeOriginal.substring(ponto + 1);
        }
        return "";
    }

    private static String resolverContentType(String contentTypeInformado, String extensao, String nomeOriginal) {
        String contentType = contentTypeInformado != null
                ? contentTypeInformado.toLowerCase(Locale.ROOT).strip()
                : "";
        if (CONTENT_TYPES_PERMITIDOS.contains(contentType)) {
            return contentType;
        }
        return switch (extensao) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "heic", "heif" -> "image/heic";
            default -> inferirContentTypePeloNome(nomeOriginal);
        };
    }

    private static String inferirContentTypePeloNome(String nomeOriginal) {
        if (nomeOriginal.endsWith(".png")) {
            return "image/png";
        }
        if (nomeOriginal.endsWith(".webp")) {
            return "image/webp";
        }
        if (nomeOriginal.endsWith(".jpg") || nomeOriginal.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "";
    }
}
