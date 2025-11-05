<?php
// 1. Inicia a sessão
// É CRÍTICO iniciar a sessão antes de usar session_unset() ou session_destroy()
session_start();

// 2. Limpa todas as variáveis de sessão
// Remove todos os dados armazenados no $_SESSION
$_SESSION = array();

// 3. Destrói a sessão atual
// Remove o arquivo de sessão do servidor
session_destroy();

// 4. Redireciona o usuário
// Redireciona o usuário para a página inicial (index.html) ou página de login.
// Recomendamos a página inicial para que ele possa começar um novo serviço ou login.
header("Location: login.php");

// Garante que o script pare de rodar imediatamente após o redirecionamento
exit;
?>