<?php
// Arquivo: produtos_buscar_rapido.php
// Responsável por receber parâmetros de busca e retornar a tabela HTML dos produtos filtrados.

// Inclui sua conexão (usando PDO neste exemplo, adapte se usar MySQLi)
require_once 'conexao.php'; 

// Variáveis de Busca (Recebidas via GET do formulário)
$busca_nome = $_GET['busca_nome'] ?? '';
$busca_categoria = $_GET['busca_categoria'] ?? '';
$busca_fornecedor = $_GET['busca_fornecedor'] ?? '';
$listar_todos = $_GET['listar_todos'] ?? 'false';

$produtos = [];
$parametros = [];
$condicoes = [];

try {
    // 1. CONSTRUÇÃO DA CLÁUSULA WHERE
    
    // Se não for para listar todos, constrói as condições de busca
    if ($listar_todos !== 'true') {
        
        // Busca por Nome/Código de Barras/Descrição
        if (!empty($busca_nome)) {
            $condicoes[] = "(p.nome LIKE :busca_nome OR p.codigo_barras LIKE :busca_nome OR p.descricao LIKE :busca_nome)";
            $parametros[':busca_nome'] = '%' . $busca_nome . '%';
        }
        
        // Busca por Categoria
        if (!empty($busca_categoria)) {
            // Busca o nome da categoria usando LIKE (para busca parcial)
            $condicoes[] = "c.nome LIKE :busca_categoria";
            $parametros[':busca_categoria'] = '%' . $busca_categoria . '%';
        }
        
        // Busca por Fornecedor
        if (!empty($busca_fornecedor)) {
            // Busca o nome fantasia do fornecedor
            $condicoes[] = "f.nome_fantasia LIKE :busca_fornecedor";
            $parametros[':busca_fornecedor'] = '%' . $busca_fornecedor . '%';
        }
    }
    
    // 2. MONTAGEM DA CONSULTA SQL
    
    $sql = "
        SELECT 
            p.id, 
            p.nome, 
            p.codigo_barras, 
            p.preco_venda, 
            p.quantidade_estoque,
            c.nome AS categoria_nome,
            f.nome_fantasia AS fornecedor_nome
        FROM 
            produto p
        LEFT JOIN 
            categoria_produto c ON p.categoria_id = c.id
        LEFT JOIN 
            fornecedor f ON p.fornecedor_padrao_id = f.id
    ";
    
    // Adiciona o WHERE se houver condições
    if (!empty($condicoes)) {
        $sql .= " WHERE " . implode(' AND ', $condicoes);
    }
    
    $sql .= " ORDER BY p.nome ASC";

    // 3. EXECUÇÃO DA CONSULTA
    $stmt = $pdo->prepare($sql);
    $stmt->execute($parametros);
    $produtos = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
} catch (PDOException $e) {
    // Retorna uma mensagem de erro em HTML para ser exibida na área de resultados
    echo '<div class="alert alert-danger">Erro na busca de produtos: ' . $e->getMessage() . '</div>';
    exit;
}

// =========================================================================
// 4. RETORNO DO BLOCO HTML COM OS RESULTADOS (Mesma estrutura da lista)
// =========================================================================
?>

<div class="card shadow-sm">
    <div class="card-body p-0">
        <div class="table-responsive">
            <table class="table table-striped table-hover table-sm mb-0" id="tabela-produtos">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Nome</th>
                        <th>Categoria</th>
                        <th>Estoque</th>
                        <th>Preço Venda</th>
                        <th>Fornecedor</th>
                        <th>Ações</th>
                    </tr>
                </thead>
                <tbody>
                    <?php if (empty($produtos)): ?>
                        <tr>
                            <td colspan="7" class="text-center">
                                Nenhuma resultado encontrado com os filtros aplicados.
                                <?php if ($listar_todos !== 'true'): ?>
                                    <a href="#" id="btn-listar-todos-produtos" class="btn btn-link p-0 m-0">Clique aqui para listar todos.</a>
                                <?php endif; ?>
                            </td>
                        </tr>
                    <?php else: ?>
                        <?php foreach ($produtos as $produto): ?>
                            <tr class="produto-item" data-estoque="<?= (int)$produto['quantidade_estoque'] ?>">
                                <td><?= htmlspecialchars($produto['id']) ?></td>
                                <td><?= htmlspecialchars($produto['nome']) ?></td> 
                                <td><?= htmlspecialchars($produto['categoria_nome'] ?? 'N/A') ?></td>
                                <td><?= htmlspecialchars($produto['quantidade_estoque']) ?></td>
                                <td>R$ <?= number_format($produto['preco_venda'], 2, ',', '.') ?></td>
                                <td><?= htmlspecialchars($produto['fornecedor_nome'] ?? 'Sem Fornecedor') ?></td>
                                <td>
                                    <button class="btn btn-sm btn-primary me-1 item-menu-ajax" 
                                            data-pagina="produtos_editar.php?id=<?= $produto['id'] ?>" 
                                            title="Editar Produto">
                                        <i class="fas fa-edit"></i>
                                    </button>
                                    
                                    <button class="btn btn-sm btn-danger btn-excluir-produto" 
                                            data-id="<?= $produto['id'] ?>" 
                                            title="Excluir Produto">
                                        <i class="fas fa-trash"></i>
                                    </button>
                                </td>
                            </tr>
                        <?php endforeach; ?>
                    <?php endif; ?>
                </tbody>
            </table>
        </div>
    </div>
</div>