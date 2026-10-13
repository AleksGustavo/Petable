package br.com.tecnodreamer.petconnect.usuario;

import br.com.tecnodreamer.petconnect.usuario.domain.Papel;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

public final class UsuarioFixtures {

    private UsuarioFixtures() {
    }

    public static Papel papel(long id, String nome) {
        Papel p = BeanUtils.instantiateClass(Papel.class);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "nome", nome);
        return p;
    }

    public static final Papel ADMIN = papel(1, "ADMIN");
    public static final Papel VENDAS = papel(2, "VENDAS");
    public static final Papel SERVICO = papel(3, "SERVICO");

    public static Usuario usuario(long id, String username, Papel papel, String senhaHash) {
        Usuario u = new Usuario(username, username + "@petconnect.test", senhaHash, papel);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}
