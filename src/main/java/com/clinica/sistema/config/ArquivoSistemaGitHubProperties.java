package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.arquivo-sistema")
public class ArquivoSistemaGitHubProperties {

    private String githubOwner = "Widineii";
    private String githubRepo = "Clinica-Afetto";
    private String githubBranch = "main";
    private String githubToken = "";
    private int cacheMinutos = 10;

    public String getGithubOwner() {
        return githubOwner;
    }

    public void setGithubOwner(String githubOwner) {
        this.githubOwner = githubOwner;
    }

    public String getGithubRepo() {
        return githubRepo;
    }

    public void setGithubRepo(String githubRepo) {
        this.githubRepo = githubRepo;
    }

    public String getGithubBranch() {
        return githubBranch;
    }

    public void setGithubBranch(String githubBranch) {
        this.githubBranch = githubBranch;
    }

    public String getGithubToken() {
        return githubToken;
    }

    public void setGithubToken(String githubToken) {
        this.githubToken = githubToken;
    }

    public int getCacheMinutos() {
        return cacheMinutos;
    }

    public void setCacheMinutos(int cacheMinutos) {
        this.cacheMinutos = cacheMinutos;
    }

    public String resolverUrlRepositorio() {
        return "https://github.com/" + githubOwner + "/" + githubRepo;
    }

    public String resolverUrlDownloadZip() {
        return resolverUrlRepositorio() + "/archive/refs/heads/" + githubBranch + ".zip";
    }

    public String resolverUrlRaw(String caminhoRelativo) {
        return "https://raw.githubusercontent.com/"
                + githubOwner + "/" + githubRepo + "/" + githubBranch + "/" + caminhoRelativo;
    }

    public String resolverUrlArquivoNoGitHub(String caminhoRelativo) {
        return resolverUrlRepositorio() + "/blob/" + githubBranch + "/" + caminhoRelativo;
    }
}
