<?php

require_once 'conexao.php'; 

header('Content-Type: application/json');

$termo = isset($_GET['q']) ? trim($_GET['q']) : '';
$response = [];

if (empty($termo) || strlen($termo) < 2) { 
    echo json_encode($response);
    exit;
}

if (!isset($conexao) || $conexao->connect_error) {
    http_response_code(500);
    echo json_encode(['error' => 'Falha crítica: Conexão com o banco de dados indisponível.']);
    exit;
}

$sql = "
    SELECT 
        id, 
        nome, 
        doenca_protecao, 
        validade_padrao_meses AS validade
    FROM vacina 
    WHERE ativo = 1 AND (nome LIKE ? OR doenca_protecao LIKE ?) 
    ORDER BY nome ASC
    LIMIT 20
";

$stmt = mysqli_prepare($conexao, $sql);

if ($stmt === false) {
    http_response_code(500);
    echo json_encode(['error' => 'Erro interno na preparação da busca SQL.']);
    exit;
}

$param_busca = "%{$termo}%"; 
mysqli_stmt_bind_param($stmt, 'ss', $param_busca, $param_busca); 

if (!mysqli_stmt_execute($stmt)) {
    http_response_code(500);
    echo json_encode(['error' => 'Erro interno ao executar a busca no banco.']);
    exit;
}

$result = mysqli_stmt_get_result($stmt);
$vacinas = [];

while ($v = mysqli_fetch_assoc($result)) {
    $vacinas[] = [
        'id' => (int)$v['id'],
        'nome' => $v['nome'],
        'validade' => (int)$v['validade'], 
        'doenca_protecao' => $v['doenca_protecao']
    ];
}

mysqli_stmt_close($stmt);

echo json_encode($vacinas);
exit;