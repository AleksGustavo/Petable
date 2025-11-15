<?php
// Arquivo: pdv_buscar_itens.php (Migrado para PDO)
// Inclui o arquivo de conexão. Ele deve fornecer a variável $pdo (PDO).
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
            AND (nome LIKE :termo1 OR codigo_barras LIKE :termo2)
        LIMIT 10
    ";
    
    // Prepara a consulta
    $stmt_produtos = $pdo->prepare($sql_produtos);
    
    // Vincula os parâmetros (PDO usa placeholders nomeados ou '?' - aqui usamos nomeados)
    $stmt_produtos->bindValue(':termo1', $termoBusca);
    $stmt_produtos->bindValue(':termo2', $termoBusca);
    
    // Executa a consulta
    $stmt_produtos->execute();
    
    while ($row = $stmt_produtos->fetch(PDO::FETCH_ASSOC)) {
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
            AND nome LIKE :termo
        LIMIT 5
    ";
    
    // Prepara a consulta
    $stmt_servicos = $pdo->prepare($sql_servicos);
    
    // Vincula o parâmetro
    $stmt_servicos->bindValue(':termo', $termoBusca);
    
    // Executa a consulta
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
    
    // ----------------------------------------------------
    // 3. RETORNA O JSON
    // ----------------------------------------------------
    echo json_encode($resultados);

} catch (PDOException $e) {
    // Captura qualquer erro de execução
    error_log("Erro na busca de itens PDV (PDO): " . $e->getMessage());
    echo json_encode(['error' => 'Erro interno do servidor.']);
}
?>