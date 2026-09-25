<?php

require_once 'conexao.php'; 

$venda_id = (int)($_GET['venda_id'] ?? 0);
$detalhes_venda = null;
$itens_venda = [];
$erro = null;

if ($venda_id > 0) {
    try {
        $sql_detalhe = "
            SELECT 
                v.*,
                COALESCE(c.nome, 'Cliente Anônimo') AS nome_cliente,
                f.nome AS nome_funcionario
            FROM 
                venda v
            LEFT JOIN 
                cliente c ON v.cliente_id = c.id
            LEFT JOIN 
                funcionario f ON v.funcionario_id = f.id
            WHERE 
                v.id = ?
        ";
        $stmt_detalhe = $pdo->prepare($sql_detalhe);
        $stmt_detalhe->execute([$venda_id]);
        $detalhes_venda = $stmt_detalhe->fetch(PDO::FETCH_ASSOC);

        if ($detalhes_venda) {
            $sql_itens = "
                SELECT 
                    iv.quantidade,
                    iv.preco_unitario,
                    COALESCE(p.nome, s.nome) AS nome_item,
                    CASE 
                        WHEN iv.produto_id IS NOT NULL THEN 'Produto'
                        WHEN iv.servico_id IS NOT NULL THEN 'Serviço'
                        ELSE 'Desconhecido' 
                    END AS tipo_item
                FROM 
                    item_venda iv
                LEFT JOIN 
                    produto p ON iv.produto_id = p.id
                LEFT JOIN 
                    servico s ON iv.servico_id = s.id
                WHERE 
                    iv.venda_id = ?
            ";
            $stmt_itens = $pdo->prepare($sql_itens);
            $stmt_itens->execute([$venda_id]);
            $itens_venda = $stmt_itens->fetchAll(PDO::FETCH_ASSOC);
        } else {
            $erro = "Venda #{$venda_id} não encontrada.";
        }
        
    } catch (\PDOException $e) {
        $erro = "Erro ao buscar os detalhes da venda: " . $e->getMessage();
        error_log($erro);
    }
} else {
    $erro = "ID da venda inválido.";
}

function formatarMoeda($valor) {
    return 'R$ ' . number_format($valor, 2, ',', '.');
}
function formatarData($data) {
    return (new DateTime($data))->format('d/m/Y H:i');
}
?>

<!DOCTYPE html>
<html lang="pt-br">
<head>
    <meta charset="UTF-8">
    <title>Detalhes da Venda #<?php echo $venda_id; ?></title>
    <link href="caminho/para/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.4/css/all.min.css">
    <style>
        .table-itens { font-size: 0.9rem; }
    </style>
</head>
<body>

<div class="container mt-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <h2>Detalhes da Venda #<?php echo $venda_id; ?></h2>
        <a href="relatorios_listar.php" class="btn btn-secondary"><i class="fas fa-arrow-left me-1"></i> Voltar</a>
    </div>

    <?php if ($erro): ?>
        <div class="alert alert-danger" role="alert">
            <?php echo $erro; ?>
        </div>
    <?php elseif ($detalhes_venda): ?>
        
        <div class="card mb-4 shadow-sm">
            <div class="card-header bg-primary text-white">
                <i class="fas fa-info-circle me-1"></i> Informações Principais
            </div>
            <div class="card-body">
                <div class="row">
                    <div class="col-md-4">
                        <p><strong>Cliente:</strong> <?php echo htmlspecialchars($detalhes_venda['nome_cliente']); ?></p>
                        <p><strong>Funcionário:</strong> <?php echo htmlspecialchars($detalhes_venda['nome_funcionario']); ?></p>
                    </div>
                    <div class="col-md-4">
                        <p><strong>Data/Hora:</strong> <?php echo formatarData($detalhes_venda['data_venda']); ?></p>
                        <p><strong>Forma de Pagamento:</strong> <?php echo ucwords(str_replace('_', ' ', $detalhes_venda['forma_pagamento'])); ?></p>
                    </div>
                    <div class="col-md-4 text-end">
                        <h4 class="mb-0">Total: <?php echo formatarMoeda($detalhes_venda['valor_total']); ?></h4>
                        <small class="text-muted">Desconto: <?php echo formatarMoeda($detalhes_venda['desconto']); ?></small>
                    </div>
                </div>
                <?php if (!empty($detalhes_venda['observacoes'])): ?>
                    <hr>
                    <p class="mb-0"><strong>Observações:</strong> <?php echo nl2br(htmlspecialchars($detalhes_venda['observacoes'])); ?></p>
                <?php endif; ?>
            </div>
        </div>

        <div class="card shadow-sm">
            <div class="card-header bg-secondary text-white">
                <i class="fas fa-list me-1"></i> Itens Vendidos (<?php echo count($itens_venda); ?>)
            </div>
            <div class="card-body p-0">
                <div class="table-responsive">
                    <table class="table table-striped table-hover table-itens mb-0">
                        <thead>
                            <tr>
                                <th>#</th>
                                <th>Item</th>
                                <th>Tipo</th>
                                <th>Quantidade</th>
                                <th>Preço Unitário</th>
                                <th>Subtotal</th>
                            </tr>
                        </thead>
                        <tbody>
                            <?php $contador = 1; ?>
                            <?php foreach ($itens_venda as $item): 
                                $subtotal = $item['quantidade'] * $item['preco_unitario'];
                            ?>
                                <tr>
                                    <td><?php echo $contador++; ?></td>
                                    <td><?php echo htmlspecialchars($item['nome_item']); ?></td>
                                    <td><?php echo $item['tipo_item']; ?></td>
                                    <td><?php echo $item['quantidade']; ?></td>
                                    <td><?php echo formatarMoeda($item['preco_unitario']); ?></td>
                                    <td><?php echo formatarMoeda($subtotal); ?></td>
                                </tr>
                            <?php endforeach; ?>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        
    <?php endif; ?>

</div>

<script src="caminho/para/bootstrap.bundle.min.js"></script>
</body>
</html>