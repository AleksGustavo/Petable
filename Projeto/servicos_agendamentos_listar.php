<?php
require_once 'conexao.php'; 

// ==============================================================================
// AJUSTE CRÍTICO: ID do serviço 'Aplicação de Vacina'
// CONFIRME ESTE VALOR EM SUA TABELA `servicos`!
$ID_SERVICO_VACINA = 6; 
// ==============================================================================

$agendamentos = [];
$erro_sql = ''; 

if (isset($conexao) && $conexao) {
    // Busca todos os agendamentos com JOINs para obter nome do Cliente, Pet e Serviço.
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
            ORDER BY 
                a.data_agendamento DESC"; 
                
    $result = mysqli_query($conexao, $sql);
    
    if ($result) {
        $agendamentos = mysqli_fetch_all($result, MYSQLI_ASSOC);
        mysqli_free_result($result);
    } else {
        $erro_sql = "Erro fatal ao buscar agendamentos: " . mysqli_error($conexao);
    }
    
    mysqli_close($conexao);
} else {
    $erro_sql = "Erro de conexão com o banco de dados.";
}

// Função para gerar as classes de badge de status
function getStatusBadgeClass($status) {
    switch ($status) {
        case 'agendado': return 'bg-info';
        case 'confirmado': return 'bg-primary';
        case 'em_andamento': return 'bg-warning text-dark';
        case 'concluido': return 'bg-success';
        case 'cancelado': return 'bg-danger';
        default: return 'bg-secondary';
    }
}
?>

<div class="container mt-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <h2><i class="fas fa-calendar-check me-2"></i> Lista de Agendamentos</h2>
        <a href="#" class="btn btn-success item-menu-ajax" data-pagina="servicos_agendamentos_cadastro.php">
            <i class="fas fa-plus me-1"></i> Novo Agendamento
        </a>
    </div>

    <?php if (!empty($erro_sql)): ?>
        <div class="alert alert-danger">
            <?php echo $erro_sql; ?>
        </div>
    <?php endif; ?>

    <?php if (empty($agendamentos) && empty($erro_sql)): ?>
        <div class="alert alert-info text-center">Nenhum agendamento encontrado.</div>
    <?php elseif (!empty($agendamentos)): ?>
        <div class="table-responsive">
            <table class="table table-striped table-hover shadow-sm">
                <thead class="table-dark">
                    <tr>
                        <th>Data/Hora</th>
                        <th>Serviço</th>
                        <th>Cliente</th>
                        <th>Pet</th>
                        <th>Status</th>
                        <th style="min-width: 250px;">Ações</th>
                    </tr>
                </thead>
                <tbody>
                    <?php foreach ($agendamentos as $agendamento): ?>
                    <tr>
                        <td><?php echo date('d/m/Y H:i', strtotime($agendamento['data_agendamento'])); ?></td>
                        <td><?php echo htmlspecialchars($agendamento['servico_nome']); ?></td>
                        <td><?php echo htmlspecialchars($agendamento['cliente_nome']); ?></td>
                        <td>
                            <a href="#" class="item-menu-ajax" data-pagina="pets_detalhes.php?id=<?php echo $agendamento['pet_id']; ?>">
                                <?php echo htmlspecialchars($agendamento['pet_nome']); ?>
                            </a>
                        </td>
                        <td>
                            <span class="badge <?php echo getStatusBadgeClass($agendamento['status']); ?>">
                                <?php echo htmlspecialchars(ucfirst(str_replace('_', ' ', $agendamento['status']))); ?>
                            </span>
                        </td>
                        <td>
                            <button class="btn btn-sm btn-outline-primary item-menu-ajax" 
                                    data-pagina="servicos_agendamentos_editar.php?id=<?php echo $agendamento['agendamento_id']; ?>"
                                    title="Editar/Detalhes">
                                <i class="fas fa-edit"></i>
                            </button>

                            <?php 
                            // Lógica para o BOTÃO FINALIZAR VACINA
                            if ($agendamento['servico_id'] == $ID_SERVICO_VACINA && 
                                in_array($agendamento['status'], ['agendado', 'confirmado'])): 
                            ?>
                                <button class="btn btn-sm btn-success btn-finalizar-vacina" 
                                        data-agendamento-id="<?php echo $agendamento['agendamento_id']; ?>" 
                                        data-pet-id="<?php echo $agendamento['pet_id']; ?>" 
                                        title="Finalizar Aplicação de Vacina e Registrar na Carteira">
                                    <i class="fas fa-syringe me-2"></i> Finalizar
                                </button>
                            <?php endif; ?>
                            
                            <button class="btn btn-sm btn-danger btn-cancelar-agendamento" 
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
