<?php
// Certifique-se de que 'conexao.php' está acessível no contexto de execução do AJAX.
// Se necessário, ajuste o caminho.
require_once 'conexao.php'; 

// Funções de formatação devem estar presentes aqui, se não estiverem no arquivo principal do dashboard.
if (!function_exists('formatarMoeda')) {
    function formatarMoeda($valor) {
        // Garantindo que $valor seja um float para formatação segura
        return 'R$ ' . number_format((float)$valor, 2, ',', '.');
    }
}

if (!function_exists('formatarData')) {
    function formatarData($data) {
        try {
            return (new DateTime($data))->format('d/m/Y H:i');
        } catch (Exception $e) {
            return 'Data Inválida';
        }
    }
}

$vendas = [];
$erro_bd = null; // Inicializa a variável de erro

try {
    $sql_vendas = "
        SELECT 
            v.id AS venda_id,
            v.data_venda,
            v.valor_total,
            v.desconto,
            v.forma_pagamento,
            COALESCE(c.nome, 'Cliente Anônimo') AS nome_cliente,
            f.nome AS nome_funcionario
        FROM 
            venda v
        LEFT JOIN 
            cliente c ON v.cliente_id = c.id
        LEFT JOIN 
            funcionario f ON v.funcionario_id = f.id
        ORDER BY 
            v.data_venda DESC
        LIMIT 100
    ";
    
    $stmt_vendas = $pdo->query($sql_vendas);
    $vendas = $stmt_vendas->fetchAll(PDO::FETCH_ASSOC);

} catch (\PDOException $e) {
    $erro_bd = "Erro ao carregar o histórico de vendas: " . $e->getMessage();
    error_log($erro_bd);
}

?>

<div class="row mb-3">
    <div class="col-12">
        <div class="d-flex justify-content-between align-items-center">
            <h2 class="h3"><i class="fas fa-history me-2"></i> Histórico de Vendas</h2>
            </div>
        <hr class="mt-1">
    </div>
</div>

<?php if (isset($erro_bd) && $erro_bd): ?>
    <div class="alert alert-danger" role="alert">
        <?php echo $erro_bd; ?>
    </div>
<?php endif; ?>

<div class="card shadow-sm">
    <div class="card-header bg-dark text-white p-3">
        <h5 class="mb-0">Últimas <?php echo count($vendas); ?> Vendas Registradas</h5>
    </div>
    <div class="card-body p-0">
        <div class="table-responsive">
            <table class="table table-striped table-hover table-vendas mb-0">
                <thead>
                    <tr>
                        <th># Venda</th>
                        <th>Data/Hora</th>
                        <th>Cliente</th>
                        <th>Funcionário</th>
                        <th>Desconto</th>
                        <th>Total Final</th>
                        <th>Pagamento</th>
                        <th>Ações</th>
                    </tr>
                </thead>
                <tbody>
                    <?php if (empty($vendas)): ?>
                        <tr>
                            <td colspan="8" class="text-center text-muted p-4">Nenhuma venda encontrada no histórico.</td>
                        </tr>
                    <?php else: ?>
                        <?php foreach ($vendas as $venda): ?>
                            <tr>
                                <td><?php echo htmlspecialchars($venda['venda_id']); ?></td>
                                <td><?php echo formatarData($venda['data_venda']); ?></td>
                                <td><?php echo htmlspecialchars($venda['nome_cliente']); ?></td>
                                <td><?php echo htmlspecialchars($venda['nome_funcionario']); ?></td>
                                <td><?php echo formatarMoeda($venda['desconto']); ?></td>
                                <td><strong class="text-success"><?php echo formatarMoeda($venda['valor_total']); ?></strong></td>
                                <td><?php echo ucwords(str_replace('_', ' ', $venda['forma_pagamento'])); ?></td>
                                <td>
                                    <a href="#" 
                                       class="btn btn-sm btn-info item-menu-ajax" 
                                       data-pagina="relatorios_detalhe_venda.php?venda_id=<?php echo $venda['venda_id']; ?>"
                                       title="Ver Detalhes">
                                        <i class="fas fa-eye"></i> Detalhes
                                    </a>
                                </td>
                            </tr>
                        <?php endforeach; ?>
                    <?php endif; ?>
                </tbody>
            </table>
        </div>
    </div>
</div>