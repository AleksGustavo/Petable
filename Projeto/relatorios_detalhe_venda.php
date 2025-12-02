<?php

// Inclui o arquivo de conexão PDO
// NOTE: O arquivo 'conexao.php' deve ser acessível.
require_once 'conexao.php'; 

$venda_id = (int)($_GET['venda_id'] ?? 0);
$detalhes_venda = null;
$itens_venda = [];
$erro = null;

// Funções de formatação (mantidas aqui para garantir que estejam disponíveis no contexto AJAX)
if (!function_exists('formatarMoeda')) {
    function formatarMoeda($valor) {
        return 'R$ ' . number_format((float)$valor, 2, ',', '.');
    }
}
if (!function_exists('formatarData')) {
    function formatarData($data) {
        try {
            return (new DateTime($data))->format('d/m/Y H:i');
        } catch (\Exception $e) {
            return 'Data Inválida';
        }
    }
}
// Função para obter o ícone de pagamento
if (!function_exists('getPaymentIcon')) {
    function getPaymentIcon($forma) {
        $forma = strtolower($forma);
        if (strpos($forma, 'dinheiro') !== false) return '<i class="fas fa-money-bill-wave"></i>';
        if (strpos($forma, 'pix') !== false) return '<i class="fas fa-qrcode"></i>';
        if (strpos($forma, 'cartao_credito') !== false) return '<i class="fas fa-credit-card"></i>';
        if (strpos($forma, 'cartao_debito') !== false) return '<i class="fas fa-credit-card"></i>';
        if (strpos($forma, 'transferencia') !== false) return '<i class="fas fa-exchange-alt"></i>';
        if (strpos($forma, 'cheque') !== false) return '<i class="fas fa-money-check-alt"></i>';
        return '<i class="fas fa-wallet"></i>';
    }
}


