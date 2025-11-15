<?php
// Arquivo: vendas_processar.php
// Processa a venda, insere dados e dá baixa no estoque

ob_start(); 
// Certifique-se de que 'conexao.php' está correto e fornece a variável $pdo (PDO)
require_once 'conexao.php'; 

header('Content-Type: application/json');
$response = ['success' => false, 'message' => 'Erro desconhecido.'];

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['message'] = 'Método de requisição inválido.';
    echo json_encode($response);
    exit;
}

// ------------------------------------------
// 1. Receber e Decodificar Dados
// ------------------------------------------
$itens_venda_json = $_POST['itens_venda_json'] ?? '[]';
$itens_venda = json_decode($itens_venda_json, true);

if (empty($itens_venda)) {
    $response['message'] = 'Nenhum item foi adicionado à venda.';
    echo json_encode($response);
    exit;
}

// Coleta dados do formulário
$cliente_id = (int)($_POST['cliente_id'] ?? 0);
$desconto = (float)str_replace(',', '.', $_POST['desconto'] ?? 0.00);
$forma_pagamento = $_POST['forma_pagamento'] ?? 'dinheiro';
$observacoes = trim($_POST['observacoes'] ?? '');
$valor_total = (float)str_replace(',', '.', $_POST['valor_total'] ?? 0.00);
// Assumindo que você tem uma variável de sessão ou ID do usuário logado
$funcionario_id = 1; // SUBSTITUA PELA VARIÁVEL REAL DO FUNCIONÁRIO LOGADO

if ($valor_total < 0) {
    $response['message'] = 'O valor total da venda não pode ser negativo.';
    echo json_encode($response);
    exit;
}

// *****************************************************************
// 2. CORREÇÃO CRÍTICA CONTRA DEADLOCK: ORDENAÇÃO DOS PRODUTOS
// *****************************************************************
if (!empty($itens_venda)) {
    // Filtra e separa produtos e serviços
    $itens_produtos = array_filter($itens_venda, function($item) {
        return $item['tipo'] === 'produto';
    });
    $itens_servicos = array_filter($itens_venda, function($item) {
        return $item['tipo'] === 'servico';
    });
    
    // Classifica os PRODUTOS pelo ID em ordem crescente
    // Isso garante que o UPDATE de estoque (bloqueio) ocorrerá na mesma ordem
    usort($itens_produtos, function($a, $b) {
        return $a['id'] <=> $b['id']; 
    });

    // Recompõe o array, com os produtos ordenados primeiro
    $itens_venda = array_merge($itens_produtos, $itens_servicos);
}
// *****************************************************************

// ------------------------------------------
// 3. Transação e Inserção no Banco de Dados
// ------------------------------------------
try {
    // Inicia a transação (CRUCIAL para estoque)
    $pdo->beginTransaction();

    // 3a. Insere a Venda na tabela 'venda'
    $sql_venda = "INSERT INTO venda 
                    (cliente_id, funcionario_id, data_venda, valor_total, desconto, forma_pagamento, observacoes) 
                  VALUES 
                    (?, ?, NOW(), ?, ?, ?, ?)";
    
    $stmt_venda = $pdo->prepare($sql_venda);
    $stmt_venda->execute([
        $cliente_id > 0 ? $cliente_id : null, // Usa NULL se for venda anônima
        $funcionario_id,
        $valor_total,
        $desconto,
        $forma_pagamento,
        $observacoes
    ]);
    
    $venda_id = $pdo->lastInsertId();

    // 3b. Insere os Itens na tabela 'item_venda' e Dá Baixa no Estoque
    $sql_item_venda = "INSERT INTO item_venda 
                        (venda_id, produto_id, servico_id, quantidade, preco_unitario) 
                       VALUES 
                        (?, ?, ?, ?, ?)";
    
    $sql_baixa_estoque = "UPDATE produto SET quantidade_estoque = quantidade_estoque - ? WHERE id = ?";
    
    foreach ($itens_venda as $item) {
        $quantidade = (int)$item['quantidade'];
        $preco_unitario = (float)$item['preco'];
        $item_id = (int)$item['id'];

        // Define o ID do produto ou do serviço (um será NULL)
        $produto_id = ($item['tipo'] === 'produto') ? $item_id : null;
        $servico_id = ($item['tipo'] === 'servico') ? $item_id : null;

        // Insere o item na tabela 'item_venda'
        $stmt_item = $pdo->prepare($sql_item_venda);
        $stmt_item->execute([
            $venda_id,
            $produto_id, // Insere NULL se for serviço
            $servico_id, // Insere NULL se for produto
            $quantidade,
            $preco_unitario
        ]);

        // Se for um PRODUTO, dá baixa no estoque
        if ($item['tipo'] === 'produto') {
            // Este update agora acontece em uma ordem consistente
            $stmt_estoque = $pdo->prepare($sql_baixa_estoque);
            $stmt_estoque->execute([$quantidade, $item_id]);
        }
    }

    // Se tudo correu bem, confirma as mudanças
    $pdo->commit();

    $response['success'] = true;
    $response['message'] = "Venda #{$venda_id} finalizada com sucesso! Itens vendidos: " . count($itens_venda);
    
} catch (\PDOException $e) {
    // Se ocorrer qualquer erro, desfaz todas as operações
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    // Mensagem mais informativa, mantendo o aviso de Deadlock
    $deadlock_message = (strpos($e->getMessage(), 'Deadlock found') !== false) 
                        ? 'Deadlock detectado. Tente finalizar a venda novamente.' 
                        : 'Erro fatal ao processar a venda. Verifique a conexão com o BD.';

    $response['message'] = $deadlock_message . ' Detalhe técnico: ' . $e->getMessage();
    error_log("Erro no processamento da venda: " . $e->getMessage());

} catch (\Exception $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    $response['message'] = 'Erro inesperado: ' . $e->getMessage();
    error_log("Erro inesperado no PDV: " . $e->getMessage());
}

ob_clean();
echo json_encode($response);
exit;
?>