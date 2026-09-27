import { randomBytes } from "node:crypto";
import { chamar, salvarEstado, tokenAdmin } from "./api";

/**
 * Prepara dados isolados por execução (DA-19): um usuário ADMIN efêmero (o caixa é por
 * operador, então não interfere em caixas existentes) e um produto com estoque.
 */
export default async function globalSetup() {
  const token = await tokenAdmin();
  const sufixo = Date.now().toString(36);
  const senha = `E2e-${randomBytes(9).toString("base64url")}9a`;
  const username = `e2e.${sufixo}`;
  const usuario = await chamar<{ id: number }>("POST", "/api/v1/usuarios", token, {
    username,
    email: `${username}@petconnect.test`,
    senha,
    papel: "ADMIN",
  });

  const categorias = await chamar<{ id: number; nome: string }[]>("GET", "/api/v1/categorias-produto", token);
  const codigo = `789${Date.now().toString().slice(-10)}`;
  const nome = `Ração E2E ${sufixo}`;
  const produto = await chamar<{ id: number }>("POST", "/api/v1/produtos", token, {
    nome,
    categoriaId: categorias[0].id,
    codigoBarras: codigo,
    precoCusto: "20.00",
    precoVenda: "49.90",
    estoqueMinimo: 1,
  });
  await chamar("POST", `/api/v1/produtos/${produto.id}/ajustes-estoque`, token, {
    quantidade: 10,
    motivo: "Estoque do teste E2E",
  });

  salvarEstado({ sufixo, usuario: { id: usuario.id, username, senha }, produto: { id: produto.id, nome, codigo, preco: "49,90" } });
}
