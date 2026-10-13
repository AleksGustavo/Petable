<?php
// Arquivo: servicos_processar_agendamento.php - COMPLETO E FINAL

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
$cliente_id = filter_input(INPUT_POST, 'cliente_id', FILTER_VALIDATE_INT);
$pet_id = filter_input(INPUT_POST, 'pet_id', FILTER_VALIDATE_INT);
$data_agendamento = filter_input(INPUT_POST, 'data_agendamento');
$hora_agendamento = filter_input(INPUT_POST, 'hora_agendamento');
$observacoes = filter_input(INPUT_POST, 'observacoes', FILTER_SANITIZE_STRING);

// Coleta o Total Estimado
$total_estimado = filter_input(INPUT_POST, 'total_estimado', FILTER_VALIDATE_FLOAT) ?: 0.00;

// Coleta o JSON de serviços agendados
$servicos_agendados_json = filter_input(INPUT_POST, 'servicos_agendados_json');
$servicos_agendados = $servicos_agendados_json ? json_decode($servicos_agendados_json, true) : [];

// Determina o ID do serviço principal (o primeiro ID de serviço válido)
$servico_id_principal = 0;
foreach ($servicos_agendados as $servico_item) {
    if (is_int($servico_item)) {
        $servico_id_principal = $servico_item;
        break;
    } elseif (is_array($servico_item) && isset($servico_item['servico_id'])) {
        $servico_id_principal = $servico_item['servico_id'];
        break;
    }
}


// Determina o funcionário
$funcionario_id = 0;
if (isset($_POST['funcionario_id_banhotosa']) && !empty($_POST['funcionario_id_banhotosa'])) {
    $funcionario_id = filter_input(INPUT_POST, 'funcionario_id_banhotosa', FILTER_VALIDATE_INT);
} elseif (isset($_POST['funcionario_id_vacina']) && !empty($_POST['funcionario_id_vacina'])) {
    $funcionario_id = filter_input(INPUT_POST, 'funcionario_id_vacina', FILTER_VALIDATE_INT);
} elseif (isset($_POST['funcionario_id_consulta']) && !empty($_POST['funcionario_id_consulta'])) {
    $funcionario_id = filter_input(INPUT_POST, 'funcionario_id_consulta', FILTER_VALIDATE_INT);
}

// Validação crítica
if (!$cliente_id || !$pet_id || !$data_agendamento || $servico_id_principal === 0) {
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
    // 2. Inserção na Tabela 'agendamento' (Incluindo total_estimado)
    // ---------------------------------------------------------
    // Colunas: pet_id, funcionario_id, data_agendamento, status, observacoes, servico_id, total_estimado
    $sql_agendamento = "INSERT INTO agendamento (pet_id, funcionario_id, data_agendamento, status, observacoes, servico_id, total_estimado) 
                         VALUES (?, ?, ?, ?, ?, ?, ?)"; // 7 placeholders
    
    $stmt_agendamento = mysqli_prepare($conexao, $sql_agendamento);

    if (!$stmt_agendamento) {
        throw new Exception("Erro na preparação da inserção principal: " . mysqli_error($conexao));
    }

    // String de tipos: iissisd (7 caracteres: i:int, s:string, d:double/decimal)
    mysqli_stmt_bind_param($stmt_agendamento, "iissisd", 
        $pet_id, 
        $funcionario_id, 
        $datahora_agendamento, 
        $status_padrao, 
        $observacoes, 
        $servico_id_principal,
        $total_estimado
    );

    if (!mysqli_stmt_execute($stmt_agendamento)) {
        throw new Exception("Erro ao executar inserção principal: " . mysqli_stmt_error($stmt_agendamento));
    }

    $agendamento_id = mysqli_insert_id($conexao);
    mysqli_stmt_close($stmt_agendamento);
    
    
    // ---------------------------------------------------------
    // 3. Inserção na Tabela 'agendamento_servico' (Detalhes de Serviços)
    // ---------------------------------------------------------
    
    if (!empty($servicos_agendados)) {
        // Esta query só funcionará se a tabela agendamento_servico existir (Passo 1.1)
        $sql_detalhe = "INSERT INTO agendamento_servico (agendamento_id, servico_id, vacina_catalogo_id) VALUES (?, ?, ?)";
        $stmt_detalhe = mysqli_prepare($conexao, $sql_detalhe);

        if (!$stmt_detalhe) {
            throw new Exception("Erro na preparação da inserção de detalhes. Verifique se a tabela 'agendamento_servico' existe: " . mysqli_error($conexao));
        }

        foreach ($servicos_agendados as $servico_item) {
            $servico_id = null;
            $vacina_catalogo_id = null;
            
            if (is_int($servico_item)) {
                $servico_id = $servico_item;
            } elseif (is_array($servico_item) && isset($servico_item['servico_id'])) {
                $servico_id = (int)$servico_item['servico_id'];
                $vacina_catalogo_id = isset($servico_item['vacina_catalogo_id']) ? (int)$servico_item['vacina_catalogo_id'] : null;
            }
            
            if ($servico_id !== null) {
                // O bind_param para `vacina_catalogo_id` lida com NULL
                $vacina_catalogo_id_bind = $vacina_catalogo_id ?: null; 

                // Tipos: i:agendamento_id, i:servico_id, i:vacina_catalogo_id
                mysqli_stmt_bind_param($stmt_detalhe, "iii", 
                    $agendamento_id, 
                    $servico_id, 
                    $vacina_catalogo_id_bind
                );

                if (!mysqli_stmt_execute($stmt_detalhe)) {
                    throw new Exception("Erro ao executar inserção de detalhe (Serviço ID: {$servico_id}): " . mysqli_stmt_error($stmt_detalhe));
                }
            }
        }
        mysqli_stmt_close($stmt_detalhe);
    }
    
    // ---------------------------------------------------------
    // FIM DA TRANSAÇÃO: COMMIT e RESPOSTA JSON DE SUCESSO
    // ---------------------------------------------------------
    mysqli_commit($conexao);
    
    $response['success'] = true; 
    $response['total_estimado'] = number_format($total_estimado, 2, ',', '.');
    $response['message'] = "✅ Agendamento (ID: {$agendamento_id}) criado com sucesso! Total Estimado: R$ {$response['total_estimado']}.";
    
} catch (Exception $e) {
    // ---------------------------------------------------------
    // TRATAMENTO DE ERROS: ROLLBACK e RESPOSTA JSON DE ERRO
    // ---------------------------------------------------------
    mysqli_rollback($conexao);
    
    // Loga o erro exato para depuração
    error_log("ERRO FATAL NO AGENDAMENTO: " . $e->getMessage() . (isset($conexao) ? " | SQL Error: " . mysqli_error($conexao) : ""));
    
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