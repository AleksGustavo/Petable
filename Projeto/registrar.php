<?php
// Arquivo: registrar.php (Versão Final com PDO)

session_start();
require_once 'conexao.php'; // Garante que a variável $pdo está disponível

$mensagem_status = "";

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    // 1. VERIFICAÇÃO CRÍTICA DE CONEXÃO
    // Se a conexão falhou em conexao.php, $pdo não existe
    if (!isset($pdo)) {
        $mensagem_status = "<h4 class='text-danger'>Erro crítico: Falha na conexão com o banco de dados.</h4>";
        // Usa goto para pular o processamento e ir direto para a exibição HTML
        goto exibir_html; 
    }
    
    // 2. Coletar e limpar os dados
    $usuario_novo = trim($_POST['username'] ?? '');
    $senha_nova = $_POST['password'] ?? '';
    $senha_confirmar = $_POST['confirm_password'] ?? '';
    
    // 3. Validação básica
    if (empty($usuario_novo) || empty($senha_nova) || empty($senha_confirmar)) {
        $mensagem_status = "<h4 class='text-danger'>Preencha todos os campos.</h4>";
    } elseif ($senha_nova !== $senha_confirmar) {
        $mensagem_status = "<h4 class='text-danger'>As senhas não coincidem.</h4>";
    } elseif (strlen($senha_nova) < 6) {
        $mensagem_status = "<h4 class='text-danger'>A senha deve ter pelo menos 6 caracteres.</h4>";
    } else {
        
        try {
            // 4. Verifica se o usuário já existe (PDO)
            $sql_check = "SELECT id FROM usuario WHERE usuario = ?";
            $stmt_check = $pdo->prepare($sql_check);
            $stmt_check->execute([$usuario_novo]);
            
            if ($stmt_check->rowCount() > 0) {
                $mensagem_status = "<h4 class='text-danger'>Usuário '$usuario_novo' já existe. Escolha outro.</h4>";
            } else {
                
                // 5. Cria o hash seguro da senha (BCRYPT)
                $hash_senha = password_hash($senha_nova, PASSWORD_DEFAULT);
                
                // 6. Insere o novo usuário (PDO)
                
                // NOTA: Sua tabela 'usuarios' exige 'email' e 'papel_id' (papel_id deve ser FOREIGN KEY)
                // Usei um email padrão e papel_id=2 (Funcionário Vendas/Comum) como suposição. AJUSTE o ID do papel se necessário!
                $email_padrao = $usuario_novo . '@petshop.com';
                $papel_id_padrao = 2; // ID do papel para novos registros (Ex: 'FuncionarioVendas')

                $sql_insert = "INSERT INTO usuario (usuario, senha_hash, email, papel_id, ativo) 
                               VALUES (?, ?, ?, ?, 1)";
                
                $stmt_insert = $pdo->prepare($sql_insert);
                
                $execucao_sucesso = $stmt_insert->execute([
                    $usuario_novo, 
                    $hash_senha, 
                    $email_padrao, 
                    $papel_id_padrao
                ]);

                if ($execucao_sucesso) {
                    $mensagem_status = "<h4 class='text-success'>Cadastro efetuado com sucesso! Redirecionando para o login...</h4>";
                    header("Refresh: 3; URL=login.php"); 
                    // NÃO USE exit() aqui para permitir que o Refresh funcione enquanto a mensagem é exibida
                    
                } else {
                    $mensagem_status = "<h4 class='text-danger'>Erro ao cadastrar. Falha na execução da query.</h4>";
                }
            }
        
        } catch (PDOException $e) {
            // Captura erros de SQL ou de Prepared Statement
            $mensagem_status = "<h4 class='text-danger'>Erro no servidor ao tentar registrar: " . $e->getMessage() . "</h4>";
        }
    }
}

// O ponto de exibição HTML após o processamento
exibir_html: 
// Não há mais necessidade de fechar a conexão no PDO
?>

<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Registro - Novo Usuário</title>
    
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH" crossorigin="anonymous">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
    <link rel="stylesheet" href="css/Style.css"> 
</head>
<body class="bg-dark d-flex justify-content-center align-items-center min-vh-100">
    <div class="card p-4 shadow-lg login-container">
        <div class="card-body">
            
            <h2 class="card-title text-center mb-4">Criar Conta</h2> 
            
            <?php if ($mensagem_status): ?>
                <div class="status-message text-center mb-4">
                    <?php echo $mensagem_status; ?>
                </div>
            <?php endif; ?>

            <?php 
            // Só exibe o formulário se o cadastro não foi um sucesso com redirecionamento ativo
            if (strpos($mensagem_status, 'Cadastro efetuado com sucesso') === false): ?>

            <form action="registrar.php" method="POST">
                <div class="mb-3 input-group">
                    <span class="input-group-text"><i class="fa-solid fa-user"></i></span>
                    <input type="text" name="username" class="form-control" placeholder="Escolha um Usuário" required>
                </div>
                
                <div class="mb-3 input-group">
                    <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                    <input type="password" name="password" class="form-control" placeholder="Digite sua Senha" required>
                </div>

                <div class="mb-3 input-group">
                    <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                    <input type="password" name="confirm_password" class="form-control" placeholder="Confirme a Senha" required>
                </div>

                <button type="submit" class="btn btn-success w-100 mt-2">CADASTRAR</button>
            </form>

            <div class="links text-center mt-3">
                <a href="login.php" class="d-block text-muted">Já tenho uma conta (Fazer Login)</a>
            </div>

            <?php endif; ?>
        </div>
    </div> 
    
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js" integrity="sha384-YvpcrYf0tY3lHB60NNkmXc5s9fDVZLESaAA55NDzOxhy9GkcIdslK1eN7N6jIeHz" crossorigin="anonymous"></script>
</body>
</html>