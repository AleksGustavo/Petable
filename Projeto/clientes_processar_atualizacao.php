<?php
// Arquivo: clientes_processar_atualizacao.php
// IMPORTANTE: NÃO HÁ NADA ANTES DA TAG <?php
header('Content-Type: application/json');

// 1. Inclui o arquivo de conexão
require_once 'conexao.php'; // Certifique-se de que este caminho está correto

// Função de resposta JSON
function json_response($status, $message, $data = []) {
    // Retorna o status e a mensagem para o JavaScript/AJAX no frontend.
    echo json_encode(['status' => $status, 'message' => $message, 'data' => $data]);
    exit();
}

// Verifica se a conexão existe
if (!isset($conexao) || !$conexao) {
    json_response('error', 'Erro de conexão com o banco de dados.');
}

// 2. Verifica se a requisição é POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_response('error', 'Método de requisição inválido.');
}

try {
    // 3. Coleta e sanitiza os dados
    $id = filter_input(INPUT_POST, 'cliente_id', FILTER_VALIDATE_INT);
    $nome = trim(filter_input(INPUT_POST, 'nome', FILTER_SANITIZE_SPECIAL_CHARS));
    $sobrenome = trim(filter_input(INPUT_POST, 'sobrenome', FILTER_SANITIZE_SPECIAL_CHARS));
    
    // Concatena nome e sobrenome
    $nome_completo = trim($nome . ' ' . $sobrenome);
    
    // Remove a máscara do CPF e do Celular
    $cpf = preg_replace('/[^0-9]/', '', filter_input(INPUT_POST, 'cpf', FILTER_SANITIZE_STRING));
    $celular = preg_replace('/[^0-9]/', '', filter_input(INPUT_POST, 'celular', FILTER_SANITIZE_STRING));
    
    $data_nascimento = filter_input(INPUT_POST, 'data_nascimento', FILTER_SANITIZE_STRING);
    $cep = preg_replace('/[^0-9]/', '', filter_input(INPUT_POST, 'cep', FILTER_SANITIZE_STRING));
    $rua = trim(filter_input(INPUT_POST, 'rua', FILTER_SANITIZE_SPECIAL_CHARS));
    $numero = trim(filter_input(INPUT_POST, 'numero', FILTER_SANITIZE_SPECIAL_CHARS));
    $bairro = trim(filter_input(INPUT_POST, 'bairro', FILTER_SANITIZE_SPECIAL_CHARS));
    $complemento = trim(filter_input(INPUT_POST, 'complemento', FILTER_SANITIZE_SPECIAL_CHARS));

    // O campo 'sexo' não está na sua tabela 'cliente', mas pode ser útil para validação
    $sexo = filter_input(INPUT_POST, 'sexo', FILTER_SANITIZE_SPECIAL_CHARS); 

    // 4. Validação básica
    if (!$id) {
        json_response('error', 'ID do cliente inválido.');
    }
    if (empty($nome_completo) || empty($cpf) || empty($celular) || empty($rua) || empty($numero)) {
        json_response('error', 'Por favor, preencha todos os campos obrigatórios (Nome, Sobrenome, CPF, Celular, Rua, Número).');
    }
    
    // Verifica se o CPF tem o tamanho correto (11 dígitos)
    if (strlen($cpf) != 11) {
        json_response('error', 'O CPF deve conter 11 dígitos.');
    }
    
    // Verifica se o Telefone/Celular tem o tamanho correto
    if (strlen($celular) < 10) { // Mínimo 10 dígitos (DDD + 8 ou 9)
        json_response('error', 'O Celular deve ter pelo menos 10 dígitos (incluindo DDD).');
    }

    // 5. Lógica de Verificação de Unicidade (CPF e Telefone)
    
    // VERIFICAÇÃO DE CPF
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

    // VERIFICAÇÃO DE TELEFONE
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


    // 6. SQL para Atualização
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
                    -- email e observacoes não estão no formulário, mas podem ser adicionados
                    WHERE id = ?";

    $stmt = mysqli_prepare($conexao, $sql_update);

    // Bind dos parâmetros: 's' para string, 'i' para integer
    mysqli_stmt_bind_param(
        $stmt, 
        "sssssssssi", // 10 parâmetros: 9 strings + 1 integer (id)
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

    // 7. Execução e Verificação
    if (mysqli_stmt_execute($stmt)) {
        if (mysqli_stmt_affected_rows($stmt) > 0) {
            // ✅ MENSAGEM CORRIGIDA: Apenas a mensagem de sucesso simples.
            json_response('success', 'Cliente atualizado com sucesso!');
        } else {
            // Caso os dados sejam os mesmos, e nenhuma linha seja afetada
            json_response('info', 'Nenhuma alteração detectada para o cliente.');
        }
    } else {
        // Loga o erro de execução para debug (não expor ao usuário final)
        error_log("Erro na execução do UPDATE do cliente ID $id: " . mysqli_error($conexao));
        json_response('error', 'Erro ao salvar as alterações no banco de dados. Tente novamente.');
    }

    mysqli_stmt_close($stmt);

} catch (Exception $e) {
    // Loga qualquer exceção
    error_log("Exceção ao processar atualização do cliente: " . $e->getMessage());
    json_response('error', 'Ocorreu um erro inesperado no servidor.');
} finally {
    // 8. Fecha a conexão
    if (isset($conexao)) {
        // A conexão só deve ser fechada aqui, no final.
        // Se ela foi fechada em 'conexao.php', isso causará erro.
        mysqli_close($conexao);
    }
}

// ❌ NÃO DEVE HAVER NADA AQUI (NENHUM ESPAÇO OU NOVA LINHA)
// O FIM DO ARQUIVO DEVE SER AQUI.