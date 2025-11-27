<?php
// Arquivo: clientes_excluir.php
// Objetivo: Recebe o ID do cliente via POST e realiza o Soft Delete (ativo = 0).

// 1. Configuração e Conexão
header('Content-Type: application/json'); // Garante que o navegador entenda a resposta JSON
require_once 'conexao.php'; 

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

// 2. Validação da Requisição e do ID
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['message'] = 'Método de requisição inválido. Esperado POST.';
    echo json_encode($response);
    exit();
}

if (empty($conexao)) {
    $response['message'] = 'Erro crítico: Conexão mysqli indisponível.';
    echo json_encode($response);
    exit();
}

// O nome da variável de POST está correto: 'id_cliente'
$id_cliente = $_POST['id_cliente'] ?? null;

if (empty($id_cliente) || !is_numeric($id_cliente)) {
    $response['message'] = 'ID do cliente inválido ou não fornecido.';
    // Fecha a conexão antes de sair
    if (isset($conexao)) mysqli_close($conexao);
    echo json_encode($response);
    exit();
}

// 3. Execução do Soft Delete (UPDATE)
// CORREÇÃO: Adicionamos 'AND ativo = 1' (opcional, mas recomendado)
$sql = "UPDATE cliente SET ativo = 0 WHERE id = ? AND ativo = 1"; 

try {
    $stmt = mysqli_prepare($conexao, $sql);
    
    if (!$stmt) {
        throw new Exception("Falha ao preparar a declaração SQL: " . mysqli_error($conexao));
    }
    
    // Liga o ID do cliente como parâmetro inteiro ('i')
    mysqli_stmt_bind_param($stmt, 'i', $id_cliente);
    
    // Executa a declaração
    $resultado = mysqli_stmt_execute($stmt);
    
    if ($resultado) {
        // Verifica se alguma linha foi realmente afetada (significa que o cliente foi inativado agora)
        if (mysqli_stmt_affected_rows($stmt) > 0) {
            $response['success'] = true;
            $response['message'] = 'Cliente ID ' . $id_cliente . ' inativado (excluído) com sucesso!';
        } else {
            // Se nenhuma linha foi afetada, pode significar que o cliente já estava ativo=0
            $response['message'] = 'Nenhum cliente ativo encontrado com o ID ' . $id_cliente . ' para inativar. Cliente pode já ter sido excluído.';
             $response['success'] = true; // Considere sucesso se o objetivo for garantir que está inativo
        }
    } else {
        throw new Exception("Falha na execução do UPDATE: " . mysqli_stmt_error($stmt));
    }
    
    mysqli_stmt_close($stmt);

} catch (Exception $e) {
    // Captura e retorna erros de execução ou preparação
    $response['message'] = 'Erro ao processar a exclusão: ' . $e->getMessage();
    error_log("ERRO DE EXCLUSÃO (clientes_excluir.php): " . $e->getMessage());
}

// 4. Fechamento e Retorno da Resposta JSON
if (isset($conexao)) {
    mysqli_close($conexao);
}
echo json_encode($response);
exit();
?>