<?php
// Arquivo: pets_processar_vacina_manual.php
// Processa a inserção de um registro na carteira de vacinas (uso manual/modal)

// Define o cabeçalho para garantir que a resposta seja JSON para o AJAX
header('Content-Type: application/json');
require_once 'conexao.php'; // Sua conexão com o banco (mysqli)

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // 1. Coleta e validação dos dados
    $pet_id = filter_input(INPUT_POST, 'pet_id', FILTER_VALIDATE_INT);
    // Sanitize para evitar XSS (Cross-Site Scripting)
    $nome_vacina = filter_input(INPUT_POST, 'nome_vacina', FILTER_SANITIZE_SPECIAL_CHARS);
    $data_aplicacao = filter_input(INPUT_POST, 'data_aplicacao', FILTER_SANITIZE_STRING);
    $data_proxima = filter_input(INPUT_POST, 'data_proxima', FILTER_SANITIZE_STRING);
    $veterinario = filter_input(INPUT_POST, 'veterinario', FILTER_SANITIZE_SPECIAL_CHARS);
    $observacoes = filter_input(INPUT_POST, 'observacoes', FILTER_SANITIZE_SPECIAL_CHARS);

    // Validação básica dos campos obrigatórios
    if (!$pet_id || !$nome_vacina || !$data_aplicacao) {
        $response['message'] = 'Dados obrigatórios (Pet ID, Nome da Vacina e Data de Aplicação) não fornecidos.';
        echo json_encode($response);
        exit();
    }
    
    // Tratamento de campos opcionais: Se vazios, devem ser passados como NULL para o SQL
    $data_proxima_sql = empty($data_proxima) ? NULL : $data_proxima;
    $veterinario_sql = empty($veterinario) ? NULL : $veterinario;
    $observacoes_sql = empty($observacoes) ? NULL : $observacoes;

    if (isset($conexao)) {
        try {
            // 2. Prepara a inserção na tabela carteira_vacinas
            $sql = "INSERT INTO carteira_vacina (pet_id, nome_vacina, data_aplicacao, data_proxima, veterinario, observacoes) 
                    VALUES (?, ?, ?, ?, ?, ?)";
            
            $stmt = mysqli_prepare($conexao, $sql);
            
            // Associa os parâmetros: i=integer (pet_id), s=string (todos os outros campos)
            // O mysqli aceita passar NULL para strings ('s')
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
                // Erro de execução do SQL
                $response['message'] = 'Erro ao inserir no banco: ' . mysqli_error($conexao);
            }

            mysqli_stmt_close($stmt);

        } catch (Exception $e) {
            // Erro de PHP ou exceção (ex: falha de conexão)
            error_log("Erro no pets_processar_vacina_manual: " . $e->getMessage());
            $response['message'] = 'Erro de Servidor/Banco de Dados. Tente novamente.';
        }
    } else {
        $response['message'] = 'Erro de conexão com o banco de dados.';
    }

} else {
    $response['message'] = 'Método de requisição inválido. (Esperado: POST)';
}

// Finaliza a resposta JSON
echo json_encode($response);
exit();
?>