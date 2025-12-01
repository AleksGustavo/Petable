<?php
// Arquivo: clientes_excluir.php


header('Content-Type: application/json'); 
require_once 'conexao.php'; 

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];


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


$id_cliente = $_POST['id_cliente'] ?? null;

if (empty($id_cliente) || !is_numeric($id_cliente)) {
    $response['message'] = 'ID do cliente inválido ou não fornecido.';
    
    if (isset($conexao)) mysqli_close($conexao);
    echo json_encode($response);
    exit();
}


$sql = "UPDATE cliente SET ativo = 0 WHERE id = ? AND ativo = 1"; 

try {
    $stmt = mysqli_prepare($conexao, $sql);
    
    if (!$stmt) {
        throw new Exception("Falha ao preparar a declaração SQL: " . mysqli_error($conexao));
    }
    
    
    mysqli_stmt_bind_param($stmt, 'i', $id_cliente);
    
    
    $resultado = mysqli_stmt_execute($stmt);
    
    if ($resultado) {
        
        if (mysqli_stmt_affected_rows($stmt) > 0) {
            $response['success'] = true;
            $response['message'] = 'Cliente ID ' . $id_cliente . ' inativado (excluído) com sucesso!';
        } else {
            
            $response['message'] = 'Nenhum cliente ativo encontrado com o ID ' . $id_cliente . ' para inativar. Cliente pode já ter sido excluído.';
            $response['success'] = true; 
        }
    } else {
        throw new Exception("Falha na execução do UPDATE: " . mysqli_stmt_error($stmt));
    }
    
    mysqli_stmt_close($stmt);

} catch (Exception $e) {
    
    $response['message'] = 'Erro ao processar a exclusão: ' . $e->getMessage();
    error_log("ERRO DE EXCLUSÃO (clientes_excluir.php): " . $e->getMessage());
}


if (isset($conexao)) {
    mysqli_close($conexao);
}
echo json_encode($response);
exit();
?>