if ($venda_id > 0) {
    try {
        // Consulta para obter os detalhes principais da venda, cliente e funcionário
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
            // Consulta para obter os itens da venda (Produtos)
            $sql_itens = "
                SELECT 
                    iv.quantidade,
                    iv.preco_unitario,
                    p.nome AS nome_item,
                    CASE 
                        WHEN iv.produto_id IS NOT NULL THEN 'Produto'
                        ELSE 'Desconhecido' 
                    END AS tipo_item
                FROM 
                    item_venda iv
                LEFT JOIN 
                    produto p ON iv.produto_id = p.id
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

?>

<div class="container mt-4 pb-5">
    
    <div class="d-flex flex-column flex-md-row justify-content-between align-items-center mb-4 gap-3">
        <h2 class="text-primary fw-bold mb-0">
            <i class="fas fa-receipt me-2"></i> Detalhes da Venda #<?php echo $venda_id; ?>
        </h2>
        
        <div class="btn-group shadow-sm">
            <a href="#" 
               class="btn btn-outline-secondary item-menu-ajax"
               data-pagina="relatorios_listar.php">
                <i class="fas fa-arrow-left me-1"></i> Voltar
            </a>
            <button onclick="window.print()" class="btn btn-outline-info">
                <i class="fas fa-print me-1"></i> Imprimir
            </button>
        </div>
    </div>
    
    <hr class="mt-1 mb-4">

    <?php if ($erro): ?>
        <div class="alert alert-danger" role="alert">
            <i class="fas fa-exclamation-triangle me-2"></i> <?php echo $erro; ?>
        </div>
    <?php elseif ($detalhes_venda): ?>
        
        <div class="card mb-4 shadow-sm" style="border-left: 5px solid var(--bs-primary); background-color: #f8f9fa;">
            <div class="card-header text-white p-3" style="background-color: var(--bs-primary) !important; font-weight: bold;">
                <i class="fas fa-info-circle me-1"></i> Resumo da Transação
            </div>
            <div class="card-body p-4">
                <div class="row g-3">
                    
                    <div class="col-lg-4 col-md-6 border-md-end">
                        <p class="mb-1 text-muted text-uppercase" style="font-size: 0.75rem;">Cliente</p>
                        <h5 class="fw-bold text-dark mb-3"><i class="fas fa-user me-2 text-primary"></i> <?php echo htmlspecialchars($detalhes_venda['nome_cliente']); ?></h5>
                        
                        <p class="mb-1 text-muted text-uppercase" style="font-size: 0.75rem;">Vendedor(a)</p>
                        <strong class="text-secondary"><i class="fas fa-user-tag me-2"></i> <?php echo htmlspecialchars($detalhes_venda['nome_funcionario']); ?></strong>
                    </div>

                    <div class="col-lg-4 col-md-6 border-lg-end">
                        <p class="mb-1 text-muted text-uppercase" style="font-size: 0.75rem;">Data e Hora</p>
                        <strong class="d-block mb-3"><i class="fas fa-calendar-alt me-2 text-info"></i> <?php echo formatarData($detalhes_venda['data_venda']); ?></strong>
                        
                        <p class="mb-1 text-muted text-uppercase" style="font-size: 0.75rem;">Pagamento</p>
                        <strong class="text-success"><?php echo getPaymentIcon($detalhes_venda['forma_pagamento']); ?> <?php echo ucwords(str_replace('_', ' ', $detalhes_venda['forma_pagamento'])); ?></strong>
                    </div>

                    <div class="col-lg-4 col-md-12 text-lg-end">
                        <p class="mb-1 text-muted text-uppercase" style="font-size: 0.75rem;">Valor Bruto</p>
                        <h5 class="text-secondary"><?php echo formatarMoeda($detalhes_venda['valor_total'] + $detalhes_venda['desconto']); ?></h5>

                        <p class="mb-1 text-muted text-uppercase" style="font-size: 0.75rem;">Desconto Aplicado</p>
                        <h5 class="text-danger mb-3">- <?php echo formatarMoeda($detalhes_venda['desconto']); ?></h5>

                        <div class="total-box mt-3" style="background-color: var(--bs-success); color: white; padding: 10px 15px; border-radius: 5px; font-size: 1.25rem; font-weight: bold; text-align: right; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
                            Total Líquido: <?php echo formatarMoeda($detalhes_venda['valor_total']); ?>
                        </div>
                    </div>
                </div>
                
                <?php if (!empty($detalhes_venda['observacoes'])): ?>
                    <hr class="my-4">
                    <p class="mb-0"><strong><i class="fas fa-comment-dots me-1 text-primary"></i> Observações:</strong></p>
                    <p class="alert alert-light p-2 mb-0"><?php echo nl2br(htmlspecialchars($detalhes_venda['observacoes'])); ?></p>
                <?php endif; ?>
            </div>
        </div>
        <div class="card shadow-sm">
            <div class="card-header text-white p-3" style="background-color: var(--bs-secondary) !important; font-weight: bold;">
                <i class="fas fa-boxes me-1"></i> Itens da Venda (<?php echo count($itens_venda); ?>)
            </div>
            <div class="card-body p-0">
                <div class="table-responsive">
                    <table class="table table-striped table-hover table-itens mb-0" style="font-size: 0.9rem;">
                        <thead>
                            <tr>
                                <th style="width: 5%;">#</th>
                                <th style="width: 45%;">Item</th>
                                <th style="width: 15%;">Tipo</th>
                                <th class="text-center" style="width: 10%;">Qtd</th>
                                <th class="text-end" style="width: 15%;">Preço Unit.</th>
                                <th class="text-end" style="width: 10%;">Subtotal</th>
                            </tr>
                        </thead>
                        <tbody>
                            <?php $contador = 1; ?>
                            <?php if (empty($itens_venda)): ?>
                                <tr>
                                    <td colspan="6" class="text-center text-muted p-4">Nenhum item encontrado para esta venda.</td>
                                </tr>
                            <?php else: ?>
                                <?php foreach ($itens_venda as $item): 
                                    $subtotal = $item['quantidade'] * $item['preco_unitario'];
                                    $tipo_classe = ($item['tipo_item'] == 'Produto') ? 'bg-info' : 'bg-danger text-white';
                                    $tipo_icone = ($item['tipo_item'] == 'Produto') ? '<i class="fas fa-box"></i>' : '<i class="fas fa-question-circle"></i>';
                                ?>
                                    <tr>
                                        <td><?php echo $contador++; ?></td>
                                        <td><?php echo htmlspecialchars($item['nome_item']); ?></td>
                                        <td><span class="badge <?php echo $tipo_classe; ?>" style="font-size: 0.75rem; padding: 0.4em 0.6em; border-radius: 0.25rem;"><?php echo $tipo_icone; ?> <?php echo $item['tipo_item']; ?></span></td>
                                        <td class="text-center"><?php echo $item['quantidade']; ?></td>
                                        <td class="text-end"><?php echo formatarMoeda($item['preco_unitario']); ?></td>
                                        <td class="text-end fw-bold text-dark"><?php echo formatarMoeda($subtotal); ?></td>
                                    </tr>
                                <?php endforeach; ?>
                            <?php endif; ?>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <?php endif; ?>

</div>