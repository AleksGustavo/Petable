<?php
// Arquivo: servicos_agendamentos_listar.php - Versão Completa (inclui Pesquisa/Ações)

require_once 'conexao.php'; 

// AJUSTE CRÍTICO: ID do serviço 'Aplicação de Vacina'
$ID_SERVICO_VACINA = 6; 

// ==============================================================================
// 1. Lógica de Pesquisa e Filtro
// ==============================================================================
$termo_busca = $_GET['busca'] ?? '';
$filtro_status = $_GET['status_filtro'] ?? 'todos';

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
$params_types = ''; // Tipos para mysqli_stmt_bind_param
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

$sql .= " ORDER BY a.data_agendamento ASC"; // Data mais próxima primeiro

$agendamentos = [];
$erro_sql = ''; 

if (isset($conexao) && $conexao) {
    try {
        $stmt = mysqli_prepare($conexao, $sql);
        
        // Bind dos parâmetros
        if (!empty($params)) {
            mysqli_stmt_bind_param($stmt, $params_types, ...$params);
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
?>

<!DOCTYPE html>
<html lang="pt-br">
<head>
    <meta charset="UTF-8">
    <title>Lista de Agendamentos</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0-beta3/css/all.min.css">
</head>
<body class="bg-light">

<div class="container mt-4">
    <h2><i class="fas fa-clipboard-list me-2"></i> Listagem de Agendamentos</h2>
    
    <div class="d-flex justify-content-between align-items-center mb-3">
        <a href="servicos_agendar_banhotosa.php" class="btn btn-success"><i class="fas fa-plus me-1"></i> Novo Agendamento</a>
    </div>

    <div id="status-message-area">
        <?php if (!empty($erro_sql)): ?>
            <div class="alert alert-danger"><?php echo $erro_sql; ?></div>
        <?php endif; ?>
        <?php if (isset($_GET['status']) && $_GET['status'] === 'success_save'): ?>
            <div class="alert alert-success alert-dismissible fade show" role="alert">
                Agendamento cadastrado com sucesso!
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        <?php endif; ?>
    </div>

    <form method="GET" action="servicos_agendamentos_listar.php" class="mb-4 p-3 bg-white shadow-sm rounded">
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
                <a href="servicos_agendamentos_listar.php" class="btn btn-secondary btn-sm"><i class="fas fa-sync-alt me-1"></i> Limpar</a>
            </div>
        </div>
    </form>


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
                    <tr>
                        <td><?php echo $agendamento['agendamento_id']; ?></td>
                        <td><?php echo date('d/m/Y H:i', strtotime($agendamento['data_agendamento'])); ?></td>
                        <td><?php echo htmlspecialchars($agendamento['pet_nome']); ?></td>
                        <td><?php echo htmlspecialchars($agendamento['cliente_nome']); ?></td>
                        <td><?php echo htmlspecialchars($agendamento['servico_nome']); ?></td>
                        <td><?php echo formatar_status($agendamento['status']); ?></td>
                        <td>
                            <a href="servicos_agendar_banhotosa.php?id=<?php echo $agendamento['agendamento_id']; ?>" 
                               class="btn btn-sm btn-warning me-1" title="Editar Agendamento">
                                <i class="fas fa-edit"></i>
                            </a>

                            <button class="btn btn-sm btn-danger btn-deletar-permanente me-1" 
                                    data-agendamento-id="<?php echo $agendamento['agendamento_id']; ?>"
                                    title="Excluir Permanentemente">
                                <i class="fas fa-trash-alt"></i>
                            </button>

                            <?php 
                            // Lógica para o BOTÃO FINALIZAR VACINA (Mantido do seu original)
                            if ($agendamento['servico_id'] == $ID_SERVICO_VACINA && 
                                in_array($agendamento['status'], ['agendado', 'confirmado'])): 
                            ?>
                                <button class="btn btn-sm btn-success btn-finalizar-vacina" 
                                        data-agendamento-id="<?php echo $agendamento['agendamento_id']; ?>" 
                                        data-pet-id="<?php echo $agendamento['pet_id']; ?>" 
                                        title="Finalizar Aplicação de Vacina e Registrar na Carteira">
                                    <i class="fas fa-syringe me-1"></i> Finalizar
                                </button>
                            <?php endif; ?>
                            
                            <button class="btn btn-sm btn-secondary btn-cancelar-agendamento" 
                                    data-agendamento-id="<?php echo $agendamento['agendamento_id']; ?>"
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

<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>

<script>
$(document).ready(function() {
    
    // =================================================================
    // NOVO: Lógica para DELETAR AGENDAMENTO PERMANENTEMENTE
    // =================================================================
    $(document).on('click', '.btn-deletar-permanente', function() {
        const id = $(this).data('agendamento-id');
        
        if (confirm(`AVISO: Tem certeza que deseja DELETAR PERMANENTEMENTE o agendamento #${id}? Esta ação não pode ser desfeita.`)) {
            $.ajax({
                url: 'agendamento_processar.php', // <== USA O NOVO ARQUIVO!
                type: 'POST',
                data: {
                    acao: 'deletar',
                    id: id
                },
                dataType: 'json',
                success: function(response) {
                    const statusArea = $('#status-message-area');
                    statusArea.empty();
                    
                    if (response.success) {
                        statusArea.html('<div class="alert alert-success">' + response.message + '</div>');
                        // Recarrega a página para atualizar a lista
                        setTimeout(() => {
                             window.location.reload(); 
                         }, 1000); 
                    } else {
                        statusArea.html('<div class="alert alert-danger">' + response.message + '</div>');
                    }
                },
                error: function() {
                    $('#status-message-area').html('<div class="alert alert-danger">Erro de comunicação ao tentar deletar o agendamento.</div>');
                }
            });
        }
    });

    // Seus códigos JS para finalizar e cancelar agendamentos devem continuar aqui.
    // Exemplo:
    /*
    $('.btn-cancelar-agendamento').on('click', function() {
        // ... (sua lógica existente de cancelamento aqui)
    });
    */
});
</script>
</body>
</html>