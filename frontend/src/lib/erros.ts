export const MODULO_INDISPONIVEL = "MODULO_INDISPONIVEL";
export const MENSAGEM_MODULO_INDISPONIVEL = "Este recurso não está disponível nesta instalação.";

/** Mensagens padrão por status HTTP (etapa 01, seção 20). */
export function mensagemDeErro(status: number, detail?: string, recurso = "Pet"): string {
  switch (status) {
    case 400:
      return detail ?? "Verifique os campos destacados.";
    case 403:
      // O 403 de módulo desligado vem com o texto uniforme da API; o de papel insuficiente, não.
      if (detail === MENSAGEM_MODULO_INDISPONIVEL) return detail;
      return "Você não possui permissão para realizar esta ação.";
    case 404:
      return `${recurso} não encontrado.`;
    case 409:
      return detail ?? "A operação conflita com os dados atuais.";
    default:
      return "Não foi possível concluir a operação. Tente novamente.";
  }
}
