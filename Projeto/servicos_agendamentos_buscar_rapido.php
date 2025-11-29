<?php

require_once 'conexao.php'; 

$ID_SERVICO_VACINA = 6;

$limite = isset($_GET['limite']) && is_numeric($_GET['limite']) ? (int)$_GET['limite'] : 10;
$pagina_atual = isset($_GET['pagina_atual']) && is_numeric($_GET['pagina_atual']) ? (int)$_GET['pagina_atual'] : 1;
$offset = ($pagina_atual - 1) * $limite;

$termo_busca = $_GET['busca'] ?? ''; 
$filtro_status = $_GET['status_filtro'] ?? 'todos';
$ordenacao_param = $_GET['ordenacao'] ?? 'data_crescente'; 

$listar_todos = $_GET['listar_todos'] ?? 'false';
$total_registros = 0;
$total_paginas = 1;
$erro_sql = null;
$agendamentos = [];

$mapa_ordenacao = [
    'data_crescente' => 'a.data_agendamento ASC',
    'data_decrescente' => 'a.data_agendamento DESC',
    'cliente' => 'c.nome ASC',
    'pet' => 'p.nome ASC',
];
$order_by = $mapa_ordenacao[$ordenacao_param] ?? 'a.data_agendamento ASC';


$condicoes = [];
$params_tipos = '';
$params_valores = [];

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

if ($listar_todos !== 'true') {
    
    if (!empty($termo_busca)) {
        $condicoes[] = "(c.nome LIKE ? OR p.nome LIKE ? OR s.nome LIKE ?)";
        $like = '%' . $termo_busca . '%';
        $params_tipos .= 'sss';
        $params_valores[] = $like;
        $params_valores[] = $like;
        $params_valores[] = $like;
    }

    // LÓGICA DE FILTRO DE STATUS ATUALIZADA
    if ($filtro_status !== 'todos') {
        if ($filtro_status === 'atrasado') {
            // Filtra por agendamentos que ainda estão com status 'agendado' ou 'confirmado'
            // mas cuja data/hora já passou em relação ao momento atual (NOW())
            $condicoes[] = "a.status IN ('agendado', 'confirmado') AND a.data_agendamento < NOW()";
        } elseif (in_array($filtro_status, ['agendado', 'confirmado', 'concluido', 'cancelado'])) {
            // Para os status fixos do banco, usa a coluna 'status'
            $condicoes[] = "a.status = ?";
            $params_tipos .= 's';
            $params_valores[] = $filtro_status;
        }
    }
}

$where_clause = !empty($condicoes) ? " WHERE " . implode(' AND ', $condicoes) : "";


try {
    $sql_count = "SELECT COUNT(DISTINCT a.id) " . $base_query . $where_clause;
    
    $stmt_count = mysqli_prepare($conexao, $sql_count);
    
    if (!empty($params_valores)) {
        mysqli_stmt_bind_param($stmt_count, $params_tipos, ...$params_valores);
    }
    
    mysqli_stmt_execute($stmt_count);
    $result_count = mysqli_stmt_get_result($stmt_count);
    $total_registros = mysqli_fetch_row($result_count)[0];
    
    $total_paginas = $total_registros > 0 ? ceil($total_registros / $limite) : 1;
    
    $pagina_atual = max(1, min($pagina_atual, $total_paginas));
    $offset = ($pagina_atual - 1) * $limite;

    if ($total_registros > 0) {
        
        $sql = "
            SELECT DISTINCT
                a.id AS agendamento_id,
                a.data_agendamento,
                a.status,
                a.servico_id,
                a.pet_id,
                c.nome AS cliente_nome,
                p.nome AS pet_nome,
                s.nome AS servico_nome,
                
                -- CAMPO DINÂMICO QUE CALCULA O STATUS DE EXIBIÇÃO
                CASE
                    -- 1. Se o status for concluído, cancelado ou confirmado, ele é o que vale.
                    WHEN a.status IN ('concluido', 'cancelado') THEN a.status
                    
                    -- 2. Se a data já passou E o status ainda é agendado/confirmado, é 'atrasado'.
                    WHEN a.data_agendamento < NOW() AND a.status IN ('agendado', 'confirmado') THEN 'atrasado'
                    
                    -- 3. Caso contrário, mantém o status real (agendado ou confirmado)
                    ELSE a.status
                END AS status_display
                
        " . $base_query . $where_clause . " ORDER BY " . $order_by . " LIMIT ? OFFSET ?";
        
        $params_tipos_principal = $params_tipos . 'ii';
        $params_valores_principal = array_merge($params_valores, [$limite, $offset]);

        $stmt = mysqli_prepare($conexao, $sql);
        mysqli_stmt_bind_param($stmt, $params_tipos_principal, ...$params_valores_principal);
        mysqli_stmt_execute($stmt);
        $result = mysqli_stmt_get_result($stmt);
        
        $agendamentos = $result ? mysqli_fetch_all($result, MYSQLI_ASSOC) : [];
        
    }
    
} catch (\Exception $e) {
    $erro_sql = 'Erro no Banco de Dados: ' . $e->getMessage() . '. SQL: ' . ($sql ?? 'N/A');
}

function formatar_status($status) {
    $classe = match ($status) {
        'agendado' => 'badge bg-primary',
        'confirmado' => 'badge bg-info',
        'atrasado' => 'badge bg-warning text-dark', 
        'concluido' => 'badge bg-success',
        'cancelado' => 'badge bg-danger',
        default => 'badge bg-secondary',
    };
    return "<span class='{$classe}'>" . ucfirst($status) . "</span>";
}

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
                        <?php foreach ($agendamentos as $agendamento): 
                            // Usa o status dinâmico para exibição na célula e na linha
                            $status_exibido = $agendamento['status_display']; 
                            
                            // Define a classe da linha
                            $row_class = match ($status_exibido) {
                                'concluido' => 'table-success',
                                'cancelado' => 'table-danger',
                                'atrasado' => 'table-warning', // Linha amarela para atrasados
                                default => '',
                            };
                        ?>
                        <tr id="agendamento-<?php echo $agendamento['agendamento_id']; ?>" 
                            class="<?php echo $row_class; ?>">
                            <td><?php echo htmlspecialchars($agendamento['agendamento_id']); ?></td>
                            <td><?php echo date('d/m/Y H:i', strtotime($agendamento['data_agendamento'])); ?></td>
                            <td><?php echo htmlspecialchars($agendamento['pet_nome']); ?></td>
                            <td><?php echo htmlspecialchars($agendamento['cliente_nome']); ?></td>
                            <td><?php echo htmlspecialchars($agendamento['servico_nome']); ?></td>
                            <td class="status-cell">
                                <?php echo formatar_status($status_exibido); ?>
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
            $start_page = max(1, $start_page); 
            ?>

            <nav aria-label="Paginação de Agendamentos" class="mt-3">
                <ul class="pagination justify-content-center flex-wrap">

                    <?php if ($pagina_atual > 1): ?>
                        <li class="page-item"><a class="page-link btn-pagina-agendamento" href="#" data-pagina="1" data-listar-todos="<?= $flag_listar_todos ?>">Primeira</a></li>
                    <?php endif; ?>

                    <?php if ($start_page > 1): ?>
                        <li class="page-item disabled"><span class="page-link">...</span></span></li>
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