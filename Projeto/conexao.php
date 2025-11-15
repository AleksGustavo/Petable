<?php
// Arquivo: conexao.php - Conexão Dupla (MySQLi para $conexao e PDO para $pdo)

// Configurações do Banco de Dados
// Se a porta for a padrão (3306), use apenas 'localhost'.
// Se o seu MySQL estiver em outra porta, o tratamento é diferente.
$host = 'localhost'; // CORRIGIDO: Removida a porta :3306
$user = 'root';
$pass = ''; 
$banco = 'petshop_db'; 
$port = 3307; // Adicionado para uso opcional, mas essencial para o PDO DSN.

// --- 1. CONEXÃO MySQLi (Variável: $conexao) ---
// Note o uso da variável $port no 5º argumento, se necessário. Se for 3306,
// o MySQLi é inteligente o suficiente para conectar sem ela se o $host for 'localhost'.
$conexao = @mysqli_connect($host, $user, $pass, $banco, $port); // Usando $port

if (!$conexao) {
    // Melhorar o erro para diagnóstico
    die("Erro crítico: Falha na conexão MySQLi com o banco de dados. Verifique o host/porta. Detalhes: " . mysqli_connect_error()); 
}
mysqli_set_charset($conexao, "utf8mb4");


// --- 2. CONEXÃO PDO (Variável: $pdo) ---
// O PDO DSN AGORA usa a variável $host e a variável $port separadamente
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