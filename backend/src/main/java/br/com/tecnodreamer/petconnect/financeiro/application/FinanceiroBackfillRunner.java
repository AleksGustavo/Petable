package br.com.tecnodreamer.petconnect.financeiro.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Só existe com --petconnect.financeiro.backfill=true (DA-18). Não há endpoint público de backfill. */
@Component
@ConditionalOnProperty(name = "petconnect.financeiro.backfill", havingValue = "true")
public class FinanceiroBackfillRunner implements ApplicationRunner {

    private final FinanceiroBackfill backfill;

    public FinanceiroBackfillRunner(FinanceiroBackfill backfill) {
        this.backfill = backfill;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill.executar();
    }
}
