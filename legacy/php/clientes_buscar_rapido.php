<?php

header('Content-Type: text/html; charset=utf-8');

require_once 'conexao.php';

$BASE_PATH = '/PHP_PI/';

if (empty($conexao)) {
    http_response_code(500);
    echo '<div class="alert alert-danger">Erro crítico: Conexão mysqli indisponível.</div>';
    exit();
}

$pagina_atual = isset($_GET['pagina_atual']) ? max(1, (int)$_GET['pagina_atual']) : 1;
$limite = isset($_GET['limite']) ? (int)$_GET['limite'] : 10;
$offset = ($pagina_atual - 1) * $limite;
$listar_todos = isset($_GET['listar_todos']) && $_GET['listar_todos'] === 'true';

$busca_id = trim($_GET['busca_id'] ?? '');
$busca_cpf = trim($_GET['busca_cpf'] ?? '');
$busca_nome = trim($_GET['busca_nome'] ?? '');

$types = '';
$parametros = [];

$where_clause = ' WHERE ativo = 1';
$search_conditions = [];

if (!$listar_todos) {
    if (!empty($busca_id) && is_numeric($busca_id)) {
        $search_conditions[] = "id = ?";
        $types .= 'i';
        $parametros[] = $busca_id;
    }

    if (!empty($busca_cpf)) {
        $cpf_limpo = preg_replace('/[^0-9]/', '', $busca_cpf);

        $search_conditions[] = "cpf LIKE ?";
        $types .= 's';
        $parametros[] = '%' . $cpf_limpo . '%';
    }

    if (!empty($busca_nome)) {
        $search_conditions[] = "nome LIKE ?";
        $types .= 's';
        $parametros[] = '%' . $busca_nome . '%';
    }
}

if (!empty($search_conditions)) {
    $where_clause .= ' AND (' . implode(' OR ', $search_conditions) . ')';
}

$total_registros = 0;
$total_paginas = 1;

try {
    $sql_count = "SELECT COUNT(id) AS total FROM cliente" . $where_clause;

    $stmt_count = mysqli_prepare($conexao, $sql_count);

    if (!empty($types)) {
        mysqli_stmt_bind_param($stmt_count, $types, ...$parametros);
    }

    mysqli_stmt_execute($stmt_count);
    $result_count = mysqli_stmt_get_result($stmt_count);

    if ($result_count) {
        $row_count = mysqli_fetch_assoc($result_count);
        $total_registros = (int)$row_count['total'];
        $total_paginas = ceil($total_registros / $limite);
    }

    mysqli_stmt_close($stmt_count);
} catch (Exception $e) {
    error_log("Erro na contagem de clientes: " . $e->getMessage());
    $total_registros = 0;
    $total_paginas = 1;
}

$clientes = [];
if ($total_registros > 0) {
    try {
        $sql_clientes = "SELECT id, nome, cpf, telefone FROM cliente"
            . $where_clause
            . " ORDER BY nome ASC LIMIT ? OFFSET ?";

        $stmt_clientes = mysqli_prepare($conexao, $sql_clientes);

        $full_types = $types . 'ii';
        $full_parametros = array_merge($parametros, [$limite, $offset]);

        mysqli_stmt_bind_param($stmt_clientes, $full_types, ...$full_parametros);

        mysqli_stmt_execute($stmt_clientes);
        $result_clientes = mysqli_stmt_get_result($stmt_clientes);
        $clientes = mysqli_fetch_all($result_clientes, MYSQLI_ASSOC);

        mysqli_stmt_close($stmt_clientes);
    } catch (Exception $e) {
        error_log("Erro na consulta de clientes: " . $e->getMessage());
        $clientes = [];
    }
}

function mask_cpf($cpf)
{
    $cpf = preg_replace('/[^0-9]/', '', $cpf);
    if (strlen($cpf) == 11) {
        return preg_replace('/(\d{3})(\d{3})(\d{3})(\d{2})/', '$1.$2.$3-$4', $cpf);
    }
    return $cpf;
}

$titulo_busca = $listar_todos ? "Todos os Clientes" : "Clientes Encontrados";

echo '<div class="card shadow-sm"><div class="card-body">';
echo '<h5 class="card-title">';
echo $titulo_busca . ': ' . $total_registros;

