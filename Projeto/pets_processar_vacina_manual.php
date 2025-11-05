<?php
// Arquivo: pets_processar_vacina_manual.php
// Processa a inserção de um registro na carteira de vacinas (uso manual/modal)

header('Content-Type: application/json');
require_once 'conexao.php'; // Sua conexão com o banco

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // 1. Coleta e validação dos dados
    $pet_id = filter_input(INPUT_POST, 'pet_id', FILTER_VALIDATE_INT);
    $nome_vacina = filter_input(INPUT_POST, 'nome_vacina', FILTER_SANITIZE_STRING);
    $data_aplicacao = filter_input(INPUT_POST, 'data_aplicacao', FILTER_SANITIZE_STRING);
    $data_proxima = filter_input(INPUT_POST, 'data_proxima', FILTER_SANITIZE_STRING);
    $veterinario = filter_input(INPUT_POST, 'veterinario', FILTER_SANITIZE_STRING);
    $observacoes = filter_input(INPUT_POST, 'observacoes', FILTER_SANITIZE_STRING);

    if (!$pet_id || !$nome_vacina || !$data_aplicacao) {
        $response['message'] = 'Dados obrigatórios (Pet ID, Nome da Vacina e Data de Aplicação) não fornecidos.';
        echo json_encode($response);
        exit();
    }
    
    // Tratamento da data próxima (pode ser NULL)
    $data_proxima_sql = empty($data_proxima) ? NULL : $data_proxima;

    if ($conexao) {
        try {
            // 2. Prepara a inserção na tabela carteira_vacinas
            $sql = "INSERT INTO carteira_vacina (pet_id, nome_vacina, data_aplicacao, data_proxima, veterinario, observacoes) 
                    VALUES (?, ?, ?, ?, ?, ?)";
            
            $stmt = mysqli_prepare($conexao, $sql);
            
            // Associa os parâmetros
            mysqli_stmt_bind_param($stmt, "isssss", 
                $pet_id, 
                $nome_vacina, 
                $data_aplicacao, 
                $data_proxima_sql, 
                $veterinario, 
                $observacoes
            );

            if (mysqli_stmt_execute($stmt)) {
                $response['success'] = true;
                $response['message'] = 'Vacina registrada com sucesso na carteira do Pet!';
            } else {
                $response['message'] = 'Erro ao inserir no banco: ' . mysqli_error($conexao);
            }

            mysqli_stmt_close($stmt);

        } catch (Exception $e) {
            $response['message'] = 'Erro de banco de dados: ' . $e->getMessage();
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