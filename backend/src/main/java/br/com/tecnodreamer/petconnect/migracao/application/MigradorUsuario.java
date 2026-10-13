package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.DestinoMigracao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Usuários (DA-12…DA-16). A consulta NÃO lê senha_hash, token_senha nem token_expira.
 * Senha nova = BCrypt de 32 bytes aleatórios descartados: ninguém a conhece, o acesso é
 * pelo "esqueci a senha". Sem e-mail utilizável → inativo + pendência (e-mail técnico
 * {@code legado-<id>@migracao.invalid}, porque a coluna é NOT NULL/UNIQUE).
 */
@Component
@Order(20)
public class MigradorUsuario implements Migrador {

    static final String DOMINIO_TECNICO = "@migracao.invalid";

    private final DestinoMigracao destino;
    private final PasswordEncoder encoder;
    private final String usernameBootstrap;
    private final SecureRandom aleatorio = new SecureRandom();

    public MigradorUsuario(DestinoMigracao destino, PasswordEncoder encoder,
            @Value("${app.bootstrap-admin.username:}") String usernameBootstrap) {
        this.destino = destino;
        this.encoder = encoder;
        this.usernameBootstrap = usernameBootstrap == null ? "" : usernameBootstrap.trim();
    }

    @Override
    public String entidade() {
        return "usuario";
    }

    @Override
    public String consulta() {
        return """
                SELECT u.id, u.usuario, u.email, u.papel_id, u.ativo,
                       EXISTS (SELECT 1 FROM super_usuario s WHERE s.usuario_id = u.id) AS super_usuario
                  FROM usuario u WHERE u.id > :ultimo ORDER BY u.id LIMIT :lote
                """;
    }

    @Override
    public Desfecho migrar(Linha l, Contexto ctx) {
        List<String> pend = new ArrayList<>();
        Long papelId = papel(l, ctx, pend);
        String username = username(l, pend);
        boolean ativo = Campos.ativo(l, pend);
        String email = Campos.email(l.texto("email"));
        if (email == null) {
            pend.add("USUARIO_SEM_EMAIL_RECUPERACAO");
        } else if (destino.emailUsuarioExiste(email)) {
            pend.add("EMAIL_DUPLICADO");
            email = null;
        }
        if (email == null) {
            email = "legado-" + l.id() + DOMINIO_TECNICO;
            ativo = false;
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("username", username);
        v.put("email", email);
        v.put("senha_hash", encoder.encode(segredoDescartavel()));
        v.put("papel_id", papelId);
        v.put("ativo", ativo);
        return Desfecho.migrado(destino.inserir("usuario", v), pend);
    }

    private Long papel(Linha l, Contexto ctx, List<String> pend) {
        if (Boolean.TRUE.equals(ConversorLegado.booleano(l.bruto("super_usuario"), false).valor())) {
            return destino.papelId("ADMIN").orElseThrow();
        }
        Long legado = l.longo("papel_id");
        Optional<Long> mapeado = legado == null ? Optional.empty() : ctx.idNovo("papel", legado);
        if (mapeado.isPresent()) {
            return mapeado.get();
        }
        pend.add("PAPEL_DESCONHECIDO");
        return destino.papelId("VENDAS").orElseThrow();
    }

    /** Colisão (inclusive com o ADMIN do bootstrap) → sufixo _legado, _legado_2, ... */
    private String username(Linha l, List<String> pend) {
        String base = TextoNormalizado.limitar(l.texto("usuario"), 50);
        if (base == null) {
            pend.add("USERNAME_AUSENTE");
            base = "usuario_" + l.id();
        }
        if (!ocupado(base)) {
            return base;
        }
        pend.add("USERNAME_RENOMEADO");
        String raiz = TextoNormalizado.limitar(base, 38) + "_legado";
        String candidato = raiz;
        for (int n = 2; ocupado(candidato); n++) {
            candidato = raiz + "_" + n;
        }
        return candidato;
    }

    private boolean ocupado(String username) {
        return username.equalsIgnoreCase(usernameBootstrap) || destino.usernameExiste(username);
    }

    private String segredoDescartavel() {
        byte[] b = new byte[32];
        aleatorio.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }
}
