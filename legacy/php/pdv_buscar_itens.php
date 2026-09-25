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
    // 1. BUSCA POR PRODUTOS (Tabela `produto`)
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
            AND (nome LIKE ? OR codigo_barras LIKE ?)
        LIMIT 10
    ";
    
    $stmt_produtos = mysqli_prepare($conexao, $sql_produtos);
    
    mysqli_stmt_bind_param($stmt_produtos, 'ss', $termoBusca, $termoBusca);
    
    mysqli_stmt_execute($stmt_produtos);
    
    $resultado_produtos = mysqli_stmt_get_result($stmt_produtos);
    
    while ($row = mysqli_fetch_assoc($resultado_produtos)) {
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

    mysqli_stmt_close($stmt_produtos);
    
    // 2. BUSCA POR SERVIÇOS (Tabela `servico`)
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
            AND nome LIKE ?
        LIMIT 5
    ";
    
    $stmt_servicos = mysqli_prepare($conexao, $sql_servicos);
    
    mysqli_stmt_bind_param($stmt_servicos, 's', $termoBusca);
    
    mysqli_stmt_execute($stmt_servicos);
    
    $resultado_servicos = mysqli_stmt_get_result($stmt_servicos);
    
    while ($row = mysqli_fetch_assoc($resultado_servicos)) {
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
    
    mysqli_stmt_close($stmt_servicos);

    // 3. RETORNA O JSON
    echo json_encode($resultados);

} catch (Exception $e) {
    error_log("Erro na busca de itens PDV (MySQLi): " . $e->getMessage());
    echo json_encode(['error' => 'Erro interno do servidor.']);
}
?>