import type { Papel } from "@/lib/jwt";

/** Espelha os @PreAuthorize da API — só para esconder botões; a API é quem barra. */
export const podeEditarClientes = (papel: Papel) => papel === "ADMIN" || papel === "VENDAS";
