<?php
// Arquivo: servicos_buscar_vacinas.php
// Responsável por buscar vacinas ativas no catálogo e retornar em formato JSON.

require_once 'conexao.php'; // Verifique se este caminho está 100% correto.

header('Content-Type: application/json');

// --- 1. Validar e Sanear o Termo de Busca ---
$termo = isset($_GET['q']) ? trim($_GET['q']) : '';
$response = [];

if (empty($termo) || strlen($termo) < 2) { // Não busca se o termo for vazio ou muito curto
    echo json_encode($response);
    exit;
}

// Verifica se a conexão está disponível
if (!isset($conexao) || $conexao->connect_error) {
    http_response_code(500);
    echo json_encode(['error' => 'Falha crítica: Conexão com o banco de dados indisponível.']);
    exit;
}

// --- 2. Prepara a consulta usando Prepared Statements para segurança ---
// CRÍTICO: Adicionado o alias 'AS validade' e busca na 'doenca_protecao' também.
$sql = "
    SELECT 
        id, 
        nome, 
        doenca_protecao, 
        validade_padrao_meses AS validade /* Renomeia a coluna para 'validade' */
    FROM vacina 
    /* Busca no nome OU na doença de proteção (muito importante para achar termos como 'raiva') */
    WHERE ativo = 1 AND (nome LIKE ? OR doenca_protecao LIKE ?) 
    ORDER BY nome ASC
    LIMIT 20
";

$stmt = mysqli_prepare($conexao, $sql);

if ($stmt === false) {
    error_log("Erro ao preparar consulta de vacinas: " . mysqli_error($conexao));
    http_response_code(500);
    echo json_encode(['error' => 'Erro interno na preparação da busca SQL.']);
    exit;
}

// --- 3. Define e Vincula os parâmetros de busca ('ss' = duas strings) ---
// CRÍTICO: Agora precisamos de dois parâmetros, um para cada '?' no SQL.
$param_busca = "%{$termo}%"; 
mysqli_stmt_bind_param($stmt, 'ss', $param_busca, $param_busca); 

// --- 4. Executa a query ---
if (!mysqli_stmt_execute($stmt)) {
    error_log("Erro ao executar busca de vacinas: " . mysqli_stmt_error($stmt));
    http_response_code(500);
    echo json_encode(['error' => 'Erro interno ao executar a busca no banco.']);
    exit;
}

// --- 5. Obtém os resultados e formata para o JavaScript ---
$result = mysqli_stmt_get_result($stmt);
$vacinas = [];

while ($v = mysqli_fetch_assoc($result)) {
    // O array já tem a chave 'validade' devido ao alias 'AS validade' no SQL
    $vacinas[] = [
        'id' => (int)$v['id'],
        'nome' => $v['nome'],
        'validade' => (int)$v['validade'], /* Acessando via alias */
        'doenca_protecao' => $v['doenca_protecao']
    ];
}

// --- 6. Limpa e retorna o JSON ---
mysqli_stmt_close($stmt);

echo json_encode($vacinas);
exit;
?>