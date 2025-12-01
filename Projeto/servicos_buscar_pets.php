<?php

header('Content-Type: application/json');

require_once 'conexao.php'; 

if (empty($conexao)) {
    http_response_code(500);
    echo json_encode(['error' => 'Erro crítico: Conexão mysqli indisponível.']);
    exit;
}

$cliente_id = filter_input(INPUT_GET, 'cliente_id', FILTER_VALIDATE_INT);

if (!$cliente_id) {
    http_response_code(400);
    echo json_encode(['error' => 'ID do Cliente inválido ou ausente.']);
    exit;
}

$sql_pets = "SELECT p.id, p.nome, p.porte, r.nome AS raca_nome 
             FROM pet p
             LEFT JOIN raca r ON p.raca_id = r.id 
             WHERE p.cliente_id = ? AND p.ativo = 1 
             ORDER BY p.nome ASC";
$stmt = mysqli_prepare($conexao, $sql_pets);

if ($stmt) {
    mysqli_stmt_bind_param($stmt, "i", $cliente_id);
    
    if (mysqli_stmt_execute($stmt)) {
        $result = mysqli_stmt_get_result($stmt);
        
        $pets = mysqli_fetch_all($result, MYSQLI_ASSOC);
        
        mysqli_stmt_close($stmt);
        mysqli_close($conexao);

        echo json_encode($pets);
    } else {
        http_response_code(500);
        echo json_encode(['error' => 'Erro ao executar a consulta no banco de dados.']);
        mysqli_stmt_close($stmt);
        mysqli_close($conexao);
    }
} else {
    http_response_code(500);
    echo json_encode(['error' => 'Erro interno na preparação da consulta.']);
    mysqli_close($conexao);
}