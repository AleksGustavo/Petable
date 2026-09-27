package br.com.tecnodreamer.petconnect.migracao.application;

import br.com.tecnodreamer.petconnect.migracao.infrastructure.LegadoConexao;
import br.com.tecnodreamer.petconnect.migracao.infrastructure.MigracaoProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ferramenta offline (DA-02, DA-03): só existe com petconnect.migracao.legado=true.
 * Por padrão encerra a aplicação ao final (código 0 = concluída, 1 = falhou), para o
 * container {@code api-migracao} terminar sozinho.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "petconnect.migracao.legado", havingValue = "true")
public class MigracaoLegadoRunner implements ApplicationRunner {

    private final MigracaoLegadoService service;
    private final LegadoConexao legado;
    private final MigracaoProperties props;
    private final ApplicationContext contexto;

    public MigracaoLegadoRunner(MigracaoLegadoService service, LegadoConexao legado, MigracaoProperties props,
            ApplicationContext contexto) {
        this.service = service;
        this.legado = legado;
        this.props = props;
        this.contexto = contexto;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResultadoMigracao r = service.executar(props.opcoes(), legado.jdbc());
        int codigo = r.status() == ResultadoMigracao.Status.CONCLUIDA ? 0 : 1;
        if (props.encerrar()) {
            System.exit(SpringApplication.exit(contexto, () -> codigo));
        }
    }
}
