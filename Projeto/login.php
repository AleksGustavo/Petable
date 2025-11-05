<?php
// Arquivo: login.php (Versão PDO com verificação BCRYPT - Recomendado)

session_start(); 

require_once 'conexao.php'; // Inclui a conexão PDO ($pdo)

$mensagem_status = "";
$sucesso = false;


if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    // 1. VERIFICAÇÃO CRÍTICA DE CONEXÃO
    if (!isset($pdo)) {
        $mensagem_status = "<h2 class='text-danger'>Erro crítico: Falha na conexão com o banco de dados.</h2>";
        goto exibir_html;
    }
    
    // 2. Coletar e limpar dados
    $usuario_digitado = trim($_POST['username'] ?? '');
    $senha_digitada = $_POST['password'] ?? '';

    // 3. Consulta Segura (Prepared Statement)
    // ATENÇÃO: Confirme se as colunas são 'usuario' e 'senha_hash'
    $sql = "SELECT id, usuario, senha_hash FROM usuario WHERE usuario = ?";
    
    try {
        $stmt = $pdo->prepare($sql);
        $stmt->execute([$usuario_digitado]);
        $usuario = $stmt->fetch(PDO::FETCH_ASSOC);

        // 4. Verifica se o usuário existe
        if ($usuario) {
            
            $hash_senha_bd = $usuario['senha_hash']; 
            
            // 5. Verifica a senha
            // Use password_verify() para senhas criptografadas com bcrypt
            if (password_verify($senha_digitada, $hash_senha_bd)) {
                
                // Sucesso no login
                $_SESSION['logado'] = true;
                $_SESSION['user_id'] = $usuario['id'];
                $_SESSION['username'] = $usuario['usuario'];
                
                $mensagem_status = "<h2 class='text-success'>Login efetuado com sucesso!</h2>";
                $sucesso = true;
                header('Refresh: 2; URL=dashboard.php'); // Redireciona após 2 segundos
                
            } else {
                // Senha incorreta
                $mensagem_status = "<div class='alert alert-danger mt-3'>Usuário ou senha inválidos.</div>";
            }
        } else {
            // Usuário não encontrado
            $mensagem_status = "<div class='alert alert-danger mt-3'>Usuário ou senha inválidos.</div>";
        }

    } catch (PDOException $e) {
        // Erro na consulta SQL
        $mensagem_status = "<div class='alert alert-danger mt-3'>Erro interno: " . $e->getMessage() . "</div>";
    }
}

exibir_html:
?>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login - PetShop</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH" crossorigin="anonymous">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
    <link rel="stylesheet" href="style.css">
    <style>
        body {
            background-color: #faf9f886; /* Fundo cinza claro */
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
        }

        /* ALTERAÇÃO PRINCIPAL: CARD MAIS LARGO */
        .login-card {
            max-width: 500px; /* Largura aumentada para 500px */
            width: 90%; 
            padding: 2rem;
            box-shadow: 0 4px 8px rgba(0,0,0,0.1);
        }

        .login-btn {
            background-color: #0d6efd; 
            border-color: #0d6efd;
            transition: background-color 0.3s;
        }

        .login-btn:hover {
            background-color: #0b5ed7; 
            border-color: #0b5ed7;
        }
    </style>
</head>
<body>

    <div class="card login-card">
        <div class="card-body">
            
            <?php if ($mensagem_status && !$sucesso): ?>
                <?php echo $mensagem_status; ?>
            <?php elseif ($sucesso): ?>
                <div class="text-center">
                    <?php echo $mensagem_status; ?>
                    <p class="mt-3">Você será redirecionado(a) em breve...</p>
                    <i class="fas fa-spinner fa-spin fa-2x text-success"></i>
                </div>
            <?php endif; ?>
            
            <?php if (!$mensagem_status || !$sucesso): ?>
                
                <div class="text-center mb-4">
                    <img src="Logo.jpeg" 
                         alt="Logo PetShop" 
                         class="img-fluid rounded-circle mb-3" 
                         style="max-width: 120px; border: 3px solid #0d6efd;"> 
                         
                    <h2 class="card-title">USER LOGIN</h2> 
                </div>

                <form action="login.php" method="POST">
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-user"></i></span>
                        <input type="text" name="username" class="form-control" placeholder="Digite seu usuário" required>
                    </div>
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                        <input type="password" name="password" class="form-control" placeholder="Digite sua senha" required>
                    </div>

                    <button type="submit" class="btn btn-primary w-100 mt-2 login-btn">Login</button>
                </form>

                <div class="links text-center mt-3">
                    <a href="#" class="d-block text-muted">Esqueci a Senha</a>
                    <a href="registrar.php" class="d-block text-muted">Criar uma nova conta</a>
                </div>
            <?php endif; ?>
        </div>
    </div> 
    
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js" integrity="sha384-YvpcrYf0tY3lHB60NNkmXc5s9fDVZLESaAA55NDzOxhy9GkcIdslK1eN7N6jIeHz" crossorigin="anonymous"></script>
</body>
</html>