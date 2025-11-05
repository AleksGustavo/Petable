<?php
// Arquivo: servicos_processar_vacina.php
header('Content-Type: application/json');

require_once 'conexao.php';

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

if (!isset($conexao) || !$conexao) {
    $response['message'] = "Erro crítico: Falha na conexão com o banco de dados.";
    echo json_encode($response);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['message'] = "Método de requisição inválido.";
    echo json_encode($response);
    exit();
}

// 1. Coleta e sanitiza dados
$pet_id          = filter_input(INPUT_POST, 'pet_id', FILTER_VALIDATE_INT);
$nome_vacina     = trim($_POST['nome_vacina'] ?? '');
$data_aplicacao  = trim($_POST['data_aplicacao'] ?? '');
$data_proxima    = trim($_POST['data_proxima'] ?? '');
$veterinario     = trim($_POST['veterinario'] ?? '');
$observacoes     = trim($_POST['observacoes'] ?? '');

// 2. Validação
if (!$pet_id || empty($nome_vacina) || empty($data_aplicacao)) {
    $response['message'] = "Dados obrigatórios (Pet ID, Vacina e Data de Aplicação) não preenchidos.";
    echo json_encode($response);
    exit();
}

// Trata campos opcionais (converte string vazia para NULL para o banco de dados)
$data_proxima = empty($data_proxima) ? null : $data_proxima;
$veterinario = empty($veterinario) ? null : $veterinario;
$observacoes = empty($observacoes) ? null : $observacoes;

// 3. Inserção no Banco de Dados (MySQLi Prepared Statement)
try {
    $sql = "INSERT INTO carteira_vacina (pet_id, nome_vacina, data_aplicacao, data_proxima, veterinario, observacoes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())";
            
    $stmt = mysqli_prepare($conexao, $sql);
    
    // Tipos: i (int) para pet_id, e 5 s (string) para nome_vacina, datas e textos.
    $tipos = "isssss"; 
    
    // Bind dos parâmetros
    mysqli_stmt_bind_param($stmt, $tipos,
        $pet_id,
        $nome_vacina,
        $data_aplicacao,
        $data_proxima,
        $veterinario,
        $observacoes
    );
    
    if (mysqli_stmt_execute($stmt)) {
        $response['success'] = true;
        $response['message'] = "Vacina **" . htmlspecialchars($nome_vacina) . "** registrada com sucesso na carteira do Pet.";
    } else {
        $response['message'] = "Erro ao registrar vacina. Detalhes: " . mysqli_error($conexao);
    }

    mysqli_stmt_close($stmt);

} catch (Exception $e) {
    $response['message'] = "Erro de aplicação inesperado: " . $e->getMessage();
}

mysqli_close($conexao);
echo json_encode($response);
exit();