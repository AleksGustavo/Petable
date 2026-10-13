<?php
session_start();
require_once 'conexao.php';

$acesso_liberado = false;

// Verifica se o administrador liberou o acesso
if (isset($_SESSION['admin_pode_registrar']) && $_SESSION['admin_pode_registrar'] === true) {
    if (isset($_SESSION['admin_auth_time']) && time() < $_SESSION['admin_auth_time']) {
        $acesso_liberado = true;
    } else {
        unset($_SESSION['admin_pode_registrar'], $_SESSION['admin_auth_time']);
    }
}

// Se não houver permissão, redireciona para a autenticação do ADM
if (!$acesso_liberado) {
    header('Location: registrar_autenticar_adm.php');
    exit;
}

$mensagem_status = "";
$sucesso = false;

// Regex para os requisitos: 8+ caracteres, Maiúscula, Minúscula, Número e Especial
const REGEX_REQUISITOS = '/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[\W_]).{8,}$/';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $usuario_novo = trim($_POST['username'] ?? '');
    $email_novo = filter_input(INPUT_POST, 'email', FILTER_VALIDATE_EMAIL);
    $senha_nova = $_POST['password'] ?? '';
    $senha_confirmar = $_POST['confirm_password'] ?? '';
    
    if (empty($usuario_novo) || empty($senha_nova) || !$email_novo) {
        $mensagem_status = "<div class='alert alert-danger'>Preencha todos os campos corretamente.</div>";
    } elseif ($senha_nova !== $senha_confirmar) {
        $mensagem_status = "<div class='alert alert-danger'>As senhas não coincidem.</div>";
    } elseif (!preg_match(REGEX_REQUISITOS, $senha_nova)) {
        $mensagem_status = "<div class='alert alert-danger'>A senha não atende aos critérios de segurança.</div>";
    } else {
        try {
            $sql_check = "SELECT id FROM usuario WHERE usuario = ? OR email = ?";
            $stmt_check = $pdo->prepare($sql_check);
            $stmt_check->execute([$usuario_novo, $email_novo]);
            
            if ($stmt_check->rowCount() > 0) {
                $mensagem_status = "<div class='alert alert-danger'>Usuário ou E-mail já existe.</div>";
            } else {
                $hash_senha = password_hash($senha_nova, PASSWORD_DEFAULT);
                $sql_insert = "INSERT INTO usuario (usuario, email, senha_hash, papel_id, ativo) VALUES (?, ?, ?, 2, 1)";
                $stmt_insert = $pdo->prepare($sql_insert);
                if ($stmt_insert->execute([$usuario_novo, $email_novo, $hash_senha])) {
                    $mensagem_status = "<div class='alert alert-success'>✅ Conta criada com sucesso!</div>";
                    $sucesso = true;
                    header("Refresh: 2; URL=login.php"); 
                }
            }
        } catch (PDOException $e) {
            $mensagem_status = "<div class='alert alert-danger'>Erro no banco de dados.</div>";
        }
    }
}
?>

