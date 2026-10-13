import * as z from "zod";
import { hojeISO } from "@/lib/datas";
import { cpfValido, somenteDigitos } from "@/lib/format";
import type { Endereco } from "@/lib/clientes";
import type { Papel } from "@/lib/jwt";

export type Sexo = "MASCULINO" | "FEMININO" | "OUTRO";

export type Usuario = {
  id: number;
  username: string;
  email: string;
  papel: Papel;
  ativo: boolean;
  funcionario: { id: number; nome: string } | null;
  createdAt: string;
};

export type Funcionario = {
  id: number;
  usuarioId: number;
  username: string;
  nome: string;
  cpf: string;
  dataNascimento: string | null;
  sexo: Sexo;
  telefone: string | null;
  endereco: Endereco | null;
  observacoes: string | null;
  disponivel: boolean;
};

export type FuncionarioResumo = { id: number; usuarioId: number; nome: string };

export type Perfil = { id: number; username: string; email: string; papel: Papel; funcionario: Funcionario | null };

export const PAPEIS: { valor: Papel; rotulo: string; descricao: string }[] = [
  { valor: "ADMIN", rotulo: "Administrador", descricao: "Acesso total, inclusive usuários e catálogo." },
  { valor: "VENDAS", rotulo: "Vendas", descricao: "Clientes, pets, agendamentos e PDV." },
  { valor: "SERVICO", rotulo: "Serviço", descricao: "Consulta a agenda e executa atendimentos." },
];

export const rotuloPapel = (p: Papel) => PAPEIS.find((x) => x.valor === p)?.rotulo ?? p;

export const SEXOS: { valor: Sexo; rotulo: string }[] = [
  { valor: "FEMININO", rotulo: "Feminino" },
  { valor: "MASCULINO", rotulo: "Masculino" },
  { valor: "OUTRO", rotulo: "Outro / prefiro não informar" },
];

/** Requisitos da política de senha (espelha PoliticaSenha.java) — usados na lista visual e na validação. */
export const REQUISITOS_SENHA: { rotulo: string; ok: (s: string) => boolean }[] = [
  { rotulo: "De 10 a 72 caracteres", ok: (s) => s.length >= 10 && s.length <= 72 },
  { rotulo: "Uma letra maiúscula", ok: (s) => /\p{Lu}/u.test(s) },
  { rotulo: "Uma letra minúscula", ok: (s) => /\p{Ll}/u.test(s) },
  { rotulo: "Um número", ok: (s) => /\d/.test(s) },
  { rotulo: "Um caractere especial", ok: (s) => /[^\p{L}\p{N}]/u.test(s) },
];

export function violacaoSenha(senha: string, username?: string, email?: string): string | null {
  const falha = REQUISITOS_SENHA.find((r) => !r.ok(senha));
  if (falha) return `A senha precisa ter: ${falha.rotulo.toLowerCase()}.`;
  const s = senha.toLowerCase();
  if (username && s === username.toLowerCase()) return "A senha não pode ser igual ao nome de usuário.";
  if (email) {
    const e = email.toLowerCase();
    if (s === e || s === e.split("@")[0]) return "A senha não pode ser igual ao e-mail.";
  }
  return null;
}

const username = z
  .string({ error: "Informe o nome de usuário" })
  .trim()
  .min(3, "O nome de usuário deve ter entre 3 e 50 caracteres")
  .max(50, "O nome de usuário deve ter entre 3 e 50 caracteres")
  .regex(/^[a-zA-Z0-9._-]+$/, "Use apenas letras, números, ponto, hífen e sublinhado");
const email = z.string({ error: "Informe o e-mail" }).trim().pipe(z.email("E-mail inválido").max(120));

export const contaSchema = z.object({ username, email });

export const novoUsuarioSchema = z
  .object({
    username,
    email,
    senha: z.string({ error: "Informe a senha" }).min(1, "Informe a senha"),
    confirmacaoSenha: z.string().optional(),
    papel: z.enum(["ADMIN", "VENDAS", "SERVICO"], { error: "Selecione o papel" }),
  })
  .superRefine((v, ctx) => {
    const erro = violacaoSenha(v.senha, v.username, v.email);
    if (erro) ctx.addIssue({ code: "custom", path: ["senha"], message: erro });
    if (v.senha !== v.confirmacaoSenha) ctx.addIssue({ code: "custom", path: ["confirmacaoSenha"], message: "As senhas não conferem." });
  })
  .transform((v) => ({ username: v.username, email: v.email, senha: v.senha, papel: v.papel }));

const opcional = (max: number) =>
  z
    .string()
    .max(max, `Use no máximo ${max} caracteres`)
    .optional()
    .transform((v) => (v?.trim() ? v.trim() : null));

