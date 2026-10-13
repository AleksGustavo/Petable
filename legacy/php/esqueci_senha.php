<?php
// Arquivo: esqueci_senha.php

session_start();
require_once 'conexao.php'; 
$pdo = $conexao; 

$mensagem_status = "";

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    if (!isset($pdo)) {
        $mensagem_status = "<div class='alert alert-danger'>Erro: Falha na conexão com o banco de dados.</div>";
        goto exibir_html;
    }
    
    $email_digitado = trim($_POST['email'] ?? '');

    if (empty($email_digitado)) {
        $mensagem_status = "<div class='alert alert-danger'>Por favor, digite seu e-mail de cadastro.</div>";
        goto exibir_html;
    }

    // 1. Verificar se o e-mail existe e está ativo
    $sql = "SELECT id, usuario FROM usuario WHERE email = ? AND ativo = 1";
    
    try {
        $stmt = $pdo->prepare($sql);
        $stmt->execute([$email_digitado]);
        $usuario = $stmt->fetch(PDO::FETCH_ASSOC);

        if ($usuario) {
            // 2. Gerar Token Único e Tempo de Expiração (Ex: 1 hora)
            $token = bin2hex(random_bytes(32)); 
            $expira = date("Y-m-d H:i:s", time() + 3600); 
            $usuario_id = $usuario['id'];
            
            // 3. Salvar Token no Banco de Dados 
            // Assumindo que você tem as colunas 'token_senha' e 'token_expira' na tabela 'usuario'.
            $sql_token = "UPDATE usuario SET token_senha = ?, token_expira = ? WHERE id = ?";
            $stmt_token = $pdo->prepare($sql_token);
            $stmt_token->execute([$token, $expira, $usuario_id]);

            // 4. Montar o Link de Redefinição
            $link_redefinicao = "http://localhost/PHP_PI/redefinir_senha.php?token=" . $token;

            // 5. SIMULAÇÃO DE ENVIO DE E-MAIL 
            $mensagem_status = "<div class='alert alert-success'>";
            $mensagem_status .= "Se o e-mail estiver cadastrado, um link de redefinição foi enviado. ";
            $mensagem_status .= "Verifique sua caixa de entrada (e spam).";
            $mensagem_status .= "</div>";
            
            // --- CÓDIGO DE DEBUG ---
            $mensagem_status .= "<p class='text-danger small'>DEBUG: Link de Redefinição (Clique AQUI para testar localmente): <a href='{$link_redefinicao}'>$link_redefinicao</a></p>";
            // ------------------------------------------

            // NOTA: Para segurança, sempre retorne a mesma mensagem 

        } else {
            $mensagem_status = "<div class='alert alert-success'>Se o e-mail estiver cadastrado, um link de redefinição foi enviado. Verifique sua caixa de entrada (e spam).</div>";
        }

    } catch (PDOException $e) {
        $mensagem_status = "<div class='alert alert-danger'>Erro interno ao processar a solicitação.</div>";
    }
}

exibir_html:
?>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Recuperar Senha - PetShop</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
<style>
    body {
        background-color: #FAFAF5; 
        display: flex;
        justify-content: center;
        align-items: center;
        height: 100vh;
        
        background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><path fill="%23EFEFEA" d="M 50 20 L 70 30 L 60 50 L 80 60 L 60 70 L 40 60 L 50 80 L 30 70 L 40 50 L 20 60 L 30 30 Z M 50 20 C 45 15, 55 15, 50 20 Z M 35 35 C 30 30, 40 30, 35 35 Z M 65 35 C 60 30, 70 30, 65 35 Z M 35 65 C 30 60, 40 60, 35 65 Z M 65 65 C 60 60, 70 60, 65 65 Z"/></svg>');
        background-size: 80px; 
        background-repeat: repeat;
        opacity: 0.9; 
    }

    .login-card {
        max-width: 500px; 
        width: 30%; 
        min-height: 550px; 
        padding: 2rem;
        box-shadow: 0 4px 15px rgba(0,0,0,0.15); 
        background-color: #fff; 
        border-radius: 10px;
    }
    
    .login-card .alert {
        text-align: center;
    }

    .btn-primary, .login-btn {
        background-color: #964B00 !important; 
        border-color: #964B00 !important;
        font-weight: bold;
        letter-spacing: 0.5px;
        transition: background-color 0.3s;
    }

    .btn-primary:hover, .login-btn:hover {
        background-color: #703600 !important; 
        border-color: #604d3cff !important;
    }
    
    .logo-borda {
        border: 3px solid #964B00 !important;
    }
</style>
</head>
<body>

    <div class="card login-card">
        <div class="card-body">

            <div class="text-center mb-4">
                    <img src="Logo.jpeg" 
                             alt="Logo PetShop" 
                             class="img-fluid rounded-circle mb-3 logo-borda" 
                             style="max-width: 120px;"> 
                             
                    
                </div>

            
            <h2 class="card-title text-center mb-4"><i class="fas fa-lock me-2"></i> Recuperar Senha</h2> 

            <?php echo $mensagem_status; ?>
            
            <?php if (strpos($mensagem_status, 'alert-success') === false): ?>
            
                <p class="text-muted text-center">Digite seu e-mail para receber o link de redefinição.</p>

                <form action="esqueci_senha.php" method="POST">
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-envelope"></i></span>
                        <input type="email" name="email" class="form-control" placeholder="E-mail de Cadastro" required>
                    </div>

                    <button type="submit" class="btn btn-primary w-100 mt-2 login-btn">
                        <i class="fas fa-paper-plane me-2"></i> Enviar Link
                    </button>
                </form>

                <div class="links text-center mt-3">
                    <a href="login.php" class="d-block text-muted">Voltar para o Login</a>
                </div>
            <?php endif; ?>
        </div>
    </div> 
</body>
</html>