<?php
<<<<<<< HEAD
// Arquivo: registrar_autenticar_adm.php
// Tela de login que aparece antes do formulário de registro de novos usuários.

// Inicia a sessão (necessário para o processo de autenticação do ADM).
=======
>>>>>>> 23e8a940afaddaa7bf552ddc3a93d92140b2b2d0
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
<<<<<<< HEAD

    <style>
        /* TEMA PET SHOP: Bege Aconchegante e Marrom Caramelo */

        /* Fundo com Patinhas (Marca D'água) */
        body {
            /* Bege Aconchegante */
            background-color: #FAFAF5;
=======
    
    <style>
        body {
            background-color: #FAFAF5; 
>>>>>>> 23e8a940afaddaa7bf552ddc3a93d92140b2b2d0
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
<<<<<<< HEAD

            /* Efeito Patinhas Sutil (via CSS) */
            background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><path fill="%23EFEFEA" d="M 50 20 L 70 30 L 60 50 L 80 60 L 60 70 L 40 60 L 50 80 L 30 70 L 40 50 L 20 60 L 30 30 Z M 50 20 C 45 15, 55 15, 50 20 Z M 35 35 C 30 30, 40 30, 35 35 Z M 65 35 C 60 30, 70 30, 65 35 Z M 35 65 C 30 60, 40 60, 35 65 Z M 65 65 C 60 60, 70 60, 65 65 Z"/></svg>');
            background-size: 80px;
            /* Tamanho da patinha */
            background-repeat: repeat;
            opacity: 0.9;
            /* Deixa o fundo opaco */
=======
            background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><path fill="%23EFEFEA" d="M 50 20 L 70 30 L 60 50 L 80 60 L 60 70 L 40 60 L 50 80 L 30 70 L 40 50 L 20 60 L 30 30 Z M 50 20 C 45 15, 55 15, 50 20 Z M 35 35 C 30 30, 40 30, 35 35 Z M 65 35 C 60 30, 70 30, 65 35 Z M 35 65 C 30 60, 40 60, 35 65 Z M 65 65 C 60 60, 70 60, 65 65 Z"/></svg>');
            background-size: 80px;
            background-repeat: repeat;
            opacity: 0.9;
>>>>>>> 23e8a940afaddaa7bf552ddc3a93d92140b2b2d0
        }

        .login-card {
            max-width: 400px;
            width: 90%;
            padding: 2rem;
<<<<<<< HEAD
            box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
            /* Sombra mais destacada */
            background-color: #fff;
            /* Fundo branco para contraste */
            border-radius: 10px;
        }

        /* Botão Primário (Marrom Caramelo) */
        .btn-primary,
        .login-btn {
            background-color: #964B00 !important;
            /* Marrom Caramelo */
=======
            box-shadow: 0 4px 15px rgba(0,0,0,0.15);
            background-color: #fff;
            border-radius: 10px;
        }

        .btn-primary, .login-btn {
            background-color: #964B00 !important;
>>>>>>> 23e8a940afaddaa7bf552ddc3a93d92140b2b2d0
            border-color: #964B00 !important;
            font-weight: bold;
            letter-spacing: 0.5px;
            transition: background-color 0.3s;
        }

<<<<<<< HEAD
        .btn-primary:hover,
        .login-btn:hover {
            background-color: #703600 !important;
            /* Marrom mais escuro no hover */
            border-color: #604d3cff !important;
        }

        /* Detalhe da Logo */
=======
        .btn-primary:hover, .login-btn:hover {
            background-color: #703600 !important;
            border-color: #604d3cff !important;
        }

>>>>>>> 23e8a940afaddaa7bf552ddc3a93d92140b2b2d0
        .logo-borda {
            border: 3px solid #964B00 !important;
        }

        /* Estilo para o ícone de mostrar/ocultar senha */
        .toggle-password {
            cursor: pointer;
            user-select: none;
            /* Impede seleção de texto */
        }
    </style>
</head>

<body>

<<<<<<< HEAD
    <div class="card login-card mx-auto mt-5">

        <div class="text-center mb-4">
            <img src="Logo.jpeg"
                alt="Logo PetShop"
                class="img-fluid rounded-circle mb-3 logo-borda"
                style="max-width: 120px;">
        </div>
        <h3 class="card-title text-center"><i class="fas fa-user-shield me-2"></i> Acesso SuperAdmin</h3>
        <p class="text-center text-muted">Apenas SuperAdministradores podem liberar a criação de novas contas.</p>

        <div id="auth-message-area" class="mt-2">
        </div>
=======
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
>>>>>>> 23e8a940afaddaa7bf552ddc3a93d92140b2b2d0

        <form id="form-login-adm" method="POST" action="registrar_processar_login_adm.php">

            <div class="mb-3 input-group">
                <span class="input-group-text"><i class="fa-solid fa-user"></i></span>
                <input type="text" id="admin_user" name="admin_user" class="form-control" placeholder="Usuário ADM" required>
            </div>

            <div class="mb-3 input-group">
                <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                <input type="password" id="adminPasswordField" name="admin_pass" class="form-control" placeholder="Senha ADM" required>

                <span class="input-group-text toggle-password" id="toggleAdminPassword">
                    <i class="fas fa-eye"></i>
                </span>
            </div>

            <button type="submit" class="btn btn-primary w-100">
                <i class="fas fa-lock-open me-2"></i> Liberar Cadastro
            </button>
        </form>

        <div class="links text-center mt-3">
            <a href="login.php" class="d-block text-muted">Voltar para a tela de Login</a>
        </div>
    </div>

<<<<<<< HEAD
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
=======
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
>>>>>>> 23e8a940afaddaa7bf552ddc3a93d92140b2b2d0

    <script>
        // Obtém a referência ao campo de senha pelo ID
        const passwordField = document.getElementById('adminPasswordField');
        // Obtém a referência ao ícone de olho pelo ID
        const togglePassword = document.getElementById('toggleAdminPassword');
        // Obtém a referência ao elemento do ícone Font Awesome dentro do span
        const toggleIcon = togglePassword.querySelector('i');

        // Adiciona um listener de evento de clique ao ícone de olho
        togglePassword.addEventListener('click', function() {
            // Verifica o tipo atual do campo: se for 'password', muda para 'text'; se for 'text', volta para 'password'.
            const type = passwordField.getAttribute('type') === 'password' ? 'text' : 'password';
            passwordField.setAttribute('type', type);

            // Alterna o ícone: se a senha for visível (type='text'), mostra o olho riscado ('fa-eye-slash');
            // caso contrário, mostra o olho normal ('fa-eye').
            if (type === 'text') {
                toggleIcon.classList.remove('fa-eye');
                toggleIcon.classList.add('fa-eye-slash');
            } else {
<<<<<<< HEAD
                toggleIcon.classList.remove('fa-eye-slash');
                toggleIcon.classList.add('fa-eye');
=======
                authMessageArea.innerHTML = `<div class="alert alert-danger text-center">${data.message}</div>`;
>>>>>>> 23e8a940afaddaa7bf552ddc3a93d92140b2b2d0
            }
        });
    </script>

    <script>
        // Adiciona um listener para a submissão do formulário ADM
        document.getElementById('form-login-adm').addEventListener('submit', function(e) {
            e.preventDefault(); // Impede o envio tradicional do formulário

            const form = e.target;
            const formData = new FormData(form);
            const authMessageArea = document.getElementById('auth-message-area');
            authMessageArea.innerHTML = ''; // Limpa mensagens anteriores

            // 1. Envia as credenciais ADM para o processador (registrar_processar_login_adm.php)
            fetch(form.action, {
                    method: 'POST',
                    body: formData
                })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        // 2. Login ADM bem-sucedido: Carrega o conteúdo do formulário de registro real (registrar.php)
                        // Se o corpo da página for a área principal, usa document.body.
                        const mainContentArea = document.getElementById('conteudo-principal') || document.body;

                        // Busca o conteúdo HTML da página de registro, que agora pode ser acessada
                        fetch('registrar.php')
                            .then(response => response.text())
                            .then(html => {
                                // Substitui o conteúdo da área principal pelo formulário de registro
                                mainContentArea.innerHTML = html;
                            })
                            .catch(err => {
                                // Exibe erro se o carregamento do formulário falhar
                                authMessageArea.innerHTML = `<div class="alert alert-danger text-center">Erro ao carregar o formulário de registro.</div>`;
                            });

                    } else {
                        // 3. Login ADM falhou: Exibe a mensagem de erro retornada pelo servidor
                        authMessageArea.innerHTML = `<div class="alert alert-danger text-center">${data.message}</div>`;
                    }
                })
                .catch(error => {
                    // Exibe erro de comunicação (ex: servidor fora do ar)
                    authMessageArea.innerHTML = `<div class="alert alert-danger text-center">Erro de comunicação com o servidor.</div>`;
                });
        });
    </script>
</body>
<<<<<<< HEAD

</html>
=======
</html>
>>>>>>> 23e8a940afaddaa7bf552ddc3a93d92140b2b2d0
