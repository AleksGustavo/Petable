<?php
// Arquivo: clientes_processar.php (CONVERTIDO PARA MYSQLI)

// 1. Configura o cabeçalho para JSON
header('Content-Type: application/json');

session_start();
require_once 'conexao.php'; 

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

// 2. Verifica a conexão e o método (usando $conexao do MySQLi)
if (!isset($conexao) || !$conexao) {
    
    $response['message'] = "Erro crítico: Falha na conexão com o banco de dados (MySQLi).";
    goto final_json;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['message'] = "Método de requisição inválido.";
    goto final_json;
}

try {
    // 3. Coleta e sanitiza dados do POST (sem alterações)
    
    $nome = trim($_POST['nome'] ?? '');
    $sobrenome = trim($_POST['sobrenome'] ?? ''); 
    $cpf = trim($_POST['cpf'] ?? '');
    $telefone = trim($_POST['celular'] ?? ''); 
    $cep = trim($_POST['cep'] ?? '');
    $rua = trim($_POST['rua'] ?? '');
    $numero = trim($_POST['numero'] ?? '');
    $bairro = trim($_POST['bairro'] ?? '');
    $complemento = trim($_POST['complemento'] ?? '');
    $data_nascimento = !empty($_POST['data_nascimento']) ? $_POST['data_nascimento'] : null;
    $nome_completo = $nome . ' ' . $sobrenome;
    
    // 4. Validação simples
    if (empty($nome) || empty($sobrenome) || empty($cpf) || empty($telefone) || empty($rua) || empty($numero)) {
        $response['message'] = "Preencha todos os campos obrigatórios (*).";
        goto final_json;
    }
    
    // 5. Inserção no Banco de Dados (MySQLi e Prepared Statement)
    
    $sql = "INSERT INTO cliente (nome, cpf, telefone, cep, rua, numero, bairro, complemento, data_nascimento, created_at) 
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())"; 
             
    $stmt = mysqli_prepare($conexao, $sql);
    
    
    $tipos = "sssssssss";
    
    
    
    mysqli_stmt_bind_param($stmt, $tipos, 
        $nome_completo,
        $cpf, 
        $telefone, 
        $cep,
        $rua,
        $numero,
        $bairro, 
        $complemento, 
        $data_nascimento 
    );
    

    if (mysqli_stmt_execute($stmt)) {
        $response['success'] = true;
        $response['message'] = "Cliente **" . htmlspecialchars($nome_completo) . "** cadastrado com sucesso!";
    } else {
        
        $error_code = mysqli_stmt_errno($stmt);
        if ($error_code == 1062) { 
            $response['message'] = "Erro: Já existe um cliente cadastrado com este CPF.";
        } else {
            $response['message'] = "Erro de BD inesperado. Detalhes: " . mysqli_error($conexao) . " (Código: " . $error_code . ")";
        }
    }

    mysqli_stmt_close($stmt);

} catch (Exception $e) {
    
    $response['message'] = "Erro de aplicação: " . $e->getMessage();
}

// 7. Bloco de saída JSON final
final_json:
// Fecha a conexão após o uso
if (isset($conexao)) {
    mysqli_close($conexao);
}
echo json_encode($response);
exit();