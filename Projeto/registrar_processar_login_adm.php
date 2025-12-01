<?php
session_start();
require_once 'conexao.php';

header('Content-Type: application/json');
$response = ['success' => false, 'message' => ''];

if ($_SERVER['REQUEST_METHOD'] !== 'POST' || !isset($pdo)) {
    $response['message'] = "Requisição inválida ou falha na conexão.";
    echo json_encode($response);
    exit;
}

$usuario = trim($_POST['admin_user'] ?? '');
$senha_digitada = $_POST['admin_pass'] ?? '';

if (empty($usuario) || empty($senha_digitada)) {
    $response['message'] = "Preencha o usuário e a senha.";
    echo json_encode($response);
    exit;
}

try {
    $sql = "SELECT senha_hash FROM usuario 
            WHERE usuario = ? AND ativo = 1 AND papel_id = 1";
    
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$usuario]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($row) {
        $senha_hash = $row['senha_hash'];
        
        if (password_verify($senha_digitada, $senha_hash)) {

            $_SESSION['admin_pode_registrar'] = true;

            $limite_segundos = 300;
            $_SESSION['admin_auth_time'] = time() + $limite_segundos;

            $response['success'] = true;
            $response['message'] = "Acesso concedido. Formulário de registro liberado.";
            
        } else {
            $response['message'] = "Acesso Negado! Senha incorreta.";
        }
    } else {
        $response['message'] = "Acesso Negado! Credenciais ou permissões insuficientes.";
    }

} catch (PDOException $e) {
    $response['message'] = "Erro interno do sistema na autenticação: " . $e->getMessage();
}

echo json_encode($response);
exit();
