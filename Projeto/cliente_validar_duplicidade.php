<?php

header('Content-Type: application/json');

require_once 'conexao.php';

$response = ['existe_duplicidade' => false];

// Verifica se a variável de conexão PDO está definida
if (!isset($pdo) || !($pdo instanceof PDO)) {
    // Se a conexão não estiver definida, retorna um erro ou a duplicidade como false (depende da sua preferência)
    error_log("Erro: Variável \$pdo (PDO object) não está definida em clientes_validar_duplicidade.php");
    // $response['erro'] = 'Erro de conexão com o banco de dados.';
    // echo json_encode($response);
    // exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['campo'], $_POST['valor'])) {
    
    $campo = $_POST['campo'];
    // O campo 'celular' no front-end corresponde ao campo 'telefone' no banco.
    $coluna_db = ($campo === 'celular') ? 'telefone' : $campo; 

    // Limpa o valor para obter apenas números (tanto para CPF quanto para Celular/Telefone)
    $valor = preg_replace('/\D/', '', $_POST['valor']); 

    // O campo 'cpf' na sua tabela tem tamanho 14 (com máscara), mas estamos checando o valor limpo (11 dígitos)
    if ($coluna_db === 'cpf' && strlen($valor) === 11) {
        
        // Consulta para verificar a existência de CPF
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
            // Em caso de erro, você pode optar por não bloquear o cadastro
        }

    // O campo 'celular' do front-end é 'telefone' no banco, e esperamos 11 dígitos limpos
    } elseif ($coluna_db === 'telefone' && strlen($valor) === 11) {
        
        // Consulta para verificar a existência de Telefone
        $sql = "SELECT COUNT(*) FROM cliente WHERE telefone = ?";
        
        try {
            $stmt = $pdo->prepare($sql);
            // Executamos a consulta com o valor limpo
            $stmt->execute([$valor]); 
            $count = $stmt->fetchColumn();

            if ($count > 0) {
                $response['existe_duplicidade'] = true;
            }
        } catch (PDOException $e) {
            error_log("Erro de PDO ao verificar Telefone: " . $e->getMessage());
            // Em caso de erro, você pode optar por não bloquear o cadastro
        }
    }
}

echo json_encode($response);
exit;
?>