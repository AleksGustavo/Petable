package br.com.tecnodreamer.petconnect.relatorio.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV para Excel em português (DA-10): BOM UTF-8, separador {@code ;}, decimal com
 * vírgula, datas dd/MM/yyyy, linhas CRLF; campos com {@code ;}, aspas ou quebra de
 * linha vão entre aspas (aspas duplicadas).
 */
public final class Csv {

    private static final String BOM = "\uFEFF";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Csv() {
    }

    public static byte[] gerar(List<String> cabecalho, List<List<Object>> linhas) {
        StringBuilder sb = new StringBuilder(BOM);
        sb.append(linha(cabecalho.stream().map(Object.class::cast).toList()));
        for (List<Object> l : linhas) {
            sb.append(linha(l));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String linha(List<Object> campos) {
        return campos.stream().map(Csv::campo).collect(Collectors.joining(";")) + "\r\n";
    }

    static String campo(Object v) {
        String s = switch (v) {
            case null -> "";
            case BigDecimal b -> b.toPlainString().replace('.', ',');
            case LocalDateTime dt -> dt.format(DATA_HORA);
            case LocalDate d -> d.format(DATA);
            default -> v.toString();
        };
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
