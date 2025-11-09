<?php
// Arquivo: servicos_processar_agendamento.php

session_start();
require_once 'conexao.php'; 

// CRÍTICO PARA AJAX: Define o cabeçalho para retornar JSON e inicializa a resposta
header('Content-Type: application/json');
$response = ['success' => false, 'message' => 'Erro desconhecido ao agendar.'];


// --- FUNÇÃO DE ERRO CENTRALIZADA PARA JSON ---
function sendJsonError($message, $conexao, $log = true) {
    global $response;
    $response['message'] = $message;
    if ($log) {
        // Envia o erro para o log do servidor.
        $sql_error = (isset($conexao) && $conexao) ? " | SQL Error: " . mysqli_error($conexao) : "";
        error_log("AGENDAMENTO ERROR: " . $message . $sql_error);
    }
    echo json_encode($response);
    exit;
}
// ---------------------------------------------


// ---------------------------------------------------------
// 1. Coleta e Validação Básica dos Dados
// ---------------------------------------------------------

if (empty($conexao)) {
    sendJsonError("Erro: Conexão com o banco de dados falhou.", null, false);
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendJsonError("Erro: Acesso inválido. Use o método POST.", $conexao);
}

// Coleta e sanitiza dados
// NOTA: O cliente_id não é usado no INSERT da tabela `agendamento`, mas é coletado para validação.
$cliente_id = filter_input(INPUT_POST, 'cliente_id', FILTER_VALIDATE_INT);
$pet_id = filter_input(INPUT_POST, 'pet_id', FILTER_VALIDATE_INT);
$data_agendamento = filter_input(INPUT_POST, 'data_agendamento');
$hora_agendamento = filter_input(INPUT_POST, 'hora_agendamento');
$observacoes = filter_input(INPUT_POST, 'observacoes', FILTER_SANITIZE_STRING);

// Coleta o JSON de serviços agendados
$servicos_agendados_json = filter_input(INPUT_POST, 'servicos_agendados_json');
$servicos_agendados = $servicos_agendados_json ? json_decode($servicos_agendados_json, true) : [];

// Determina o ID do serviço principal (o primeiro da lista) para a tabela `agendamento`
$servico_id_principal = !empty($servicos_agendados) ? (int)array_values($servicos_agendados)[0] : 0;


// Determina o funcionário
$funcionario_id = 0;
// Prioriza o funcionário do campo específico ou usa 0 (NULL no banco) se não houver
if (isset($_POST['funcionario_id_banhotosa']) && !empty($_POST['funcionario_id_banhotosa'])) {
    $funcionario_id = filter_input(INPUT_POST, 'funcionario_id_banhotosa', FILTER_VALIDATE_INT);
} elseif (isset($_POST['funcionario_id_vacina']) && !empty($_POST['funcionario_id_vacina'])) {
    $funcionario_id = filter_input(INPUT_POST, 'funcionario_id_vacina', FILTER_VALIDATE_INT);
} elseif (isset($_POST['funcionario_id_consulta']) && !empty($_POST['funcionario_id_consulta'])) {
    $funcionario_id = filter_input(INPUT_POST, 'funcionario_id_consulta', FILTER_VALIDATE_INT);
}

// Validação crítica
if (!$cliente_id || !$pet_id || !$data_agendamento || empty($servicos_agendados) || $servico_id_principal === 0) {
    sendJsonError("Erro de validação: Cliente, Pet, Data e Serviço principal são obrigatórios.", $conexao);
}

// Combina data e hora para o formato DATETIME
$datahora_agendamento = $data_agendamento . ' ' . ($hora_agendamento ? $hora_agendamento : '00:00') . ':00';

$status_padrao = 'agendado';
$agendamento_id = null;


// ---------------------------------------------------------
// INÍCIO DA TRANSAÇÃO
// ---------------------------------------------------------
mysqli_begin_transaction($conexao);

try {
    // ---------------------------------------------------------
    // 2. Inserção na Tabela 'agendamento' (ÚNICA INSERÇÃO)
    // ---------------------------------------------------------
    // Colunas: pet_id, funcionario_id, data_agendamento, status, observacoes, servico_id
    $sql_agendamento = "INSERT INTO agendamento (pet_id, funcionario_id, data_agendamento, status, observacoes, servico_id) 
                        VALUES (?, ?, ?, ?, ?, ?)"; // 6 placeholders
    
    $stmt_agendamento = mysqli_prepare($conexao, $sql_agendamento);

    if (!$stmt_agendamento) {
        throw new Exception("Erro na preparação da inserção principal: " . mysqli_error($conexao));
    }

    // String de tipos: iissis (6 caracteres, sem espaços)
    // Tipos: (i:pet_id, i:funcionario_id, s:data_hora, s:status, s:observacoes, i:servico_id)
    mysqli_stmt_bind_param($stmt_agendamento, "iissis", 
        $pet_id, 
        $funcionario_id, 
        $datahora_agendamento, 
        $status_padrao, 
        $observacoes, 
        $servico_id_principal
    );

    if (!mysqli_stmt_execute($stmt_agendamento)) {
        throw new Exception("Erro ao executar inserção principal: " . mysqli_stmt_error($stmt_agendamento));
    }

    $agendamento_id = mysqli_insert_id($conexao);
    mysqli_stmt_close($stmt_agendamento);
    
    // ---------------------------------------------------------
    // FIM DA TRANSAÇÃO: COMMIT e RESPOSTA JSON DE SUCESSO
    // ---------------------------------------------------------
    mysqli_commit($conexao);
    
    $response['success'] = true; 
    $response['message'] = "✅ Agendamento (ID: {$agendamento_id}) criado com sucesso!";
    
} catch (Exception $e) {
    // ---------------------------------------------------------
    // TRATAMENTO DE ERROS: ROLLBACK e RESPOSTA JSON DE ERRO
    // ---------------------------------------------------------
    mysqli_rollback($conexao);
    
    $response['message'] = "Falha ao processar o agendamento no servidor. Detalhe: " . $e->getMessage();
    
} finally {
    if (isset($conexao) && $conexao) {
        mysqli_close($conexao);
    }
    // RESPOSTA FINAL (JSON)
    echo json_encode($response); 
    exit;
}
?>