<?php
// Arquivo: registrar.php

session_start();
require_once 'conexao.php';

// ------------------------------------------------------------------
// NOVO BLOCO DE VERIFICAÇÃO DE SEGURANÇA (VERIFICA SESSÃO DE ADM)
// ------------------------------------------------------------------
if (!isset($_SESSION['admin_pode_registrar']) || $_SESSION['admin_pode_registrar'] !== true) {
    
    // Se for uma tentativa de POST (registro) sem a sessão de ADM, bloqueia e exibe erro.
    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $mensagem_status = "<h4 class='text-danger'>Acesso negado. A sessão de SuperAdmin expirou ou é inválida. Recarregue a página e tente novamente.</h4>";
        // Usa goto para pular diretamente à seção de exibição de HTML (ponto de segurança)
        goto exibir_html; 
    }
    
    // Se for requisição GET (carregamento inicial), exibe a tela de bloqueio e finaliza o script.
    header('Content-Type: text/html'); 
    ?>
    <!DOCTYPE html>
    <html lang="pt-BR">
    <head>
        <title>Acesso Negado</title>
        <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
        <style>
             body { background-color: #FAFAF5; display: flex; justify-content: center; align-items: center; height: 100vh; }
        </style>
    </head>
    <body>
        <div class="alert alert-danger text-center mt-5 p-4 shadow mx-auto" style="max-width: 450px;">
            <h2><i class="fas fa-lock me-2"></i> Acesso Negado!</h2>
            <p>O formulário de criação de contas está bloqueado. Apenas um SuperAdmin pode liberá-lo.</p>
            
            <a href="registrar_autenticar_adm.php" class="btn btn-warning mt-3">
                <i class="fas fa-user-shield"></i> Entrar Como Administrador
            </a>
            <a href="login.php" class="btn btn-secondary mt-3">
                <i class="fas fa-sign-in-alt"></i> Voltar ao Login
            </a>
            <p class="small text-muted mt-2"></p>
        </div>
    </body>
    </html>
    <?php
    exit; // INTERROMPE O SCRIPT AQUI
}
// ------------------------------------------------------------------
// FIM DO BLOQUEIO DE SEGURANÇA
// ------------------------------------------------------------------

$mensagem_status = "";
$sucesso = false;

