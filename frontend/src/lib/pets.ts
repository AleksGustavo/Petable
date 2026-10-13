import * as z from "zod";
import { hojeISO, agoraLocalISO } from "@/lib/datas";

export type Porte = "PEQUENO" | "MEDIO" | "GRANDE";
export type StatusVacina = "VENCIDA" | "VENCE_EM_30_DIAS" | "EM_DIA" | "SEM_PROXIMA_DOSE";

export type CatalogoItem = { id: number; nome: string };
export type EspecieComRacas = CatalogoItem & { racas: CatalogoItem[] };
export type VacinaCatalogo = CatalogoItem & { doencaProtecao: string | null; validadePadraoMeses: number };
export type Idade = { anos: number; meses: number; descricao: string };

export type Pet = {
  id: number;
  nome: string;
  tutor: { id: number; nome: string; telefone: string };
  especie: CatalogoItem;
  raca: CatalogoItem | null;
  dataNascimento: string | null;
  idade: Idade | null;
  peso: number | null;
  cor: string | null;
  porte: Porte | null;
  castrado: boolean;
  fotoUrl: string | null;
  observacoes: string | null;
  createdAt: string;
  updatedAt: string;
};

export type LancamentoVacina = {
  id: number;
  vacina: CatalogoItem;
  dataAplicacao: string;
  dataProxima: string | null;
  veterinario: string | null;
  lote: string | null;
  observacoes: string | null;
  status: StatusVacina;
  statusDescricao: string;
};

export type Consulta = {
  id: number;
  dataConsulta: string;
  veterinario: string;
  motivo: string | null;
  diagnostico: string | null;
  tratamento: string | null;
  observacoes: string | null;
};

export const PORTES: { valor: Porte; rotulo: string }[] = [
  { valor: "PEQUENO", rotulo: "Pequeno" },
  { valor: "MEDIO", rotulo: "Médio" },
  { valor: "GRANDE", rotulo: "Grande" },
];

export const rotuloPorte = (p: Porte | null) => PORTES.find((x) => x.valor === p)?.rotulo ?? "—";

const texto = (max: number) =>
  z
    .string()
    .max(max, `Use no máximo ${max} caracteres`)
    .optional()
    .transform((v) => (v?.trim() ? v.trim() : null));

const id = (mensagem: string) =>
  z
    .string({ error: mensagem })
    .trim()
    .regex(/^\d+$/, mensagem)
    .transform(Number);

const idOpcional = z
  .string()
  .optional()
  .transform((v) => (v && /^\d+$/.test(v) ? Number(v) : null));

const dataOpcional = z
  .string()
  .optional()
  .transform((v) => (v ? v : null));

/** Mesmas regras do PetRequest da API. */
export const petSchema = z
  .object({
    clienteId: id("Selecione o tutor"),
    nome: z.string({ error: "Informe o nome" }).trim().min(1, "Informe o nome").max(50, "Use no máximo 50 caracteres"),
    especieId: id("Selecione a espécie"),
    racaId: idOpcional,
    dataNascimento: dataOpcional.refine((v) => !v || v <= hojeISO(), "A data de nascimento não pode ser futura"),
    peso: z
      .string()
      .optional()
      .transform((v) => (v?.trim() ? Number(v.replace(",", ".")) : null))
      .refine((v) => v === null || (!Number.isNaN(v) && v > 0), "O peso deve ser maior que zero")
      .refine((v) => v === null || v <= 999.99, "O peso deve ser no máximo 999,99 kg"),
    cor: texto(30),
    porte: z
      .union([z.literal(""), z.enum(["PEQUENO", "MEDIO", "GRANDE"], { error: "Porte inválido" })])
      .optional()
      .transform((v) => (v ? v : null)),
    castrado: z
      .string()
      .optional()
      .transform((v) => v === "on" || v === "true"),
    observacoes: texto(2000),
  });

export type PetPayload = z.output<typeof petSchema>;

export const vacinaSchema = z
  .object({
    vacinaId: id("Selecione a vacina"),
    dataAplicacao: z
      .string({ error: "Informe a data de aplicação" })
      .min(1, "Informe a data de aplicação")
      .refine((v) => v <= hojeISO(), "A data de aplicação não pode ser futura"),
    dataProxima: dataOpcional,
    veterinario: texto(100),
    lote: texto(50),
    observacoes: texto(2000),
  })
  .refine((v) => !v.dataProxima || v.dataProxima >= v.dataAplicacao, {
    path: ["dataProxima"],
    message: "A próxima dose não pode ser anterior à aplicação",
  });

export const consultaSchema = z.object({
  dataConsulta: z
    .string({ error: "Informe a data da consulta" })
    .min(1, "Informe a data da consulta")
    .refine((v) => v <= agoraLocalISO(), "A data da consulta não pode ser futura"),
  veterinario: z
    .string({ error: "Informe o veterinário" })
    .trim()
    .min(2, "Informe o veterinário (mínimo 2 caracteres)")
    .max(100, "Use no máximo 100 caracteres"),
  motivo: texto(2000),
  diagnostico: texto(2000),
  tratamento: texto(2000),
  observacoes: texto(2000),
});

export const FOTO_TIPOS = ["image/jpeg", "image/png", "image/webp"];
export const FOTO_MAX_BYTES = 5 * 1024 * 1024;

/** Validação prévia da foto no servidor do Next; a API confere de novo pelo conteúdo. */
export function erroDaFoto(foto: File): string | null {
  if (!FOTO_TIPOS.includes(foto.type)) return "Formato não permitido: envie JPEG, PNG ou WebP.";
  if (foto.size > FOTO_MAX_BYTES) return "A foto deve ter no máximo 5 MB.";
  return null;
}
