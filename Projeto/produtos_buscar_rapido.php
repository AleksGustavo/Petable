<?php
require_once 'conexao.php';

$limite = isset($_GET['limite']) && is_numeric($_GET['limite']) ? (int)$_GET['limite'] : 10;
$pagina_atual = isset($_GET['pagina_atual']) && is_numeric($_GET['pagina_atual']) ? (int)$_GET['pagina_atual'] : 1;
$offset = ($pagina_atual - 1) * $limite;

$busca_nome = $_GET['busca_nome'] ?? '';
$busca_categoria = $_GET['busca_categoria'] ?? '';
$busca_fornecedor = $_GET['busca_fornecedor'] ?? '';
$listar_todos = $_GET['listar_todos'] ?? 'false';
$ordenacao_param = $_GET['ordenacao'] ?? 'nome';

$produtos = [];
$parametros = [];
$condicoes = [];

$mapa_ordenacao = [
    'nome' => 'p.nome ASC',
    'id' => 'p.id ASC',
    'fornecedor' => 'f.nome_fantasia ASC'
];
$order_by = $mapa_ordenacao[$ordenacao_param] ?? 'p.nome ASC';

$total_registros = 0;
$total_paginas = 1;

try {
    if ($listar_todos !== 'true') {

        if (!empty($busca_nome)) {
            $condicoes[] = "p.nome LIKE :busca_nome";
            $parametros[':busca_nome'] = '%' . $busca_nome . '%';
        }

        if (!empty($busca_categoria)) {
            $condicoes[] = "c.nome LIKE :busca_categoria";
            $parametros[':busca_categoria'] = '%' . $busca_categoria . '%';
        }

        if (!empty($busca_fornecedor)) {
            $condicoes[] = "f.nome_fantasia LIKE :busca_fornecedor";
            $parametros[':busca_fornecedor'] = '%' . $busca_fornecedor . '%';
        }
    }

    $sql_count = "
        SELECT COUNT(p.id) FROM produto p
        LEFT JOIN categoria_produto c ON p.categoria_id = c.id
        LEFT JOIN fornecedor f ON p.fornecedor_padrao_id = f.id
    ";

    if (!empty($condicoes)) {
        $sql_count .= " WHERE " . implode(' AND ', $condicoes);
    }

    $stmt_count = $pdo->prepare($sql_count);
    $stmt_count->execute($parametros);

    $total_registros = $stmt_count->fetchColumn();
    $total_paginas = ceil($total_registros / $limite);

    $pagina_atual = max(1, min($pagina_atual, $total_paginas));
    $offset = ($pagina_atual - 1) * $limite;

    $sql = "
        SELECT 
            p.id, 
            p.nome, 
            p.codigo_barras, 
            p.preco_venda, 
            p.quantidade_estoque,
            c.nome AS categoria_nome,
            f.nome_fantasia AS fornecedor_nome
        FROM produto p
        LEFT JOIN categoria_produto c ON p.categoria_id = c.id
        LEFT JOIN fornecedor f ON p.fornecedor_padrao_id = f.id
    ";

    if (!empty($condicoes)) {
        $sql .= " WHERE " . implode(' AND ', $condicoes);
    }

    $sql .= " ORDER BY $order_by LIMIT :limite OFFSET :offset";

    $stmt = $pdo->prepare($sql);

    foreach ($parametros as $key => $value) {
        $stmt->bindValue($key, $value, PDO::PARAM_STR);
    }

    $stmt->bindValue(':limite', $limite, PDO::PARAM_INT);
    $stmt->bindValue(':offset', $offset, PDO::PARAM_INT);

    $stmt->execute();
    $produtos = $stmt->fetchAll(PDO::FETCH_ASSOC);

} catch (PDOException $e) {
    echo '<div class="alert alert-danger">Erro de Banco de Dados: ' . $e->getMessage() . '</div>';
    exit;
}

$titulo_busca = $listar_todos === 'true' ? "Todos os Produtos" : "Produtos Encontrados";

echo '<div class="card shadow-sm"><div class="card-body">';
echo '<h5 class="card-title">';
echo $titulo_busca . ': ' . $total_registros;

if ($total_registros > 0) {
    echo ' (Pág. ' . $pagina_atual . ' de ' . $total_paginas . ')';
}
echo '</h5>';

if ($total_registros > 0):
?>
<div class="table-responsive">
    <table class="table table-striped table-hover table-sm mb-0" id="tabela-produtos">
        <thead class="table-dark">
            <tr>
                <th>ID</th>
                <th>Nome</th>
                <th>Cód. Barras</th>
                <th>Categoria</th>
                <th>Estoque</th>
                <th>Preço Venda</th>
                <th>Fornecedor</th>
                <th>Ações</th>
            </tr>
        </thead>
        <tbody>
            <?php foreach ($produtos as $produto): ?>
                <tr class="produto-item" data-estoque="<?= (int)$produto['quantidade_estoque'] ?>">
                    <td><?= htmlspecialchars($produto['id']) ?></td>
                    <td><?= htmlspecialchars($produto['nome']) ?></td>
                    <td><?= htmlspecialchars($produto['codigo_barras'] ?? 'N/A') ?></td>
                    <td><?= htmlspecialchars($produto['categoria_nome'] ?? 'N/A') ?></td>
                    <td class="text-center">
                        <span class="badge <?= $produto['quantidade_estoque'] > 0 ? 'bg-success' : 'bg-danger' ?>">
                            <?= htmlspecialchars($produto['quantidade_estoque']) ?>
                        </span>
                    </td>
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
        </tbody>
    </table>
</div>

<?php
$flag_listar_todos = $listar_todos === 'true' ? 'true' : 'false';

$max_botoes = 7;
$botoes_antes_depois = floor(($max_botoes - 1) / 2);

$start_page = max(1, $pagina_atual - $botoes_antes_depois);
$end_page = min($total_paginas, $pagina_atual + $botoes_antes_depois);

if ($start_page == 1) {
    $end_page = min($total_paginas, $max_botoes);
}

if ($end_page == $total_paginas) {
    $start_page = max(1, $total_paginas - $max_botoes + 1);
}
?>

<nav aria-label="Paginação de Produtos" class="mt-3">
    <ul class="pagination justify-content-center flex-wrap">
        <?php if ($pagina_atual > 1)_
