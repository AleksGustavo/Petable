<?php
// Arquivo: produtos_buscar_rapido.php
// Responsável por receber parâmetros de busca, ordenação e paginação, e retornar a tabela HTML.

require_once 'conexao.php'; // Inclua o arquivo de conexão

// 1. Coleta e Sanitização de Parâmetros
$limite = isset($_GET['limite']) && is_numeric($_GET['limite']) ? (int)$_GET['limite'] : 10;
$pagina_atual = isset($_GET['pagina_atual']) && is_numeric($_GET['pagina_atual']) ? (int)$_GET['pagina_atual'] : 1;
$offset = ($pagina_atual - 1) * $limite;

$busca_nome = $_GET['busca_nome'] ?? '';
$busca_categoria = $_GET['busca_categoria'] ?? '';
$busca_fornecedor = $_GET['busca_fornecedor'] ?? '';
$listar_todos = $_GET['listar_todos'] ?? 'false';
$ordenacao_param = $_GET['ordenacao'] ?? 'nome'; // Novo parâmetro de ordenação

$produtos = [];
$parametros = [];
$condicoes = [];

// Mapeamento de Ordenação
$mapa_ordenacao = [
    'nome' => 'p.nome ASC',
    'id' => 'p.id ASC',
    'fornecedor' => 'f.nome_fantasia ASC',
];
$order_by = $mapa_ordenacao[$ordenacao_param] ?? 'p.nome ASC';

// Inicializa $total_registros e $total_paginas
$total_registros = 0;
$total_paginas = 1;


try {
    // 2. CONSTRUÇÃO DA CLÁUSULA WHERE
    
    // Constrói as condições de busca se não for para listar todos
    if ($listar_todos !== 'true') {
        
        // Busca por Nome (Apenas nome, conforme solicitado)
        if (!empty($busca_nome)) {
            // CORRIGIDO: Busca apenas pelo nome
            $condicoes[] = "p.nome LIKE :busca_nome"; 
            $parametros[':busca_nome'] = '%' . $busca_nome . '%';
        }
        
        // Busca por Categoria
        if (!empty($busca_categoria)) {
            $condicoes[] = "c.nome LIKE :busca_categoria";
            $parametros[':busca_categoria'] = '%' . $busca_categoria . '%';
        }
        
        // Busca por Fornecedor
        if (!empty($busca_fornecedor)) {
            $condicoes[] = "f.nome_fantasia LIKE :busca_fornecedor";
            $parametros[':busca_fornecedor'] = '%' . $busca_fornecedor . '%';
        }
    }
    
    // 3. CONTAR TOTAL DE REGISTROS (Para Paginação)
    $sql_count = "
        SELECT COUNT(p.id) FROM produto p
        LEFT JOIN categoria_produto c ON p.categoria_id = c.id
        LEFT JOIN fornecedor f ON p.fornecedor_padrao_id = f.id
    ";
    if (!empty($condicoes)) {
        $sql_count .= " WHERE " . implode(' AND ', $condicoes);
    }
    
    $stmt_count = $pdo->prepare($sql_count);
    $stmt_count->execute($parametros); // Executa usando os mesmos parâmetros de busca
    $total_registros = $stmt_count->fetchColumn();
    $total_paginas = ceil($total_registros / $limite);

    // Garante que a página atual não exceda o total (se houver registros)
    $pagina_atual = max(1, min($pagina_atual, $total_paginas));
    $offset = ($pagina_atual - 1) * $limite;

    // 4. MONTAGEM DA CONSULTA SQL PRINCIPAL
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
    
    // Adiciona a Ordenação e Paginação (LIMIT e OFFSET)
    $sql .= " ORDER BY " . $order_by;
    // O LIMIT e OFFSET usam parâmetros nomeados para serem vinculados com segurança
    $sql .= " LIMIT :limite OFFSET :offset";

    // 5. EXECUÇÃO DA CONSULTA PRINCIPAL (Onde o erro HY093 é resolvido)
    $stmt = $pdo->prepare($sql);
    
    // Vincula os parâmetros de busca (strings)
    foreach ($parametros as $key => $value) {
        $stmt->bindValue($key, $value, PDO::PARAM_STR);
    }

    // Vincula os parâmetros de paginação (inteiros) - ESSENCIAL para evitar o erro HY093
    $stmt->bindValue(':limite', $limite, PDO::PARAM_INT);
    $stmt->bindValue(':offset', $offset, PDO::PARAM_INT);

    // Executa a consulta, usando os valores já vinculados
    $stmt->execute(); 
    $produtos = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
} catch (PDOException $e) {
    // Retorna uma mensagem de erro em HTML
    echo '<div class="alert alert-danger">Erro de Banco de Dados: ' . $e->getMessage() . '</div>';
    exit;
}

