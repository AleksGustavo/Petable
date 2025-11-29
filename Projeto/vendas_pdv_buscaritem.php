<?php

require_once 'conexao.php'; 

header('Content-Type: application/json');

if (!isset($_GET['term']) || empty(trim($_GET['term']))) {
    echo json_encode([]);
    exit;
}

$termoBusca = '%' . trim($_GET['term']) . '%';
$resultados = [];

try {
    $sql_produtos = "
        SELECT 
            id, 
            nome, 
            preco_venda AS preco, 
            'produto' AS tipo,
            codigo_barras
        FROM 
            produto
        WHERE 
            ativo = 1 
            AND (nome LIKE :termo1 OR codigo_barras LIKE :termo2)
        LIMIT 10
    ";
    
    $stmt_produtos = $pdo->prepare($sql_produtos);
    
    $stmt_produtos->bindValue(':termo1', $termoBusca);
    $stmt_produtos->bindValue(':termo2', $termoBusca);
    
    $stmt_produtos->execute();
    
    while ($row = $stmt_produtos->fetch(PDO::FETCH_ASSOC)) {
        $preco_float = (float)$row['preco'];
        $resultados[] = [
            'id' => $row['id'],
            'nome' => $row['nome'],
            'preco' => $preco_float,
            'tipo' => $row['tipo'],
            'label' => $row['nome'] . " (R$ " . number_format($preco_float, 2, ',', '.') . ")",
            'codigo_barras' => $row['codigo_barras']
        ];
    }
    
    $sql_servicos = "
        SELECT 
            id, 
            nome, 
            preco, 
            'servico' AS tipo
        FROM 
            servico
        WHERE 
            ativo = 1 
            AND nome LIKE :termo
        LIMIT 5
    ";
    
    $stmt_servicos = $pdo->prepare($sql_servicos);
    
    $stmt_servicos->bindValue(':termo', $termoBusca);
    
    $stmt_servicos->execute();
    
    while ($row = $stmt_servicos->fetch(PDO::FETCH_ASSOC)) {
        $preco_float = (float)$row['preco'];
        $resultados[] = [
            'id' => $row['id'],
            'nome' => $row['nome'],
            'preco' => $preco_float,
            'tipo' => $row['tipo'],
            'label' => "[SERVIÇO] " . $row['nome'] . " (R$ " . number_format($preco_float, 2, ',', '.') . ")",
            'codigo_barras' => null
        ];
    }
    
    echo json_encode($resultados);

} catch (PDOException $e) {
    error_log("Erro na busca de itens PDV (PDO): " . $e->getMessage());
    echo json_encode(['error' => 'Erro interno do servidor.']);
}
?>