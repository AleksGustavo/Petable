<?php

header('Content-Type: application/json');

require_once 'conexao.php';

$response = ['existe_duplicidade' => false];

if (!isset($pdo) || !($pdo instanceof PDO)) {
    error_log("Erro: Variável \$pdo (PDO object) não está definida em clientes_validar_duplicidade.php");
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['campo'], $_POST['valor'])) {
    
    $campo = $_POST['campo'];
    $coluna_db = ($campo === 'celular') ? 'telefone' : $campo; 

    $valor = preg_replace('/\D/', '', $_POST['valor']); 

    if ($coluna_db === 'cpf' && strlen($valor) === 11) {
        
        $sql = "SELECT COUNT(*) FROM cliente WHERE cpf = ?";
        
        try {
            $stmt = $pdo->prepare($sql);
            $stmt->execute([$valor]);
            $count = $stmt->fetchColumn();

            if ($count > 0) {
                $response['existe_duplicidade'] = true;
            }
        } catch (PDOException $e) {
            error_log("Erro de PDO ao verificar CPF: " . $e->getMessage());
        }

    } elseif ($coluna_db === 'telefone' && strlen($valor) === 11) {
        
        $sql = "SELECT COUNT(*) FROM cliente WHERE telefone = ?";
        
        try {
            $stmt = $pdo->prepare($sql);
            $stmt->execute([$valor]); 
            $count = $stmt->fetchColumn();

            if ($count > 0) {
                $response['existe_duplicidade'] = true;
            }
        } catch (PDOException $e) {
            error_log("Erro de PDO ao verificar Telefone: " . $e->getMessage());
        }
    }
}

echo json_encode($response);
exit;
?>