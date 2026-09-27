package br.com.tecnodreamer.petconnect.usuario.application;

import br.com.tecnodreamer.petconnect.usuario.domain.Papel;
import br.com.tecnodreamer.petconnect.usuario.domain.PoliticaSenha;
import br.com.tecnodreamer.petconnect.usuario.domain.Usuario;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.PapelRepository;
import br.com.tecnodreamer.petconnect.usuario.infrastructure.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro ADMIN a partir de ADMIN_USERNAME/ADMIN_EMAIL/ADMIN_PASSWORD
 * quando ainda não existe nenhum. Evita senha fixa em script SQL.
 */
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapAdminProperties props;
    private final UsuarioRepository usuarios;
    private final PapelRepository papeis;
    private final PasswordEncoder encoder;

    public AdminBootstrap(BootstrapAdminProperties props, UsuarioRepository usuarios, PapelRepository papeis,
            PasswordEncoder encoder) {
        this.props = props;
        this.usuarios = usuarios;
        this.papeis = papeis;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.existsByPapelNome(Papel.ADMIN)) {
            return;
        }
        if (!props.configurado()) {
            log.warn("Nenhum usuário ADMIN cadastrado. Defina ADMIN_USERNAME, ADMIN_EMAIL e ADMIN_PASSWORD para criar o primeiro.");
            return;
        }
        PoliticaSenha.violacao(props.password(), props.username(), props.email()).ifPresent(msg -> {
            throw new IllegalStateException("ADMIN_PASSWORD não atende à política de senha: " + msg);
        });
        Papel admin = papeis.findByNome(Papel.ADMIN).orElseThrow();
        usuarios.save(new Usuario(props.username(), props.email(), encoder.encode(props.password()), admin));
        log.info("Usuário ADMIN inicial '{}' criado.", props.username());
    }
}
