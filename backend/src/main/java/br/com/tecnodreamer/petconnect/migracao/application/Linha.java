package br.com.tecnodreamer.petconnect.migracao.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Uma linha lida do legado, com leitura tolerante aos tipos devolvidos pelo driver. */
public record Linha(Map<String, Object> valores) {

    public static Linha of(Object... chavesEValores) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            m.put((String) chavesEValores[i], chavesEValores[i + 1]);
        }
        return new Linha(m);
    }

    public Long id() {
        return longo("id");
    }

    public Object bruto(String coluna) {
        return valores.get(coluna);
    }

    /** Texto aparado; vazio vira {@code null}. */
    public String texto(String coluna) {
        Object v = valores.get(coluna);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public Long longo(String coluna) {
        Object v = valores.get(coluna);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1L : 0L;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }

    public Integer inteiro(String coluna) {
        Long l = longo(coluna);
        return l == null ? null : Math.toIntExact(l);
    }

    /** Dinheiro/decimal com escala 2 HALF_UP. */
    public BigDecimal decimal(String coluna) {
        Object v = valores.get(coluna);
        if (v == null) {
            return null;
        }
        BigDecimal d = v instanceof BigDecimal b ? b : new BigDecimal(v.toString().trim());
        return d.setScale(2, RoundingMode.HALF_UP);
    }

    public LocalDate data(String coluna) {
        Object v = valores.get(coluna);
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (v instanceof LocalDateTime dt) {
            return dt.toLocalDate();
        }
        if (v instanceof Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(v.toString().trim().substring(0, 10));
    }

    /** Datas do legado são horário local de São Paulo, como no sistema novo. */
    public LocalDateTime dataHora(String coluna) {
        Object v = valores.get(coluna);
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime dt) {
            return dt;
        }
        if (v instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        if (v instanceof LocalDate d) {
            return d.atStartOfDay();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().atStartOfDay();
        }
        return LocalDateTime.parse(v.toString().trim().replace(' ', 'T'));
    }
}
