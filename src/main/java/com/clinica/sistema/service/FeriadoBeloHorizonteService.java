package com.clinica.sistema.service;

import com.clinica.sistema.dto.DiaEspecialAgendaView;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FeriadoBeloHorizonteService {

    public Map<String, DiaEspecialAgendaView> resolverDiasEspeciaisDaSemana(List<LocalDate> diasSemana) {
        Map<String, DiaEspecialAgendaView> diasEspeciais = new LinkedHashMap<>();
        if (diasSemana == null || diasSemana.isEmpty()) {
            return diasEspeciais;
        }
        for (LocalDate dia : diasSemana) {
            resolverDiaEspecial(dia).ifPresent(especial -> diasEspeciais.put(dia.toString(), especial));
        }
        return diasEspeciais;
    }

    public Optional<DiaEspecialAgendaView> resolverDiaEspecial(LocalDate data) {
        if (data == null) {
            return Optional.empty();
        }
        return nomeFeriado(data).map(nome -> new DiaEspecialAgendaView(nome, false))
                .or(() -> nomePontoFacultativo(data).map(nome -> new DiaEspecialAgendaView(nome, true)));
    }

    public Optional<String> nomeFeriado(LocalDate data) {
        int ano = data.getYear();
        LocalDate pascoa = calcularPascoa(ano);

        if (data.equals(LocalDate.of(ano, 1, 1))) {
            return Optional.of("Ano Novo");
        }
        if (data.equals(pascoa.minusDays(2))) {
            return Optional.of("Sexta-feira Santa");
        }
        if (data.equals(LocalDate.of(ano, 4, 21))) {
            return Optional.of("Tiradentes");
        }
        if (data.equals(LocalDate.of(ano, 5, 1))) {
            return Optional.of("Dia do Trabalhador");
        }
        if (data.equals(pascoa.plusDays(60))) {
            return Optional.of("Corpus Christi");
        }
        if (data.equals(LocalDate.of(ano, 8, 15))) {
            return Optional.of("Assunção de Nossa Senhora da Boa Viagem");
        }
        if (data.equals(LocalDate.of(ano, 9, 7))) {
            return Optional.of("Independência do Brasil");
        }
        if (data.equals(LocalDate.of(ano, 10, 12))) {
            return Optional.of("Nossa Senhora Aparecida");
        }
        if (data.equals(LocalDate.of(ano, 11, 2))) {
            return Optional.of("Finados");
        }
        if (data.equals(LocalDate.of(ano, 11, 15))) {
            return Optional.of("Proclamação da República");
        }
        if (data.equals(LocalDate.of(ano, 11, 20))) {
            return Optional.of("Consciência Negra");
        }
        if (data.equals(LocalDate.of(ano, 12, 8))) {
            return Optional.of("Imaculada Conceição");
        }
        if (data.equals(LocalDate.of(ano, 12, 12))) {
            return Optional.of("Aniversário de Belo Horizonte");
        }
        if (data.equals(LocalDate.of(ano, 12, 25))) {
            return Optional.of("Natal");
        }
        return Optional.empty();
    }

    public Optional<String> nomePontoFacultativo(LocalDate data) {
        int ano = data.getYear();
        LocalDate pascoa = calcularPascoa(ano);

        if (data.equals(pascoa.minusDays(48))) {
            return Optional.of("Segunda de Carnaval");
        }
        if (data.equals(pascoa.minusDays(47))) {
            return Optional.of("Terça de Carnaval");
        }
        if (data.equals(pascoa.minusDays(46))) {
            return Optional.of("Quarta-feira de Cinzas");
        }
        if (data.equals(resolverDiaServidorPublico(ano))) {
            return Optional.of("Dia do Servidor Público");
        }
        if (data.equals(LocalDate.of(ano, 12, 24))) {
            return Optional.of("Véspera de Natal");
        }
        if (data.equals(LocalDate.of(ano, 12, 31))) {
            return Optional.of("Véspera de Ano Novo");
        }
        return Optional.empty();
    }

    static LocalDate resolverDiaServidorPublico(int ano) {
        LocalDate diaLegal = LocalDate.of(ano, 10, 28);
        if (diaLegal.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return diaLegal.minusDays(1);
        }
        if (diaLegal.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return diaLegal.plusDays(1);
        }
        return diaLegal;
    }

    static LocalDate calcularPascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(ano, mes, dia);
    }
}
