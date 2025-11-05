<?php
// Arquivo: pets_processar.php
// Objetivo: Receber e processar o formulário de cadastro de Pet, incluindo o upload de foto.

header('Content-Type: application/json');

require_once 'conexao.php'; 

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

// Diretório onde as fotos serão salvas
$UPLOAD_DIR = __DIR__ . '/uploads/fotos_pets/'; 

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['message'] = "Método de requisição inválido.";
    goto final_json;
}

try {
    // 1. Coleta e sanitiza dados
    $acao = $_POST['acao'] ?? ''; 
    $cliente_id = (int)($_POST['cliente_id'] ?? 0);
    $nome = trim($_POST['nome'] ?? '');
    $especie_id = (int)($_POST['especie_id'] ?? 0);
    $raca_id = (int)($_POST['raca_id'] ?? 0); // 0 se for opcional
    $data_nascimento = $_POST['data_nascimento'] ?? null;
    $peso = (float)($_POST['peso'] ?? 0);
    $castrado = isset($_POST['castrado']) ? 1 : 0;
    $vacinado = isset($_POST['vacinado']) ? 1 : 0;
    
    $nome_arquivo_foto = null; // Inicializa a variável da foto

    if ($acao !== 'cadastrar' || $cliente_id <= 0 || empty($nome) || $especie_id <= 0) {
        $response['message'] = "Dados obrigatórios faltando.";
        goto final_json;
    }

    // 2. Processa o Upload da Foto
    if (isset($_FILES['foto']) && $_FILES['foto']['error'] === UPLOAD_ERR_OK) {
        
        // Garante que o diretório exista
        if (!is_dir($UPLOAD_DIR)) {
            mkdir($UPLOAD_DIR, 0777, true);
        }

        $arquivo_temporario = $_FILES['foto']['tmp_name'];
        $nome_original = basename($_FILES['foto']['name']);
        
        // Cria um nome de arquivo único para evitar colisões
        $extensao = pathinfo($nome_original, PATHINFO_EXTENSION);
        $nome_arquivo_foto = uniqid('pet_') . '.' . $extensao;
        $caminho_destino = $UPLOAD_DIR . $nome_arquivo_foto;

        // Tenta mover o arquivo
        if (!move_uploaded_file($arquivo_temporario, $caminho_destino)) {
            // Se falhar o move, ainda assim cadastra o pet, mas sem a foto
            error_log("Falha ao mover arquivo de foto: " . $nome_original);
            $nome_arquivo_foto = null; 
        }
    }

    // 3. Insere o Pet no Banco de Dados
    $sql = "INSERT INTO pet 
            (cliente_id, nome, especie_id, raca_id, data_nascimento, peso, castrado, vacinado, foto, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
            
    // Ajusta o raca_id para NULL se for 0 (opcional no formulário)
    $raca_id_db = ($raca_id > 0) ? $raca_id : null;

    $stmt = $pdo->prepare($sql);
    
    $stmt->execute([
        $cliente_id, 
        $nome, 
        $especie_id, 
        $raca_id_db, 
        $data_nascimento ?: null, // Salva como NULL se vazio
        $peso, 
        $castrado, 
        $vacinado, 
        $nome_arquivo_foto // Salva o nome do arquivo gerado
    ]);

    $response['success'] = true;
    $response['message'] = "Pet **" . htmlspecialchars($nome) . "** cadastrado com sucesso! Redirecionando para o fichário do cliente...";

} catch (\PDOException $e) {
    // Trata erros do banco de dados
    error_log("Erro no cadastro de Pet: " . $e->getMessage());
    $response['message'] = "Erro de Banco de Dados: " . $e->getMessage();
    
} catch (\Exception $e) {
    // Trata outros erros, como falha de permissão no upload (raro, mas possível)
    $response['message'] = "Erro interno: " . $e->getMessage();
}


final_json:
echo json_encode($response);
exit;
?>