<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Registro - PetShop</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
    <style>
        body { background-color: #FAFAF5; display: flex; justify-content: center; align-items: center; min-height: 100vh; background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><path fill="%23EFEFEA" d="M 50 20 L 70 30 L 60 50 L 80 60 L 60 70 L 40 60 L 50 80 L 30 70 L 40 50 L 20 60 L 30 30 Z"/></svg>'); background-size: 80px; }
        .login-card { max-width: 450px; width: 95%; padding: 2rem; box-shadow: 0 4px 15px rgba(0,0,0,0.15); background-color: #fff; border-radius: 12px; }
        .btn-primary { background-color: #964B00 !important; border: none; font-weight: bold; }
        .btn-primary:hover { background-color: #703600 !important; }
        .logo-borda { border: 3px solid #795548 !important; box-shadow: 0 0 10px rgba(121, 85, 72, 0.3); }
        
        /* Checklist de Requisitos */
        .password-validation-card { 
            background-color: #fdfdfd; 
            border: 1px solid #ddd; 
            border-radius: 8px; 
            padding: 15px; 
            margin-top: 10px; 
            display: none; 
        }
        .check-item { font-size: 0.85rem; color: #dc3545; margin-bottom: 5px; display: flex; align-items: center; }
        .check-item.valid { color: #198754; font-weight: bold; }
        .check-item i { margin-right: 8px; }
        .toggle-password { cursor: pointer; }

        /* Estilo do link sublinhado */
        .link-login-entrar {
            text-decoration: underline !important;
            color: #6c757d;
            font-size: 0.875rem;
            transition: color 0.2s;
        }
        .link-login-entrar:hover {
            color: #333;
        }
    </style>
</head>
<body>

    <div class="card login-card">
        <div class="card-body">
            <div id="status-msg"><?php echo $mensagem_status; ?></div>

            <?php if (!$sucesso): ?>
                <div class="text-center mb-4">
                    <img src="Logo.jpeg" class="img-fluid rounded-circle mb-3 logo-borda" style="max-width: 110px;"> 
                    <h2 class="card-title">CRIAR CONTA</h2> 
                </div>

                <form action="registrar.php" method="POST" id="registroForm">
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-user"></i></span>
                        <input type="text" name="username" class="form-control" placeholder="Usuário" required>
                    </div>
                    
                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-envelope"></i></span>
                        <input type="email" name="email" class="form-control" placeholder="E-mail" required>
                    </div>

                    <div class="mb-3">
                        <div class="input-group">
                            <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                            <input type="password" name="password" id="password" class="form-control" placeholder="Senha" required autocomplete="off">
                            <span class="input-group-text toggle-password" data-target="password"><i class="fa-solid fa-eye"></i></span>
                        </div>
                        
                        <div class="password-validation-card" id="validationCard">
                            <p class="small text-muted mb-2">A sua senha deve conter:</p>
                            <div id="req-len" class="check-item"><i class="fas fa-times-circle"></i> Mínimo de 8 caracteres</div>
                            <div id="req-up" class="check-item"><i class="fas fa-times-circle"></i> Pelo menos 1 letra Maiúscula</div>
                            <div id="req-low" class="check-item"><i class="fas fa-times-circle"></i> Pelo menos 1 letra Minúscula</div>
                            <div id="req-num" class="check-item"><i class="fas fa-times-circle"></i> Pelo menos 1 número</div>
                            <div id="req-spc" class="check-item"><i class="fas fa-times-circle"></i> Caractere especial (@#$!%&)</div>
                        </div>
                    </div>

                    <div class="mb-3 input-group">
                        <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                        <input type="password" name="confirm_password" id="confirm_password" class="form-control" placeholder="Confirme a Senha" required>
                    </div>

                    <button type="submit" class="btn btn-primary w-100 py-2">CADASTRAR</button>
                </form>

                <div class="text-center mt-3">
                    <a href="login.php" class="link-login-entrar">Já tem conta? Entrar</a>
                </div>
            <?php endif; ?>
        </div>
    </div> 

    <script>
        document.addEventListener('DOMContentLoaded', function() {
            const pwd = document.getElementById('password');
            const card = document.getElementById('validationCard');
            const form = document.getElementById('registroForm');

            // CORREÇÃO: Mostrar no exato momento do clique ou foco
            const showCard = () => card.style.display = 'block';
            pwd.addEventListener('mousedown', showCard);
            pwd.addEventListener('focus', showCard);

            // Validação em tempo real
            pwd.addEventListener('input', function() {
                const val = pwd.value;
                
                updateStatus('req-len', val.length >= 8);
                updateStatus('req-up', /[A-Z]/.test(val));
                updateStatus('req-low', /[a-z]/.test(val));
                updateStatus('req-num', /[0-9]/.test(val));
                updateStatus('req-spc', /[\W_]/.test(val));
            });

            function updateStatus(id, isValid) {
                const el = document.getElementById(id);
                if (!el) return;
                const icon = el.querySelector('i');
                if (isValid) {
                    el.classList.add('valid');
                    icon.className = 'fas fa-check-circle';
                } else {
                    el.classList.remove('valid');
                    icon.className = 'fas fa-times-circle';
                }
            }

            // Alternar visibilidade da senha
            document.querySelectorAll('.toggle-password').forEach(btn => {
                btn.addEventListener('click', function() {
                    const input = document.getElementById(this.dataset.target);
                    const icon = this.querySelector('i');
                    input.type = input.type === 'password' ? 'text' : 'password';
                    icon.classList.toggle('fa-eye');
                    icon.classList.toggle('fa-eye-slash');
                });
            });

            form.addEventListener('submit', function(e) {
                const invalidos = document.querySelectorAll('.check-item:not(.valid)');
                if (invalidos.length > 0) {
                    e.preventDefault();
                    alert('Sua senha não cumpre todos os requisitos.');
                }
            });
        });
    </script>
</body>
</html>