<?php
session_start();
?>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Acesso ADM - Registro</title>
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

<div class="card login-card mx-auto mt-5">
    <h3 class="card-title text-center"><i class="fas fa-user-shield me-2"></i> Acesso SuperAdmin</h3>
    <p class="text-center text-muted">Apenas SuperAdministradores podem liberar a criação de novas contas.</p>
      
    <div class="text-center mb-4">
        <img src="Logo.jpeg" 
             alt="Logo PetShop" 
             class="img-fluid rounded-circle mb-3 logo-borda" 
             style="max-width: 120px;"> 
    </div>

    <div id="auth-message-area" class="mt-2"></div>

    <form id="form-login-adm" method="POST" action="registrar_processar_login_adm.php">
        
        <div class="mb-3">
            <label for="admin_user" class="form-label">Usuário:</label>
            <input type="text" id="admin_user" name="admin_user" class="form-control" required>
        </div>
        
        <div class="mb-3">
            <label for="admin_pass" class="form-label">Senha:</label>
            <input type="password" id="admin_pass" name="admin_pass" class="form-control" required>
        </div>
        
        <button type="submit" class="btn btn-primary w-100">
            <i class="fas fa-lock-open me-2"></i> Liberar Cadastro
        </button>
    </form>
    
    <div class="links text-center mt-3">
        <a href="login.php" class="d-block text-muted">Voltar para a tela de Login</a>
    </div>
</div>

<script>
    document.getElementById('form-login-adm').addEventListener('submit', function(e) {
        e.preventDefault();
        
        const form = e.target;
        const formData = new FormData(form);
        const authMessageArea = document.getElementById('auth-message-area');
        authMessageArea.innerHTML = '';
        
        fetch(form.action, {
            method: 'POST',
            body: formData
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const mainContentArea = document.getElementById('conteudo-principal') || document.body;
                
                fetch('registrar.php') 
                    .then(response => response.text())
                    .then(html => {
                        mainContentArea.innerHTML = html;
                    })
                    .catch(err => {
                        mainContentArea.innerHTML = `<div class="alert alert-danger">Erro ao carregar o formulário de registro.</div>`;
                    });

            } else {
                authMessageArea.innerHTML = `<div class="alert alert-danger text-center">${data.message}</div>`;
            }
        })
        .catch(error => {
            authMessageArea.innerHTML = `<div class="alert alert-danger text-center">Erro de comunicação com o servidor.</div>`;
        });
    });
</script>
</body>
</html>
