package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
public final class AcompanhamentoAgendaFiltros {

    private static final DateTimeFormatter ROTULO_PERIODO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private AcompanhamentoAgendaFiltros() {
    }

    public static final String PARAM_PROFISSIONAL_TODOS = "todos";

    /**
     * @param profissionalId {@code null} = todos os profissionais; senão filtra pelo id.
     */
    public record FiltroProfissional(Long profissionalId) {
        public boolean todos() {
            return profissionalId == null;
        }

        public String parametroUrl() {
            return profissionalId == null ? PARAM_PROFISSIONAL_TODOS : profissionalId.toString();
        }

        public static FiltroProfissional fromParam(String valor) {
            if (valor == null || valor.isBlank() || PARAM_PROFISSIONAL_TODOS.equalsIgnoreCase(valor.trim())) {
                return new FiltroProfissional(null);
            }
            try {
                long id = Long.parseLong(valor.trim());
                return id > 0 ? new FiltroProfissional(id) : new FiltroProfissional(null);
            } catch (NumberFormatException ex) {
                return new FiltroProfissional(null);
            }
        }
    }

    public enum Periodo {
        HOJE("hoje", "Hoje"),
        AMANHA("amanha", "Amanhã"),
        PROXIMOS_3_DIAS("proximos3", "Próximos 3 dias"),
        SEMANA("semana", "Semana inteira");

        private final String param;
        private final String rotulo;

        Periodo(String param, String rotulo) {
            this.param = param;
            this.rotulo = rotulo;
        }

        public String getParam() {
            return param;
        }

        public String getRotulo() {
            return rotulo;
        }

        public static Periodo fromParam(String valor) {
            if (valor == null) {
                return HOJE;
            }
            for (Periodo periodo : values()) {
                if (periodo.param.equalsIgnoreCase(valor.trim())) {
                    return periodo;
                }
            }
            return HOJE;
        }
    }

    public enum RecorrenciaConsulta {
        TODOS("todos", "Todas"),
        AVULSO("avulso", "Avulso"),
        FIXO("fixo", "Fixo semanal"),
        QUINZENAL("quinzenal", "Quinzenal"),
        MENSAL("mensal", "Mensal");

        private final String param;
        private final String rotulo;

        RecorrenciaConsulta(String param, String rotulo) {
            this.param = param;
            this.rotulo = rotulo;
        }

        public String getParam() {
            return param;
        }

        public String getRotulo() {
            return rotulo;
        }

        public static RecorrenciaConsulta fromParam(String valor) {
            if (valor == null) {
                return TODOS;
            }
            for (RecorrenciaConsulta recorrencia : values()) {
                if (recorrencia.param.equalsIgnoreCase(valor.trim())) {
                    return recorrencia;
                }
            }
            return TODOS;
        }

        public boolean aceita(Agendamento agendamento) {
            if (agendamento == null) {
                return false;
            }
            return switch (this) {
                case TODOS -> true;
                case AVULSO -> agendamento.isAvulsoSemMensal();
                case FIXO -> agendamento.isFixoSemanal();
                case QUINZENAL -> agendamento.isQuinzenal();
                case MENSAL -> agendamento.isMensal();
            };
        }
    }

    public record IntervaloPeriodo(LocalDate inicio, LocalDate fim) {
        public String rotulo() {
            if (inicio == null || fim == null) {
                return "";
            }
            if (inicio.equals(fim)) {
                return inicio.format(ROTULO_PERIODO);
            }
            return inicio.format(ROTULO_PERIODO) + " a " + fim.format(ROTULO_PERIODO);
        }
    }

    public static IntervaloPeriodo resolverIntervalo(Periodo periodo, LocalDate referenciaSemana) {
        LocalDate hoje = LocalDate.now();
        return switch (periodo) {
            case HOJE -> new IntervaloPeriodo(hoje, hoje);
            case AMANHA -> {
                LocalDate amanha = hoje.plusDays(1);
                yield new IntervaloPeriodo(amanha, amanha);
            }
            case PROXIMOS_3_DIAS -> new IntervaloPeriodo(hoje, hoje.plusDays(2));
            case SEMANA -> {
                LocalDate ref = referenciaSemana != null ? referenciaSemana : hoje;
                LocalDate inicio = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new IntervaloPeriodo(inicio, inicio.plusDays(6));
            }
        };
    }
}
