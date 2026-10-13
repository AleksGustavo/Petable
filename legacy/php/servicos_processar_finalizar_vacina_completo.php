<?php

header('Content-Type: application/json');
require_once 'conexao.php'; 

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $agendamento_id = filter_input(INPUT_POST, 'agendamento_id', FILTER_VALIDATE_INT);
    $pet_id = filter_input(INPUT_POST, 'pet_id', FILTER_VALIDATE_INT);
    $nome_vacina = filter_input(INPUT_POST, 'nome_vacina', FILTER_SANITIZE_STRING);
    $veterinario = filter_input(INPUT_POST, 'veterinario', FILTER_SANITIZE_STRING);
    $data_proxima = filter_input(INPUT_POST, 'data_proxima', FILTER_SANITIZE_STRING);
    $funcionario_id = 1; 
    $data_aplicacao = date('Y-m-d'); 

    if (!$agendamento_id || !$pet_id || !$nome_vacina) {
        $response['message'] = 'Dados obrigatórios (ID do Agendamento, ID do Pet e Nome da Vacina) não fornecidos.';
        echo json_encode($response);
        exit();
    }
    
    $data_proxima_sql = empty($data_proxima) ? NULL : $data_proxima;

    if ($conexao) {
        mysqli_begin_transaction($conexao);

        try {
            $sql_agendamento = "UPDATE agendamento
                                SET status = 'concluido', funcionario_id = ?, updated_at = NOW() 
                                WHERE id = ?";
            $stmt_agendamento = mysqli_prepare($conexao, $sql_agendamento);
            mysqli_stmt_bind_param($stmt_agendamento, "ii", $funcionario_id, $agendamento_id);
            mysqli_stmt_execute($stmt_agendamento);
            mysqli_stmt_close($stmt_agendamento);

            $sql_vacina = "INSERT INTO carteira_vacina (pet_id, nome_vacina, data_aplicacao, data_proxima, veterinario) 
                           VALUES (?, ?, ?, ?, ?)";
            $stmt_vacina = mysqli_prepare($conexao, $sql_vacina);
            mysqli_stmt_bind_param($stmt_vacina, "issss", 
                $pet_id, 
                $nome_vacina, 
                $data_aplicacao, 
                $data_proxima_sql, 
                $veterinario
            );
            mysqli_stmt_execute($stmt_vacina);
            mysqli_stmt_close($stmt_vacina);

            mysqli_commit($conexao);

            $response['success'] = true;
            $response['message'] = 'Agendamento finalizado e vacina registrada com sucesso na carteira!';

        } catch (Exception $e) {
            mysqli_rollback($conexao);
            error_log("Erro ao finalizar vacina (Transação): " . $e->getMessage());
            $response['message'] = 'Erro no processamento da transação. Nenhuma alteração foi salva: ' . $e->getMessage();
        }
    } else {
        $response['message'] = 'Erro de conexão com o banco de dados.';
    }
} else {
    $response['message'] = 'Método de requisição inválido.';
}

echo json_encode($response);

if (isset($conexao)) {
    mysqli_close($conexao);
}
?>