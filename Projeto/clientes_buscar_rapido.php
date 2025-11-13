<?php
// Arquivo: clientes_buscar_rapido.php
// Objetivo: Busca e paginação de clientes utilizando a conexão mysqli do conexao.php, 
//           respeitando as colunas (id, nome, cpf, telefone) e ações do sistema Pet & Pet.

require_once 'conexao.php'; // Assume que 'conexao.php' retorna a variável $conexao (mysqli)

// Configuração de Caminhos WEB: Necessário para os links de ação
$BASE_PATH = '/PHP_PI/';

// Verifica se a conexão mysqli está ativa.
if (empty($conexao)) {
    http_response_code(500);
    echo '<div class="alert alert-danger">Erro crítico: Conexão mysqli indisponível.</div>';
    exit();
}

// Parâmetros de Paginação
$pagina_atual = isset($_GET['pagina_atual']) ? max(1, (int)$_GET['pagina_atual']) : 1;
$limite = isset($_GET['limite']) ? (int)$_GET['limite'] : 10;
$offset = ($pagina_atual - 1) * $limite;
$listar_todos = isset($_GET['listar_todos']) && $_GET['listar_todos'] === 'true';

// Parâmetros de Busca (Sanitizados antes de usar)
$busca_id = trim($_GET['busca_id'] ?? '');
$busca_cpf = trim($_GET['busca_cpf'] ?? '');
$busca_nome = trim($_GET['busca_nome'] ?? '');

// Variáveis para montar a consulta SQL
$types = ''; // String de tipos para mysqli_stmt_bind_param
$parametros = []; // Array de valores para mysqli_stmt_bind_param

// ---------------------------------------------------------------------
// 1. MONTAGEM DA CLÁUSULA WHERE (Busca por Clientes ATIVOS e filtros OR)
// ---------------------------------------------------------------------

// Condição base: Apenas clientes ativos
$where_clause = ' WHERE ativo = 1';
$search_conditions = [];

if (!$listar_todos) {
    // Busca por ID (exata)
    if (!empty($busca_id) && is_numeric($busca_id)) {
        $search_conditions[] = "id = ?";
        $types .= 'i';
        $parametros[] = $busca_id;
    }

    // Busca por CPF (ignora máscara e busca o valor limpo)
    if (!empty($busca_cpf)) {
        // 1. Remove caracteres não numéricos do CPF digitado pelo usuário
        $cpf_limpo = preg_replace('/[^0-9]/', '', $busca_cpf);

        // 2. Busca pelo valor limpo ou mascarado (usa LIKE para ser permissivo)
        $search_conditions[] = "cpf LIKE ?";
        $types .= 's';
        // Adiciona o '%' para buscar a correspondência em qualquer parte da string 'cpf'
        // no banco, caso ela esteja mascarada.
        $parametros[] = '%' . $cpf_limpo . '%';
    }

    // Busca por Nome (parcial, usando LIKE)
    if (!empty($busca_nome)) {
        $search_conditions[] = "nome LIKE ?";
        $types .= 's';
        $parametros[] = '%' . $busca_nome . '%';
    }
}

if (!empty($search_conditions)) {
    // Aplica a busca com a lógica OR E a condição ativo=1
    $where_clause .= ' AND (' . implode(' OR ', $search_conditions) . ')';
}

// ---------------------------------------------------------------------
// 2. CONTAGEM TOTAL DE REGISTROS (Para calcular a paginação)
// ---------------------------------------------------------------------

$total_registros = 0;
$total_paginas = 1;

