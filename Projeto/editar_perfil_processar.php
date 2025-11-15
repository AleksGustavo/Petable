<?php
// Arquivo: editar_perfil_processar.php

session_start();
require_once 'conexao.php'; // Inclui a conexão
$pdo = $conexao; // Assume que $conexao é o PDO

header('Content-Type: application/json'); // Responde em JSON

$response = ['sucesso' => false, 'mensagem' => ''];

// 1. Verificação de Acesso e Método
if ($_SERVER['REQUEST_METHOD'] !== 'POST' || !isset($_SESSION['logado']) || !isset($_SESSION['id_usuario'])) {
    $response['mensagem'] = 'Acesso inválido ou usuário não logado.';
    echo json_encode($response);
    exit();
}

// 2. Coleta e Validação dos Dados
$usuario_id = intval($_POST['usuario_id'] ?? 0);
$primeiro_nome = trim($_POST['primeiro_nome'] ?? '');
$sobrenome = trim($_POST['sobrenome'] ?? '');
$data_nascimento = trim($_POST['data_nascimento'] ?? '');
$telefone = trim($_POST['telefone'] ?? '');
$cep = trim($_POST['cep'] ?? '');
$rua = trim($_POST['rua'] ?? '');
$bairro = trim($_POST['bairro'] ?? '');
$numero = trim($_POST['numero'] ?? '');
$complemento = trim($_POST['complemento'] ?? '');

// Validação Básica
if ($usuario_id === 0 || $usuario_id !== $_SESSION['id_usuario']) {
    $response['mensagem'] = 'ID de usuário inválido ou tentativa de editar outro perfil.';
    echo json_encode($response);
    exit();
}

if (empty($primeiro_nome) || empty($sobrenome) || empty($data_nascimento)) {
    $response['mensagem'] = 'Os campos Nome, Sobrenome e Data de Nascimento são obrigatórios.';
    echo json_encode($response);
    exit();
}

// Junta o nome completo
$nome_completo = $primeiro_nome . ' ' . $sobrenome;

// 3. Atualização no Banco de Dados (Tabela funcionario)
$sql = "UPDATE funcionario 
        SET nome = ?, 
            data_nascimento = ?, 
            telefone = ?, 
            cep = ?, 
            rua = ?, 
            bairro = ?, 
            numero = ?, 
            complemento = ?
        WHERE usuario_id = ?";
        
try {
    $stmt = $pdo->prepare($sql);
    $exec = $stmt->execute([
        $nome_completo,
        $data_nascimento,
        $telefone,
        $cep,
        $rua,
        $bairro,
        $numero,
        $complemento,
        $usuario_id
    ]);

    if ($exec) {
        $response['sucesso'] = true;
        $response['mensagem'] = 'Dados pessoais atualizados com sucesso!';
    } else {
        $response['mensagem'] = 'Nenhuma alteração foi salva ou erro ao executar a atualização.';
    }
} catch (PDOException $e) {
    // Em caso de erro, por exemplo, CPF já existente (embora CPF não esteja sendo alterado, é bom ter)
    if ($e->getCode() == 23000) { // Código de erro de violação de restrição de integridade
        $response['mensagem'] = 'Erro: Os dados inseridos (ex: telefone) já existem no sistema.';
    } else {
        $response['mensagem'] = 'Erro fatal ao salvar: ' . $e->getMessage();
    }
}

echo json_encode($response);
exit();