<?php
// Arquivo: conexao.php - Conexão Dupla (MySQLi para $conexao e PDO para $pdo)

$host = 'localhost'; 
$user = 'root';
$pass = ''; 
<<<<<<< HEAD
$banco = 'petshop_db'; 
$port = 3307; // Adicionado para uso opcional, mas essencial para o PDO DSN.
=======
$banco = 'pet_e_pet_db'; 
$port = 3306; 
>>>>>>> 23e8a940afaddaa7bf552ddc3a93d92140b2b2d0

// --- 1. CONEXÃO MySQLi 
$conexao = @mysqli_connect($host, $user, $pass, $banco, $port); 

if (!$conexao) {
    
    die("Erro crítico: Falha na conexão MySQLi com o banco de dados. Verifique o host/porta. Detalhes: " . mysqli_connect_error()); 
}
mysqli_set_charset($conexao, "utf8mb4");


// --- 2. CONEXÃO PDO 
$dsn = "mysql:host={$host};port={$port};dbname={$banco};charset=utf8mb4";
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