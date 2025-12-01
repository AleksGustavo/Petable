<?php
// Arquivo: login.php (Versão PDO - Login Apenas por Usuário e Senha)

// Inicia a sessão para gerenciamento do estado do usuário.
session_start(); 

// Inclui o arquivo de conexão PDO ($pdo).
require_once 'conexao.php'; 

// Inicializa variáveis de controle de status e sucesso.
$mensagem_status = "";
$sucesso = false;


// Verifica se o formulário foi submetido via método POST.
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    // 1. VERIFICAÇÃO CRÍTICA DE CONEXÃO: Verifica se a variável $pdo (conexão) foi estabelecida.
    if (!isset($pdo)) {
        $mensagem_status = "<h2 class='text-danger'>Erro crítico: Falha na conexão com o banco de dados.</h2>";
        goto exibir_html; // Salta para a exibição do HTML se houver falha na conexão.
    }
    
    // 2. Coletar e limpar dados: Coleta e remove espaços em branco do nome de usuário, e coleta a senha.
    $usuario_digitado = trim($_POST['username'] ?? ''); 
    $senha_digitada = $_POST['password'] ?? '';

    // 3. Consulta Segura: Prepara a query para buscar o usuário ativo pelo nome.
    $sql = "SELECT id, usuario, email, senha_hash, papel_id FROM usuario 
            WHERE usuario = ? AND ativo = 1";
    
    try {
        $stmt = $pdo->prepare($sql);
        // EXECUÇÃO: Executa a consulta, passando o usuário digitado como parâmetro.
        $stmt->execute([$usuario_digitado]);
        $usuario = $stmt->fetch(PDO::FETCH_ASSOC);

        // 4. Verifica se o usuário existe e está ativo.
        if ($usuario) {
            
            $hash_senha_bd = $usuario['senha_hash']; 
            
            // 5. Verifica a senha com BCRYPT: Compara a senha digitada com o hash armazenado de forma segura.
            if (password_verify($senha_digitada, $hash_senha_bd)) {
                
                // Sucesso no login: Define as variáveis de sessão.
                $_SESSION['logado'] = true;
                $_SESSION['id_usuario'] = $usuario['id'];
                $_SESSION['usuario'] = $usuario['usuario'];
                $_SESSION['papel_id'] = $usuario['papel_id']; 
                
                // ------------------------------------------------------------------
                // 6. BUSCAR E DEFINIR O ID DO FUNCIONÁRIO (para módulos como PDV).
                // ------------------------------------------------------------------
                
                $sql_func = "SELECT id FROM funcionario WHERE usuario_id = ?";
                $stmt_func = $pdo->prepare($sql_func);
                // Busca o ID do funcionário associado ao ID do usuário logado.
                $stmt_func->execute([$_SESSION['id_usuario']]); 
                $funcionario_detalhes = $stmt_func->fetch(PDO::FETCH_ASSOC);

                if ($funcionario_detalhes) {
                    // Armazena o ID da tabela 'funcionario' na sessão.
                    $_SESSION['id_funcionario'] = $funcionario_detalhes['id']; 
                } else {
                    // Define como 0 se não houver perfil de funcionário associado.
                    $_SESSION['id_funcionario'] = 0; 
                }
                
                // ------------------------------------------------------------------
                
                $mensagem_status = "<h2 class='text-success'>Login efetuado com sucesso!</h2>";
                $sucesso = true;
                
                // Redireciona para o dashboard após 2 segundos.
                header('Refresh: 2; URL=dashboard.php'); 
                
            } else {
                // Senha incorreta: Exibe mensagem de erro genérica.
                $mensagem_status = "<div class='alert alert-danger mt-3'>Usuário ou Senha incorretos.</div>";
            }
        } else {
            // Usuário não encontrado ou inativo: Exibe mensagem de erro genérica.
            $mensagem_status = "<div class='alert alert-danger mt-3'>Usuário ou Senha incorretos.</div>";
        }

    } catch (PDOException $e) {
        // Erro na consulta SQL: Exibe mensagem de erro interno.
        $mensagem_status = "<div class='alert alert alert-danger mt-3'>Erro interno: Falha ao tentar autenticar.</div>";
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
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
     <style>
        /* CSS para estilização de layout, cores e tema Pet Shop */
        
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
        
        /* Estilo para o ícone de mostrar/ocultar senha */
        .toggle-password {
            cursor: pointer;
            user-select: none; /* Impede seleção de texto */
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
            
            <?php if (!$sucesso): ?>
                
                <div class="text-center mb-4">
                    <img src="Logo.jpeg" 
                             alt="Logo PetShop" 
                             class="img-fluid rounded-circle mb-3 logo-borda" 
                             style="max-width: 120px;"> 
                              
                    <h2 class="card-title">Acesso PetShop</h2> 
                </div>

                <form action="login.php" method="POST">
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-user"></i></span>
                        <input type="text" name="username" class="form-control" placeholder="Usuário" required>
                    </div>
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                        <input type="password" name="password" id="passwordField" class="form-control" placeholder="Senha" required>
                        
                        <span class="input-group-text toggle-password" id="togglePassword">
                            <i class="fas fa-eye"></i> 
                        </span>
                    </div>
                    <button type="submit" class="btn btn-primary w-100 mt-2 login-btn">
                        <i class="fas fa-sign-in-alt me-2"></i> Entrar
                    </button>
                </form>

                <div class="links text-center mt-3">
                    <a href="esqueci_senha.php" class="d-block text-muted">Esqueci a Senha</a>
                    <a href="registrar.php" class="d-block text-muted">Criar uma nova conta</a>
                </div>
            <?php endif; ?>
        </div>
    </div> 
    
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>

    <script>
        // Obtém a referência ao campo de senha pelo ID
        const passwordField = document.getElementById('passwordField');
        // Obtém a referência ao ícone de olho pelo ID
        const togglePassword = document.getElementById('togglePassword');
        // Obtém a referência ao elemento do ícone Font Awesome dentro do span
        const toggleIcon = togglePassword.querySelector('i');

        // Adiciona um listener de evento de clique ao ícone de olho
        togglePassword.addEventListener('click', function () {
            // Verifica o tipo atual do campo: se for 'password', muda para 'text'; se for 'text', volta para 'password'.
            const type = passwordField.getAttribute('type') === 'password' ? 'text' : 'password';
            passwordField.setAttribute('type', type);

            // Alterna o ícone: se a senha for visível (type='text'), mostra o olho riscado ('fa-eye-slash');
            // caso contrário, mostra o olho normal ('fa-eye').
            if (type === 'text') {
                toggleIcon.classList.remove('fa-eye');
                toggleIcon.classList.add('fa-eye-slash');
            } else {
                toggleIcon.classList.remove('fa-eye-slash');
                toggleIcon.classList.add('fa-eye');
            }
        });
    </script>
</body>
</html>