if ($total_registros > 0) {
    echo ' (Pág. ' . $pagina_atual . ' de ' . $total_paginas . ')';
}
echo '</h5>';

if ($total_registros > 0) {
    echo '<div class="table-responsive">';
    echo '<table class="table table-striped table-hover table-sm shadow-sm">';
    echo '<thead class="table-dark"><tr><th>Cliente (Dono)</th><th>CPF</th><th>Telefone</th><th style="min-width: 380px;">Ações</th></tr></thead>';
    echo '<tbody>';

    foreach ($clientes as $cliente) {
        $cliente_id = htmlspecialchars($cliente['id']);
        $nome_completo = htmlspecialchars($cliente['nome']);
        $cpf = htmlspecialchars($cliente['cpf']);
        $telefone = htmlspecialchars($cliente['telefone']);

        echo '<tr>';
        echo '<td>' . $nome_completo . '</td>';
        echo '<td>' . mask_cpf($cpf) . '</td>';
        echo '<td>' . $telefone . '</td>';
        echo '<td>';

        echo '<a href="#" class="btn btn-sm btn-info item-menu-ajax me-2" data-pagina="clientes_detalhes.php?id=' . $cliente_id . '" title="Ver a lista de Pets cadastrados">';
        echo '<i class="fas fa-paw me-1"></i> Ver Pets';
        echo '</a>';

        echo '<a href="#" class="btn btn-sm btn-success item-menu-ajax me-2" data-pagina="pets_cadastro.php?cliente_id=' . $cliente_id . '" title="Adicionar um novo Pet">';
        echo '<i class="fas fa-plus me-1"></i> Add Pet';
        echo '</a>';

        echo '<a href="#" class="btn btn-sm btn-primary item-menu-ajax me-2" data-pagina="clientes_editar.php?id=' . $cliente_id . '" title="Editar Cliente">';
        echo '<i class="fas fa-user-edit"></i> Editar';
        echo '</a>';

        echo '<a href="#" class="btn btn-sm btn-danger btn-excluir-cliente" data-id="' . $cliente_id . '" title="Excluir Cliente">';
        echo '<i class="fas fa-trash-alt"></i>';
        echo '</a>';

        echo '</td>';
        echo '</tr>';
    }

    echo '</tbody>';
    echo '</table>';
    echo '</div>';

    echo '<nav aria-label="Paginação de Clientes" class="mt-3">';
    echo '<ul class="pagination justify-content-center flex-wrap">';

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

    $listar_todos_js = $listar_todos ? 'true' : 'false';

    if ($pagina_atual > 1) {
        echo '<li class="page-item"><a class="page-link btn-pagina-cliente" href="#" data-pagina="1" data-listar-todos="' . $listar_todos_js . '">Primeira</a></li>';
    }

    if ($start_page > 1) {
        echo '<li class="page-item disabled"><span class="page-link">...</span></li>';
    }

    for ($i = $start_page; $i <= $end_page; $i++) {
        $active = ($i == $pagina_atual) ? 'active' : '';
        echo '<li class="page-item ' . $active . '">';
        echo '<a class="page-link btn-pagina-cliente" href="#" data-pagina="' . $i . '" data-listar-todos="' . $listar_todos_js . '">' . $i . '</a>';
        echo '</li>';
    }

    if ($end_page < $total_paginas) {
        echo '<li class="page-item disabled"><span class="page-link">...</span></li>';
    }

    if ($pagina_atual < $total_paginas) {
        $proxima_pagina = $pagina_atual + 1;
        echo '<li class="page-item"><a class="page-link btn-pagina-cliente" href="#" data-pagina="' . $proxima_pagina . '" data-listar-todos="' . $listar_todos_js . '">Próxima</a></li>';

        if ($proxima_pagina < $total_paginas) {
            echo '<li class="page-item"><a class="page-link btn-pagina-cliente" href="#" data-pagina="' . $total_paginas . '" data-listar-todos="' . $listar_todos_js . '">Última</a></li>';
        }
    }

    echo '</ul></nav>';
} else {
    echo '<div class="alert alert-warning">Nenhum cliente encontrado com os critérios de busca.</div>';
}

echo '</div></div>';
?>