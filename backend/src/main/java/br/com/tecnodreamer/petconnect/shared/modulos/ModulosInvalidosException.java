package br.com.tecnodreamer.petconnect.shared.modulos;

/** Configuração de módulos inválida: o sistema não sobe (falha rápida, com a explicação do que corrigir). */
public class ModulosInvalidosException extends IllegalStateException {

    public ModulosInvalidosException(String mensagem) {
        super(mensagem);
    }
}
