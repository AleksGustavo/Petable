<?php
// Inclui o arquivo de conexão. Ele deve fornecer a variável $conexao (MySQLi).
require_once 'conexao.php'; 

header('Content-Type: application/json');

// Verifica se o termo de busca foi enviado
if (!isset($_GET['term']) || empty(trim($_GET['term']))) {
    echo json_encode([]);
    exit;
}

// O termo de busca será usado para LIKE %termo%
$termoBusca = '%' . trim($_GET['term']) . '%';
$resultados = [];

// Usamos prepared statements para segurança

try {
    // ----------------------------------------------------
    // 1. BUSCA POR PRODUTOS (Tabela `produto`)
    // ----------------------------------------------------
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
    
    // Prepara a consulta
    $stmt_produtos = mysqli_prepare($conexao, $sql_produtos);
    
    // Vincula os parâmetros (ss = string, string)
    mysqli_stmt_bind_param($stmt_produtos, 'ss', $termoBusca, $termoBusca);
    
    // Executa a consulta
    mysqli_stmt_execute($stmt_produtos);
    
    // Obtém o resultado
    $resultado_produtos = mysqli_stmt_get_result($stmt_produtos);
    
    while ($row = mysqli_fetch_assoc($resultado_produtos)) {
        $preco_float = (float)$row['preco'];
        // Formato esperado pelo jQuery UI Autocomplete
        $resultados[] = [
            'id' => $row['id'],
            'nome' => $row['nome'],
            'preco' => $preco_float,
            'tipo' => $row['tipo'],
            // O label é o que será exibido na lista de sugestões
            'label' => $row['nome'] . " (R$ " . number_format($preco_float, 2, ',', '.') . ")",
            'codigo_barras' => $row['codigo_barras']
        ];
    }

    // Libera o statement
    mysqli_stmt_close($stmt_produtos);
    
    // ----------------------------------------------------
    // 2. BUSCA POR SERVIÇOS (Tabela `servico`)
    // ----------------------------------------------------
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
    
    // Prepara a consulta
    $stmt_servicos = mysqli_prepare($conexao, $sql_servicos);
    
    // Vincula o parâmetro (s = string)
    mysqli_stmt_bind_param($stmt_servicos, 's', $termoBusca);
    
    // Executa a consulta
    mysqli_stmt_execute($stmt_servicos);
    
    // Obtém o resultado
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
    
    // Libera o statement
    mysqli_stmt_close($stmt_servicos);

    // ----------------------------------------------------
    // 3. RETORNA O JSON
    // ----------------------------------------------------
    echo json_encode($resultados);

} catch (Exception $e) {
    // Captura qualquer erro de execução
    error_log("Erro na busca de itens PDV (MySQLi): " . $e->getMessage());
    echo json_encode(['error' => 'Erro interno do servidor.']);
}
?>