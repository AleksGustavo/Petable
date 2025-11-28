<?php
header('Content-Type: application/json');

require_once 'conexao.php'; 

function json_response($status, $message, $data = []) {
    echo json_encode(['status' => $status, 'message' => $message, 'data' => $data]);
    exit();
}

if (!isset($conexao) || !$conexao) {
    json_response('error', 'Erro de conexão com o banco de dados.');
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_response('error', 'Método de requisição inválido.');
}

try {
    
    $id = filter_input(INPUT_POST, 'cliente_id', FILTER_VALIDATE_INT);
    $nome = trim(filter_input(INPUT_POST, 'nome', FILTER_SANITIZE_SPECIAL_CHARS));
    $sobrenome = trim(filter_input(INPUT_POST, 'sobrenome', FILTER_SANITIZE_SPECIAL_CHARS));
    
    
    $nome_completo = trim($nome . ' ' . $sobrenome);
    
    
    $cpf = preg_replace('/[^0-9]/', '', filter_input(INPUT_POST, 'cpf', FILTER_SANITIZE_STRING));
    $celular = preg_replace('/[^0-9]/', '', filter_input(INPUT_POST, 'celular', FILTER_SANITIZE_STRING));
    
    $data_nascimento = filter_input(INPUT_POST, 'data_nascimento', FILTER_SANITIZE_STRING);
    $cep = preg_replace('/[^0-9]/', '', filter_input(INPUT_POST, 'cep', FILTER_SANITIZE_STRING));
    $rua = trim(filter_input(INPUT_POST, 'rua', FILTER_SANITIZE_SPECIAL_CHARS));
    $numero = trim(filter_input(INPUT_POST, 'numero', FILTER_SANITIZE_SPECIAL_CHARS));
    $bairro = trim(filter_input(INPUT_POST, 'bairro', FILTER_SANITIZE_SPECIAL_CHARS));
    $complemento = trim(filter_input(INPUT_POST, 'complemento', FILTER_SANITIZE_SPECIAL_CHARS));

    
    $sexo = filter_input(INPUT_POST, 'sexo', FILTER_SANITIZE_SPECIAL_CHARS); 

    
    if (!$id) {
        json_response('error', 'ID do cliente inválido.');
    }
    if (empty($nome_completo) || empty($cpf) || empty($celular) || empty($rua) || empty($numero)) {
        json_response('error', 'Por favor, preencha todos os campos obrigatórios (Nome, Sobrenome, CPF, Celular, Rua, Número).');
    }
    
    
    if (strlen($cpf) != 11) {
        json_response('error', 'O CPF deve conter 11 dígitos.');
    }
    
    
    if (strlen($celular) < 10) { 
        json_response('error', 'O Celular deve ter pelo menos 10 dígitos (incluindo DDD).');
    }

    
    
    
    $sql_check_cpf = "SELECT id FROM cliente WHERE cpf = ? AND id != ?";
    $stmt_check_cpf = mysqli_prepare($conexao, $sql_check_cpf);
    mysqli_stmt_bind_param($stmt_check_cpf, "si", $cpf, $id);
    mysqli_stmt_execute($stmt_check_cpf);
    mysqli_stmt_store_result($stmt_check_cpf);
    
    if (mysqli_stmt_num_rows($stmt_check_cpf) > 0) {
        mysqli_stmt_close($stmt_check_cpf);
        json_response('error', 'Este CPF já está cadastrado em outro cliente.');
    }
    mysqli_stmt_close($stmt_check_cpf);

    
    $sql_check_tel = "SELECT id FROM cliente WHERE telefone = ? AND id != ?";
    $stmt_check_tel = mysqli_prepare($conexao, $sql_check_tel);
    mysqli_stmt_bind_param($stmt_check_tel, "si", $celular, $id);
    mysqli_stmt_execute($stmt_check_tel);
    mysqli_stmt_store_result($stmt_check_tel);

    if (mysqli_stmt_num_rows($stmt_check_tel) > 0) {
        mysqli_stmt_close($stmt_check_tel);
        json_response('error', 'Este Telefone/Celular já está cadastrado em outro cliente.');
    }
    mysqli_stmt_close($stmt_check_tel);


    
    $sql_update = "UPDATE cliente SET 
                      nome = ?, 
                      cpf = ?, 
                      telefone = ?,
                      data_nascimento = ?, 
                      cep = ?, 
                      rua = ?, 
                      numero = ?, 
                      bairro = ?, 
                      complemento = ?
                      
                      WHERE id = ?";

    $stmt = mysqli_prepare($conexao, $sql_update);

    
    mysqli_stmt_bind_param(
        $stmt, 
        "sssssssssi", 
        $nome_completo, 
        $cpf, 
        $celular, 
        $data_nascimento, 
        $cep, 
        $rua, 
        $numero, 
        $bairro, 
        $complemento,
        $id
    );

    
    if (mysqli_stmt_execute($stmt)) {
        if (mysqli_stmt_affected_rows($stmt) > 0) {
            
            json_response('success', 'Cliente atualizado com sucesso!');
        } else {
            
            json_response('info', 'Nenhuma alteração detectada para o cliente.');
        }
    } else {
        
        error_log("Erro na execução do UPDATE do cliente ID $id: " . mysqli_error($conexao));
        json_response('error', 'Erro ao salvar as alterações no banco de dados. Tente novamente.');
    }

    mysqli_stmt_close($stmt);

} catch (Exception $e) {
    
    error_log("Exceção ao processar atualização do cliente: " . $e->getMessage());
    json_response('error', 'Ocorreu um erro inesperado no servidor.');
} finally {
    
    if (isset($conexao)) {
        
        mysqli_close($conexao);
    }
}