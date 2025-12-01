<?php
session_start();
require_once 'conexao.php';

$acesso_liberado = false;

if (isset($_SESSION['admin_pode_registrar']) && $_SESSION['admin_pode_registrar'] === true) {
    
    if (isset($_SESSION['admin_auth_time']) && time() < $_SESSION['admin_auth_time']) {
        $acesso_liberado = true;
    } else {
        unset($_SESSION['admin_pode_registrar']);
        unset($_SESSION['admin_auth_time']);
    }
}


if (!$acesso_liberado) {
    
    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $mensagem_status = "<h4 class='text-danger'>Acesso negado. A sessão de SuperAdmin expirou ou é inválida. Tente o login novamente.</h4>";
        goto exibir_html; 
    }
    
    header('Content-Type: text/html'); 
    ?>
    <!DOCTYPE html>
    <html lang="pt-BR">
    <head>
        <title>Acesso Negado</title>
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
            max-width: 400px; 
            width: 90%; 
            padding: 2rem;
            box-shadow: 0 4px 15px rgba(0,0,0,0.15); 
            background-color: #fff; 
            border-radius: 10px;
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
        <div class="alert alert-danger text-center mt-5 p-4 shadow mx-auto" style="max-width: 450px;">
            <h2><i class="fas fa-lock me-2"></i> Acesso Negado!</h2>
            <p>O formulário de criação de contas está bloqueado. Apenas um SuperAdmin pode liberá-lo.</p>
            
            <a href="registrar_autenticar_adm.php" class="btn btn-warning mt-3">
                <i class="fas fa-user-shield"></i> Entra como Administrador
            </a>
            <a href="login.php" class="btn btn-secondary mt-3">
                <i class="fas fa-sign-in-alt"></i> Voltar ao Login
            </a>
        </div>
    </body>
    </html>
    <?php
    exit; 
}


$mensagem_status = "";
$sucesso = false;

