<?php
// Arquivo: servicos_agendamento_buscar_rapido.php
// Responsável por receber parâmetros de busca, ordenação e paginação, e retornar a tabela HTML dos agendamentos.

require_once 'conexao.php'; // Certifique-se de que este arquivo existe e conecta ao DB

// Constante utilizada para lógica específica, se necessário
$ID_SERVICO_VACINA = 6;

// =========================================================================
// 1. COLETAR E SANITIZAR PARÂMETROS DE BUSCA E PAGINAÇÃO
// =========================================================================
$limite = isset($_GET['limite']) && is_numeric($_GET['limite']) ? (int)$_GET['limite'] : 10;
$pagina_atual = isset($_GET['pagina_atual']) && is_numeric($_GET['pagina_atual']) ? (int)$_GET['pagina_atual'] : 1;
$offset = ($pagina_atual - 1) * $limite;

// Parâmetros de Filtro e Ordenação
$termo_busca = $_GET['busca'] ?? ''; // Pet, Cliente ou Serviço
$filtro_status = $_GET['status_filtro'] ?? 'todos';
$ordenacao_param = $_GET['ordenacao'] ?? 'data_crescente'; // Novo parâmetro de ordenação

// Flags de controle
// Note que 'listar_todos' é tratado como string 'true'/'false' devido à passagem via GET
$listar_todos = $_GET['listar_todos'] ?? 'false';
$total_registros = 0;
$total_paginas = 1;
$erro_sql = null;
$agendamentos = [];

// Mapeamento de Ordenação
$mapa_ordenacao = [
    'data_crescente' => 'a.data_agendamento ASC',
    'data_decrescente' => 'a.data_agendamento DESC',
    'cliente' => 'c.nome ASC',
    'pet' => 'p.nome ASC',
];
$order_by = $mapa_ordenacao[$ordenacao_param] ?? 'a.data_agendamento ASC';


// =========================================================================
// 2. CONSTRUÇÃO DA CLÁUSULA WHERE (CONTADOR E CONSULTA PRINCIPAL)
// =========================================================================

$condicoes = [];
$params_tipos = '';
$params_valores = [];

// A base da consulta sempre inclui os JOINs
$base_query = "
    FROM 
        agendamento a
    JOIN 
        pet p ON a.pet_id = p.id
    JOIN 
        cliente c ON p.cliente_id = c.id
    JOIN
        servico s ON a.servico_id = s.id
";

// Constrói as condições de busca se não for para listar todos
if ($listar_todos !== 'true') {
    
    // Filtro por termo de busca (Cliente, Pet ou Serviço)
    if (!empty($termo_busca)) {
        $condicoes[] = "(c.nome LIKE ? OR p.nome LIKE ? OR s.nome LIKE ?)";
        $like = '%' . $termo_busca . '%';
        $params_tipos .= 'sss';
        // É crucial usar $params_valores[] = $like; para que o array seja populado corretamente
        $params_valores[] = $like;
        $params_valores[] = $like;
        $params_valores[] = $like;
    }

    // Filtro por Status
    if ($filtro_status !== 'todos' && in_array($filtro_status, ['agendado', 'confirmado', 'concluido', 'cancelado'])) {
        $condicoes[] = "a.status = ?";
        $params_tipos .= 's';
        $params_valores[] = $filtro_status;
    }
}

// Adiciona as condições WHERE
$where_clause = !empty($condicoes) ? " WHERE " . implode(' AND ', $condicoes) : "";


// =========================================================================
// 3. CONTAR TOTAL DE REGISTROS (Para Paginação)
// =========================================================================

