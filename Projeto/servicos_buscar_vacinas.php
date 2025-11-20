<?php
// servicos_buscar_vacinas.php - VERSÃO SEGURA E CORRIGIDA
require_once 'conexao.php';

// Define o cabeçalho para garantir que a resposta seja JSON
header('Content-Type: application/json');

// 1. Validar e Sanear o Termo de Busca
$termo = isset($_GET['q']) ? trim($_GET['q']) : '';

if (empty($termo)) {
    echo json_encode([]);
    exit;
}

// 2. Prepara a consulta com placeholders (?)
// O campo 'validade_padrao_meses' é o que deve ser retornado como 'validade_meses'
$sql = "
    SELECT id, nome, doenca_protecao, validade_padrao_meses 
    FROM vacina 
    WHERE ativo = 1 AND nome LIKE ? 
    ORDER BY nome ASC
    LIMIT 20
";

$stmt = mysqli_prepare($conexao, $sql);

if ($stmt === false) {
    // Trata erro na preparação da query
    error_log("Erro ao preparar consulta de vacinas: " . mysqli_error($conexao));
    http_response_code(500);
    echo json_encode(['error' => 'Erro interno na preparação da busca.']);
    exit;
}

// 3. Define o parâmetro de busca (com os curingas '%')
// i.e., busca o termo em qualquer lugar do nome
$param_busca = "%{$termo}%"; 

// 4. Vincula o parâmetro (s = string)
mysqli_stmt_bind_param($stmt, 's', $param_busca);

// 5. Executa a query
if (!mysqli_stmt_execute($stmt)) {
    error_log("Erro ao executar busca de vacinas: " . mysqli_stmt_error($stmt));
    http_response_code(500);
    echo json_encode(['error' => 'Erro interno ao executar a busca.']);
    exit;
}

// 6. Obtém os resultados
$result = mysqli_stmt_get_result($stmt);
$vacinas = [];

while ($v = mysqli_fetch_assoc($result)) {
    // Renomeia 'validade_padrao_meses' para 'validade_meses'
    $vacinas[] = [
        'id' => $v['id'],
        'nome' => $v['nome'],
        'validade_meses' => (int)$v['validade_padrao_meses'],
        'doenca_protecao' => $v['doenca_protecao']
    ];
}

// 7. Limpa e retorna o JSON
mysqli_stmt_close($stmt);
mysqli_close($conexao);

echo json_encode($vacinas);
?>