const REGEX_SENHA_FORTE = '/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[\W_]).{8,}$/';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    if (!isset($pdo)) {
        $mensagem_status = "<h4 class='text-danger'>Erro crítico: Falha na conexão com o banco de dados.</h4>";
        goto exibir_html; 
    }
    
    $usuario_novo = trim($_POST['username'] ?? '');
    $email_novo = filter_input(INPUT_POST, 'email', FILTER_VALIDATE_EMAIL);
    $senha_nova = $_POST['password'] ?? '';
    $senha_confirmar = $_POST['confirm_password'] ?? '';
    
    if (empty($usuario_novo) || empty($senha_nova) || empty($senha_confirmar) || $email_novo === false) {
        $mensagem_status = "<h4 class='text-danger'>Preencha todos os campos corretamente, incluindo um e-mail válido.</h4>";
    } elseif ($senha_nova !== $senha_confirmar) {
        $mensagem_status = "<h4 class='text-danger'>As senhas não coincidem.</h4>";
    } elseif (!preg_match(REGEX_SENHA_FORTE, $senha_nova)) {
        $mensagem_status = "<h4 class='text-danger'>A senha não atende aos requisitos mínimos de segurança (8 digitos, maiúscula, minúscula, número e caractere especial).</h4>";
    } else {
        
        try {
            $sql_check = "SELECT id FROM usuario WHERE usuario = ? OR email = ?";
            $stmt_check = $pdo->prepare($sql_check);
            $stmt_check->execute([$usuario_novo, $email_novo]);
            
            if ($stmt_check->rowCount() > 0) {
                $mensagem_status = "<h4 class='text-danger'>Usuário ou E-mail já existe. Escolha credenciais diferentes.</h4>";
            } else {
                
                $hash_senha = password_hash($senha_nova, PASSWORD_DEFAULT);
                
                $papel_id_padrao = 2; 

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
                    
                    if (isset($_SESSION['admin_pode_registrar'])) {
                        unset($_SESSION['admin_pode_registrar']); 
                        unset($_SESSION['admin_auth_time']);
                    }
                    
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
            max-width: 450px; 
            width: 90%; 
            padding: 2rem;
            box-shadow: 0 4px 15px rgba(0,0,0,0.15); 
            background-color: #fff; 
            border-radius: 10px;
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
            border-color: #703600 !important;
        }
        
        .logo-borda {
             border: 3px solid #795548 !important;
             box-shadow: 0 0 10px rgba(121, 85, 72, 0.7);
        }
        
        .password-validation-card {
            position: absolute;
            width: 100%; 
            background-color: #fff;
            border: 1px solid #ccc;
            border-radius: 5px;
            padding: 10px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            z-index: 10;
            margin-top: 5px;
            display: none;
        }

        .password-check {
            font-size: 0.85rem;
            padding: 0;
            margin: 0;
        }

        .check-item {
            color: #dc3545; 
            transition: color 0.3s;
            margin-bottom: 2px;
        }

        .check-item.valid {
            color: #198754; 
        }
        
        .toggle-password {
            cursor: pointer;
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
                        <input type="text" name="username" class="form-control" placeholder="Escolha um Usuário" required value="<?php echo htmlspecialchars($usuario_novo ?? ''); ?>">
                    </div>
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-envelope"></i></span>
                        <input type="email" name="email" class="form-control" placeholder="Digite seu E-mail" required value="<?php echo htmlspecialchars($email_novo ?? ''); ?>">
                    </div>

                    <div class="mb-3" style="position: relative;">
                        <div class="input-group">
                            <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                            <input type="password" name="password" id="password" class="form-control" placeholder="Digite sua Senha" required value="<?php echo htmlspecialchars($senha_nova ?? ''); ?>">
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
                        <input type="password" name="confirm_password" id="confirm_password" class="form-control" placeholder="Confirme a Senha" required value="<?php echo htmlspecialchars($senha_confirmar ?? ''); ?>">
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
            
            const checkLength = document.getElementById('check-length');
            const checkUpper = document.getElementById('check-upper');
            const checkLower = document.getElementById('check-lower');
            const checkNumber = document.getElementById('check-number');
            const checkSpecial = document.getElementById('check-special');

            const regexUpper = /[A-Z]/;
            const regexLower = /[a-z]/;
            const regexNumber = /[0-9]/;
            const regexSpecial = /[\W_]/; 
            
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
                const iconClass = isValid ? 'fa-check-circle' : 'fa-times-circle';
                const textContent = element.textContent.substring(element.textContent.indexOf(' ') + 1).trim();
                element.innerHTML = `<i class="fas ${iconClass} me-1"></i> ${textContent}`;
            }

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
                
                validationWarning.style.display = isPasswordSecure ? 'none' : 'block';
                
                return isPasswordSecure;
            }

            passwordInput.addEventListener('focus', function() {
                validationCard.style.display = 'block';
                validatePassword(); 
            });

            passwordInput.addEventListener('blur', function() {
                if (validatePassword() && document.activeElement !== confirmPasswordInput) {
                    validationCard.style.display = 'none';
                }
            });
            
            confirmPasswordInput.addEventListener('focus', function() {
                if (validationCard.style.display !== 'none') {
                    validationCard.style.display = 'none';
                }
            });

            passwordInput.addEventListener('keyup', validatePassword);
            passwordInput.addEventListener('change', validatePassword);

            form.addEventListener('submit', function(e) {
                const isPasswordSecure = validatePassword();
                const senhasCoincidem = passwordInput.value === confirmPasswordInput.value;
                
                if (!isPasswordSecure) {
                    alert('ERRO: A senha não atende a todos os requisitos de segurança. Por favor, verifique o checklist.');
                    validationCard.style.display = 'block'; 
                    passwordInput.focus(); 
                    e.preventDefault();
                    return;
                }
                
                if (!senhasCoincidem) {
                    alert('ERRO: As senhas digitadas não coincidem.');
                    confirmPasswordInput.focus(); 
                    e.preventDefault();
                    return;
                }
            });
            
            if (passwordInput.value.length > 0) {
                 validatePassword();
            }
        });
    </script>
</body>
</html>