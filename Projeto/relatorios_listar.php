<?php
// Arquivo: relatorios_listar.php

// ------------------------------------------------------------
// 1. INCLUSÃO DA CONEXÃO
// ------------------------------------------------------------
// Garanta que 'conexao.php' inicializa a variável $pdo (PDO)
require_once 'conexao.php'; 

// ------------------------------------------------------------
// 2. LÓGICA DE BUSCA DE VENDAS
// ------------------------------------------------------------
$vendas = [];
try {
    // Consulta SQL para listar as vendas com informações do cliente e funcionário
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
        LIMIT 100 -- Limita para não sobrecarregar
    ";
    
    $stmt_vendas = $pdo->query($sql_vendas);
    $vendas = $stmt_vendas->fetchAll(PDO::FETCH_ASSOC);

} catch (\PDOException $e) {
    $erro_bd = "Erro ao carregar o histórico de vendas: " . $e->getMessage();
    error_log($erro_bd);
}

// Formatação para exibição
function formatarMoeda($valor) {
    return 'R$ ' . number_format($valor, 2, ',', '.');
}

function formatarData($data) {
    // Converte de 'AAAA-MM-DD HH:MM:SS' para 'DD/MM/AAAA HH:MM'
    return (new DateTime($data))->format('d/m/Y H:i');
}
?>

<!DOCTYPE html>
<html lang="pt-br">
<head>
    <meta charset="UTF-8">
    <title>Histórico de Vendas</title>
    <link href="caminho/para/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.4/css/all.min.css">
    <style>
        /* Estilos básicos para a tabela */
        .table-vendas { font-size: 0.9rem; }
        .table-vendas th, .table-vendas td { vertical-align: middle; }
    </style>
</head>
<body>

<div class="container mt-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <h2><i class="fas fa-history me-2"></i> Histórico de Vendas</h2>
    </div>

    <?php if (isset($erro_bd)): ?>
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
                                    <td><?php echo $venda['venda_id']; ?></td>
                                    <td><?php echo formatarData($venda['data_venda']); ?></td>
                                    <td><?php echo htmlspecialchars($venda['nome_cliente']); ?></td>
                                    <td><?php echo htmlspecialchars($venda['nome_funcionario']); ?></td>
                                    <td><?php echo formatarMoeda($venda['desconto']); ?></td>
                                    <td><strong><?php echo formatarMoeda($venda['valor_total']); ?></strong></td>
                                    <td><?php echo ucwords(str_replace('_', ' ', $venda['forma_pagamento'])); ?></td>
                                    <td>
                                        <a href="relatorios_detalhe_venda.php?venda_id=<?php echo $venda['venda_id']; ?>" 
                                           class="btn btn-sm btn-info" 
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
</div>

<script src="caminho/para/bootstrap.bundle.min.js"></script>
</body>
</html>