// =========================================================================
// 6. RETORNO DO BLOCO HTML COM OS RESULTADOS E PAGINAÇÃO (ESTILO PADRONIZADO)
// =========================================================================

$titulo_busca = $listar_todos === 'true' ? "Todos os Produtos" : "Produtos Encontrados";

// INÍCIO DO CARD COM O TÍTULO
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

<!-- INÍCIO DA PAGINAÇÃO (NOVO ESTILO) -->
<?php 
// Define a flag de listar_todos para ser usada nos botões de paginação
$flag_listar_todos = $listar_todos === 'true' ? 'true' : 'false';

// Lógica de paginação padronizada (máximo de 7 botões + reticências)
$max_botoes = 7;
$botoes_antes_depois = floor(($max_botoes - 1) / 2);

// Calcula o início e o fim do range de botões a mostrar
$start_page = max(1, $pagina_atual - $botoes_antes_depois);
$end_page = min($total_paginas, $pagina_atual + $botoes_antes_depois);

// Ajusta o range se estiver muito perto do início ou do fim
if ($start_page == 1) {
    $end_page = min($total_paginas, $max_botoes);
}
// Ajusta o início se o fim estiver muito perto do final
if ($end_page == $total_paginas) {
    $start_page = max(1, $total_paginas - $max_botoes + 1);
}

// Garante que o início seja no mínimo 1
$start_page = max(1, $start_page);
?>

<nav aria-label="Paginação de Produtos" class="mt-3">
    <ul class="pagination justify-content-center flex-wrap">

        <!-- Botão "Primeira" -->
        <?php if ($pagina_atual > 1): ?>
            <li class="page-item"><a class="page-link btn-pagina-produto" href="#" data-pagina="1" data-listar-todos="<?= $flag_listar_todos ?>">Primeira</a></li>
        <?php endif; ?>

        <!-- Botão de reticências antes -->
        <?php if ($start_page > 1): ?>
            <li class="page-item disabled"><span class="page-link">...</span></li>
        <?php endif; ?>

        <!-- Números das Páginas -->
        <?php for ($i = $start_page; $i <= $end_page; $i++): ?>
            <li class="page-item <?= ($i == $pagina_atual) ? 'active' : '' ?>">
                <a class="page-link btn-pagina-produto" href="#" data-pagina="<?= $i ?>" data-listar-todos="<?= $flag_listar_todos ?>">
                    <?= $i ?>
                </a>
            </li>
        <?php endfor; ?>

        <!-- Botão de reticências depois -->
        <?php if ($end_page < $total_paginas): ?>
            <li class="page-item disabled"><span class="page-link">...</span></li>
        <?php endif; ?>

        <!-- Botão "Próxima" e "Última" -->
        <?php if ($pagina_atual < $total_paginas): 
            $proxima_pagina = $pagina_atual + 1;
        ?>
            <li class="page-item"><a class="page-link btn-pagina-produto" href="#" data-pagina="<?= $proxima_pagina ?>" data-listar-todos="<?= $flag_listar_todos ?>">Próxima</a></li>

            <?php if ($proxima_pagina < $total_paginas): ?>
                <li class="page-item"><a class="page-link btn-pagina-produto" href="#" data-pagina="<?= $total_paginas ?>" data-listar-todos="<?= $flag_listar_todos ?>">Última</a></li>
            <?php endif; ?>
        <?php endif; ?>

    </ul>
    <div class="text-center text-muted">Exibindo <?= count($produtos) ?> de <?= $total_registros ?> produtos.</div>
</nav>
<!-- FIM DA PAGINAÇÃO (NOVO ESTILO) -->

<?php else: // $total_registros é 0 ?>
    <div class="alert alert-warning">Nenhum resultado encontrado com os filtros aplicados.</div>
<?php endif; ?>

<?php
echo '</div></div>'; // Fecha o card-body e o card
?>