// Regex para Senha Forte
const REGEX_SENHA_FORTE = '/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[\W_]).{8,}$/';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    // 1. VERIFICAÇÃO CRÍTICA DE CONEXÃO
    if (!isset($pdo)) {
        $mensagem_status = "<h4 class='text-danger'>Erro crítico: Falha na conexão com o banco de dados.</h4>";
        goto exibir_html; 
    }
    
    // 2. Coletar e limpar os dados
    $usuario_novo = trim($_POST['username'] ?? '');
    $email_novo = filter_input(INPUT_POST, 'email', FILTER_VALIDATE_EMAIL);
    $senha_nova = $_POST['password'] ?? '';
    $senha_confirmar = $_POST['confirm_password'] ?? '';
    
    // 3. Validação básica e de segurança da senha
    if (empty($usuario_novo) || empty($senha_nova) || empty($senha_confirmar) || $email_novo === false) {
        $mensagem_status = "<h4 class='text-danger'>Preencha todos os campos corretamente, incluindo um e-mail válido.</h4>";
    } elseif ($senha_nova !== $senha_confirmar) {
        $mensagem_status = "<h4 class='text-danger'>As senhas não coincidem.</h4>";
    } elseif (!preg_match(REGEX_SENHA_FORTE, $senha_nova)) {
        // ESSA VALIDAÇÃO É MANTIDA POR SEGURANÇA (SERVER-SIDE)
        $mensagem_status = "<h4 class='text-danger'>A senha não atende aos requisitos mínimos de segurança (8 digitos, maiúscula, minúscula, número e caractere especial).</h4>";
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

                $sql_insert = "INSERT INTO usuario (usuario, email, senha_hash, papel_id, ativo) 
                                     VALUES (?, ?, ?, ?, 1)";
                
                $stmt_insert = $pdo->prepare($sql_insert);
                
                $execucao_sucesso = $stmt_insert->execute([
                    $usuario_novo, 
                    $email_novo, 
                    $hash_senha, 
                    $papel_id_padrao
                ]);

                if ($execucao_sucesso) {
                    $mensagem_status = "<h4 class='text-success'>✅ Cadastro efetuado com sucesso! Redirecionando para o login...</h4>";
                    $sucesso = true;
                    // Remove a sessão de ADM após o registro para forçar nova autenticação na próxima vez
                    unset($_SESSION['admin_pode_registrar']); 
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
// O restante do HTML SÓ é exibido se a verificação de segurança no topo passar.
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
            
            <?php if ($mensagem_status): ?>
                <div class="status-message text-center mb-4">
                    <?php echo $mensagem_status; ?>
                </div>
            <?php endif; ?>

            <?php 
            if (!$sucesso): ?>
            
                <div class="text-center mb-4">
                    <img src="Logo.jpeg" 
                             alt="Logo PetShop" 
                             class="img-fluid rounded-circle mb-3 logo-borda" 
                             style="max-width: 120px;"> 
                                 
                    <h2 class="card-title">CRIAR CONTA</h2> 
                </div>

                <form action="registrar.php" method="POST" id="registroForm">
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-user"></i></span>
                        <input type="text" name="username" class="form-control" placeholder="Escolha um Usuário" required value="<?php echo htmlspecialchars($usuario_novo ?? ''); // Manter valor preenchido ?>">
                    </div>
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-envelope"></i></span>
                        <input type="email" name="email" class="form-control" placeholder="Digite seu E-mail" required value="<?php echo htmlspecialchars($email_novo ?? ''); // Manter valor preenchido ?>">
                    </div>

                    <div class="mb-3" style="position: relative;">
                        <div class="input-group">
                            <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                            <input type="password" name="password" id="password" class="form-control" placeholder="Digite sua Senha" required value="<?php echo htmlspecialchars($senha_nova ?? ''); // Manter valor preenchido ?>">
                            <span class="input-group-text toggle-password" data-target="password">
                                <i class="fa-solid fa-eye" id="togglePasswordIcon"></i>
                            </span>
                        </div>
                        
                        <div class="password-validation-card" id="validationCard">
                            <p class="text-muted mb-1" style="font-weight: bold;">Requisitos da Senha:</p>
                            <ul class="list-unstyled password-check">
                                <li id="check-length" class="check-item">
                                    <i class="fas fa-times-circle me-1"></i> Mínimo 8 caracteres
                                </li>
                                <li id="check-upper" class="check-item">
                                    <i class="fas fa-times-circle me-1"></i> Pelo menos 1 Letra Maiúscula
                                </li>
                                <li id="check-lower" class="check-item">
                                    <i class="fas fa-times-circle me-1"></i> Pelo menos 1 Letra Minúscula
                                </li>
                                <li id="check-number" class="check-item">
                                    <i class="fas fa-times-circle me-1"></i> Pelo menos 1 Número
                                </li>
                                <li id="check-special" class="check-item">
                                    <i class="fas fa-times-circle me-1"></i> Pelo menos 1 Caractere Especial
                                </li>
                            </ul>
                            <p id="validationWarning" class="text-danger small mt-2" style="display:none;">**A senha não atende a todos os requisitos.**</p>
                        </div>
                    </div>

                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                        <input type="password" name="confirm_password" id="confirm_password" class="form-control" placeholder="Confirme a Senha" required value="<?php echo htmlspecialchars($senha_confirmar ?? ''); // Manter valor preenchido ?>">
                        <span class="input-group-text toggle-password" data-target="confirm_password">
                            <i class="fa-solid fa-eye" id="toggleConfirmIcon"></i>
                        </span>
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
    
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            const passwordInput = document.getElementById('password');
            const confirmPasswordInput = document.getElementById('confirm_password');
            const form = document.getElementById('registroForm');
            const validationCard = document.getElementById('validationCard');
            const validationWarning = document.getElementById('validationWarning'); 
            
            // Elementos do Checklist
            const checkLength = document.getElementById('check-length');
            const checkUpper = document.getElementById('check-upper');
            const checkLower = document.getElementById('check-lower');
            const checkNumber = document.getElementById('check-number');
            const checkSpecial = document.getElementById('check-special');

            // Regex de validação para o Cliente (mesma lógica do PHP)
            const regexUpper = /[A-Z]/;
            const regexLower = /[a-z]/;
            const regexNumber = /[0-9]/;
            const regexSpecial = /[\W_]/; 
            
            // Função de toggle de senha
            document.querySelectorAll('.toggle-password').forEach(toggle => {
                toggle.addEventListener('click', function() {
                    const targetId = this.getAttribute('data-target');
                    const targetInput = document.getElementById(targetId);
                    const icon = this.querySelector('i');
                    
                    if (targetInput.type === 'password') {
                        targetInput.type = 'text';
                        icon.classList.remove('fa-eye');
                        icon.classList.add('fa-eye-slash');
                    } else {
                        targetInput.type = 'password';
                        icon.classList.remove('fa-eye-slash');
                        icon.classList.add('fa-eye');
                    }
                });
            });


            function updateCheckItem(element, isValid) {
                element.classList.toggle('valid', isValid);
                // Mudar o ícone para check-circle ou times-circle
                const iconClass = isValid ? 'fa-check-circle' : 'fa-times-circle';
                const textContent = element.textContent.substring(element.textContent.indexOf(' ') + 1).trim();
                element.innerHTML = `<i class="fas ${iconClass} me-1"></i> ${textContent}`;
            }

            // Função principal de validação em tempo real
            function validatePassword() {
                const senha = passwordInput.value;
                
                const isLengthValid = senha.length >= 8;
                const isUpperValid = regexUpper.test(senha);
                const isLowerValid = regexLower.test(senha);
                const isNumberValid = regexNumber.test(senha);
                const isSpecialValid = regexSpecial.test(senha);

                updateCheckItem(checkLength, isLengthValid);
                updateCheckItem(checkUpper, isUpperValid);
                updateCheckItem(checkLower, isLowerValid);
                updateCheckItem(checkNumber, isNumberValid);
                updateCheckItem(checkSpecial, isSpecialValid);
                
                const isPasswordSecure = isLengthValid && isUpperValid && isLowerValid && isNumberValid && isSpecialValid;
                
                // Exibe ou oculta o aviso de falha na validação
                validationWarning.style.display = isPasswordSecure ? 'none' : 'block';
                
                return isPasswordSecure;
            }

            // EXIBIÇÃO FLUIDA DO CARD
            passwordInput.addEventListener('focus', function() {
                validationCard.style.display = 'block';
                validatePassword(); // Atualiza o status imediatamente
            });

            passwordInput.addEventListener('blur', function() {
                // Oculta o card se a senha estiver válida E o campo de confirmação não estiver focado
                if (validatePassword() && document.activeElement !== confirmPasswordInput) {
                    validationCard.style.display = 'none';
                }
            });
            
            // Oculta o card se estiver visível e o foco for para a confirmação de senha
            confirmPasswordInput.addEventListener('focus', function() {
                if (validationCard.style.display !== 'none') {
                    validationCard.style.display = 'none';
                }
            });

            // Evento de digitação na senha para feedback em tempo real
            passwordInput.addEventListener('keyup', validatePassword);
            passwordInput.addEventListener('change', validatePassword);

            // Validação final ao tentar submeter o formulário (Regra Crítica)
            form.addEventListener('submit', function(e) {
                const isPasswordSecure = validatePassword();
                const senhasCoincidem = passwordInput.value === confirmPasswordInput.value;
                
                // 1. IMPEDE O SUBMIT se a senha não for segura (Atendendo ao Requisito Crítico)
                if (!isPasswordSecure) {
                    alert('ERRO: A senha não atende a todos os requisitos de segurança. Por favor, verifique o checklist.');
                    validationCard.style.display = 'block'; // Mostra o card
                    passwordInput.focus(); // Retorna o foco para o campo de senha
                    e.preventDefault();
                    return;
                }
                
                // 2. IMPEDE O SUBMIT se as senhas não coincidirem
                if (!senhasCoincidem) {
                    alert('ERRO: As senhas digitadas não coincidem.');
                    confirmPasswordInput.focus(); // Retorna o foco
                    e.preventDefault();
                    return;
                }
                
                // Se tudo OK, o formulário é enviado para o PHP
            });
            
            // Chama a validação no carregamento para senhas que já vieram preenchidas do PHP (erro de server-side)
            if (passwordInput.value.length > 0) {
                 validatePassword();
            }
        });
    </script>
</body>
</html>