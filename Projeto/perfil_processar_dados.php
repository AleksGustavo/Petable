<?php
// Arquivo: perfil_processar_dados.php
// CORRIGIDO PARA ATUALIZAR NOME, DATA DE NASCIMENTO E SEXO NA TABELA 'funcionario'

session_start();
require_once 'conexao.php';

header('Content-Type: application/json');

$response = ['sucesso' => false, 'mensagem' => ''];

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['mensagem'] = 'Método de requisição inválido.';
    echo json_encode($response);
    exit();
}

$id_usuario = $_POST['id_usuario'] ?? null; // ID da tabela usuario (FK em funcionario)
$nome = $_POST['nome'] ?? '';
$sobrenome = $_POST['sobrenome'] ?? '';
// Agora estes campos existem no banco de dados e serão processados
$data_nascimento = $_POST['data_nascimento'] ?? null; 
$sexo = $_POST['sexo'] ?? null; 

// 1. RECOMPOSIÇÃO DO NOME COMPLETO
$nome_completo_final = trim("$nome $sobrenome");

// Validação básica
if (empty($id_usuario) || empty($nome_completo_final)) {
    $response['mensagem'] = 'ID do usuário e Nome Completo são obrigatórios para a atualização.';
    echo json_encode($response);
    exit();
}

if (isset($conexao)) {
    // 2. ATUALIZAR AS COLUNAS 'nome', 'data_nascimento' e 'sexo'
    $sql_update = "UPDATE funcionario SET nome = ?, data_nascimento = ?, sexo = ? WHERE usuario_id = ?";
    
    if ($stmt = mysqli_prepare($conexao, $sql_update)) {
        // Tipos: s (nome), s (data_nascimento), s (sexo), i (usuario_id)
        mysqli_stmt_bind_param($stmt, "sssi", $nome_completo_final, $data_nascimento, $sexo, $id_usuario);
        
        if (mysqli_stmt_execute($stmt)) {
            
            // Verifica se alguma linha foi realmente afetada
            if (mysqli_stmt_affected_rows($stmt) > 0) {
                $response['sucesso'] = true;
                $response['mensagem'] = 'Seus dados (Nome, Nascimento e Sexo) foram atualizados com sucesso!';
                
                // Atualizar a sessão para refletir a mudança no nome completo
                $_SESSION['nome_completo'] = $nome_completo_final;
            } else {
                 $response['sucesso'] = true; // Considere sucesso se não houve alteração
                 $response['mensagem'] = 'Nenhuma alteração de dados detectada ou usuário não encontrado.';
            }
            
        } else {
            $response['mensagem'] = 'Erro ao atualizar no banco de dados: ' . mysqli_error($conexao);
        }
        mysqli_stmt_close($stmt);
    } else {
        $response['mensagem'] = 'Erro ao preparar a declaração de atualização: ' . mysqli_error($conexao);
    }
    @mysqli_close($conexao);
} else {
    $response['mensagem'] = 'Erro de conexão com o banco de dados.';
}

echo json_encode($response);
?>