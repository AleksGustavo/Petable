<?php

header('Content-Type: application/json');

require_once 'conexao.php';

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['message'] = "Método de requisição inválido.";
    echo json_encode($response);
    exit();
}

$agendamento_id = filter_input(INPUT_POST, 'agendamento_id', FILTER_VALIDATE_INT);
$pet_id         = filter_input(INPUT_POST, 'pet_id', FILTER_VALIDATE_INT);

if (!$agendamento_id || !$pet_id) {
    $response['message'] = "ID do agendamento ou ID do Pet ausente/inválido.";
    echo json_encode($response);
    exit();
}

if (!isset($conexao) || !$conexao) {
    $response['message'] = "Erro crítico: Falha na conexão com o banco de dados.";
    echo json_encode($response);
    exit();
}

try {
    mysqli_begin_transaction($conexao);

    $sql_detalhes = "SELECT s.nome AS nome_vacina
                     FROM agendamento a
                     JOIN servico s ON a.servico_id = s.id
                     WHERE a.id = ?";
    $stmt_detalhes = mysqli_prepare($conexao, $sql_detalhes);
    mysqli_stmt_bind_param($stmt_detalhes, "i", $agendamento_id);
    mysqli_stmt_execute($stmt_detalhes);
    $result_detalhes = mysqli_stmt_get_result($stmt_detalhes);
    $agendamento = mysqli_fetch_assoc($result_detalhes);
    mysqli_stmt_close($stmt_detalhes);

    if (!$agendamento) {
        throw new Exception("Agendamento não encontrado no banco de dados.");
    }
    
    $nome_vacina = $agendamento['nome_vacina'];
    $data_aplicacao = date('Y-m-d H:i:s');
    
    $sql_carteira = "INSERT INTO carteira_vacina (pet_id, nome_vacina, data_aplicacao, data_proxima, created_at)
                     VALUES (?, ?, ?, NULL, NOW())";
    $stmt_carteira = mysqli_prepare($conexao, $sql_carteira);
    mysqli_stmt_bind_param($stmt_carteira, "iss", $pet_id, $nome_vacina, $data_aplicacao);
    
    if (!mysqli_stmt_execute($stmt_carteira)) {
        throw new Exception("Erro ao inserir na carteira: " . mysqli_stmt_error($stmt_carteira));
    }
    mysqli_stmt_close($stmt_carteira);
    
    $status_concluido = 'concluido';
    $sql_update = "UPDATE agendamento SET status = ?, updated_at = NOW() WHERE id = ?";
    $stmt_update = mysqli_prepare($conexao, $sql_update);
    mysqli_stmt_bind_param($stmt_update, "si", $status_concluido, $agendamento_id);
    
    if (!mysqli_stmt_execute($stmt_update)) {
        throw new Exception("Erro ao atualizar agendamento: " . mysqli_stmt_error($stmt_update));
    }
    mysqli_stmt_close($stmt_update);

    mysqli_commit($conexao);

    $response['success'] = true;
    $response['message'] = "Vacina **" . htmlspecialchars($nome_vacina) . "** aplicada e registrada com sucesso na carteira do Pet!";

} catch (Exception $e) {
    mysqli_rollback($conexao);
    $response['message'] = "Falha na transação: " . $e->getMessage();
}

mysqli_close($conexao);
echo json_encode($response);
exit();