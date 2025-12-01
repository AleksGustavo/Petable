<?php
// Arquivo temporário: gerar_hash.php

$senha_texto_simples = '@ADMMARCOS'; 

// Gera o hash BCRYPT, o mesmo método usado no seu registrar.php
$hash_seguro = password_hash($senha_texto_simples, PASSWORD_DEFAULT);

echo "<h2>NOVO HASH GERADO:</h2>";
echo "<p>Senha em texto simples: <strong>" . htmlspecialchars($senha_texto_simples) . "</strong></p>";
echo "<p>HASH BCRYPT: <strong style='color: red;'>" . htmlspecialchars($hash_seguro) . "</strong></p>";
echo "<hr>";
echo "<p>Agora, copie o HASH BCRYPT (vermelho) e cole-o diretamente no campo <strong>senha_hash</strong> do SuperAdmin na sua tabela <strong>usuario</strong>.</p>";
?>