try {
    // Consulta de contagem
    $sql_count = "SELECT COUNT(DISTINCT a.id) " . $base_query . $where_clause;
    
    $stmt_count = mysqli_prepare($conexao, $sql_count);
    
    // Vincula os parâmetros, se houver
    if (!empty($params_valores)) {
        // Usa o operador ... para descompactar o array para mysqli_stmt_bind_param
        mysqli_stmt_bind_param($stmt_count, $params_tipos, ...$params_valores);
    }
    
    mysqli_stmt_execute($stmt_count);
    $result_count = mysqli_stmt_get_result($stmt_count);
    $total_registros = mysqli_fetch_row($result_count)[0];
    
    // Evita divisão por zero
    $total_paginas = $total_registros > 0 ? ceil($total_registros / $limite) : 1;
    
    // Garante que a página atual seja válida
    $pagina_atual = max(1, min($pagina_atual, $total_paginas));
    $offset = ($pagina_atual - 1) * $limite;

    // Se a busca não encontrou registros, não tenta buscar
    if ($total_registros > 0) {
        
        // =========================================================================
        // 4. MONTAGEM DA CONSULTA SQL PRINCIPAL
        // =========================================================================
        
        $sql = "
            SELECT DISTINCT
                a.id AS agendamento_id,
                a.data_agendamento,
                a.status,
                a.servico_id,
                a.pet_id,
                c.nome AS cliente_nome,
                p.nome AS pet_nome,
                s.nome AS servico_nome
        " . $base_query . $where_clause . " ORDER BY " . $order_by . " LIMIT ? OFFSET ?";
        
        // Tipos e Valores para a consulta principal (adiciona limit e offset)
        $params_tipos_principal = $params_tipos . 'ii';
        $params_valores_principal = array_merge($params_valores, [$limite, $offset]);

        // Execução da consulta
        $stmt = mysqli_prepare($conexao, $sql);
        // Bind dos parâmetros com os novos valores (limit e offset)
        mysqli_stmt_bind_param($stmt, $params_tipos_principal, ...$params_valores_principal);
        mysqli_stmt_execute($stmt);
        $result = mysqli_stmt_get_result($stmt);
        
        $agendamentos = $result ? mysqli_fetch_all($result, MYSQLI_ASSOC) : [];
        
    }
    
} catch (\Exception $e) {
    $erro_sql = 'Erro no Banco de Dados: ' . $e->getMessage() . '. SQL: ' . ($sql ?? 'N/A');
}

// Função para formatar o status com cor
function formatar_status($status) {
    $classe = match ($status) {
        'agendado' => 'badge bg-primary',
        'confirmado' => 'badge bg-info',
        'concluido' => 'badge bg-success',
        'cancelado' => 'badge bg-danger',
        default => 'badge bg-secondary',
    };
    return "<span class='{$classe}'>" . ucfirst($status) . "</span>";
}

// =========================================================================
// 5. RETORNO DO BLOCO HTML COM OS RESULTADOS E PAGINAÇÃO
// =========================================================================

if (!empty($erro_sql)) {
    echo '<div class="alert alert-danger">' . $erro_sql . '</div>';
    exit;
}

$titulo_busca = $listar_todos === 'true' ? "Todos os Agendamentos" : "Agendamentos Encontrados";
$titulo_completo = $titulo_busca . ': ' . $total_registros;
if ($total_registros > 0) {
    $titulo_completo .= ' (Pág. ' . $pagina_atual . ' de ' . $total_paginas . ')';
}
?>