try {
    // Consulta de contagem total
    $sql_count = "SELECT COUNT(id) AS total FROM cliente" . $where_clause;

    $stmt_count = mysqli_prepare($conexao, $sql_count);

    if (!empty($types)) {
        // Usa call_user_func_array para passar a string de tipos e os parâmetros.
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

// ---------------------------------------------------------------------
// 3. CONSULTA DOS REGISTROS DA PÁGINA ATUAL (Colunas: id, nome, cpf, telefone)
// ---------------------------------------------------------------------

$clientes = [];
if ($total_registros > 0) {
    try {
        // Consulta dos clientes da página atual, ordenando por nome e usando LIMIT/OFFSET
        $sql_clientes = "SELECT id, nome, cpf, telefone FROM cliente"
            . $where_clause
            . " ORDER BY nome ASC LIMIT ? OFFSET ?";

        $stmt_clientes = mysqli_prepare($conexao, $sql_clientes);

        // Adiciona os parâmetros de paginação ('ii') aos tipos e valores existentes
        $full_types = $types . 'ii';
        $full_parametros = array_merge($parametros, [$limite, $offset]);

        // Usa call_user_func_array para passar a string de tipos e os parâmetros.
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

// Função de máscara de CPF (mantida a lógica de mascaramento)
function mask_cpf($cpf)
{
    $cpf = preg_replace('/[^0-9]/', '', $cpf);
    if (strlen($cpf) == 11) {
        return preg_replace('/(\d{3})(\d{3})(\d{3})(\d{2})/', '$1.$2.$3-$4', $cpf);
    }
    return $cpf;
}

// ---------------------------------------------------------------------
// 4. GERAÇÃO DO HTML DE RESULTADOS E AÇÕES (Omitido por ser apenas HTML, mantendo a originalidade)
// ---------------------------------------------------------------------
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
    // HEADER: Adotando o header do código anterior do usuário
    echo '<thead class="table-dark"><tr><th>Cliente (Dono)</th><th>CPF</th><th>Telefone</th><th style="min-width: 380px;">Ações</th></tr></thead>';
    echo '<tbody>';

    // LOOP AGORA USA DADOS REAIS DO BANCO DE DADOS
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

        // BOTÃO VER PETS (Links baseados no seu código anterior)
        echo '<a href="#" class="btn btn-sm btn-info item-menu-ajax me-2" data-pagina="clientes_detalhes.php?id=' . $cliente_id . '" title="Ver a lista de Pets cadastrados">';
        echo '<i class="fas fa-paw me-1"></i> Ver Pets';
        echo '</a>';

        // Botão ADICIONAR PET
        echo '<a href="#" class="btn btn-sm btn-success item-menu-ajax me-2" data-pagina="pets_cadastro.php?cliente_id=' . $cliente_id . '" title="Adicionar um novo Pet">';
        echo '<i class="fas fa-plus me-1"></i> Add Pet';
        echo '</a>';

        // Botão EDITAR
        echo '<a href="#" class="btn btn-sm btn-primary item-menu-ajax me-2" data-pagina="clientes_editar.php?id=' . $cliente_id . '" title="Editar Cliente">';
        echo '<i class="fas fa-user-edit"></i> Editar';
        echo '</a>';

        // Botão EXCLUIR (CORRIGIDO: Classe 'excluir-cliente' para 'btn-excluir-cliente')
        echo '<a href="#" class="btn btn-sm btn-danger btn-excluir-cliente" data-id="' . $cliente_id . '" title="Excluir Cliente">';
        echo '<i class="fas fa-trash-alt"></i>';
        echo '</a>';

        echo '</td>';
        echo '</tr>';
    }

    echo '</tbody>';
    echo '</table>';
    echo '</div>';

    // ---------------------------------------------------------------------
    // 5. Geração da Paginação (Mantida a lógica existente)
    // ---------------------------------------------------------------------
    echo '<nav aria-label="Paginação de Clientes" class="mt-3">';
    echo '<ul class="pagination justify-content-center flex-wrap">';

    $max_botoes = 7;
    $botoes_antes_depois = floor(($max_botoes - 1) / 2);

    // Calcula o início e o fim do range de botões a mostrar
    $start_page = max(1, $pagina_atual - $botoes_antes_depois);
    $end_page = min($total_paginas, $pagina_atual + $botoes_antes_depois);

    // Ajusta o range se estiver muito perto do início ou do fim
    if ($start_page == 1) {
        $end_page = min($total_paginas, $max_botoes);
    }
    if ($end_page == $total_paginas) {
        $start_page = max(1, $total_paginas - $max_botoes + 1);
    }

    $listar_todos_js = $listar_todos ? 'true' : 'false'; // Passa o estado de busca para o JS

    // Botão "Primeira"
    if ($pagina_atual > 1) {
        echo '<li class="page-item"><a class="page-link btn-pagina-cliente" href="#" data-pagina="1" data-listar-todos="' . $listar_todos_js . '">Primeira</a></li>';
    }

    // Botão de reticências antes
    if ($start_page > 1) {
        echo '<li class="page-item disabled"><span class="page-link">...</span></li>';
    }

    // Números das Páginas
    for ($i = $start_page; $i <= $end_page; $i++) {
        $active = ($i == $pagina_atual) ? 'active' : '';
        echo '<li class="page-item ' . $active . '">';
        echo '<a class="page-link btn-pagina-cliente" href="#" data-pagina="' . $i . '" data-listar-todos="' . $listar_todos_js . '">' . $i . '</a>';
        echo '</li>';
    }

    // Botão de reticências depois
    if ($end_page < $total_paginas) {
        echo '<li class="page-item disabled"><span class="page-link">...</span></li>';
    }

    // Botão "Próxima" (ou Última)
    if ($pagina_atual < $total_paginas) {
        $proxima_pagina = $pagina_atual + 1;
        echo '<li class="page-item"><a class="page-link btn-pagina-cliente" href="#" data-pagina="' . $proxima_pagina . '" data-listar-todos="' . $listar_todos_js . '">Próxima</a></li>';

        // Adicionar o botão "Última" se a próxima página não for a última
        if ($proxima_pagina < $total_paginas) {
            echo '<li class="page-item"><a class="page-link btn-pagina-cliente" href="#" data-pagina="' . $total_paginas . '" data-listar-todos="' . $listar_todos_js . '">Última</a></li>';
        }
    }

    echo '</ul></nav>';
} else {
    echo '<div class="alert alert-warning">Nenhum cliente encontrado com os critérios de busca.</div>';
}

echo '</div></div>'; // Fecha o card e o body