const camposFuncionario = {
  nome: z.string({ error: "Informe o nome" }).trim().min(2, "O nome deve ter entre 2 e 100 caracteres").max(100, "O nome deve ter entre 2 e 100 caracteres"),
  dataNascimento: z
    .string()
    .optional()
    .transform((v) => (v ? v : null))
    .refine((v) => !v || v <= hojeISO(), "A data de nascimento não pode ser futura"),
  sexo: z.union([z.literal(""), z.enum(["MASCULINO", "FEMININO", "OUTRO"])]).optional().transform((v) => (v ? v : "OUTRO")),
  telefone: z
    .string()
    .optional()
    .transform((v) => (somenteDigitos(v) ? somenteDigitos(v) : null))
    .refine((v) => v === null || v.length === 10 || v.length === 11, "Telefone inválido: informe DDD + número"),
  observacoes: opcional(2000),
  cep: z.string().optional().transform((v) => somenteDigitos(v)),
  logradouro: opcional(150),
  numero: opcional(20),
  complemento: opcional(100),
  bairro: opcional(100),
  cidade: opcional(100),
  uf: opcional(2),
};

type CamposFuncionario = {
  nome: string;
  dataNascimento: string | null;
  sexo: string;
  telefone: string | null;
  observacoes: string | null;
  cep: string;
  logradouro: string | null;
  numero: string | null;
  complemento: string | null;
  bairro: string | null;
  cidade: string | null;
  uf: string | null;
};

function montarFuncionario(v: CamposFuncionario) {
  return {
    nome: v.nome,
    dataNascimento: v.dataNascimento,
    sexo: v.sexo,
    telefone: v.telefone,
    observacoes: v.observacoes,
    endereco: v.cep
      ? { cep: v.cep, logradouro: v.logradouro, numero: v.numero, complemento: v.complemento, bairro: v.bairro, cidade: v.cidade, uf: v.uf?.toUpperCase() ?? null }
      : null,
  };
}

function validarEndereco(v: CamposFuncionario, ctx: z.RefinementCtx) {
  const algum = v.cep || v.logradouro || v.cidade || v.uf || v.numero || v.bairro;
  if (!algum) return;
  if (v.cep.length !== 8) ctx.addIssue({ code: "custom", path: ["cep"], message: "CEP inválido" });
  if (!v.logradouro) ctx.addIssue({ code: "custom", path: ["logradouro"], message: "Informe o logradouro" });
  if (!v.cidade) ctx.addIssue({ code: "custom", path: ["cidade"], message: "Informe a cidade" });
  if (!v.uf || !/^[A-Za-z]{2}$/.test(v.uf)) ctx.addIssue({ code: "custom", path: ["uf"], message: "UF inválida" });
}

/** Perfil do próprio funcionário: sem CPF (não é alterável por ele). */
export const meuFuncionarioSchema = z.object(camposFuncionario).superRefine(validarEndereco).transform(montarFuncionario);

/** Cadastro/edição administrativa: CPF obrigatório e válido. */
export const funcionarioSchema = z
  .object({
    ...camposFuncionario,
    cpf: z
      .string({ error: "Informe o CPF" })
      .refine((v) => cpfValido(v), "CPF inválido")
      .transform(somenteDigitos),
  })
  .superRefine(validarEndereco)
  .transform((v) => ({ ...montarFuncionario(v), cpf: v.cpf }));

export function trocarSenhaSchema(usernameAtual?: string, emailAtual?: string) {
  return z
    .object({
      senhaAtual: z.string({ error: "Informe a senha atual" }).min(1, "Informe a senha atual"),
      novaSenha: z.string({ error: "Informe a nova senha" }).min(1, "Informe a nova senha"),
      confirmacaoNovaSenha: z.string({ error: "Confirme a nova senha" }),
    })
    .superRefine((v, ctx) => {
      const erro = violacaoSenha(v.novaSenha, usernameAtual, emailAtual);
      if (erro) ctx.addIssue({ code: "custom", path: ["novaSenha"], message: erro });
      else if (v.novaSenha === v.senhaAtual) ctx.addIssue({ code: "custom", path: ["novaSenha"], message: "A nova senha deve ser diferente da atual." });
      if (v.novaSenha !== v.confirmacaoNovaSenha) {
        ctx.addIssue({ code: "custom", path: ["confirmacaoNovaSenha"], message: "A confirmação não confere com a nova senha." });
      }
    });
}

export const redefinirSenhaSchema = z
  .object({
    token: z.string({ error: "O link de redefinição é inválido ou está incompleto." }).min(1, "O link de redefinição é inválido ou está incompleto."),
    novaSenha: z.string({ error: "Informe a nova senha" }),
    confirmacaoNovaSenha: z.string({ error: "Confirme a nova senha" }),
  })
  .superRefine((v, ctx) => {
    const erro = violacaoSenha(v.novaSenha);
    if (erro) ctx.addIssue({ code: "custom", path: ["novaSenha"], message: erro });
    if (v.novaSenha !== v.confirmacaoNovaSenha) {
      ctx.addIssue({ code: "custom", path: ["confirmacaoNovaSenha"], message: "A confirmação não confere com a nova senha." });
    }
  });

export const MENSAGEM_RECUPERACAO =
  "Se existir uma conta ativa para o e-mail informado, enviaremos as instruções de redefinição.";
