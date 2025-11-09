<?php
// Arquivo: registrar.php (Versão Final com PDO e E-mail)

session_start();
require_once 'conexao.php'; // Garante que a variável $pdo está disponível

$mensagem_status = "";
$sucesso = false; // Flag de sucesso

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    // 1. VERIFICAÇÃO CRÍTICA DE CONEXÃO
    if (!isset($pdo)) {
        $mensagem_status = "<h4 class='text-danger'>Erro crítico: Falha na conexão com o banco de dados.</h4>";
        goto exibir_html; 
    }
    
    // 2. Coletar e limpar os dados
    $usuario_novo = trim($_POST['username'] ?? '');
    $email_novo = filter_input(INPUT_POST, 'email', FILTER_VALIDATE_EMAIL); // NOVO CAMPO: Coleta e valida o e-mail
    $senha_nova = $_POST['password'] ?? '';
    $senha_confirmar = $_POST['confirm_password'] ?? '';
    
    // 3. Validação básica
    if (empty($usuario_novo) || empty($senha_nova) || empty($senha_confirmar) || $email_novo === false) {
        $mensagem_status = "<h4 class='text-danger'>Preencha todos os campos corretamente, incluindo um e-mail válido.</h4>";
    } elseif ($senha_nova !== $senha_confirmar) {
        $mensagem_status = "<h4 class='text-danger'>As senhas não coincidem.</h4>";
    } elseif (strlen($senha_nova) < 6) {
        $mensagem_status = "<h4 class='text-danger'>A senha deve ter pelo menos 6 caracteres.</h4>";
    } else {
        
        try {
            // 4. Verifica se o USUÁRIO ou o E-MAIL já existe
            $sql_check = "SELECT id FROM usuario WHERE usuario = ? OR email = ?";
            $stmt_check = $pdo->prepare($sql_check);
            $stmt_check->execute([$usuario_novo, $email_novo]);
            
            if ($stmt_check->rowCount() > 0) {
                $mensagem_status = "<h4 class='text-danger'>Usuário ou E-mail já existe. Escolha credenciais diferentes.</h4>";
            } else {
                
                // 5. Cria o hash seguro da senha (BCRYPT)
                $hash_senha = password_hash($senha_nova, PASSWORD_DEFAULT);
                
                // 6. Insere o novo usuário (PDO)
                $papel_id_padrao = 2; // Ex: 'FuncionarioVendas'

                // ATUALIZADO: Inclui 'email' na inserção
                $sql_insert = "INSERT INTO usuario (usuario, email, senha_hash, papel_id, ativo) 
                               VALUES (?, ?, ?, ?, 1)";
                
                $stmt_insert = $pdo->prepare($sql_insert);
                
                $execucao_sucesso = $stmt_insert->execute([
                    $usuario_novo, 
                    $email_novo, // Usa o e-mail digitado
                    $hash_senha, 
                    $papel_id_padrao
                ]);

                if ($execucao_sucesso) {
                    $mensagem_status = "<h4 class='text-success'>✅ Cadastro efetuado com sucesso! Redirecionando para o login...</h4>";
                    $sucesso = true;
                    header("Refresh: 3; URL=login.php"); 
                    
                } else {
                    $mensagem_status = "<h4 class='text-danger'>Erro ao cadastrar. Falha na execução da query.</h4>";
                }
            }
        
        } catch (PDOException $e) {
            $mensagem_status = "<h4 class='text-danger'>Erro no servidor ao tentar registrar: " . $e->getMessage() . "</h4>";
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
    <title>Registro - Novo Usuário</title>
    
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
            opacity: 0.9;
        }

        /* Card de Login/Registro */
        .login-card {
            max-width: 450px; 
            width: 90%; 
            padding: 2rem;
            box-shadow: 0 4px 15px rgba(0,0,0,0.15); 
            background-color: #fff; 
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
            border-color: #703600 !important;
        }
        
        /* Detalhe da Logo */
        .logo-borda {
             border: 3px solid #795548 !important; /* Coerência com login */
             box-shadow: 0 0 10px rgba(121, 85, 72, 0.7); /* Coerência com login */
        }
    </style>
</head>
<body>
    
    <div class="card login-card">
        <div class="card-body">
            
            <?php if ($mensagem_status): ?>
                <div class="status-message text-center mb-4">
                    <?php echo $mensagem_status; ?>
                </div>
            <?php endif; ?>

            <?php 
            // Só exibe o formulário se o cadastro não foi um sucesso
            if (!$sucesso): ?>
            
                <div class="text-center mb-4">
                    <img src="Logo.jpeg" 
                         alt="Logo PetShop" 
                         class="img-fluid rounded-circle mb-3 logo-borda" 
                         style="max-width: 120px;"> 
                         
                    <h2 class="card-title">CRIAR CONTA</h2> 
                </div>

                <form action="registrar.php" method="POST">
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-user"></i></span>
                        <input type="text" name="username" class="form-control" placeholder="Escolha um Usuário" required>
                    </div>
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-envelope"></i></span>
                        <input type="email" name="email" class="form-control" placeholder="Digite seu E-mail" required>
                    </div>

                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                        <input type="password" name="password" class="form-control" placeholder="Digite sua Senha" required>
                    </div>

                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                        <input type="password" name="confirm_password" class="form-control" placeholder="Confirme a Senha" required>
                    </div>

                    <button type="submit" class="btn btn-primary w-100 mt-2 login-btn">
                        <i class="fas fa-user-plus me-2"></i> CADASTRAR
                    </button>
                </form>

                <div class="links text-center mt-3">
                    <a href="login.php" class="d-block text-muted">Já tenho uma conta (Fazer Login)</a>
                </div>

            <?php endif; ?>
        </div>
    </div> 
    
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>