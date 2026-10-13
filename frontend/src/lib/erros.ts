/** Mensagens padrão por status HTTP (etapa 01, seção 20). */
export function mensagemDeErro(status: number, detail?: string, recurso = "Pet"): string {
  switch (status) {
    case 400:
      return detail ?? "Verifique os campos destacados.";
    case 403:
      return "Você não possui permissão para realizar esta ação.";
    case 404:
      return `${recurso} não encontrado.`;
    case 409:
      return detail ?? "A operação conflita com os dados atuais.";
    default:
      return "Não foi possível concluir a operação. Tente novamente.";
  }
}
