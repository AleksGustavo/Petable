<?php
header('Content-Type: application/json');
require_once 'conexao.php';

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $pet_id = filter_input(INPUT_POST, 'pet_id', FILTER_VALIDATE_INT);
    $nome_vacina = filter_input(INPUT_POST, 'nome_vacina', FILTER_SANITIZE_SPECIAL_CHARS);
    $data_aplicacao = filter_input(INPUT_POST, 'data_aplicacao', FILTER_SANITIZE_STRING);
    $data_proxima = filter_input(INPUT_POST, 'data_proxima', FILTER_SANITIZE_STRING);
    $veterinario = filter_input(INPUT_POST, 'veterinario', FILTER_SANITIZE_SPECIAL_CHARS);
    $observacoes = filter_input(INPUT_POST, 'observacoes', FILTER_SANITIZE_SPECIAL_CHARS);

    if (!$pet_id || !$nome_vacina || !$data_aplicacao) {
        $response['message'] = 'Dados obrigatórios (Pet ID, Nome da Vacina e Data de Aplicação) não fornecidos.';
        echo json_encode($response);
        exit();
    }
    
    $data_proxima_sql = empty($data_proxima) ? NULL : $data_proxima;
    $veterinario_sql = empty($veterinario) ? NULL : $veterinario;
    $observacoes_sql = empty($observacoes) ? NULL : $observacoes;

    if (isset($conexao)) {
        try {
            $sql = "INSERT INTO carteira_vacina (pet_id, nome_vacina, data_aplicacao, data_proxima, veterinario, observacoes) 
                    VALUES (?, ?, ?, ?, ?, ?)";
            
            $stmt = mysqli_prepare($conexao, $sql);
            
            mysqli_stmt_bind_param($stmt, "isssss", 
                $pet_id, 
                $nome_vacina, 
                $data_aplicacao, 
                $data_proxima_sql, 
                $veterinario_sql,    
                $observacoes_sql     
            );

            if (mysqli_stmt_execute($stmt)) {
                $response['success'] = true;
                $response['message'] = 'Vacina registrada com sucesso na carteira do Pet: **' . htmlspecialchars($nome_vacina) . '**!';
            } else {
                $response['message'] = 'Erro ao inserir no banco: ' . mysqli_error($conexao);
            }

            mysqli_stmt_close($stmt);

        } catch (Exception $e) {
            error_log("Erro no pets_processar_vacina_manual: " . $e->getMessage());
            $response['message'] = 'Erro de Servidor/Banco de Dados. Tente novamente.';
        }
    } else {
        $response['message'] = 'Erro de conexão com o banco de dados.';
    }

} else {
    $response['message'] = 'Método de requisição inválido. (Esperado: POST)';
}

echo json_encode($response);
exit();
?>