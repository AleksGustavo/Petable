<?php
// Arquivo: conexao.php - Conexão Dupla (MySQLi para $conexao e PDO para $pdo)

$host = 'localhost';
$user = 'root';
$pass = '8AcZp6dmS'; 
$banco = 'petshop_db'; 

$conexao = @mysqli_connect($host, $user, $pass, $banco);

if (!$conexao) {
    die("Erro crítico: Falha na conexão MySQLi com o banco de dados. Verifique o host/porta. Detalhes: " . mysqli_connect_error()); 
}
mysqli_set_charset($conexao, "utf8mb4");

$dsn = "mysql:host={$host};dbname={$banco};charset=utf8mb4";
$options = [
    PDO::ATTR_ERRMODE  => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES => false,
];
try {
     $pdo = new PDO($dsn, $user, $pass, $options);
} catch (\PDOException $e) {
     error_log("Falha na conexão PDO: " . $e->getMessage());
     $pdo = null; 
}
?>