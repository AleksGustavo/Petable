<?php
// Arquivo: servicos_agendamentos_listar.php - Versão Final Corrigida (Puro Conteúdo)

// NOTA: ESTE ARQUIVO AGORA CONTÉM APENAS O CONTEÚDO HTML PARA SER CARREGADO VIA AJAX.
// TODO O JAVASCRIPT FOI MOVIDO PARA O dashboard.php (OU PARA ONDE O AJAX É CHAMADO).

require_once 'conexao.php'; 

$ID_SERVICO_VACINA = 6; 

// ==============================================================================
// 1. Lógica de Pesquisa e Filtro (CORRIGIDA COM GROUP BY)
// ==============================================================================
$termo_busca = $_GET['busca'] ?? '';
$filtro_status = $_GET['status_filtro'] ?? 'todos';

// Removido o DISTINCT e o GROUP BY será adicionado no final
$sql = "SELECT 
            a.id AS agendamento_id, 
            a.data_agendamento, 
            a.status, 
            a.servico_id,     
            a.pet_id,         
            c.nome AS cliente_nome, 
            p.nome AS pet_nome,
            s.nome AS servico_nome 
        FROM 
            agendamento a 
        JOIN 
            pet p ON a.pet_id = p.id
        JOIN 
            cliente c ON p.cliente_id = c.id
        JOIN
            servico s ON a.servico_id = s.id
        WHERE 1=1 "; 
$params_types = ''; 
$params = [];

// Filtro por termo de busca (Cliente, Pet ou Serviço)
if (!empty($termo_busca)) {
    $sql .= " AND (c.nome LIKE ? OR p.nome LIKE ? OR s.nome LIKE ?) ";
    $like = '%' . $termo_busca . '%';
    $params_types .= 'sss';
    $params[] = $like;
    $params[] = $like;
    $params[] = $like;
}

// Filtro por Status
if ($filtro_status !== 'todos' && in_array($filtro_status, ['agendado', 'confirmado', 'concluido', 'cancelado'])) {
    $sql .= " AND a.status = ? ";
    $params_types .= 's';
    $params[] = $filtro_status;
}

// Adiciona o GROUP BY para evitar duplicação no resultado final da agregação
$sql .= " GROUP BY a.id, a.data_agendamento, a.status, a.servico_id, a.pet_id, c.nome, p.nome, s.nome";

$sql .= " ORDER BY a.data_agendamento ASC"; 

$agendamentos = [];
$erro_sql = ''; 

// Sua conexão usa mysqli, vamos adaptar a execução para garantir
if (isset($conexao) && $conexao) {
    try {
        $stmt = mysqli_prepare($conexao, $sql);
        
        if (!empty($params)) {
            // Se você está usando PHP < 8.1, pode precisar de uma chamada dinâmica para mysqli_stmt_bind_param
            // CORREÇÃO: Usando o array_merge correto para a chamada dinâmica
            $bind_params = array_merge([$stmt, $params_types], $params);
            call_user_func_array('mysqli_stmt_bind_param', array_ref($bind_params));
        }

        mysqli_stmt_execute($stmt);
        $result = mysqli_stmt_get_result($stmt);
        
        if ($result) {
            $agendamentos = mysqli_fetch_all($result, MYSQLI_ASSOC);
        } else {
            $erro_sql = 'Erro ao executar a consulta: ' . mysqli_error($conexao);
        }
    } catch (\Exception $e) {
        $erro_sql = 'Erro na preparação da consulta: ' . $e->getMessage();
    }
} else {
    $erro_sql = 'Erro crítico: Conexão mysqli indisponível.';
}

// Função auxiliar para referências (necessária para call_user_func_array com bind_param no PHP antigo)
if (!function_exists('array_ref')) {
    function array_ref(&$arr) {
        $refs = [];
        foreach ($arr as $key => $value)
            $refs[$key] = &$arr[$key];
        return $refs;
    }
}


// Função para formatar o status com cor (Usada na listagem)
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
?>

<div class="container mt-4">
    <h2><i class="fas fa-clipboard-list me-2"></i> Listagem de Agendamentos</h2>
    
    <div class="d-flex justify-content-between align-items-center mb-3">
        <a href="#" class="btn btn-success item-menu-ajax" data-pagina="servicos_agendar_banhotosa.php"><i class="fas fa-plus me-1"></i> Novo Agendamento</a>
        
        <button id="toggle-concluidos" class="btn btn-info btn-sm">
            <i class="fas fa-eye-slash me-1"></i> Ocultar Concluídos
        </button>
    </div>

    <form method="GET" id="filter-form" class="mb-4 p-3 bg-white shadow-sm rounded">
        <h5><i class="fas fa-filter me-1"></i> Filtrar Agendamentos</h5>
        <div class="row g-2 align-items-end">
            <div class="col-md-5">
                <label for="busca" class="form-label mb-1">Pesquisar por Nome</label>
                <input type="text" class="form-control form-control-sm" id="busca" name="busca" 
                        value="<?php echo htmlspecialchars($termo_busca); ?>" placeholder="Pet, Cliente ou Serviço">
            </div>
            <div class="col-md-4">
                <label for="status_filtro" class="form-label mb-1">Status</label>
                <select class="form-select form-select-sm" id="status_filtro" name="status_filtro">
                    <option value="todos">Todos</option>
                    <option value="agendado" <?php if ($filtro_status === 'agendado') echo 'selected'; ?>>Agendado</option>
                    <option value="confirmado" <?php if ($filtro_status === 'confirmado') echo 'selected'; ?>>Confirmado</option>
                    <option value="concluido" <?php if ($filtro_status === 'concluido') echo 'selected'; ?>>Concluído</option>
                    <option value="cancelado" <?php if ($filtro_status === 'cancelado') echo 'selected'; ?>>Cancelado</option>
                </select>
            </div>
            <div class="col-md-3">
                <button type="submit" class="btn btn-primary btn-sm me-2"><i class="fas fa-search me-1"></i> Buscar</button>
                <a href="#" class="btn btn-secondary btn-sm item-menu-ajax" data-pagina="servicos_agendamentos_listar.php"><i class="fas fa-sync-alt me-1"></i> Limpar</a>
            </div>
        </div>
    </form>


    <div id="agendamentos-content">
        <?php if (!empty($erro_sql)): ?>
            <div class="alert alert-danger"><?php echo $erro_sql; ?></div>
        <?php endif; ?>
        <?php if (empty($agendamentos)): ?>
            <div class="alert alert-warning text-center">Nenhum agendamento encontrado com os filtros aplicados.</div>
        <?php else: ?>
            <div class="table-responsive">
                <table class="table table-striped table-hover align-middle">
                    <thead>
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
                        <tr id="agendamento-<?php echo $agendamento['agendamento_id']; ?>" class="<?php echo $agendamento['status'] === 'concluido' ? 'status-concluido' : ''; ?>">
                            <td><?php echo $agendamento['agendamento_id']; ?></td>
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
        <?php endif; ?>
    </div>
</div>