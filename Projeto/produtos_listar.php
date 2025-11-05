<?php
// Arquivo: produtos_listar.php
require_once 'conexao.php'; 

$erro_sql = null;
$produtos = []; 

try {
    // Consulta SQL CORRIGIDA
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
        ORDER BY 
            p.nome ASC
    ";
    
    // Supondo que $pdo é a sua conexão PDO
    $stmt = $pdo->prepare($sql);
    $stmt->execute();
    $produtos = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
} catch (PDOException $e) {
    $erro_sql = "Erro ao buscar produtos: " . $e->getMessage();
    $produtos = []; 
}
?>

<div class="d-flex justify-content-between align-items-center mb-4">
    <h2><i class="fas fa-boxes me-2"></i> Lista de Produtos </h2>
    
    <div class="btn-group" role="group">
        <a href="#" id="btn-listar-todos-produtos" class="btn btn-info btn-sm">
            <i class="fas fa-redo me-2"></i> Listar Todos
        </a>
        
        <a href="#" id="btn-esconder-produtos" class="btn btn-warning btn-sm">
            <i class="fas fa-eye-slash me-2"></i> Esconder Fora de Estoque
        </a>
        
        <a href="#" class="btn btn-success btn-sm item-menu-ajax" data-pagina="produtos_cadastro.php">
            <i class="fas fa-plus me-2"></i> Novo Produto
        </a>
    </div>
</div>

<div id="status-message-area">
    <?php if (isset($erro_sql)): ?>
        <div class="alert alert-danger"><?= $erro_sql ?></div>
    <?php endif; ?>
</div>

<div class="card mb-4 shadow-sm">
    <div class="card-header bg-secondary text-white">
        <i class="fas fa-search me-2"></i> Pesquisar Produto (Busca Permissiva)
    </div>
    <div class="card-body">
        <form id="form-busca-produto-rapida"> 
            <div class="row g-3">
                
                <div class="col-md-4">
                    <label for="busca_nome" class="form-label">Nome ou Código de Barras</label>
                    <input type="text" id="busca_nome" name="busca_nome" class="form-control" placeholder="Nome, descrição ou código">
                </div>
                
                <div class="col-md-4">
                    <label for="busca_categoria" class="form-label">Buscar por Categoria</label>
                    <input type="text" id="busca_categoria" name="busca_categoria" class="form-control" placeholder="Nome da Categoria">
                </div>
                
                <div class="col-md-4">
                    <label for="busca_fornecedor" class="form-label">Buscar por Fornecedor</label>
                    <input type="text" id="busca_fornecedor" name="busca_fornecedor" class="form-control" placeholder="Nome do Fornecedor">
                </div>
            </div>

            <div class="mt-3">
                <button type="submit" class="btn btn-primary w-100 w-md-auto">
                    <i class="fas fa-search me-1"></i> Buscar Produtos
                </button>
            </div>
        </form>
    </div>
</div>
<div id="resultado-busca-rapida" class="mt-4">
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
                                    Nenhum produto cadastrado.
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
</div>