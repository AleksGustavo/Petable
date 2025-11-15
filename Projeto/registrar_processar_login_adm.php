<?php
// Arquivo: registrar_processar_login_adm.php
// Verifica se o usuário é um SuperAdmin (papel_id = 1) para liberar o registro.

session_start();
require_once 'conexao.php'; // Inclui sua conexão PDO ($pdo)

// Prepara para responder em JSON (obrigatório para o AJAX)
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
    // Busca a senha_hash, verificando se o usuário está ativo E é um SuperAdmin (papel_id = 1)
    $sql = "SELECT senha_hash FROM usuario 
            WHERE usuario = ? AND ativo = 1 AND papel_id = 1";
    
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$usuario]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($row) {
        $senha_hash = $row['senha_hash'];
        
        // 1. Verifica a senha com BCRYPT
        if (password_verify($senha_digitada, $senha_hash)) {
            
            // SUCESSO! Define a sessão que libera o acesso ao registrar.php
            $_SESSION['admin_pode_registrar'] = true;
            $response['success'] = true;
            $response['message'] = "Acesso concedido. Formulário de registro liberado.";
            
        } else {
            // Senha incorreta
            $response['message'] = "Acesso Negado! Senha incorreta.";
        }
    } else {
        // Usuário não encontrado ou não é SuperAdmin
        $response['message'] = "Acesso Negado! Credenciais ou permissões insuficientes.";
    }

} catch (PDOException $e) {
    // Erro no banco de dados
    $response['message'] = "Erro interno do sistema na autenticação: " . $e->getMessage();
}

echo json_encode($response);
exit();
?>