<?php
// Arquivo: redefinir_senha.php

session_start();
require_once 'conexao.php'; 

$mensagem_status = "";
$token_valido = false;
$token = $_GET['token'] ?? '';
$usuario_id = null;

if (!isset($pdo)) {
    $mensagem_status = "<div class='alert alert-danger'>Erro: Falha na conexão com o banco de dados.</div>";
    goto exibir_html;
}

// --- VERIFICAÇÃO INICIAL DO TOKEN ---
if (!empty($token)) {
    // 1. Buscar usuário pelo token
    $sql = "SELECT id FROM usuario WHERE token_senha = ? AND token_expira > NOW()";
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$token]);
    $usuario = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($usuario) {
        $token_valido = true;
        $usuario_id = $usuario['id'];
    } else {
        $mensagem_status = "<div class='alert alert-danger'>Link inválido ou expirado. Solicite a recuperação novamente.</div>";
    }
} else {
    $mensagem_status = "<div class='alert alert-danger'>Token de redefinição não fornecido.</div>";
}

// --- PROCESSAMENTO DA NOVA SENHA (apenas se o token for válido e o formulário enviado) ---
if ($token_valido && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $nova_senha = $_POST['nova_senha'] ?? '';
    $confirma_senha = $_POST['confirma_senha'] ?? '';
    
    if (empty($nova_senha) || empty($confirma_senha)) {
        $mensagem_status = "<div class='alert alert-warning'>Preencha todos os campos.</div>";
    } elseif ($nova_senha !== $confirma_senha) {
        $mensagem_status = "<div class='alert alert-danger'>As senhas não coincidem.</div>";
    } elseif (strlen($nova_senha) < 6) {
        $mensagem_status = "<div class='alert alert-warning'>A senha deve ter pelo menos 6 caracteres.</div>";
    } else {
        try {
            // 2. Hash da nova senha
            $nova_senha_hash = password_hash($nova_senha, PASSWORD_BCRYPT);

            // 3. Atualizar a senha e invalidar o token (limpar as colunas)
            $sql_update = "UPDATE usuario SET senha_hash = ?, token_senha = NULL, token_expira = NULL WHERE id = ?";
            $stmt_update = $pdo->prepare($sql_update);
            $stmt_update->execute([$nova_senha_hash, $usuario_id]);

            $mensagem_status = "<div class='alert alert-success'>Senha redefinida com sucesso! Você pode fazer login agora.</div>";
            $token_valido = false; // Impede a exibição do formulário
            
        } catch (PDOException $e) {
            $mensagem_status = "<div class='alert alert-danger'>Erro interno ao salvar a nova senha.</div>";
        }
    }
}


exibir_html:
?>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Redefinir Senha - PetShop</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
      <style>
        /* TEMA PET SHOP: Bege Aconchegante e Marrom Caramelo */
        
        /* Fundo com Patinhas (Marca D'água) */
        body {
            /* Bege Aconchegante */
            background-color: #FAFAF5; 
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            
            /* Efeito Patinhas Sutil (via CSS) */
            background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><path fill="%23EFEFEA" d="M 50 20 L 70 30 L 60 50 L 80 60 L 60 70 L 40 60 L 50 80 L 30 70 L 40 50 L 20 60 L 30 30 Z M 50 20 C 45 15, 55 15, 50 20 Z M 35 35 C 30 30, 40 30, 35 35 Z M 65 35 C 60 30, 70 30, 65 35 Z M 35 65 C 30 60, 40 60, 35 65 Z M 65 65 C 60 60, 70 60, 65 65 Z"/></svg>');
            background-size: 80px; /* Tamanho da patinha */
            background-repeat: repeat;
            opacity: 0.9; /* Deixa o fundo opaco */
        }

        /* Card de Login */
        .login-card {
            max-width: 400px; 
            width: 90%; 
            padding: 2rem;
            box-shadow: 0 4px 15px rgba(0,0,0,0.15); /* Sombra mais destacada */
            background-color: #fff; /* Fundo branco para contraste */
            border-radius: 10px;
        }

        /* Botão Primário (Marrom Caramelo) */
        .btn-primary, .login-btn {
            background-color: #964B00 !important; /* Marrom Caramelo */
            border-color: #964B00 !important;
            font-weight: bold;
            letter-spacing: 0.5px;
            transition: background-color 0.3s;
        }

        .btn-primary:hover, .login-btn:hover {
            background-color: #703600 !important; /* Marrom mais escuro no hover */
            border-color: #604d3cff !important;
        }
        
        /* Detalhe da Logo */
        .logo-borda {
             border: 3px solid #964B00 !important;
        }
    </style>
</head>
<body>

    <div class="card login-card">
        <div class="card-body">
            
            <h2 class="card-title text-center mb-4"><i class="fas fa-redo-alt me-2"></i> Nova Senha</h2> 

            <?php echo $mensagem_status; ?>
            
            <?php if ($token_valido): ?>
            
                <form action="redefinir_senha.php?token=<?= htmlspecialchars($token) ?>" method="POST">
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-key"></i></span>
                        <input type="password" name="nova_senha" class="form-control" placeholder="Nova Senha" required>
                    </div>

                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-key"></i></span>
                        <input type="password" name="confirma_senha" class="form-control" placeholder="Confirme a Nova Senha" required>
                    </div>

                    <button type="submit" class="btn btn-primary w-100 mt-2 login-btn">
                        <i class="fas fa-check me-2"></i> Redefinir Senha
                    </button>
                </form>

            <?php endif; ?>

            <div class="links text-center mt-3">
                <a href="login.php" class="d-block text-muted">Fazer Login</a>
            </div>
        </div>
    </div> 
</body>
</html>