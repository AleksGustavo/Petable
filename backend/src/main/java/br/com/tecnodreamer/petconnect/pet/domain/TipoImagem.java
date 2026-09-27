package br.com.tecnodreamer.petconnect.pet.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos aceitos para a foto do pet. O tipo é detectado pela assinatura
 * (magic bytes) do conteúdo — o Content-Type e o nome enviados pelo cliente
 * não são confiáveis. SVG e GIF ficam de fora de propósito.
 */
public enum TipoImagem {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extensao;

    TipoImagem(String contentType, String extensao) {
        this.contentType = contentType;
        this.extensao = extensao;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtensao() {
        return extensao;
    }

    public static Optional<TipoImagem> detectar(byte[] b) {
        if (b == null) {
            return Optional.empty();
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return Optional.of(PNG);
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public static Optional<TipoImagem> porChave(String chave) {
        return Arrays.stream(values()).filter(t -> chave != null && chave.endsWith("." + t.extensao)).findFirst();
    }
}
