import * as z from "zod";
import { cpfValido, somenteDigitos } from "@/lib/format";

export type Endereco = {
  id: number;
  cep: string;
  logradouro: string;
  numero: string | null;
  complemento: string | null;
  bairro: string | null;
  cidade: string;
  uf: string;
};

export type Cliente = {
  id: number;
  nome: string;
  cpf: string | null;
  dataNascimento: string | null;
  telefone: string;
  email: string | null;
  endereco: Endereco | null;
  observacoes: string | null;
  ativo: boolean;
  createdAt: string;
  updatedAt: string;
};

export type Pagina<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

const opcional = z.string().trim().optional().transform((v) => (v ? v : null));

/** Mesmas regras do ClienteRequest da API, para dar retorno antes do round-trip. */
export const clienteSchema = z
  .object({
    nome: z.string().trim().min(1, "Informe o nome").max(100),
    cpf: z
      .string()
      .optional()
      .refine((v) => !v || cpfValido(v), "CPF inválido")
      .transform((v) => (v ? somenteDigitos(v) : null)),
    dataNascimento: opcional,
    telefone: z
      .string()
      .transform(somenteDigitos)
      .refine((v) => v.length === 10 || v.length === 11, "Telefone inválido: informe DDD + número"),
    email: z
      .union([z.literal(""), z.email("E-mail inválido")])
      .optional()
      .transform((v) => (v ? v : null)),
    observacoes: opcional,
    cep: z.string().optional().transform((v) => somenteDigitos(v)),
    logradouro: opcional,
    numero: opcional,
    complemento: opcional,
    bairro: opcional,
    cidade: opcional,
    uf: opcional,
  })
  .superRefine((v, ctx) => {
    const algumCampo = v.cep || v.logradouro || v.cidade || v.uf || v.numero || v.bairro;
    if (!algumCampo) return;
    if (v.cep.length !== 8) ctx.addIssue({ code: "custom", path: ["cep"], message: "CEP inválido" });
    if (!v.logradouro) ctx.addIssue({ code: "custom", path: ["logradouro"], message: "Informe o logradouro" });
    if (!v.cidade) ctx.addIssue({ code: "custom", path: ["cidade"], message: "Informe a cidade" });
    if (!v.uf || !/^[A-Za-z]{2}$/.test(v.uf)) ctx.addIssue({ code: "custom", path: ["uf"], message: "UF inválida" });
  })
  .transform((v) => ({
    nome: v.nome,
    cpf: v.cpf,
    dataNascimento: v.dataNascimento,
    telefone: v.telefone,
    email: v.email,
    observacoes: v.observacoes,
    endereco: v.cep
      ? {
          cep: v.cep,
          logradouro: v.logradouro,
          numero: v.numero,
          complemento: v.complemento,
          bairro: v.bairro,
          cidade: v.cidade,
          uf: v.uf?.toUpperCase() ?? null,
        }
      : null,
  }));

export type ClientePayload = z.output<typeof clienteSchema>;

export type FormState = {
  erros?: Record<string, string>;
  mensagem?: string;
  valores?: Record<string, string>;
};

/** Converte "endereco.cep" (chave da API) para "cep" (nome do campo no formulário). */
export function errosDaApi(errors: Record<string, string> | undefined): Record<string, string> {
  return Object.fromEntries(Object.entries(errors ?? {}).map(([k, v]) => [k.replace(/^endereco\./, ""), v]));
}
