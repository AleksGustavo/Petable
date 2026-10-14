import type { Papel } from "@/lib/jwt";

/** Espelham os @PreAuthorize da API — só para esconder botões; a API é quem barra. */
export const podeEditarClientes = (papel: Papel) => papel === "ADMIN" || papel === "VENDAS";
/** LGPD: exportar e anonimizar o titular (etapa 09). */
export const podeAnonimizarClientes = (papel: Papel) => papel === "ADMIN";
export const podeEditarPets = (papel: Papel) => papel === "ADMIN" || papel === "VENDAS";
export const podeAgendar = (papel: Papel) => papel === "ADMIN" || papel === "VENDAS";
export const podeAdministrarServicos = (papel: Papel) => papel === "ADMIN";
export const podeAdministrarUsuarios = (papel: Papel) => papel === "ADMIN";
export const podeEditarProdutos = (papel: Papel) => papel === "ADMIN" || papel === "VENDAS";
export const podeReativarProdutos = (papel: Papel) => papel === "ADMIN";
export const podeAjustarEstoque = (papel: Papel) => papel === "ADMIN";
export const podeVerFornecedoresECompras = (papel: Papel) => papel === "ADMIN" || papel === "VENDAS";
export const podeAdministrarFornecedores = (papel: Papel) => papel === "ADMIN";
export const podeAdministrarCompras = (papel: Papel) => papel === "ADMIN";
/** Lotes (etapa 04): ler é de ADMIN e VENDAS; bloquear, dar baixa, regularizar e ajustar é só ADMIN. A API confere de novo. */
export const podeVerLotes = (papel: Papel) => papel === "ADMIN" || papel === "VENDAS";
export const podeGerenciarLotes = (papel: Papel) => papel === "ADMIN";
export const podeVender = (papel: Papel) => papel === "ADMIN" || papel === "VENDAS";
export const podeCancelarVendas = (papel: Papel) => papel === "ADMIN";
export const podeAdministrarFinanceiro = (papel: Papel) => papel === "ADMIN";
