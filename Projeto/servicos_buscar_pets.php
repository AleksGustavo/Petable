<?php
// Arquivo: servicos_buscar_pets.php
// Objetivo: Retornar a lista de pets ativos de um cliente específico via AJAX.

// 1. OBRIGATÓRIO: NENHUMA SAÍDA DEVE PRECEDER ESTA LINHA.
header('Content-Type: application/json');

// Inclui o arquivo de conexão.
require_once 'conexao.php'; 

// Verifica se a conexão está ativa (garantindo que conexao.php não falhou silenciosamente)
if (empty($conexao)) {
    http_response_code(500);
    echo json_encode(['error' => 'Erro crítico: Conexão mysqli indisponível.']);
    exit;
}

// 2. Valida a entrada do cliente_id
$cliente_id = filter_input(INPUT_GET, 'cliente_id', FILTER_VALIDATE_INT);

if (!$cliente_id) {
    // Retorna erro 400 se o parâmetro estiver faltando ou for inválido
    http_response_code(400);
    echo json_encode(['error' => 'ID do Cliente inválido ou ausente.']);
    exit;
}

// 3. Query para buscar pets do cliente (usando prepared statement mysqli)
// As colunas id, nome e porte são essenciais.
$sql_pets = "SELECT id, nome, porte FROM pet WHERE cliente_id = ? AND ativo = 1 ORDER BY nome ASC";
$stmt = mysqli_prepare($conexao, $sql_pets);

if ($stmt) {
    mysqli_stmt_bind_param($stmt, "i", $cliente_id);
    
    if (mysqli_stmt_execute($stmt)) {
        $result = mysqli_stmt_get_result($stmt);
        
        $pets = mysqli_fetch_all($result, MYSQLI_ASSOC);
        
        mysqli_stmt_close($stmt);
        mysqli_close($conexao);

        // 4. Retorna os pets em formato JSON (SUCESSO)
        echo json_encode($pets);
    } else {
        // Erro na execução da query
        http_response_code(500);
        echo json_encode(['error' => 'Erro ao executar a consulta no banco de dados.']);
        mysqli_stmt_close($stmt);
        mysqli_close($conexao);
    }
} else {
    // Erro na preparação da query
    error_log("Erro MySQL na busca de pets: " . mysqli_error($conexao));
    http_response_code(500);
    echo json_encode(['error' => 'Erro interno na preparação da consulta.']);
    mysqli_close($conexao);
}
// NOTA IMPORTANTE: Este arquivo não deve ter a tag de fechamento ?>