<div class="card shadow-sm">
    <div class="card-body">
        <h5 class="card-title"><i class="fas fa-search me-1"></i> <?php echo $titulo_completo; ?></h5>

        <?php if ($total_registros > 0): ?>
            <div class="table-responsive">
                <table class="table table-striped table-hover table-sm align-middle mb-0" id="tabela-agendamentos">
                    <thead class="table-dark">
                        <tr>
                            <th>ID</th>
                            <th>Data/Hora</th>
                            <th>Pet</th>
                            <th>Cliente</th>
                            <th>Serviço</th>
                            <th>Status</th>
                            <th>Ações</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php foreach ($agendamentos as $agendamento): ?>
                        <tr id="agendamento-<?php echo $agendamento['agendamento_id']; ?>" 
                            class="<?php echo $agendamento['status'] === 'concluido' ? 'table-success' : ($agendamento['status'] === 'cancelado' ? 'table-danger' : ''); ?>">
                            <td><?php echo htmlspecialchars($agendamento['agendamento_id']); ?></td>
                            <td><?php echo date('d/m/Y H:i', strtotime($agendamento['data_agendamento'])); ?></td>
                            <td><?php echo htmlspecialchars($agendamento['pet_nome']); ?></td>
                            <td><?php echo htmlspecialchars($agendamento['cliente_nome']); ?></td>
                            <td><?php echo htmlspecialchars($agendamento['servico_nome']); ?></td>
                            <td class="status-cell">
                                <?php echo formatar_status($agendamento['status']); ?>
                            </td>
                            <td>
                                <a href="#"
                                   class="btn btn-sm btn-warning me-1 item-menu-ajax"
                                   data-pagina="servicos_agendar_banhotosa.php?id=<?php echo $agendamento['agendamento_id']; ?>"
                                   title="Editar Agendamento">
                                    <i class="fas fa-edit"></i>
                                </a>

                                <?php if (!in_array($agendamento['status'], ['concluido', 'cancelado'])): ?>
                                <button class="btn btn-sm btn-success btn-processar-agendamento me-1"
                                        data-id="<?php echo $agendamento['agendamento_id']; ?>"
                                        data-acao="concluir_status"
                                        title="Marcar como Concluído">
                                    <i class="fas fa-check-circle"></i>
                                </button>
                                <?php endif; ?>

                                <button class="btn btn-sm btn-danger btn-processar-agendamento me-1"
                                        data-id="<?php echo $agendamento['agendamento_id']; ?>"
                                        data-acao="deletar"
                                        title="Excluir Permanentemente">
                                    <i class="fas fa-trash-alt"></i>
                                </button>

                                <button class="btn btn-sm btn-secondary btn-processar-agendamento"
                                        data-id="<?php echo $agendamento['agendamento_id']; ?>"
                                        data-acao="cancelar_status"
                                        title="Cancelar Agendamento">
                                    <i class="fas fa-times"></i>
                                </button>
                            </td>
                        </tr>
                        <?php endforeach; ?>
                    </tbody>
                </table>
            </div>

            <?php
            // Lógica de Paginação
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
            $start_page = max(1, $start_page); // Garante que não comece abaixo de 1
            ?>

            <nav aria-label="Paginação de Agendamentos" class="mt-3">
                <ul class="pagination justify-content-center flex-wrap">

                    <?php if ($pagina_atual > 1): ?>
                        <li class="page-item"><a class="page-link btn-pagina-agendamento" href="#" data-pagina="1" data-listar-todos="<?= $flag_listar_todos ?>">Primeira</a></li>
                    <?php endif; ?>

                    <?php if ($start_page > 1): ?>
                        <li class="page-item disabled"><span class="page-link">...</span></li>
                    <?php endif; ?>

                    <?php for ($i = $start_page; $i <= $end_page; $i++): ?>
                        <li class="page-item <?= ($i == $pagina_atual) ? 'active' : '' ?>">
                            <a class="page-link btn-pagina-agendamento" href="#" data-pagina="<?= $i ?>" data-listar-todos="<?= $flag_listar_todos ?>">
                                <?= $i ?>
                            </a>
                        </li>
                    <?php endfor; ?>

                    <?php if ($end_page < $total_paginas): ?>
                        <li class="page-item disabled"><span class="page-link">...</span></li>
                    <?php endif; ?>

                    <?php if ($pagina_atual < $total_paginas):
                        $proxima_pagina = $pagina_atual + 1;
                    ?>
                        <li class="page-item"><a class="page-link btn-pagina-agendamento" href="#" data-pagina="<?= $proxima_pagina ?>" data-listar-todos="<?= $flag_listar_todos ?>">Próxima</a></li>

                        <?php if ($proxima_pagina < $total_paginas): ?>
                            <li class="page-item"><a class="page-link btn-pagina-agendamento" href="#" data-pagina="<?= $total_paginas ?>" data-listar-todos="<?= $flag_listar_todos ?>">Última</a></li>
                        <?php endif; ?>
                    <?php endif; ?>
                </ul>
                <div class="text-center text-muted">Exibindo <?= count($agendamentos) ?> de <?= $total_registros ?> agendamentos.</div>
            </nav>
            <?php else: ?>
            <div class="alert alert-warning">Nenhum agendamento encontrado com os filtros aplicados.</div>
        <?php endif; ?>
    </div>
</div>