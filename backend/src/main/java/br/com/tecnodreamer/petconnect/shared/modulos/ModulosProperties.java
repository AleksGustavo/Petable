package br.com.tecnodreamer.petconnect.shared.modulos;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code petconnect.modulos} ({@code PETCONNECT_MODULOS}): módulos contratados desta instalação. {@code todos} liga todos os
 * módulos existentes (padrão das instalações já existentes: atualizar não tira funcionalidade de ninguém); caso contrário, uma lista de ids
 * do catálogo separados por vírgula (ex.: {@code pdv,caixa,produtos,estoque}). O que não está na lista é NEGADO. Valor vazio ou id
 * desconhecido recusa o boot. Carregado uma vez na partida: mudar exige reiniciar (não há recarga a quente).
 */
@ConfigurationProperties("petconnect")
public record ModulosProperties(@DefaultValue("todos") String modulos) {
}
